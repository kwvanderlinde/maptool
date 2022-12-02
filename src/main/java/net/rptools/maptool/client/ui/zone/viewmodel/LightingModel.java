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
package net.rptools.maptool.client.ui.zone.viewmodel;

import com.google.common.eventbus.Subscribe;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.FogUtil;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.AttachedLightSource;
import net.rptools.maptool.model.Direction;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Light;
import net.rptools.maptool.model.LightSource;
import net.rptools.maptool.model.SightType;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.zones.TokensAdded;
import net.rptools.maptool.model.zones.TokensChanged;
import net.rptools.maptool.model.zones.TokensRemoved;

/**
 * Manages a zone's lighting.
 *
 * <p>Lights are defined by the campaign and attached to tokens. This model is responsible for the
 * behaviour of lights, including:
 *
 * <ul>
 *   <li>How lumens works (light vs darkness)
 *   <li>How lights interact with topology
 *   <li>How lights interact with sights
 * </ul>
 *
 * <p>We do not directly expose light sources, but instead work with _lit regions_. A lit region is
 * an area of the map with an associated light and lumens value.
 */
public class LightingModel {
  // TODO I don't think I'm pulling the drawable light caches out of here, so I should include them
  //  as a core feature. They can't simply be thought of a "caches" because they are relied upon by
  //  other components. Also DrawableLight is Swing-specific, we should expose something that is
  //  just as convenient, but which doesn't require a drawable light. E.g., a KnownLightSource that
  //  carries the same information as the parameters to addLightSourceToCache()
  //  So what does this model look like? I think the important part is that we avoid plain get-style
  //  operations if they also are required to add to the drawable caches. Rather we should have
  //  operations that are explicit as "adding" lights.
  //  Even better, the model should be adding _sights_. When a sight is added, then for each light
  //  source (plus the current token which may not be included) we calculate the lit areas by
  //  lumens, and add each light to the drawableLightCache. Additionally, we add the sight's
  //  personal light source to the personalDrawableLightCache.
  //  Big question is: do we hold onto the set of sights/sighted tokens? The current model is to
  //  flush aggressively and rely on the fact that ZoneView will add them back. We can stick with
  //  that for now, it's food for thought.
  //  Auras tbd, those red-headed step-children of lights :p

  public record LitRegion(Area lightArea, int lumens, Light light) {}

  /** Lumen for personal vision (darkvision). */
  // TODO Better name.
  private static final int LUMEN_VISION = 100;

  private final Zone zone;
  private final TopologyModel topologyModel;
  private final Function<GUID, LightSource> lightSourceResolver;
  private final Function<String, SightType> sightResolver;

  /**
   * Map light source type to all tokens with that type.
   *
   * <p>This is a reflection of zone state, so it must be kept in sync with the zone.
   */
  private final Map<LightSource.Type, Set<GUID>> lightSourceMap = new HashMap<>();

  // TODO Consider reversing the nesting of these maps. Look up by sight first, then token. That
  //  could enable better sight-sharing.
  /**
   * Caches lit areas per token and sight type.
   *
   * <p>The lit areas are a mapping from lumens to corresponding areas.
   */
  // TODO Ensure that each light source always exists in this cache. It's just sights that need to
  //  be added beneath it.
  private final Map<GUID, Map<String, Map<Integer, Area>>> lightSourceCache = new HashMap<>();
  /** Map each token to their map between sight and set of lit regions. */
  // TODO Ensure that each light source always exists in this cache. It's just sights that need to
  //  be added beneath it.
  private final Map<GUID, Map<String, Set<LitRegion>>> litRegionsCache = new HashMap<>();
  /**
   * Map each token to their personal lit regions.
   *
   * <p>Unlike the other caches, we don't need to include the sight type because a token only has
   * one sight, and the personal sight only applies to that token.
   *
   * <p>Also unlike the other caches, the top-level GUID identifies any old token, not necessarily a
   * light source token.
   */
  private final Map<GUID, Set<LitRegion>> personalLitRegionsCache = new HashMap<>();

