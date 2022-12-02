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
import java.util.stream.Collectors;
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

    final var allLightAreaMap =
        viewModel
            .getLightingModel()
            .addSightedToken(
                token,
                (tokenVisibleArea != null && zone.getVisionType() != Zone.VisionType.NIGHT)
                    ? tokenVisibleArea
                    : new Area());
    if (tokenVisibleArea != null) {
      Rectangle2D origBounds = tokenVisibleArea.getBounds();
      // Jamz: OK, we should have ALL light areas in one map sorted by lumens. Lets apply it to the
      // map
      Area allLightArea = new Area();
      for (Entry<Integer, Path2D> light : allLightAreaMap.entrySet()) {
        final var lightPath = light.getValue();
        final var isDarkness = light.getKey() < 0;

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

  /** @return the list of drawable lights for auras. */
  public List<DrawableLight> getAuras() {
    List<DrawableLight> lightList = new LinkedList<DrawableLight>();
    viewModel
        .getLightingModel()
        .getAuras()
        .forEach(
            token -> {
              Point p = FogUtil.calculateVisionCenter(token, zone);

              for (AttachedLightSource als : token.getLightSources()) {
                LightSource lightSource =
                    MapTool.getCampaign().getLightSource(als.getLightSourceId());
                if (lightSource == null) {
                  continue;
                }
                if (lightSource.getType() == LightSource.Type.AURA) {
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
                        new DrawableLight(
                            LightSource.Type.AURA,
                            light.getPaint(),
                            visibleArea,
                            lightSource.getLumens()));
                  }
                }
              }
            });

    return lightList;
  }

  /**
   * Get the drawable lights from the drawableLightCache and from personalDrawableLightCache.
   *
   * @param view the player view for which to get the personal lights.
   * @return the set of drawable lights.
   */
  public Set<DrawableLight> getDrawableLights(PlayerView view) {
    return viewModel.getLightingModel().getLitRegions(view).stream()
        .map(
            litArea ->
                new DrawableLight(
                    LightSource.Type.NORMAL,
                    litArea.light().getPaint(),
                    litArea.lightArea(),
                    litArea.lumens()))
        .collect(Collectors.toSet());
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

  @Subscribe
  private void onTokensAdded(TokensAdded event) {
    if (event.zone() != zone) {
      return;
    }

    // What if all tokens lost their sight? Shouldn't we clear in that case too?
    final var hasSight = event.tokens().stream().anyMatch(Token::getHasSight);
    if (hasSight) {
      exposedAreaMap.clear();
      visibleAreaMap.clear();
    }
    boolean topologyChanged = event.tokens().stream().anyMatch(Token::hasAnyTopology);
    if (topologyChanged) {
      flush();
    }
  }

  @Subscribe
  private void onTokensRemoved(TokensRemoved event) {
    if (event.zone() != zone) {
      return;
    }

    event.tokens().forEach(this::flush);
    boolean topologyChanged = event.tokens().stream().anyMatch(Token::hasAnyTopology);
    if (topologyChanged) {
      flush();
    }
  }

  @Subscribe
  private void onTokensChanged(TokensChanged event) {
    if (event.zone() != zone) {
      return;
    }

    event.tokens().forEach(this::flush);
    // What if all tokens lost their sight? Shouldn't we clear in that case too?
    final var hasSight = event.tokens().stream().anyMatch(Token::getHasSight);
    if (hasSight) {
      exposedAreaMap.clear();
      visibleAreaMap.clear();
    }
    boolean topologyChanged = event.tokens().stream().anyMatch(Token::hasAnyTopology);
    if (topologyChanged) {
      flush();
    }
  }

  /** Has a single field: the visibleArea area */
  private static class VisibleAreaMeta {
    Area visibleArea;
  }
}
