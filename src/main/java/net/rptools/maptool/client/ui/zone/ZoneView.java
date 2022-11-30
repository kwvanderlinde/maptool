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
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.viewmodel.ZoneViewModel;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.*;
import net.rptools.maptool.model.zones.TokensAdded;
import net.rptools.maptool.model.zones.TokensChanged;
import net.rptools.maptool.model.zones.TokensRemoved;
import net.rptools.maptool.model.zones.TopologyChanged;

/** Responsible for calculating lights and vision. */
public class ZoneView {
  /** The zone of the ZoneView. */
  private final Zone zone;

  private final ZoneViewModel viewModel;

  // VISION
  /** Map each token to the area they can see by themselves. */
  private final Map<GUID, Area> tokenVisibleAreaCache = new HashMap<>();
  /** Map each token to their current vision, depending on other lights. */
  private final Map<GUID, Area> tokenVisionCache = new HashMap<>();
  /** Map light source type to all tokens with that type. */
  private final Map<LightSource.Type, Set<GUID>> lightSourceMap = new HashMap<>();
  /** Map the PlayerView to its exposed area. */
  private final Map<PlayerView, Area> exposedAreaMap = new HashMap<>();
  /** Map the PlayerView to its visible area. */
  private final Map<PlayerView, VisibleAreaMeta> visibleAreaMap = new HashMap<>();

  /** Lumen for personal vision (darkvision). */
  private static final int LUMEN_VISION = 100;

  /**
   * Construct ZoneView from zone. Build lightSourceMap, and add ZoneView to Zone as listener.
   *
   * @param zone the Zone to add.
   */
  public ZoneView(Zone zone, ZoneViewModel viewModel) {
    this.zone = zone;
    this.viewModel = viewModel;
    findLightSources();

    new MapToolEventBus().getMainEventBus().register(this);
  }

  public Area getExposedArea(PlayerView view) {
    Area exposed = exposedAreaMap.get(view);

    if (exposed == null) {
      boolean combinedView =
          !isUsingVision()
              || MapTool.isPersonalServer()
              || !MapTool.getServerPolicy().isUseIndividualFOW()
              || view.isGMView();

      if (view.isUsingTokenView() || combinedView) {
        exposed = zone.getExposedArea(view);
      } else {
        // Not a token-specific view, but we are using Individual FoW. So we build up all the owned
        // tokens' exposed areas to build the soft FoW. Note that not all owned tokens may still
        // have sight (so weren't included in the PlayerView), but could still have previously
        // exposed areas.
        exposed = new Area();
        for (Token tok : zone.getTokens()) {
          if (!AppUtil.playerOwns(tok)) {
            continue;
          }
          ExposedAreaMetaData meta = zone.getExposedAreaMetaData(tok.getExposedAreaGUID());
          Area exposedArea = meta.getExposedAreaHistory();
          exposed.add(new Area(exposedArea));
        }
      }

      exposedAreaMap.put(view, exposed);
    }
    return exposed;
  }

  /**
   * Calculate the visible area of the view, cache it in visibleAreaMap, and return it
   *
   * @param view the PlayerView
   * @return the visible area
   */
  public Area getVisibleArea(PlayerView view) {
    calculateVisibleArea(view);
    ZoneView.VisibleAreaMeta visible = visibleAreaMap.get(view);

    return visible != null ? visible.visibleArea : new Area();
  }

  /**
   * Get the vision status of the zone.
   *
   * @return true if the vision of the zone is not of type VisionType.OFF
   */
  public boolean isUsingVision() {
    return zone.isUsingVision();
  }