  public LightingModel(
      Zone zone,
      TopologyModel topologyModel,
      Function<GUID, LightSource> lightSourceResolver,
      Function<String, SightType> sightResolver) {
    this.zone = zone;
    this.topologyModel = topologyModel;
    this.lightSourceResolver = lightSourceResolver;
    this.sightResolver = sightResolver;

    new MapToolEventBus().getMainEventBus().register(this);

    // Add all light sources right away.
    for (final var token : zone.getAllTokens()) {
      if (token.hasLightSources() && token.isVisible()) {
        // TODO Would love to not depend on AppUtil here and instead require a current player to be
        //  passed in somehow.
        if (!token.isVisibleOnlyToOwner() || AppUtil.playerOwns(token)) {
          for (AttachedLightSource als : token.getLightSources()) {
            // TODO Should we unify this with the logic in getLumensToLitAreas()?
            LightSource lightSource = lightSourceResolver.apply(als.getLightSourceId());
            if (lightSource == null) {
              continue;
            }
            Set<GUID> lightSet =
                lightSourceMap.computeIfAbsent(lightSource.getType(), k -> new HashSet<>());
            lightSet.add(token.getId());
          }
        }
      }
    }
  }

  // region Events

  /**
   * Update the known light sources according to the new zone state.
   *
   * @param tokens the list of tokens that were added, changed or removed from the zone.
   */
  private void processTokenAddChangeRemoveEvent(
      List<Token> tokens, boolean flushTokens, boolean forceLightRemoval) {
    boolean topologyChanged = tokens.stream().anyMatch(Token::hasAnyTopology);
    if (flushTokens) {
      tokens.forEach(this::flush);
    }

    // TODO Don't rely on MapTool.getPlayer(), but get the view model's player.
    for (Token token : tokens) {
      final var hasLightSource =
          token.hasLightSources() && (token.isVisible() || MapTool.getPlayer().isEffectiveGM());
      final var removeLight = forceLightRemoval || !hasLightSource;
      for (AttachedLightSource als : token.getLightSources()) {
        // TODO Can we unify this with the logic in the three other places?
        LightSource lightSource = lightSourceResolver.apply(als.getLightSourceId());
        if (lightSource != null) {
          Set<GUID> lightSet = lightSourceMap.get(lightSource.getType());
          if (removeLight) {
            if (lightSet != null) {
              lightSet.remove(token.getId());
            }
          } else {
            if (lightSet == null) {
              lightSet = new HashSet<>();
              lightSourceMap.put(lightSource.getType(), lightSet);
            }
            lightSet.add(token.getId());
          }
        }
      }
    }

    if (topologyChanged) {
      flush();
    }
  }

  @Subscribe
  private void onTokensAdded(TokensAdded event) {
    if (event.zone() != zone) {
      return;
    }

    processTokenAddChangeRemoveEvent(event.tokens(), false, false);
  }

  @Subscribe
  private void onTokensRemoved(TokensRemoved event) {
    if (event.zone() != zone) {
      return;
    }

    processTokenAddChangeRemoveEvent(event.tokens(), true, true);
  }

  @Subscribe
  private void onTokensChanged(TokensChanged event) {
    if (event.zone() != zone) {
      return;
    }

    processTokenAddChangeRemoveEvent(event.tokens(), true, false);
  }

  // endregion

  // region Cache invalidation

  public void flush() {
    lightSourceCache.clear();
    // TODO Consider whether these are truly caches and whether they actually belong in a
    //  dedicated data structure.
    litRegionsCache.clear();
    personalLitRegionsCache.clear();
  }

  public void flush(Token token) {
    lightSourceCache.remove(token.getId());
    litRegionsCache.remove(token.getId());
    personalLitRegionsCache.remove(token.getId());
  }

  // endregion

