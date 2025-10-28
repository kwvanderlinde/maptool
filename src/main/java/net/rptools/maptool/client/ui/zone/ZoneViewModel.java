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
package net.rptools.maptool.client.ui.zone;

import com.google.common.eventbus.Subscribe;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.rptools.lib.CollectionUtil;
import net.rptools.lib.MD5Key;
import net.rptools.lib.StringUtil;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.events.RepaintZoneRequested;
import net.rptools.maptool.client.events.ZoneLoaded;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.ui.zone.renderer.LabelLocation;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.AttachedLightSource;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Label;
import net.rptools.maptool.model.LightSource;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.util.GraphicsUtil;
import net.rptools.maptool.util.ImageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data used for rendering.
 *
 * <p>This is deliberately not much encapsulated.
 */
public class ZoneViewModel {
  private static final Logger log = LoggerFactory.getLogger(ZoneViewModel.class);

  /**
   * Represents the selectable bounds of a token.
   *
   * <p>Obsoletes various other TokenPosition types that are either screen-based or inconsistent.
   *
   * @param token The token that this record was created for.
   * @param footprintBounds The bounds of the token, not accounting for rotation, e.g., as returned
   *     by {@link Token#getFootprintBounds(Zone)}
   * @param transformedBounds For top-down tokens, the rotated version of {@code footprintBounds}
   *     that accounts for the token's facing. This shape is rotated around the token's anchor
   *     point.
   */
  public record TokenPosition(Token token, Rectangle2D footprintBounds, Area transformedBounds) {
    public static TokenPosition fromToken(Token token, Zone zone) {
      Rectangle2D footprintBounds = token.getFootprintBounds(zone);

      final Area transformedBounds = new Area(footprintBounds);
      if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
        // facing defaults to down or -90 degrees
        double centerX = footprintBounds.getCenterX() - token.getAnchorX();
        double centerY = footprintBounds.getCenterY() - token.getAnchorY();
        var at =
            AffineTransform.getRotateInstance(
                Math.toRadians(token.getFacingInDegrees()), centerX, centerY);

        transformedBounds.transform(at);
      }