  /**
   * Return the token visible area from tokenVisionCache. If null, create it.
   *
   * @param token the token to get the visible area of.
   * @return the visible area of a token, including the effect of other lights.
   */
  public Area getVisibleArea(Token token) {
    // Sanity
    if (token == null || !token.getHasSight()) {
      return null;
    }

    // Cache ?
    Area tokenVisibleArea = tokenVisionCache.get(token.getId());
    // System.out.println("tokenVisionCache size? " + tokenVisionCache.size());

    if (tokenVisibleArea != null) return tokenVisibleArea;

    SightType sight = MapTool.getCampaign().getSightType(token.getSightType());
    // More sanity checks; maybe sight type removed from campaign after token set?
    if (sight == null) {
      // TODO Should we turn off the token's HasSight flag? Would speed things up for later...
      return null;
    }

    // Combine the player visible area with the available light sources
    tokenVisibleArea = tokenVisibleAreaCache.get(token.getId());
    if (tokenVisibleArea == null) {
      Point p = FogUtil.calculateVisionCenter(token, zone);
      Area visibleArea = sight.getVisionShape(token, zone);
      tokenVisibleArea =
          FogUtil.calculateVisibility(
              p.x,
              p.y,
              visibleArea,
              viewModel.getTopologyModel().getTopologyTree(Zone.TopologyType.WALL_VBL),
              viewModel.getTopologyModel().getTopologyTree(Zone.TopologyType.HILL_VBL),
              viewModel.getTopologyModel().getTopologyTree(Zone.TopologyType.PIT_VBL));

      tokenVisibleAreaCache.put(token.getId(), tokenVisibleArea);
    }

    // Stopwatch stopwatch = Stopwatch.createStarted();

    // Combine in the visible light areas
    if (tokenVisibleArea != null) {
      Rectangle2D origBounds = tokenVisibleArea.getBounds();
      List<Token> lightSourceTokens = new ArrayList<Token>();

      // Add the tokens from the lightSourceMap with normal (not aura) lights
      if (lightSourceMap.get(LightSource.Type.NORMAL) != null) {
        for (GUID lightSourceTokenId : lightSourceMap.get(LightSource.Type.NORMAL)) {
          Token lightSourceToken = zone.getToken(lightSourceTokenId);
          // Verify if the token still exists
          if (lightSourceToken != null) {
            lightSourceTokens.add(lightSourceToken);
          }
        }
      }

      if (token.hasLightSources() && !lightSourceTokens.contains(token)) {
        // This accounts for temporary tokens (such as during an Expose Last Path)
        lightSourceTokens.add(token);
      }

      // stopwatch.reset();
      // stopwatch.start();
      // Jamz: Iterate through all tokens and combine light areas by lumens
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
                // value is
                // positive and the other negative. We want negative values to come after positive
                // values,
                // which is simply the opposite of the natural order.
                return -comparison;
              });

      SightType tokenSight = MapTool.getCampaign().getSightType(token.getSightType());
      getLightAreasByLumens(allLightAreaMap, tokenSight, lightSourceTokens);

      // Check for daylight and add it to the overall light map.
      if (zone.getVisionType() != Zone.VisionType.NIGHT) {
        // Treat the entire visible area like a light source of minimal lumens.
        addLightAreaByLumens(allLightAreaMap, 1, tokenVisibleArea);
      }

      // Check for personal vision and add to overall light map
      if (sight.hasPersonalLightSource()) {
        Area lightArea =
            viewModel
                .getLightingModel()
                .calculatePersonalLightSourceArea(
                    sight.getPersonalLightSource(), token, sight, Direction.CENTER);
        if (lightArea != null) {
          var lumens = sight.getPersonalLightSource().getLumens();
          lumens = (lumens == 0) ? LUMEN_VISION : lumens;
          // maybe some kind of imposed blindness?  Anyway, make sure to handle personal darkness..
          addLightAreaByLumens(allLightAreaMap, lumens, lightArea);
        }
      }

      // Jamz: OK, we should have ALL light areas in one map sorted by lumens. Lets apply it to the
      // map
      Area allLightArea = new Area();
      for (Entry<Integer, Path2D> light : allLightAreaMap.entrySet()) {
        final var lightPath = light.getValue();
        boolean isDarkness = false;
        if (light.getKey() < 0) isDarkness = true;

        if (origBounds.intersects(lightPath.getBounds2D())) {
          Area intersection = new Area(tokenVisibleArea);
          intersection.intersect(new Area(lightPath));
          if (isDarkness) {
            allLightArea.subtract(intersection);
          } else {
            allLightArea.add(intersection);
          }
        }
      }
      allLightAreaMap.clear(); // Dispose of object, only needed for the scope of this method