  /**
   * Add a sighted token to the lighting model.
   *
   * <p>Internal: the token will be matched up with each known light source, and each light is added
   * to lightSourceCache, litRegionsCache and personalLitRegionsCache along with the token's sight.
   *
   * @param token The token with the sight.
   * @param tokenDaylightVision Any daylight to associate with the token's vision.
   */
  public Map<Integer, Path2D> addSightedToken(
      @Nonnull Token token, @Nonnull Area tokenDaylightVision) {
    // TODO Rework this method to fit the concept:
    //  1. Add to lightSourceCache
    //  2. Add to drawable lights cache / personal drawable lights cache.
    //  3. Return the lumens areas (as paths; we don't want to convert to areas if they might be
    // discarded anyways).
    //  Beyond that, store the lumens association rather than returning it. Have the caller retrieve
    //  the map after making sure it is added, as that will free them up to do things in different
    //  orders.
    // TODO Another way of handling daylight. The only real reason we need tokenVisibleArea to have
    //  already been calculated for us is so we can add daylight to the map. If another strategy is
    //  devised, we might be okay.

    // Sanity
    if (!token.getHasSight()) {
      return Collections.emptyMap();
    }

    // TODO Check if already added and skip. We should be able to assume that tokenVisibleArea is
    //  the same unless things are going really wrong.

    SightType sight = sightResolver.apply(token.getSightType());
    // More sanity checks; maybe sight type removed from campaign after token set?
    if (sight == null) {
      return Collections.emptyMap();
    }

    // Combine in the visible light areas
    final var lightSourceTokens = new ArrayList<Token>();
    // Add the tokens from the lightSourceMap with normal (not aura) lights
    getLightSources().forEach(lightSourceTokens::add);
    if (token.hasLightSources() && !lightSourceTokens.contains(token)) {
      // This accounts for temporary tokens (such as during an "Expose Last Path" operation)
      lightSourceTokens.add(token);
    }

    /* Hold all of our lights combined by lumens. Used for hard FoW reveal. */
    final SortedMap<Integer, Path2D> allLightAreaMap =
        new TreeMap<>(
            (lhsLumens, rhsLumens) -> {
              int comparison = Integer.compare(lhsLumens, rhsLumens);
              if (comparison == 0) {
                // Values are equal. Not much else to do.
                return 0;
              }

              // Primarily order lumens by magnitude.
              int absComparison = Integer.compare(Math.abs(lhsLumens), Math.abs(rhsLumens));
              if (absComparison != 0) {
                return absComparison;
              }

              // At this point we know have different values with the same magnitude. I.e., one
              // value is positive and the other negative. We want negative values to come after
              // positive values, which is simply the opposite of the natural order.
              return -comparison;
            });

    getLightAreasByLumens(allLightAreaMap, sight, lightSourceTokens);

    if (!tokenDaylightVision.isEmpty()) {
      // Treat daylight as a light source of minimal lumens.
      addLightAreaByLumens(allLightAreaMap, 1, tokenDaylightVision);
    }

    // Check for personal vision and add to overall light map
    if (sight.hasPersonalLightSource()) {
      Area lightArea =
          calculatePersonalLightSourceArea(
              sight.getPersonalLightSource(), token, sight, Direction.CENTER);
      if (lightArea != null) {
        var lumens = sight.getPersonalLightSource().getLumens();
        lumens = (lumens == 0) ? LUMEN_VISION : lumens;
        // maybe some kind of imposed blindness?  Anyway, make sure to handle personal darkness.
        addLightAreaByLumens(allLightAreaMap, lumens, lightArea);
      }
    }

    return allLightAreaMap;
  }

  private static void addLightAreaByLumens(
      Map<Integer, Path2D> lightAreasByLumens, int lumens, Shape area) {
    var totalPath = lightAreasByLumens.computeIfAbsent(lumens, key -> new Path2D.Double());
    totalPath.append(area.getPathIterator(null, 1), false);
  }

  private void getLightAreasByLumens(
      Map<Integer, Path2D> allLightPathMap, SightType sight, List<Token> lightSourceTokens) {
    for (Token lightSourceToken : lightSourceTokens) {
      final Map<Integer, Area> lightArea = getLumensToLitAreas(sight, lightSourceToken);

      for (final var light : lightArea.entrySet()) {
        // Add the token's light area to the global area in `allLightPathMap`.
        addLightAreaByLumens(allLightPathMap, light.getKey(), light.getValue());
      }
    }
  }

  // TODO Temporary evil. Only used by ZoneView deciding when to flush. That's why we get away with
  //  returning from the cache, but in principle we should return from lightSourceMap.
  public boolean isKnownLightSource(Token token) {
    return lightSourceCache.get(token.getId()) != null;
  }

