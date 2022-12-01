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

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.ui.zone.DrawableLight;
import net.rptools.maptool.client.ui.zone.FogUtil;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.model.AttachedLightSource;
import net.rptools.maptool.model.Direction;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Light;
import net.rptools.maptool.model.LightSource;
import net.rptools.maptool.model.SightType;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;

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
 * <p>We do not directly expose light sources, but instead work with _drawable lights_. A drawable
 * light is little more than a paint combined with an area, so it is something that can be easily
 * rendered. Each range of a light corresponds to a different drawable light.
 */
public class LightingModel {
  // TODO At least some of the "caches" were relied upon by ZoneRenderer to have been explicitly
  //  added to. But no more! We must manage them ourselves, or else give up the guise of being a
  //  cache!

  private final Zone zone;
  private final TopologyModel topologyModel;
  private final Function<GUID, LightSource> lightSourceResolver;

  /**
   * Caches lit areas per token and sight type.
   *
   * <p>The lit areas are a mapping from lumens to corresponding areas.
   */
  private final Map<GUID, Map<String, Map<Integer, Area>>> lightSourceCache = new HashMap<>();
  // TODO Are these even caches? I think they are just drawable light storage.
  /** Map each token to their map between sightType and set of lights. */
  private final Map<GUID, Map<String, Set<DrawableLight>>> drawableLightCache = new HashMap<>();
  /**
   * Map each token to their personal drawable lights.
   *
   * <p>Unlike the other caches, we don't need to include the sight type because a token only has
   * one sight, and the personal sight only applies to that token.</p>
   */
  private final Map<GUID, Set<DrawableLight>> personalDrawableLightCache = new HashMap<>();

  public LightingModel(
      Zone zone, TopologyModel topologyModel, Function<GUID, LightSource> lightSourceResolver) {
    this.zone = zone;
    this.topologyModel = topologyModel;
    this.lightSourceResolver = lightSourceResolver;
  }

  // TODO Temporary evil. In the future, we hopefully won't need to expose the cache this way.
  public boolean isKnownLightSource(Token token) {
    return lightSourceCache.get(token.getId()) != null;
  }

  public void flush() {
    lightSourceCache.clear();
    // TODO Consider whether these are truly caches and whether they actually belong in a
    //  dedicated data structure.
    drawableLightCache.clear();
    personalDrawableLightCache.clear();
  }

  public void flush(Token token) {
    lightSourceCache.remove(token.getId());
    drawableLightCache.remove(token.getId());
    personalDrawableLightCache.remove(token.getId());
  }

  // TODO Ideally this can move into its own data structure.
  public Set<DrawableLight> getDrawableLights(@Nonnull PlayerView view) {
    Set<DrawableLight> lightSet = new HashSet<>();

    for (Map<String, Set<DrawableLight>> map : drawableLightCache.values()) {
      for (Set<DrawableLight> set : map.values()) {
        lightSet.addAll(set);
      }
    }
    if (view.isUsingTokenView()) {
      // Get the personal drawable lights of the tokens of the player view
      for (Token token : view.getTokens()) {
        Set<DrawableLight> lights = personalDrawableLightCache.get(token.getId());
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
  // TODO Accept a SightType instead of a String for first parameter.
  public Map<Integer, Area> getLumensToLitAreas(SightType sight, Token lightSourceToken) {
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
   * Get the area visible to a token given its sight, and add a corresponding drawable light.
   *
   * <p>The given sight must be the sight for the given token.
   *
   * <p>Internal: the results will be added to personalDrawableLightCache. Note that the calculated
   * area is <emph>not</emph> cached.
   *
   * @param lightSource the personal light source.
   * @param lightSourceToken the token holding the light source.
   * @param sight the sight type.
   * @param direction the direction of the light source.
   * @return the area visible.
   */
  public Area calculatePersonalLightSourceArea(
      LightSource lightSource, Token lightSourceToken, SightType sight, Direction direction) {
    return calculateLightSourceArea(lightSource, lightSourceToken, sight, direction, true);
  }

  /**
   * Get the area visible for a given sight and light, and add a corresponding drawable light.
   *
   * <p>Internal: the results will be added to drawableLightCache. Note that the calculated area is
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
   * drawableLightCache.
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

    // TODO I don't want to cache drawables here. I would rather have the caller iterate over
    //  known lights and build the drawables on-demand, it may well be outside the scope of the
    //  LightingModel altogether. Then again, might as well do it in here rather than elsewhere,
    //  but I still don't like how this "cache" is ultimately used as the source of truth.
    if (visibleArea != null && lightSource.getType() == LightSource.Type.NORMAL) {
      addLightSourceToCache(
          visibleArea, p, lightSource, lightSourceToken, sight, direction, isPersonalLight);
    }
    return visibleArea;
  }

  /**
   * Adds the light source as seen by a given sight to the corresponding cache. Lights (but not
   * darkness) with a color CSS value are stored in the drawableLightCache.
   *
   * @param visibleArea the area visible from the light source token
   * @param p the vision center of the light source token
   * @param lightSource the light source
   * @param lightSourceToken the light source token
   * @param sight the sight
   * @param direction the direction of the light source
   */
  // TODO I don't like it. Lots of work being done eagerly here for a "cache". Also, IIRC, the
  //  drawable light "caches" are taken as a source of truth during rendering, potentially a big
  //  no-no.
  //  Except... it seems the idea is that the same lights that had their areas calculated for
  //  exposure are the ones that need to be drawn. Not a bad assumption, but I would assert that
  //  it would be easier to follow this class and the general flow if this caching/stashing was
  //  done on an as-needed basis.
  // TODO Rename this. This isn't caching light sources, it is building a source-of-truth of
  //  drawable lights that the renderer can rely on. In principle this should be its own data
  //  structure that the caller builds.
  private void addLightSourceToCache(
      Area visibleArea,
      Point p,
      LightSource lightSource,
      Token lightSourceToken,
      SightType sight,
      Direction direction,
      boolean isPersonalLight) {
    // Keep track of colored light
    Set<DrawableLight> lightSet = new HashSet<>();
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
        lightSet.add(
            new DrawableLight(
                lightSource.getType(), light.getPaint(), lightArea, lightSource.getLumens()));
      }
    }
    // FIXME There was a bug report of a ConcurrentModificationException regarding
    // drawableLightCache.
    // I don't see how, but perhaps this code -- and the ones in flush() and flush(Token) -- should
    // be
    // wrapped in a synchronization block? This method is probably called only on the same thread as
    // getDrawableLights() but the two flush() methods may be called from different threads. How to
    // verify this with Eclipse? Maybe the flush() methods should defer modifications to the
    // EventDispatchingThread?
    if (isPersonalLight) {
      personalDrawableLightCache.put(lightSourceToken.getId(), lightSet);
    } else {
      Map<String, Set<DrawableLight>> lightMap =
          drawableLightCache.computeIfAbsent(lightSourceToken.getId(), k -> new HashMap<>());
      if (lightMap.get(sight.getName()) != null) {
        lightMap.get(sight.getName()).addAll(lightSet);
      } else {
        lightMap.put(sight.getName(), lightSet);
      }
    }
  }
}