      tokenVisibleArea = allLightArea;
    }

    tokenVisionCache.put(token.getId(), tokenVisibleArea);

    // log.info("getVisibleArea: \t\t" + stopwatch);

    return tokenVisibleArea;
  }

  private static void addLightAreaByLumens(
      Map<Integer, Path2D> lightAreasByLumens, int lumens, Shape area) {
    var totalPath = lightAreasByLumens.computeIfAbsent(lumens, key -> new Path2D.Double());
    totalPath.append(area.getPathIterator(null, 1), false);
  }

  private void getLightAreasByLumens(
      Map<Integer, Path2D> allLightPathMap, SightType sight, List<Token> lightSourceTokens) {
    for (Token lightSourceToken : lightSourceTokens) {
      final Map<Integer, Area> lightArea =
          viewModel.getLightingModel().getLumensToLitAreas(sight, lightSourceToken);

      for (final var light : lightArea.entrySet()) {
        // Add the token's light area to the global area in `allLightPathMap`.
        addLightAreaByLumens(allLightPathMap, light.getKey(), light.getValue());
      }
    }
  }

  /**
   * Get the lists of drawable light from lightSourceMap.
   *
   * @param type the type of lights to get.
   * @return the list of drawable lights of the given type.
   */
  // TODO Only used for auras, so no need to interact with sights or anything.
  public List<DrawableLight> getLights(LightSource.Type type) {
    List<DrawableLight> lightList = new LinkedList<DrawableLight>();
    if (lightSourceMap.get(type) != null) {
      for (GUID lightSourceToken : lightSourceMap.get(type)) {
        Token token = zone.getToken(lightSourceToken);
        if (token == null) {
          continue;
        }
        Point p = FogUtil.calculateVisionCenter(token, zone);

        for (AttachedLightSource als : token.getLightSources()) {
          LightSource lightSource = MapTool.getCampaign().getLightSource(als.getLightSourceId());
          if (lightSource == null) {
            continue;
          }
          if (lightSource.getType() == type) {
            // This needs to be cached somehow
            Area lightSourceArea = lightSource.getArea(token, zone, Direction.CENTER);
            Area visibleArea =
                FogUtil.calculateVisibility(
                    p.x,
                    p.y,
                    lightSourceArea,
                    viewModel.getTopologyModel().getTopologyTree(Zone.TopologyType.WALL_VBL),
                    viewModel.getTopologyModel().getTopologyTree(Zone.TopologyType.HILL_VBL),
                    viewModel.getTopologyModel().getTopologyTree(Zone.TopologyType.PIT_VBL));
            if (visibleArea == null) {
              continue;
            }
            for (Light light : lightSource.getLightList()) {
              boolean isOwner = token.getOwners().contains(MapTool.getPlayer().getName());
              if ((light.isGM() && !MapTool.getPlayer().isEffectiveGM())) {
                continue;
              }
              if ((!token.isVisible()) && !MapTool.getPlayer().isEffectiveGM()) {
                continue;
              }
              if (token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token)) {
                continue;
              }
              if (light.isOwnerOnly()
                  && lightSource.getType() == LightSource.Type.AURA
                  && !isOwner
                  && !MapTool.getPlayer().isEffectiveGM()) {
                continue;
              }
              lightList.add(
                  new DrawableLight(type, light.getPaint(), visibleArea, lightSource.getLumens()));
            }
          }
        }
      }
    }
    return lightList;
  }

  /** Find the light sources from all appropriate tokens, and store them in lightSourceMap. */
  private void findLightSources() {
    lightSourceMap.clear();

    for (Token token : zone.getAllTokens()) {
      if (token.hasLightSources() && token.isVisible()) {
        if (!token.isVisibleOnlyToOwner() || AppUtil.playerOwns(token)) {
          for (AttachedLightSource als : token.getLightSources()) {
            LightSource lightSource = MapTool.getCampaign().getLightSource(als.getLightSourceId());
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

  /**
   * Get the drawable lights from the drawableLightCache and from personalDrawableLightCache.
   *
   * @param view the player view for which to get the personal lights.
   * @return the set of drawable lights.
   */
  public Set<DrawableLight> getDrawableLights(PlayerView view) {
    return viewModel.getLightingModel().getDrawableLights(view);
  }

  /**
   * Clear the tokenVisibleAreaCache, tokenVisionCache, lightSourceCache, visibleAreaMap,
   * drawableLightCache, and personal drawable light caches.
   */
  public void flush() {
    tokenVisibleAreaCache.clear();
    tokenVisionCache.clear();
    exposedAreaMap.clear();
    visibleAreaMap.clear();

    // TODO Temporary evil. The model should decide in which circumstances it needs to flush.
    viewModel.getLightingModel().flush();
  }

  public void flushFog() {
    exposedAreaMap.clear();
  }

  /**
   * Flush the ZoneView cache of the token. Remove token from tokenVisibleAreaCache,
   * tokenVisionCache, lightSourceCache, drawableLightCache, and personal light caches. Can clear
   * tokenVisionCache and visibleAreaMap depending on the token.
   *
   * @param token the token to flush.
   */
  public void flush(Token token) {
    boolean hadLightSource = viewModel.getLightingModel().isKnownLightSource(token);

    // TODO Temporary evil. The model should figure out when it should flush. And once we do that,
    //  we'll need some way to tell whether the token lost a light source (another event?), so that
    //  we can clear the other models too.
    viewModel.getLightingModel().flush(token);

    tokenVisionCache.remove(token.getId());
    tokenVisibleAreaCache.remove(token.getId());

    if (hadLightSource || token.hasLightSources()) {
      // Have to recalculate all token vision
      tokenVisionCache.clear();
      exposedAreaMap.clear();
      visibleAreaMap.clear();
    } else if (token.getHasSight()) {
      exposedAreaMap.clear();
      visibleAreaMap.clear();
    }
  }

  /**
   * Construct the visibleAreaMap entry for a player view.
   *
   * @param view the player view.
   */
  private void calculateVisibleArea(PlayerView view) {
    if (visibleAreaMap.get(view) != null
        && visibleAreaMap.get(view).visibleArea.getBounds().getCenterX() != 0.0d) {
      return;
    }
    // Cache it
    VisibleAreaMeta meta = new VisibleAreaMeta();
    meta.visibleArea = new Area();

    visibleAreaMap.put(view, meta);

    // Calculate it
    final boolean isGMview = view.isGMView();
    final boolean checkOwnership =
        MapTool.getServerPolicy().isUseIndividualViews() || MapTool.isPersonalServer();
    List<Token> tokenList =
        view.isUsingTokenView()
            ? view.getTokens()
            : zone.getTokensFiltered(
                t -> t.isToken() && t.getHasSight() && (isGMview || t.isVisible()));

    for (Token token : tokenList) {
      boolean weOwnIt = AppUtil.playerOwns(token);
      // Permission
      if (checkOwnership) {
        if (!weOwnIt) {
          continue;
        }
      } else {
        // If we're viewing the map as a player and the token is not a PC, then skip it.
        if (!isGMview && token.getType() != Token.Type.PC && !AppUtil.ownedByOnePlayer(token)) {
          continue;
        }
      }
      // player ownership permission
      if (token.isVisibleOnlyToOwner() && !weOwnIt) {
        continue;
      }
      Area tokenVision = getVisibleArea(token);
      if (tokenVision != null) {
        meta.visibleArea.add(tokenVision);
      }
    }

    // System.out.println("calculateVisibleArea: " + (System.currentTimeMillis() - startTime) +
    // "ms");
  }

  @Subscribe
  private void onTopologyChanged(TopologyChanged event) {
    if (event.zone() != this.zone) {
      return;
    }

    flush();
  }

  private boolean flushExistingTokens(List<Token> tokens) {
    boolean tokenChangedTopology = false;
    for (Token token : tokens) {
      if (token.hasAnyTopology()) tokenChangedTopology = true;
      flush(token);
    }
    // Ug, stupid hack here, can't find a bug where if a NPC token is moved before lights are
    // cleared on another token, changes aren't pushed to client?
    // tokenVisionCache.clear();
    return tokenChangedTopology;
  }

  @Subscribe
  private void onTokensAdded(TokensAdded event) {
    if (event.zone() != zone) {
      return;
    }

    boolean tokenChangedTopology = processTokenAddChangeEvent(event.tokens());

    // Moved this event to the bottom so we can check the other events
    // since if a token that has topology is added/removed/edited (rotated/moved/etc)
    // it should also trip a Topology change
    if (tokenChangedTopology) {
      flush();
    }
  }

  @Subscribe
  private void onTokensRemoved(TokensRemoved event) {
    if (event.zone() != zone) {
      return;
    }

    boolean tokenChangedTopology = flushExistingTokens(event.tokens());

    for (Token token : event.tokens()) {
      if (token.hasAnyTopology()) tokenChangedTopology = true;
      for (AttachedLightSource als : token.getLightSources()) {
        LightSource lightSource = MapTool.getCampaign().getLightSource(als.getLightSourceId());
        if (lightSource == null) {
          continue;
        }
        Set<GUID> lightSet = lightSourceMap.get(lightSource.getType());
        if (lightSet != null) {
          lightSet.remove(token.getId());
        }
      }
    }

    // Moved this event to the bottom so we can check the other events
    // since if a token that has topology is added/removed/edited (rotated/moved/etc)
    // it should also trip a Topology change
    if (tokenChangedTopology) {
      flush();
    }
  }

  @Subscribe
  private void onTokensChanged(TokensChanged event) {
    if (event.zone() != zone) {
      return;
    }

    flushExistingTokens(event.tokens());

    boolean tokenChangedTopology = processTokenAddChangeEvent(event.tokens());

    // Moved this event to the bottom so we can check the other events
    // since if a token that has topology is added/removed/edited (rotated/moved/etc)
    // it should also trip a Topology change
    if (tokenChangedTopology) {
      flush();
    }
  }

  /**
   * Update lightSourceMap with the light sources of the tokens, and clear visibleAreaMap if one of
   * the tokens has sight.
   *
   * @param tokens the list of tokens
   * @return if one of the token has topology or not
   */
  private boolean processTokenAddChangeEvent(List<Token> tokens) {
    boolean hasSight = false;
    boolean hasTopology = false;
    Campaign c = MapTool.getCampaign();

    for (Token token : tokens) {
      boolean hasLightSource =
          token.hasLightSources() && (token.isVisible() || MapTool.getPlayer().isEffectiveGM());
      if (token.hasAnyTopology()) hasTopology = true;
      for (AttachedLightSource als : token.getLightSources()) {
        LightSource lightSource = c.getLightSource(als.getLightSourceId());
        if (lightSource != null) {
          Set<GUID> lightSet = lightSourceMap.get(lightSource.getType());
          if (hasLightSource) {
            if (lightSet == null) {
              lightSet = new HashSet<GUID>();
              lightSourceMap.put(lightSource.getType(), lightSet);
            }
            lightSet.add(token.getId());
          } else if (lightSet != null) lightSet.remove(token.getId());
        }
      }
      hasSight |= token.getHasSight();
    }

    if (hasSight) {
      exposedAreaMap.clear();
      visibleAreaMap.clear();
    }

    return hasTopology;
  }

  /** Has a single field: the visibleArea area */
  private static class VisibleAreaMeta {
    Area visibleArea;
  }
}