  public Stream<Token> getLightSources() {
    final var guids = lightSourceMap.get(LightSource.Type.NORMAL);
    if (guids == null) {
      return Stream.empty();
    }

    return guids.stream().map(zone::getToken).filter(Objects::nonNull);
  }

  // TODO I know auras are _like_ lights, and are defined as lights, but let's be real they are not.
  //  Notably, they should never be subject to light-vs-darkness and lumens never matter.
  public Stream<Token> getAuras() {
    final var guids = lightSourceMap.get(LightSource.Type.AURA);
    if (guids == null) {
      return Stream.empty();
    }

    return guids.stream().map(zone::getToken).filter(Objects::nonNull);
  }

  // TODO Ideally this can move into its own data structure.
  public Set<LitRegion> getLitRegions(@Nonnull PlayerView view) {
    Set<LitRegion> lightSet = new HashSet<>();

    for (Map<String, Set<LitRegion>> map : litRegionsCache.values()) {
      for (Set<LitRegion> set : map.values()) {
        lightSet.addAll(set);
      }
    }
    if (view.isUsingTokenView()) {
      // Get the personal lit regions of the tokens of the player view
      for (Token token : view.getTokens()) {
        Set<LitRegion> lights = personalLitRegionsCache.get(token.getId());
        if (lights != null) {
          lightSet.addAll(lights);
        }
      }
    }
    return lightSet;
  }

  /**
   * Get a light source token's lit area, organized by lumens value.
   *
   * <p>Internal: results are cached in lightSourceCache.
   *
   * @param sight The sight used to calculate light areas. Sights with multipliers will increase the
   *     light ranges compared to their default.
   * @param lightSourceToken The token holding light sources.
   * @return The areas lit by the light source token, where the keys are lumens values.
   */
  private Map<Integer, Area> getLumensToLitAreas(SightType sight, Token lightSourceToken) {
    GUID tokenId = lightSourceToken.getId();
    Map<String, Map<Integer, Area>> areaBySightMap = lightSourceCache.get(tokenId);
    if (areaBySightMap != null) {
      Map<Integer, Area> lightSourceArea = areaBySightMap.get(sight.getName());
      if (lightSourceArea != null) {
        return lightSourceArea;
      }
    } else {
      areaBySightMap = new HashMap<>();
      lightSourceCache.put(lightSourceToken.getId(), areaBySightMap);
    }

    Map<Integer, Area> lightSourceAreaMap = new HashMap<>();

    for (AttachedLightSource attachedLightSource : lightSourceToken.getLightSources()) {
      LightSource lightSource = lightSourceResolver.apply(attachedLightSource.getLightSourceId());
      if (lightSource == null) {
        continue;
      }
      Area visibleArea =
          calculateLightSourceArea(
              lightSource, lightSourceToken, sight, attachedLightSource.getDirection());

      if (visibleArea != null && lightSource.getType() == LightSource.Type.NORMAL) {
        var lumens = lightSource.getLumens();
        // Group all the light areas by lumens so there is only one area per lumen value
        if (lightSourceAreaMap.containsKey(lumens)) {
          visibleArea.add(lightSourceAreaMap.get(lumens));
        }
        lightSourceAreaMap.put(lumens, visibleArea);
      }
    }

    // Cache
    areaBySightMap.put(sight.getName(), lightSourceAreaMap);
    return lightSourceAreaMap;
  }

  /**
   * Get the area visible to a token given its sight, and cache a corresponding lit region.
   *
   * <p>The given sight must be the sight for the given token.
   *
   * <p>Internal: the results will be added to personalLitRegionCache. Note that the calculated area
   * is <emph>not</emph> cached.
   *
   * @param lightSource the personal light source.
   * @param lightSourceToken the token holding the light source.
   * @param sight the sight type.
   * @param direction the direction of the light source.
   * @return the area visible.
   */
  private Area calculatePersonalLightSourceArea(
      LightSource lightSource, Token lightSourceToken, SightType sight, Direction direction) {
    return calculateLightSourceArea(lightSource, lightSourceToken, sight, direction, true);
  }

