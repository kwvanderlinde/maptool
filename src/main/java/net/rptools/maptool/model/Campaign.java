/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.model;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.SafeObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.MD5Key;
import net.rptools.lib.net.Location;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.ToolbarPanel;
import net.rptools.maptool.client.ui.macrobuttons.panels.AbstractMacroPanel;
import net.rptools.maptool.client.ui.token.BarTokenOverlay;
import net.rptools.maptool.client.ui.token.BooleanTokenOverlay;
import net.rptools.maptool.model.library.token.LibraryTokenManager;
import net.rptools.maptool.model.sheet.stats.StatSheetProperties;
import net.rptools.maptool.model.zones.CampaignEvent;
import net.rptools.maptool.model.zones.TokenEdited;
import net.rptools.maptool.model.zones.TokensAdded;
import net.rptools.maptool.model.zones.TokensChanged;
import net.rptools.maptool.model.zones.TokensRemoved;
import net.rptools.maptool.server.proto.CampaignDto;

/**
 * This object contains {@link Zone}s and {@link Asset}s that make up a campaign as well as links to
 * a variety of other campaign characteristics (campaign macros, properties, lookup tables, and so
 * on).
 *
 * <p>Roughly this is equivalent to multiple tabs that will appear on the client and all of the
 * images that will appear on it (and also campaign macro buttons).
 */
public class Campaign implements Serializable {
  private @Nonnull GUID id;

  /** The {@link Zone}s that make up this {@code Campaign}. */
  private @Nonnull Map<GUID, Zone> zones;

  private @Nonnull String
      name; // the name of the campaign, to be displayed in the MapToolFrame title bar

  // Static data isn't written to the campaign file when saved; these two fields hold the output
  // location and type, and the
  // settings of all JToggleButton objects (JRadioButtons and JCheckBoxes).
  private @Nullable Location exportLocation;
  // the state of each checkbox/radiobutton for the Export>ScreenshotAs dialog
  private @Nonnull Map<String, Boolean> exportSettings;

  private @Nonnull CampaignProperties campaignProperties;

  // campaign macro button properties. these are saved along with the campaign.
  // as of 1.3b32
  private @Nonnull List<MacroButtonProperties> macroButtonProperties;
  // need to have a counter for additions to macroButtonProperties array
  // otherwise deletions/insertions from/to that array will go out of sync
  private int macroButtonLastIndex;
  private int gmMacroButtonLastIndex;

  // campaign GM macro button properties. these are saved along with the campaign.
  // as of 1.5.6
  private @Nonnull List<MacroButtonProperties> gmMacroButtonProperties;

  // DEPRECATED: As of 1.3b20 these are now in campaignProperties, but are here for backward
  // compatibility
  @Deprecated private Map<String, List<TokenProperty>> tokenTypeMap;

  @Deprecated private List<String> remoteRepositoryList;

  @Deprecated private Map<String, Map<GUID, LightSource>> lightSourcesMap;

  @Deprecated private Map<String, LookupTable> lookupTableMap;

  /**
   * This flag indicates whether the manual fog tools have been used in this campaign while a server
   * is not running. See {@link ToolbarPanel} for details.
   *
   * <p>Non-null outside of {@link #readResolve().
   *
   * <ul>
   *   <li>null - server never started for this campaign
   *   <li>false - server started and IndividualFog == off
   *   <li>true - server started and IndividualFog == on
   * </ul>
   */
  private @Nonnull Boolean hasUsedFogToolbar;

  /** When a player connects to a server, this will be the map they are sent to at first. */
  private @Nullable GUID landingMapId;

  private transient boolean isBeingSerialized;

  private transient AssetTracker assetTracker;

  private transient LibraryTokenManager libraryTokenManager;

  private transient Map<GUID, Disposable> zoneSubscriptions;

  private transient PublishSubject<CampaignEvent> onCampaignEvent;

