package net.rptools.maptool.client.ui.zone.viewmodel;

import net.rptools.maptool.client.ui.zone.DrawableLight;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.LightSource;

import java.awt.geom.Area;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages a zone's lighting, caching important information.
 *
 * <p>We do not directly expose light sources, but instead work with _drawable lights_. A drawable
 * light is little more than a paint combined with an area, so it is something that can be easily
 * rendered. Each range of a light corresponds to a different drawable light.
 * */
public class LightingModel {
    // TODO At least some of the "caches" were relied upon by ZoneRenderer to have been explicitly
    //  added to. But no more! We must manage them ourselves, or else give up the guise of being a
    //  cache!

    /** Map lightSourceToken to the areaBySightMap. */
    private final Map<GUID, Map<String, Map<Integer, Area>>> lightSourceCache = new HashMap<>();
    /** Map light source type to all tokens with that type. */
    private final Map<LightSource.Type, Set<GUID>> lightSourceMap = new HashMap<>();
    /** Map each token to their map between sightType and set of lights. */
    private final Map<GUID, Map<String, Set<DrawableLight>>> drawableLightCache = new HashMap<>();
    /** Map each token to their personal drawable lights. */
    private final Map<GUID, Set<DrawableLight>> personalDrawableLightCache = new HashMap<>();
}