  /**
   * Get the area visible for a given sight and light, and cache a corresponding lit region.
   *
   * <p>Internal: the results will be added to litRegionCache. Note that the calculated area is
   * <emph>not</emph> cached.
   *
   * @param lightSource the personal light source.
   * @param lightSourceToken the token holding the light source.
   * @param sight the sight type.
   * @param direction the direction of the light source.
   * @return the area visible.
   */
  // TODO Remove this in favour of direct calls as they are easier to navigate.
  // TODO Use enum instead of boolean for personal lights.
  private Area calculateLightSourceArea(
      LightSource lightSource, Token lightSourceToken, SightType sight, Direction direction) {
    return calculateLightSourceArea(lightSource, lightSourceToken, sight, direction, false);
  }

  /**
   * Calculate the area visible by a sight type for a given lightSource, and put the lights in
   * litRegionsCache (for lights) or personalLitRegionsCache (for personal lights).
   *
   * @param lightSource the light source. Not a personal light.
   * @param lightSourceToken the token holding the light source.
   * @param sight the sight type.
   * @param direction the direction of the light source.
   * @param isPersonalLight is the light a personal light?
   * @return the area visible.
   */
  private Area calculateLightSourceArea(
      LightSource lightSource,
      Token lightSourceToken,
      SightType sight,
      Direction direction,
      boolean isPersonalLight) {
    if (sight == null) {
      return null;
    }
    Point p = FogUtil.calculateVisionCenter(lightSourceToken, zone);
    Area lightSourceArea = lightSource.getArea(lightSourceToken, zone, direction);

    // Calculate exposed area
    // Jamz: OK, let not have lowlight vision type multiply darkness radius
    if (sight.getMultiplier() != 1 && lightSource.getLumens() >= 0) {
      lightSourceArea.transform(
          AffineTransform.getScaleInstance(sight.getMultiplier(), sight.getMultiplier()));
    }
    Area visibleArea =
        FogUtil.calculateVisibility(
            p.x,
            p.y,
            lightSourceArea,
            topologyModel.getTopologyTree(Zone.TopologyType.WALL_VBL),
            topologyModel.getTopologyTree(Zone.TopologyType.HILL_VBL),
            topologyModel.getTopologyTree(Zone.TopologyType.PIT_VBL));

    if (visibleArea != null && lightSource.getType() == LightSource.Type.NORMAL) {
      addLitRegion(
          visibleArea, p, lightSource, lightSourceToken, sight, direction, isPersonalLight);
    }
    return visibleArea;
  }

  /**
   * Adds the light source as seen by a given sight to the corresponding cache. Lights (but not
   * darkness) with a color CSS value are stored in the litRegionsCache or personalLitRegionsCache.
   *
   * @param visibleArea the area visible from the light source token
   * @param p the vision center of the light source token
   * @param lightSource the light source
   * @param lightSourceToken the light source token
   * @param sight the sight
   * @param direction the direction of the light source
   */
  private void addLitRegion(
      Area visibleArea,
      Point p,
      LightSource lightSource,
      Token lightSourceToken,
      SightType sight,
      Direction direction,
      boolean isPersonalLight) {
    // Keep track of colored light
    Set<LitRegion> lightSet = new HashSet<>();
    for (Light light : lightSource.getLightList()) {
      Area lightArea = lightSource.getArea(lightSourceToken, zone, direction, light);
      if (sight.getMultiplier() != 1) {
        lightArea.transform(
            AffineTransform.getScaleInstance(sight.getMultiplier(), sight.getMultiplier()));
      }
      lightArea.transform(AffineTransform.getTranslateInstance(p.x, p.y));
      lightArea.intersect(visibleArea);

      // If a light has no paint, it's a "bright light" that just reveal FoW but doesn't need to be
      // rendered.
      if (light.getPaint() != null || lightSource.getLumens() < 0) {
        lightSet.add(new LitRegion(lightArea, lightSource.getLumens(), light));
      }
    }

    if (isPersonalLight) {
      personalLitRegionsCache.put(lightSourceToken.getId(), lightSet);
    } else {
      Map<String, Set<LitRegion>> lightMap =
          litRegionsCache.computeIfAbsent(lightSourceToken.getId(), k -> new HashMap<>());
      if (lightMap.get(sight.getName()) != null) {
        lightMap.get(sight.getName()).addAll(lightSet);
      } else {
        lightMap.put(sight.getName(), lightSet);
      }
    }
  }
}