  // Primary constructor
  private Campaign(
      @Nonnull GUID id,
      @Nonnull String name,
      @Nullable GUID landingMapId,
      boolean hasUsedFogToolbar,
      @Nonnull CampaignProperties campaignProperties,
      @Nullable Location exportLocation,
      @Nonnull Map<String, Boolean> exportSettings,
      int macroButtonLastIndex,
      int gmMacroButtonLastIndex,
      @Nonnull List<MacroButtonProperties> macroButtonProperties,
      @Nonnull List<MacroButtonProperties> gmMacroButtonProperties,
      @Nonnull List<Zone> zones) {
    this.assetTracker = new AssetTracker(this);
    this.libraryTokenManager = new LibraryTokenManager(/*this*/ );

    this.id = id;
    this.name = name;
    this.landingMapId = landingMapId;
    this.hasUsedFogToolbar = hasUsedFogToolbar;
    this.campaignProperties = campaignProperties;
    this.exportLocation = exportLocation;
    this.exportSettings = new HashMap<>();
    this.exportSettings.putAll(exportSettings);
    this.macroButtonLastIndex = macroButtonLastIndex;
    this.gmMacroButtonLastIndex = gmMacroButtonLastIndex;
    this.macroButtonProperties = macroButtonProperties;
    this.gmMacroButtonProperties = gmMacroButtonProperties;

    this.onCampaignEvent = PublishSubject.create();
    this.zoneSubscriptions = new HashMap<>();

    this.zones = Collections.synchronizedMap(new LinkedHashMap<>());

    /*
     * Don't forget that since these are new zones AND new tokens created here from the old one,
     * if you have any data that needs to transfer over you will need to manually copy it
     * as is done below for the campaign properties and macro buttons. Iteration over a synchronized
     *  map must lock the map.
     */
    for (var zone : zones) {
      putZone(zone);
    }
  }

  public Campaign() {
    this(
        new GUID(),
        "Default",
        null,
        false,
        new CampaignProperties(),
        null,
        Map.of(),
        0,
        0,
        List.of(),
        List.of(),
        List.of());
  }

  /**
   * Create a new campaign with an old campaign's properties.
   *
   * @param campaign The campaign to copy from.
   */
  public Campaign(Campaign campaign) {
    this(
        campaign.id,
        campaign.name,
        campaign.landingMapId,
        campaign.hasUsedFogToolbar,
        new CampaignProperties(campaign.campaignProperties),
        campaign.exportLocation,
        campaign.exportSettings,
        campaign.macroButtonLastIndex,
        campaign.gmMacroButtonLastIndex,
        new ArrayList<>(campaign.macroButtonProperties),
        new ArrayList<>(campaign.gmMacroButtonProperties),
        campaign.zones.values().stream().map(z -> new Zone(z, true)).toList());
  }

  private void onTokensAdded(TokensAdded event) {
    // Ensure tokens are recognized as libraries if needed.
    libraryTokenManager.addTokens(event.tokens());
  }

  private void onTokensRemoved(TokensRemoved event) {
    libraryTokenManager.removeTokens(event.tokens());
  }

  private void onTokensChanged(TokensChanged event) {
    libraryTokenManager.changeTokens(event.tokens());
  }

  private void onTokenEdited(TokenEdited event) {
    libraryTokenManager.changeTokens(Collections.singleton(event.token()));
  }

  // region Events

  public Observable<CampaignEvent> onCampaignEvent() {
    return onCampaignEvent;
  }

  // endregion

  public void setLandingMapId(@Nullable GUID zoneId) {
    // Doesn't really matter if it belongs to {@link #zones}, that can be checked at lookup time.
    this.landingMapId = zoneId;
  }

  public @Nullable GUID getLandingMapId() {
    return this.landingMapId;
  }