      return new TokenPosition(token, footprintBounds, transformedBounds);
    }
  }

  public enum DebugCategory {
    Drag,
    Labels,
    Tokens,
  }

  public enum DebugType {
    DragMouseStart(DebugCategory.Drag, Color.blue),
    DragMouseCurrent(DebugCategory.Drag, Color.cyan),
    DragAnchorStart(DebugCategory.Drag, Color.red),
    DragAnchorCurrent(DebugCategory.Drag, Color.magenta),
    LabelBounds(DebugCategory.Labels, Color.yellow),
    TokenClips(DebugCategory.Tokens, Color.orange.darker());

    public final DebugCategory category;
    public final Color color;

    DebugType(DebugCategory category, Color color) {
      this.category = category;
      this.color = color;
    }
  }

  private final Campaign campaign;
  public final Zone zone;

  // region These are updated externally.

  private @Nonnull Zone.Layer activeLayer = Zone.Layer.getDefaultPlayerLayer();
  private Scale zoneScale = new Scale();
  private final ZoneView zoneView;
  private @Nullable GUID tokenUnderMouse = null;
  private final SelectionModel selectionModel;
  private final List<GUID> tokensWithPathsShowing = new ArrayList<>();
  private final List<GUID> highlightCommonMacros = new ArrayList<>();
  private final Map<DebugType, Shape> debugShapes = new EnumMap<>(DebugType.class);

  // endregion

  // region These are updated at the start of each render via `update()`.

  private boolean isUsingGdxRenderer = false;

  /** The status message describing how loaded the zone is, or {@code null} if fully loaded. */
  private @Nullable String loadingProgress = "";

  private PlayerView playerView = new PlayerView(Player.Role.PLAYER);
  private final Rectangle2D viewport = new Rectangle2D.Double();
  private Area visibleArea = new Area();

  private final Set<GUID> movingTokens = new HashSet<>();

  private final Map<GUID, TokenPosition> tokenPositions = new HashMap<>();
  private final Set<GUID> onScreenTokens = new HashSet<>();
  private final Map<Zone.Layer, List<TokenPosition>> tokenPositionsByLayer =
      CollectionUtil.newFilledEnumMap(Zone.Layer.class, l -> new LinkedList<>());

  private final List<TokenPosition> markerList = new ArrayList<>();
  private final Map<GUID, Set<Token>> tokenStackMap = new HashMap<>();

  private final Map<Zone.Layer, Set<GUID>> visibleTokensByLayer =
      CollectionUtil.newFilledEnumMap(Zone.Layer.class, layer -> new HashSet<>());

  private final List<Point2D> lightPositions = new ArrayList<>();

  private final List<LabelLocation> labelLocations = new ArrayList<>();

  // endregion

  public ZoneViewModel(Campaign campaign, Zone zone, ZoneView zoneView) {
    this.campaign = campaign;
    this.zone = zone;
    this.zoneView = zoneView;
    this.selectionModel = new SelectionModel(zone);

    new MapToolEventBus()
        .getMainEventBus()
        .register(
            new Object() {
              @Subscribe
              private void onSelectionChanged(SelectionModel.SelectionChanged event) {
                if (event.zone() != zone) {
                  return;
                }
                tokensWithPathsShowing.clear();
                repaintNeeded();
              }
            });
  }

  public void repaintNeeded() {
    new MapToolEventBus().getMainEventBus().post(new RepaintZoneRequested(zone));
  }

  /** Marks the zone as not loaded, so that it ensures once again that all assets are loaded. */
  public void flush() {
    loadingProgress = "";
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public SelectionModel getSelectionModel() {
    return selectionModel;
  }

  public boolean isUsingGdxRenderer() {
    return isUsingGdxRenderer;
  }

  public void clearDebugCategory(DebugCategory category) {
    debugShapes.entrySet().removeIf(entry -> entry.getKey().category.equals(category));
  }

  public void setDebugShape(DebugType type, Shape shape) {
    debugShapes.put(type, shape);
  }

  public Map<DebugType, Shape> getDebugShapes() {
    return Collections.unmodifiableMap(debugShapes);
  }

  /**
   * Gets a string describing how much of the zone is loaded.
   *
   * <p>If the zone is fully loaded, the result will be empty.
   *
   * @return An optional containing the loading status, or the empty optional if the zone is loaded.
   */
  public Optional<String> getLoadingStatus() {
    return Optional.ofNullable(loadingProgress);
  }

  public Scale getZoneScale() {
    return zoneScale;
  }

  public void setZoneScale(Scale scale) {
    if (!this.zoneScale.equals(scale)) {
      this.zoneScale = scale;
      MapTool.getFrame().getZoomStatusBar().update();
      repaintNeeded();
      // TODO Should we be calling renderer.maybeForcePlayersView() here?
    }
  }

  public Rectangle2D getViewport() {
    return new Rectangle2D.Double(
        viewport.getMinX(), viewport.getMinY(), viewport.getWidth(), viewport.getHeight());
  }

  public Area getVisibleArea() {
    return visibleArea;
  }

  public PlayerView getPlayerView() {
    return playerView;
  }

  /**
   * The returned {@link PlayerView} contains a list of tokens that includes either all selected
   * tokens that this player owns and that have their <code>HasSight</code> checkbox enabled, or all
   * owned tokens that have <code>HasSight</code> enabled.
   *
   * @param role the player role
   * @param selected whether to get the view of selected tokens, or all owned
   * @return the player view
   */
  public PlayerView makePlayerView(Player.Role role, boolean selected) {
    List<Token> selectedTokens = null;
    if (selected && selectionModel.isAnyTokenSelected()) {
      selectedTokens = new ArrayList<>(getSelectedTokenList());
      selectedTokens.removeIf(token -> !token.getHasSight() || !AppUtil.playerOwns(token));
    }
    if (selectedTokens == null || selectedTokens.isEmpty()) {
      // if no selected token qualifying for view, use owned tokens or player tokens with sight
      final boolean checkOwnership =
          MapTool.getServerPolicy().isUseIndividualViews() || MapTool.isPersonalServer();
      selectedTokens =
          checkOwnership
              ? zone.getOwnedTokensWithSight(MapTool.getPlayer())
              : zone.getPlayerTokensWithSight();
    }
    if (selectedTokens == null || selectedTokens.isEmpty()) {
      return new PlayerView(role);
    }

    return new PlayerView(role, selectedTokens);
  }

  public Map<GUID, TokenPosition> getTokenPositions() {
    return Collections.unmodifiableMap(tokenPositions);
  }

  public Set<GUID> getOnScreenTokens() {
    return Collections.unmodifiableSet(onScreenTokens);
  }

  public List<TokenPosition> getTokenPositionsForLayer(Zone.Layer layer) {
    return Collections.unmodifiableList(
        tokenPositionsByLayer.getOrDefault(layer, Collections.emptyList()));
  }

  public List<TokenPosition> getMarkerPositions() {
    return Collections.unmodifiableList(markerList);
  }

  public Map<GUID, Set<Token>> getTokenStackMap() {
    return Collections.unmodifiableMap(tokenStackMap);
  }

  public @Nonnull Zone.Layer getActiveLayer() {
    return activeLayer;
  }

  public void setActiveLayer(@Nonnull Zone.Layer layer) {
    activeLayer = Objects.requireNonNullElse(layer, Zone.Layer.getDefaultPlayerLayer());
    selectionModel.replaceSelection(Collections.emptyList());
    repaintNeeded();
  }

  // TODO Make Optional<GUID>
  public @Nullable GUID getTokenUnderMouse() {
    return tokenUnderMouse;
  }

  public void setTokenUnderMouse(GUID id) {
    if (Objects.equals(tokenUnderMouse, id)) {
      return;
    }

    tokenUnderMouse = id;
    repaintNeeded();
  }

  public List<Token> getSelectedTokenList() {
    var tokens = new ArrayList<Token>();
    for (GUID g : selectionModel.getSelectedTokenIds()) {
      final var token = zone.getToken(g);
      if (token != null) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  public boolean isPathShowing(GUID tokenId) {
    return tokensWithPathsShowing.contains(tokenId);
  }

  public void showPath(GUID tokenId) {
    tokensWithPathsShowing.add(tokenId);
  }

  public void hidePath(GUID tokenId) {
    tokensWithPathsShowing.remove(tokenId);
  }

  public void toggleShowPath(GUID tokenId) {
    if (tokensWithPathsShowing.contains(tokenId)) {
      tokensWithPathsShowing.remove(tokenId);
    } else {
      tokensWithPathsShowing.add(tokenId);
    }
  }

  public Set<GUID> getVisibleTokens(Zone.Layer layer) {
    return Collections.unmodifiableSet(visibleTokensByLayer.get(layer));
  }

  public boolean isTokenMoving(GUID tokenId) {
    return movingTokens.contains(tokenId);
  }

  public List<GUID> getHighlightCommonMacros() {
    return Collections.unmodifiableList(highlightCommonMacros);
  }

  public void setHighlightCommonMacros(List<Token> affectedTokens) {
    highlightCommonMacros.clear();
    affectedTokens.stream().map(Token::getId).forEach(highlightCommonMacros::add);
  }

  public List<Point2D> getLightPositions() {
    return Collections.unmodifiableList(lightPositions);
  }

  public List<LabelLocation> getLabelLocations() {
    return Collections.unmodifiableList(labelLocations);
  }

  /**
   * Look up the label location for given label ID.
   *
   * @param id The ID of the label to look up.
   * @return The label location for the label with ID {@code id}.
   */
  public Optional<LabelLocation> getLabelLocation(@Nonnull GUID id) {
    // TODO Why do we not reverse the list like in getLabelLocationAt()?
    for (LabelLocation location : labelLocations) {
      if (id.equals(location.label().getId())) {
        return Optional.of(location);
      }
    }
    return Optional.empty();
  }

  /**
   * Look up the label location for a given screen point.
   *
   * @param x The screen location x
   * @param y The screen location y
   * @return The label location for the topmost label at (x, y).
   */
  public Optional<LabelLocation> getLabelLocationAt(double x, double y) {
    for (LabelLocation location : labelLocations.reversed()) {
      if (location.bounds().contains(x, y)) {
        return Optional.of(location);
      }
    }
    return Optional.empty();
  }

  public void update() {
    updateIsUsingGdxRenderer();
    updateIsLoading();
    updateViewport();
    updatePlayerView();
    updateVisibleArea();
    updateMovingTokens();
    updateTokenPositions();
    updateMarkerPositions();
    updateTokenStacks();
    updateVisibleTokens();
    updateLightPosition();
    updateLabelPositions();
  }

  // What follows are "systems".

  /** Updates {@link #isUsingGdxRenderer}. */
  private void updateIsUsingGdxRenderer() {
    isUsingGdxRenderer = MapTool.getFrame().getGdxPanel().isVisible();
  }

  /**
   * If the zone is not already loaded, updates the {@link #loadingProgress} and emits {@link
   * ZoneLoaded} if it becomes loaded.
   */
  private void updateIsLoading() {
    if (loadingProgress == null) {
      // We're done, until the cache is cleared
      return;
    }

    ImageObserver observer =
        Objects.requireNonNullElseGet(
            MapTool.getFrame().getZoneRenderer(this.zone),
            () -> (ImageObserver) (img, infoflags, x, y, width, height) -> false);

    // Get a list of all the assets in the zone
    Set<MD5Key> assetSet = zone.getAllAssetIds();
    assetSet.remove(null); // remove bad data

    // Make sure they are loaded
    int downloadCount = 0;
    int cacheCount = 0;
    boolean loaded = true;
    for (MD5Key id : assetSet) {
      // Have we gotten the actual data yet ?
      Asset asset = AssetManager.getAsset(id);
      if (asset == null) {
        AssetManager.getAssetAsynchronously(id);
        loaded = false;
        continue;
      }
      downloadCount++;

      // Have we loaded the image into memory yet ?
      Image image = ImageManager.getImage(asset.getMD5Key(), observer);
      if (image == null || image == ImageManager.TRANSFERING_IMAGE) {
        loaded = false;
        continue;
      }
      cacheCount++;
    }

    if (loaded) {
      // Indicate that loading is finished.
      loadingProgress = null;

      // Notify the token tree that it should update
      MapTool.getFrame().updateTokenTree();
      new MapToolEventBus().getMainEventBus().post(new ZoneLoaded(zone));
    } else {
      loadingProgress =
          String.format(
              " Loading Map '%s' - %d/%d Loaded %d/%d Cached",
              zone.getDisplayName(), downloadCount, assetSet.size(), cacheCount, assetSet.size());
    }
  }

  /** Updates {@link #viewport} based on {@link #zoneScale}. */
  private void updateViewport() {
    var renderer = MapTool.getFrame().getZoneRenderer(this.zone);
    if (renderer == null) {
      // No viewport.
      viewport.setFrame(0, 0, 0, 0);
      return;
    }

    var screenBounds = new Rectangle2D.Double(0, 0, renderer.getWidth(), renderer.getHeight());
    viewport.setFrame(zoneScale.toWorldSpace(screenBounds));
  }

  /** Updates {@link #visibleArea} based on {@link #playerView}. */
  private void updateVisibleArea() {
    visibleArea = zoneView.getVisibility(playerView).visibleArea();
  }

  /** Updates {@link #playerView}. */
  private void updatePlayerView() {
    playerView = makePlayerView(MapTool.getPlayer().getEffectiveRole(), true);
  }

  /** Clears and populates {@link #tokenPositions} and {@link #tokenPositionsByLayer}. */
  private void updateTokenPositions() {
    tokenPositions.clear();

    for (var layer : Zone.Layer.values()) {
      var layerList = tokenPositionsByLayer.get(layer);
      layerList.clear();

      // Note the order: the top most token is at the end of the list.
      var tokens = zone.getTokensOnLayer(layer);
      for (var token : tokens) {
        if ((!token.isVisible() || !token.getLayer().isVisibleToPlayers())
            && !this.playerView.isGMView()) {
          continue;
        }
        if (token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token)) {
          continue;
        }

        var tokenPosition = TokenPosition.fromToken(token, zone);

        tokenPositions.put(token.getId(), tokenPosition);
        layerList.add(tokenPosition);
      }
    }
  }

  /** Updates {@link #markerList} based on {@link #tokenPositionsByLayer}. */
  private void updateMarkerPositions() {
    markerList.clear();

    for (var list : tokenPositionsByLayer.values()) {
      for (var tokenPosition : list) {
        var token = tokenPosition.token();
        var playerCanSeeMarker =
            MapTool.getPlayer().isGM() || !StringUtil.isEmpty(token.getNotes());
        if (tokenPosition.token().isMarker() && playerCanSeeMarker) {
          markerList.add(tokenPosition);
        }
      }
    }
  }

  /** Updates {@link #tokenStackMap} based on {@link #tokenPositionsByLayer}. */
  private void updateTokenStacks() {
    tokenStackMap.clear();
    var tokenPositions = tokenPositionsByLayer.get(Zone.Layer.TOKEN);
    for (var tokenPosition : tokenPositions) {
      var token = tokenPosition.token();
      Set<Token> tokenStackSet = new HashSet<>();

      for (var otherPosition : tokenPositions) {
        if (tokenPosition.token().getId().equals(otherPosition.token().getId())) {
          // Don't self-stack.
          continue;
        }

        // Are we covering anyone ?
        if (!tokenPosition.footprintBounds().contains(otherPosition.footprintBounds())) {
          continue;
        }

        tokenStackSet.add(otherPosition.token());

        var reverse = tokenStackMap.get(otherPosition.token());
        if (reverse != null) {
          // The other token also covers tokens, so incorporate them into this one.
          tokenStackSet.addAll(reverse);
          tokenStackMap.remove(otherPosition.token());
        }
      }

      if (!tokenStackSet.isEmpty()) {
        tokenStackSet.add(token);
        tokenStackMap.put(token.getId(), tokenStackSet);
      }
    }
  }

  /**
   * Updates {@link #onScreenTokens} and {@link #visibleTokensByLayer} based on {@link #zoneScale},
   * {@link #tokenPositionsByLayer}, {@link #viewport}, {@link #playerView}, and {@link
   * #visibleArea}.
   */
  private void updateVisibleTokens() {
    double scale = zoneScale.getScale();

    onScreenTokens.clear();

    for (var layer : Zone.Layer.values()) {
      var tokenPositions = tokenPositionsByLayer.get(layer);

      var visibleTokens = visibleTokensByLayer.get(layer);
      visibleTokens.clear();
      for (var tokenPosition : tokenPositions) {
        // First make sure it is on screen.
        if (!tokenPosition.transformedBounds().intersects(viewport)) {
          continue;
        }

        onScreenTokens.add(tokenPosition.token().getId());

        // Then make sure it is in revealed area (for players).
        if (!playerView.isGMView()
            && layer.supportsVision()
            && zoneView.isUsingVision()
            && !GraphicsUtil.intersects(tokenPosition.transformedBounds(), visibleArea)) {
          continue;
        }

        var bounds = tokenPosition.transformedBounds().getBounds2D();
        if (bounds.getWidth() * scale < 1 || bounds.getHeight() * scale < 1) {
          continue;
        }

        visibleTokens.add(tokenPosition.token().getId());
      }
    }
  }

  /** Updates {@link #movingTokens}. */
  private void updateMovingTokens() {
    movingTokens.clear();

    var renderer = MapTool.getFrame().getZoneRenderer(this.zone);
    if (renderer == null) {
      return;
    }

    for (final var selectionSet : renderer.getSelectionSetMap().values()) {
      movingTokens.addAll(selectionSet.getTokens());
    }
  }

  /**
   * Updates {@link #lightPositions} based on {@link #playerView}, {@link #tokenPositions}, and
   * {@link #onScreenTokens}.
   */
  private void updateLightPosition() {
    lightPositions.clear();

    if (!AppState.isShowLightSources() || !playerView.isGMView()) {
      return;
    }

    for (TokenPosition position : tokenPositions.values()) {
      var token = position.token();

      if (!token.hasLightSources()) {
        continue;
      }
      if (!onScreenTokens.contains(token.getId())) {
        continue;
      }

      boolean foundNormalLight = false;
      for (AttachedLightSource attachedLightSource : token.getLightSources()) {
        LightSource lightSource = attachedLightSource.resolve(token, campaign);
        if (lightSource != null && lightSource.getType() == LightSource.Type.NORMAL) {
          foundNormalLight = true;
          break;
        }
      }
      if (foundNormalLight) {
        var bounds = position.transformedBounds().getBounds2D();
        lightPositions.add(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()));
      }
    }
  }

  /** Updates {@link #labelLocations} based on {@link #playerView} and {@link #zoneScale}. */
  private void updateLabelPositions() {
    labelLocations.clear();

    if (!AppState.getShowTextLabels()) {
      return;
    }

    final var paddingX = 4;
    final var paddingY = 4;

    for (Label label : zone.getLabels()) {
      ZonePoint zp = new ZonePoint(label.getX(), label.getY());
      // TODO I feel like this visibility check could be much refined. Why only consider the center
      //  point as opposed to the entire bounds?
      if (!zone.isPointVisible(zp, playerView)) {
        continue;
      }
      // TODO Why round the results?
      ScreenPoint sp = zoneScale.toScreenSpace(zp.x, zp.y);
      sp.x = Math.round(sp.x);
      sp.y = Math.round(sp.y);

      var font = AppStyle.labelFont.deriveFont(AppStyle.labelFont.getStyle(), label.getFontSize());

      var canvas = new Canvas();
      var fm = canvas.getFontMetrics(font);
      int strWidth = SwingUtilities.computeStringWidth(fm, label.getLabel());
      int strHeight = fm.getHeight();
      var dimensions =
          new Dimension(
              strWidth + paddingX * 2 + label.getBorderWidth() * 2,
              strHeight + paddingY * 2 + label.getBorderWidth() * 2);

      var bounds =
          new Rectangle2D.Double(
              sp.x - dimensions.width / 2.,
              sp.y - dimensions.height / 2.,
              dimensions.width,
              dimensions.height);

      labelLocations.add(new LabelLocation(bounds, label, font));
    }
  }
}