  @Serial
  private Object readResolve() {
    var props = Objects.requireNonNullElseGet(campaignProperties, CampaignProperties::new);
    // region Move deprecated fields into campaign properties.
    if (tokenTypeMap != null) {
      props.setTokenTypeMap(tokenTypeMap);
    }
    if (remoteRepositoryList != null) {
      props.setRemoteRepositoryList(remoteRepositoryList);
    }
    if (lightSourcesMap != null) {
      props.setLightSources(CategorizedLights.copyOf(lightSourcesMap));
    }
    if (lookupTableMap != null) {
      props.setLookupTableMap(lookupTableMap);
    }
    // endregion

    return new Campaign(
        id,
        name,
        landingMapId,
        hasUsedFogToolbar != null && hasUsedFogToolbar,
        props,
        exportLocation,
        Objects.requireNonNullElseGet(exportSettings, HashMap::new),
        macroButtonLastIndex,
        gmMacroButtonLastIndex,
        Objects.requireNonNullElseGet(macroButtonProperties, List::of),
        Objects.requireNonNullElseGet(gmMacroButtonProperties, List::of),
        new ArrayList<>(zones.values()));
  }

  public List<String> getRemoteRepositoryList() {
    return campaignProperties.getRemoteRepositoryList();
  }

  public GUID getId() {
    return id;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * This is a workaround to avoid the renderer and the serializer interating on the drawables at
   * the same time
   *
   * @return true if currently being serialized
   */
  public boolean isBeingSerialized() {
    return isBeingSerialized;
  }

  /**
   * This is a workaround to avoid the renderer and the serializer interating on the drawables at
   * the same time
   *
   * @param isBeingSerialized the new value of isBeingSerialized, should be true if the object is
   *     being serialized
   */
  public void setBeingSerialized(boolean isBeingSerialized) {
    this.isBeingSerialized = isBeingSerialized;
  }

  public List<String> getTokenTypes() {
    List<String> list = new ArrayList<String>(getTokenTypeMap().keySet());
    Collections.sort(list);
    return list;
  }

  /**
   * Returns the default Stat Sheet ID for the specified token property type.
   *
   * @param tokenProperty the token property type to get the sheet ID for.
   * @return the properties of the Stat Sheet.
   */
  public StatSheetProperties getTokenTypeDefaultSheetId(String tokenProperty) {
    return campaignProperties.getTokenTypeDefaultStatSheet(tokenProperty);
  }

  /**
   * Sets the default Stat Sheet ID for the specified token property type.
   *
   * @param tokenProperty the token property type to set the sheet ID of.
   * @param sheetId the Stat Sheet properties.
   */
  public void setTokenTypeDefaultSheetId(String tokenProperty, StatSheetProperties sheetId) {
    campaignProperties.setTokenTypeDefaultStatSheet(tokenProperty, sheetId);
  }

  /**
   * Sets the default property type for tokens.
   *
   * @param def the default property type.
   */
  public void setDefaultTokenPropertyType(String def) {
    campaignProperties.setDefaultTokenPropertyType(def);
  }

  public Sights getSightTypes() {
    return campaignProperties.getSightTypes();
  }

  public void setSightTypes(Sights sights) {
    campaignProperties.setSightTypes(sights);
  }

  public List<TokenProperty> getTokenPropertyList(String tokenType) {
    return getTokenTypeMap().containsKey(tokenType)
        ? getTokenTypeMap().get(tokenType)
        : new ArrayList<TokenProperty>();
  }

  public void putTokenType(String name, List<TokenProperty> propertyList) {
    getTokenTypeMap().put(name, propertyList);
  }

  /**
   * Stub that calls <code>campaignProperties.getTokenTypeMap()</code>.
   *
   * @return the {@link Map} of token types
   */
  public Map<String, List<TokenProperty>> getTokenTypeMap() {
    return campaignProperties.getTokenTypeMap();
  }

  /**
   * Convenience method that calls {@link #getSightTypes()} and returns the value for the key <code>
   * type</code>.
   *
   * @param type the String corresponding to the SightType.
   * @return the SightType.
   */
  public SightType getSightType(String type) {
    if (type == null) {
      type = campaignProperties.getDefaultSightType();
    }

    return getSightTypes().get(type).orElse(null);
  }

  /**
   * Stub that calls <code>campaignProperties.getLookupTableMap()</code>.
   *
   * @return the {@link Map} of {@link LookupTable}s types
   */
  public Map<String, LookupTable> getLookupTableMap() {
    return campaignProperties.getLookupTableMap();
  }

  /**
   * Stub that calls <code>campaignProperties.getLightSourcesMap()</code>.
   *
   * @return the {@link Map} of between lightSourceIds and {@link LightSource}s
   */
  public CategorizedLights getLightSources() {
    return campaignProperties.getLightSources();
  }

  public void setLightSources(CategorizedLights map) {
    campaignProperties.setLightSources(map);
  }

  /**
   * Stub that calls <code>campaignProperties.getHaloSourcesMap()</code>.
   *
   * @return the {@link Map} of between haloSourceIds and {@link Halo}s
   */
  public CategorizedHalos getCategorizedHalos() {
    return campaignProperties.getCategorizedHalos();
  }

  public void setCategorizedHalos(CategorizedHalos map) {
    campaignProperties.setCategorizedHalos(map);
  }

  /**
   * Stub that calls <code>campaignProperties.getTokenStatesMap()</code>.
   *
   * @return the {@link Map} for token states
   */
  public Map<String, BooleanTokenOverlay> getTokenStatesMap() {
    return campaignProperties.getTokenStatesMap();
  }

  /**
   * Stub that calls <code>campaignProperties.getTokenBarsMap()</code>.
   *
   * @return the {@link Map} for token bars
   */
  public Map<String, BarTokenOverlay> getTokenBarsMap() {
    return campaignProperties.getTokenBarsMap();
  }

  public void setId(GUID id) {
    this.id = id;
  }

  /**
   * Returns an <code>ArrayList</code> of all available <code>Zone</code>s from the <code>zones
   * </code> <code>LinkedHashMap</code>.
   *
   * @return a list of zones
   */
  public List<Zone> getZones() {
    synchronized (zones) { // Must lock synchronized map while iterating over contents.
      return new ArrayList<Zone>(zones.values());
    }
  }

  /**
   * Return the <code>Zone</code> with the given GUID.
   *
   * @param id the id to look for
   * @return the Zone for the id, or {@code null} if there is no such zone.
   */
  public @Nullable Zone getZone(GUID id) {
    return zones.get(id);
  }

  /**
   * Create an entry for the given <code>Zone</code> in the map, using <code>zone</code>'s {@link
   * Zone#getId()} method.
   *
   * @param zone the zone to put into zones.
   */
  public void putZone(Zone zone) {
    zones.put(zone.getId(), zone);

    var existing = zoneSubscriptions.remove(zone.getId());
    if (existing != null) {
      existing.dispose();
    }

    zoneSubscriptions.put(
        zone.getId(), zone.onZoneEvent().subscribeWith(new SafeObserver<>(this.onCampaignEvent)));

    // Register the zone's library tokens that already exist.
    onTokensAdded(new TokensAdded(zone, zone.getAllTokens()));
  }

  public void removeAllZones() {
    zones.clear();
    zoneSubscriptions.values().forEach(Disposable::dispose);
  }

  /**
   * Remove a zone from zones.
   *
   * @param id the GUID of the zone.
   */
  public void removeZone(GUID id) {
    zones.remove(id);

    var existing = zoneSubscriptions.remove(id);
    if (existing != null) {
      existing.dispose();
    }
  }

  public AssetTracker getAssetTracker() {
    return assetTracker;
  }

  public LibraryTokenManager getLibraryTokenManager() {
    return libraryTokenManager;
  }

  public boolean containsAsset(MD5Key key) {
    // TODO What about lookup tables, states, bars, and other non-zone assets?

    Collection<Zone> zonesToCheck;
    synchronized (zones) { // iteration over synchronized map must lock the map
      zonesToCheck = zones.values();
    }

    for (Zone zone : zonesToCheck) {
      Set<MD5Key> assetSet = zone.getAllAssetIds();
      if (assetSet.contains(key)) {
        return true;
      }
    }

    if (getCampaignProperties().getAllImageAssets().contains(key)) {
      return true;
    }

    return false;
  }

  /**
   * Whether a server has been started using this campaign and, if so, whether the IndividualFog
   * feature was turned on at the time. This method returns <code>true</code> IFF a server has been
   * started with the IF feature turned on.
   *
   * @return <code>true</code> if IF feature has ever been used; <code>false</code> otherwise
   */
  public boolean hasUsedFogToolbar() {
    return hasUsedFogToolbar;
  }

  public void setHasUsedFogToolbar(boolean b) {
    hasUsedFogToolbar = b;
  }

  public void mergeCampaignProperties(CampaignProperties properties) {
    properties.mergeInto(campaignProperties);
  }

  public void replaceCampaignProperties(CampaignProperties properties) {
    campaignProperties = new CampaignProperties(properties);
  }

  /**
   * Get a copy of the properties. This is for persistence. Modification of the properties do not
   * affect this campaign
   *
   * @return a copy of the properties
   */
  public CampaignProperties getCampaignProperties() {
    return new CampaignProperties(campaignProperties);
  }

  /**
   * Getter for the array of Campaign macros
   *
   * @return the Campaign macros
   */
  public List<MacroButtonProperties> getMacroButtonPropertiesArray() {
    return macroButtonProperties;
  }

  /**
   * Setter for the list of Campaign macros
   *
   * @param properties the List of Campaign Macros
   */
  public void setMacroButtonPropertiesArray(List<MacroButtonProperties> properties) {
    macroButtonProperties = properties;
  }

  /**
   * Getter for the array of GM macros
   *
   * @return the GM macros
   */
  public List<MacroButtonProperties> getGmMacroButtonPropertiesArray() {
    return gmMacroButtonProperties;
  }

  /**
   * Setter for the list of GM macros
   *
   * @param properties the List of GM Macros
   */
  public void setGmMacroButtonPropertiesArray(List<MacroButtonProperties> properties) {
    gmMacroButtonProperties = properties;
  }

  /**
   * Adds multiple MacroButtonProperties to the GM or Campaign macro panel, starting at the next
   * appropriate index.
   *
   * @param toSave the list of button properties to add
   * @param gmPanel true for the GM panel, false for the Campaign panel.
   */
  public void addMacroButtonPropertiesAtNextIndex(
      List<MacroButtonProperties> toSave, boolean gmPanel) {
    List<MacroButtonProperties> macroButtonList =
        gmPanel ? gmMacroButtonProperties : macroButtonProperties;
    int lastIndex = gmPanel ? gmMacroButtonLastIndex : macroButtonLastIndex;
    // populate the lookup table and confirm lastIndex
    for (MacroButtonProperties prop : macroButtonList) {
      int curIndex = prop.getIndex();
      if (curIndex > lastIndex) {
        lastIndex = curIndex;
      }
    }
    // set the indexes and add to list
    for (MacroButtonProperties newProp : toSave) {
      newProp.setIndex(++lastIndex);
    }
    macroButtonList.addAll(toSave);

    // update the ButtonLastIndex prop as appropriate
    if (gmPanel) {
      gmMacroButtonLastIndex = lastIndex;
    } else {
      macroButtonLastIndex = lastIndex;
    }

    AbstractMacroPanel macroPanel =
        gmPanel ? MapTool.getFrame().getGmPanel() : MapTool.getFrame().getCampaignPanel();
    macroPanel.reset();
  }

  /**
   * Saves a button in the Campaign or GM panel.
   *
   * @param properties the properties of the button to save
   * @param gmPanel whether the button is in the GM panel.
   */
  public void saveMacroButtonProperty(MacroButtonProperties properties, boolean gmPanel) {
    List<MacroButtonProperties> macroButtonList =
        gmPanel ? gmMacroButtonProperties : macroButtonProperties;
    AbstractMacroPanel macroPanel =
        gmPanel ? MapTool.getFrame().getGmPanel() : MapTool.getFrame().getCampaignPanel();

    for (MacroButtonProperties prop : macroButtonList) {
      if (prop.getIndex() == properties.getIndex()) {
        prop.setColorKey(properties.getColorKey());
        prop.setAutoExecute(properties.getAutoExecute());
        prop.setCommand(properties.getCommand());
        prop.setHotKey(properties.getHotKey());
        prop.setIncludeLabel(properties.getIncludeLabel());
        prop.setApplyToTokens(properties.getApplyToTokens());
        prop.setLabel(properties.getLabel());
        prop.setGroup(properties.getGroup());
        prop.setSortby(properties.getSortby());
        prop.setFontColorKey(properties.getFontColorKey());
        prop.setFontSize(properties.getFontSize());
        prop.setMinWidth(properties.getMinWidth());
        prop.setMaxWidth(properties.getMaxWidth());
        prop.setToolTip(properties.getToolTip());
        prop.setAllowPlayerEdits(properties.getAllowPlayerEdits());
        prop.setDisplayHotKey(properties.getDisplayHotKey());
        prop.setCompareIncludeLabel(properties.getCompareIncludeLabel());
        prop.setCompareAutoExecute(properties.getCompareAutoExecute());
        prop.setCompareApplyToSelectedTokens(properties.getCompareApplyToSelectedTokens());
        prop.setCompareGroup(properties.getCompareGroup());
        prop.setCompareSortPrefix(properties.getCompareSortPrefix());
        prop.setCompareCommand(properties.getCompareCommand());

        macroPanel.reset();
        return;
      }
    }
    macroButtonList.add(properties);
    macroPanel.reset();
  }

  public int getMacroButtonNextIndex() {
    for (MacroButtonProperties prop : macroButtonProperties) {
      if (prop.getIndex() > macroButtonLastIndex) {
        macroButtonLastIndex = prop.getIndex();
      }
    }
    return ++macroButtonLastIndex;
  }

  public int getGmMacroButtonNextIndex() {
    for (MacroButtonProperties prop : gmMacroButtonProperties) {
      if (prop.getIndex() > gmMacroButtonLastIndex) {
        gmMacroButtonLastIndex = prop.getIndex();
      }
    }
    return ++gmMacroButtonLastIndex;
  }

  public void deleteMacroButton(MacroButtonProperties properties) {
    macroButtonProperties.remove(properties);
    MapTool.getFrame().getCampaignPanel().reset();
  }

  public void deleteGmMacroButton(MacroButtonProperties properties) {
    gmMacroButtonProperties.remove(properties);
    MapTool.getFrame().getGmPanel().reset();
  }

  /**
   * This method iterates through all Zones, TokenStates, TokenBars, and LookupTables and writes the
   * keys into a new, empty set. That set is the return value.
   *
   * @return a set of MD5 keys
   */
  public Set<MD5Key> getAllAssetIds() {

    // Maps (tokens are implicit)
    Set<MD5Key> assetSet = new HashSet<>();
    for (Zone zone : getZones()) {
      assetSet.addAll(zone.getAllAssetIds());
    }

    assetSet.addAll(getCampaignProperties().getAllImageAssets());

    return assetSet;
  }

  /**
   * @return Getter for initiativeOwnerPermissions
   */
  public boolean isInitiativeOwnerPermissions() {
    return campaignProperties.isInitiativeOwnerPermissions();
  }

  /**
   * @param initiativeOwnerPermissions Setter for initiativeOwnerPermissions
   */
  public void setInitiativeOwnerPermissions(boolean initiativeOwnerPermissions) {
    campaignProperties.setInitiativeOwnerPermissions(initiativeOwnerPermissions);
  }

  /**
   * @return Getter for initiativeMovementLock
   */
  public boolean isInitiativeMovementLock() {
    return campaignProperties.isInitiativeMovementLock();
  }

  /**
   * @param initiativeMovementLock Setter for initiativeMovementLock
   */
  public void setInitiativeMovementLock(boolean initiativeMovementLock) {
    campaignProperties.setInitiativeMovementLock(initiativeMovementLock);
  }

  public boolean isInitiativeUseReverseSort() {
    return campaignProperties.isInitiativeUseReverseSort();
  }

  public void setInitiativeUseReverseSort(boolean initiativeUseReverseSort) {
    campaignProperties.setInitiativeUseReverseSort(initiativeUseReverseSort);
  }

  public boolean isInitiativePanelButtonsDisabled() {
    return campaignProperties.isInitiativePanelButtonsDisabled();
  }

  public void setInitiativePanelButtonsDisabled(boolean disabled) {
    campaignProperties.setInitiativePanelButtonsDisabled(disabled);
  }

  /**
   * @return Getter for characterSheets
   */
  public Map<String, String> getCharacterSheets() {
    return getCampaignProperties().getCharacterSheets();
  }

  public @Nullable Location getExportLocation() {
    return exportLocation;
  }

  public void setExportLocation(@Nullable Location exportLocation) {
    this.exportLocation = exportLocation;
  }

  public Map<String, Boolean> getExportSettings() {
    return Collections.unmodifiableMap(exportSettings);
  }

  public void setExportSettings(Map<String, Boolean> exportSettings) {
    this.exportSettings.clear();
    this.exportSettings.putAll(exportSettings);
  }

  public void initDefault() {
    campaignProperties.initDefaultProperties();
  }

  public static Campaign fromDto(CampaignDto dto) {
    return new Campaign(
        GUID.valueOf(dto.getId()),
        dto.getName(),
        dto.hasLandingMapId() ? GUID.valueOf(dto.getLandingMapId()) : null,
        dto.getHasUsedFogToolbar(),
        CampaignProperties.fromDto(dto.getProperties()),
        dto.hasExportLocation() ? Location.fromDto(dto.getExportLocation()) : null,
        dto.getExportSettingsMap(),
        dto.getMacroButtonLastIndex(),
        dto.getGmMacroButtonLastIndex(),
        dto.getMacroButtonPropertiesList().stream()
            .map(MacroButtonProperties::fromDto)
            .collect(Collectors.toList()),
        dto.getGmMacroButtonPropertiesList().stream()
            .map(MacroButtonProperties::fromDto)
            .collect(Collectors.toList()),
        dto.getZonesList().stream().map(Zone::fromDto).toList());
  }

  public CampaignDto toDto() {
    var dto = CampaignDto.newBuilder();
    dto.setId(id.toString());
    dto.setName(name);
    if (landingMapId != null) {
      dto.setLandingMapId(landingMapId.toString());
    }
    dto.setHasUsedFogToolbar(hasUsedFogToolbar);
    dto.setProperties(campaignProperties.toDto());
    if (exportLocation != null) {
      dto.setExportLocation(exportLocation.toDto());
    }
    dto.putAllExportSettings(exportSettings);
    dto.setMacroButtonLastIndex(macroButtonLastIndex);
    dto.setGmMacroButtonLastIndex(gmMacroButtonLastIndex);
    dto.addAllMacroButtonProperties(
        macroButtonProperties.stream()
            .map(MacroButtonProperties::toDto)
            .collect(Collectors.toList()));
    dto.addAllZones(zones.values().stream().map(Zone::toDto).collect(Collectors.toList()));
    dto.addAllGmMacroButtonProperties(
        gmMacroButtonProperties.stream()
            .map(MacroButtonProperties::toDto)
            .collect(Collectors.toList()));
    return dto.build();
  }

  /**
   * Renames the token types on existing tokens in the campaign.
   *
   * @param oldName the token type to rename from.
   * @param newName the token type to rename to.
   */
  public void renameTokenTypes(@Nonnull String oldName, @Nonnull String newName) {
    for (Zone zone : getZones()) {
      zone.getAllTokens()
          .forEach(
              t -> {
                if (oldName.equals(t.getPropertyType())) {
                  t.setPropertyType(newName);
                  zone.putToken(t);
                }
              });
    }
  }
}
