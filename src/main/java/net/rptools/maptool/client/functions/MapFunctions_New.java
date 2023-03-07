package net.rptools.maptool.client.functions;

import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.util.AnnotatedFunctionException;
import net.rptools.maptool.client.functions.util.Delimited;
import net.rptools.maptool.client.functions.util.MacroFunction;
import net.rptools.maptool.client.functions.util.Transitional;
import net.rptools.maptool.client.functions.util.Trusted;
import net.rptools.maptool.client.ui.zone.ZoneRenderer;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.FunctionUtil;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class MapFunctions_New {
    @MacroFunction
    @Transitional(minParameters = 0, maxParameters = 0)
    public Object getCurrentMapName(List<Object> parameters) throws AnnotatedFunctionException {
        ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
        if (currentZR == null) {
            throw new AnnotatedFunctionException("macro.function.map.none");
        } else {
            return currentZR.getZone().getName();
        }
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 0, maxParameters = 1)
    public Object getMapDisplayName(List<Object> parameters) throws AnnotatedFunctionException {
        if (parameters.size() == 0) {
            ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
            if (currentZR == null) {
                throw new AnnotatedFunctionException("macro.function.map.none");
            }
            else {
                return currentZR.getZone().getPlayerAlias();
            }
        } else {
            List<ZoneRenderer> rendererList =
                    new LinkedList<ZoneRenderer>(
                            MapTool.getFrame().getZoneRenderers()); // copied from ZoneSelectionPopup
            String searchMap = parameters.get(0).toString();
            String foundMap = null;
            for (int i = 0; i < rendererList.size(); i++) {
                if (rendererList.get(i).getZone().getName().equals(searchMap)) {
                    foundMap = rendererList.get(i).getZone().getPlayerAlias();
                    break;
                }
            }
            if (foundMap == null) {
                throw new AnnotatedFunctionException("macro.function.map.notFound");
            } else {
                return foundMap;
            }
        }
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 1)
    public Object setCurrentMap(List<Object> parameters) throws AnnotatedFunctionException {
        String mapName = parameters.get(0).toString();
        ZoneRenderer zr = getNamedMap(mapName);
        MapTool.getFrame().setCurrentZoneRenderer(zr);
        return mapName;
    }

    @MacroFunction
    @Transitional(minParameters = 0, maxParameters = 1)
    public Object getMapVisible(List<Object> parameters) throws AnnotatedFunctionException {
        if (parameters.size() > 0) {
            String mapName = parameters.get(0).toString();
            return getNamedMap(mapName).getZone().isVisible()
                    ? BigDecimal.ONE
                    : BigDecimal.ZERO;
        } else {
            // Return the visibility of the current map/zone
            ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
            if (currentZR == null) {
                throw new AnnotatedFunctionException("macro.function.map.none");
            } else {
                return currentZR.getZone().isVisible() ? BigDecimal.ONE : BigDecimal.ZERO;
            }
        }
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 2)
    public Object setMapVisible(List<Object> parameters) throws AnnotatedFunctionException {
        boolean visible = FunctionUtil.getBooleanValue(parameters.get(0).toString());
        Zone zone;
        if (parameters.size() > 1) {
            String mapName = parameters.get(1).toString();
            zone = getNamedMap(mapName).getZone();
        } else {
            ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
            if (currentZR == null) {
                throw new AnnotatedFunctionException("macro.function.map.none");
            } else {
                zone = currentZR.getZone();
            }
        }
        // Set the zone and return the visibility of the current map/zone
        zone.setVisible(visible);
        MapTool.serverCommand().setZoneVisibility(zone.getId(), zone.isVisible());
        MapTool.getFrame().getZoneMiniMapPanel().flush();
        MapTool.getFrame().repaint();
        return zone.isVisible() ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 2, maxParameters = 2)
    public Object setMapName(List<Object> parameters) throws AnnotatedFunctionException {
        String oldMapName = parameters.get(0).toString();
        String newMapName = parameters.get(1).toString();
        Zone zone = getNamedMap(oldMapName).getZone();
        zone.setName(newMapName);
        MapTool.serverCommand().renameZone(zone.getId(), newMapName);
        if (zone == MapTool.getFrame().getCurrentZoneRenderer().getZone())
            MapTool.getFrame().setCurrentZoneRenderer(MapTool.getFrame().getCurrentZoneRenderer());
        return zone.getName();
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 2, maxParameters = 2)
    public Object setMapDisplayName(List<Object> parameters) throws AnnotatedFunctionException {
        String mapName = parameters.get(0).toString();
        String newMapDisplayName = parameters.get(1).toString();
        Zone zone = getNamedMap(mapName).getZone();
        String oldName;
        oldName = zone.getPlayerAlias();
        zone.setPlayerAlias(newMapDisplayName);
        if (oldName.equals(newMapDisplayName)) return zone.getPlayerAlias();
        MapTool.serverCommand().changeZoneDispName(zone.getId(), newMapDisplayName);
        if (zone == MapTool.getFrame().getCurrentZoneRenderer().getZone())
            MapTool.getFrame().setCurrentZoneRenderer(MapTool.getFrame().getCurrentZoneRenderer());
        if (oldName.equals(zone.getPlayerAlias()))
            throw new AnnotatedFunctionException("macro.function.map.duplicateDisplay");
        return zone.getPlayerAlias();
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 2, maxParameters = 2)
    public Object copyMap(List<Object> parameters) throws AnnotatedFunctionException {
        String oldName = parameters.get(0).toString();
        String newName = parameters.get(1).toString();
        Zone oldMap = getNamedMap(oldName).getZone();
        Zone newMap = new Zone(oldMap);
        newMap.setName(newName);
        MapTool.addZone(newMap, false);
        MapTool.serverCommand().putZone(newMap);
        return newMap.getName();
    }

    @MacroFunction
    @Trusted
    public Object getAllMapNames() {
        return getAllMapNames(",");
    }

    @MacroFunction
    @Trusted
    public Object getAllMapNames(Object delimiter) {
        return FunctionUtil.delimited(
                getMapAttributes(zone -> true, Zone::getName),
                delimiter.toString()
        );
    }

    @MacroFunction
    public Object getVisibleMapNames() {
        return getVisibleMapNames(",");
    }

    @MacroFunction
    public Object getVisibleMapNames(Object delimiter) {
        return FunctionUtil.delimited(
                getMapAttributes(Zone::isVisible, Zone::getName),
                delimiter.toString()
        );
    }

    @MacroFunction
    @Trusted
    public Object getAllMapDisplayNames() {
        return getAllMapDisplayNames(",");
    }

    @MacroFunction
    @Trusted
    public Object getAllMapDisplayNames(Object delimiter) {
        return FunctionUtil.delimited(
                getMapAttributes(zone -> true, Zone::getPlayerAlias),
                delimiter.toString()
        );
    }

    @MacroFunction
    public Object getVisibleMapDisplayNames() {
        return getVisibleMapDisplayNames(",");
    }

    @MacroFunction
    public Object getVisibleMapDisplayNames(Object delimiter) {
        return FunctionUtil.delimited(
                getMapAttributes(Zone::isVisible, Zone::getPlayerAlias),
                delimiter.toString()
        );
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 1)
    public Object getMapName(List<Object> parameters) throws AnnotatedFunctionException {
        String dispName = parameters.get(0).toString();

        for (ZoneRenderer zr : MapTool.getFrame().getZoneRenderers()) {
            if (zr.getZone().getPlayerAlias().equals(dispName)) {
                return zr.getZone().getName();
            }
        }
        throw new AnnotatedFunctionException("macro.function.map.notFound");
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 1)
    public Object setMapSelectButton(List<Object> parameters) {
        // this is kind of a map function? :)
        boolean vis = !parameters.get(0).toString().equals("0");
        if (MapTool.getFrame().getFullsZoneButton() != null) {
            MapTool.getFrame().getFullsZoneButton().setVisible(vis);
        }
        MapTool.getFrame().getToolbarPanel().getMapselect().setVisible(vis);
        return (MapTool.getFrame().getToolbarPanel().getMapselect().isVisible()
                ? BigDecimal.ONE
                : BigDecimal.ZERO);
    }

    private <T> List<T> getMapAttributes(Predicate<Zone> predicate, Function<Zone, T> mapping) {
        List<T> results = new LinkedList<>();
        for (ZoneRenderer zr : MapTool.getFrame().getZoneRenderers()) {
            if (predicate.test(zr.getZone())) {
                results.add(mapping.apply(zr.getZone()));
            }
        }
        return results;
    }

    /**
     * Find the map/zone for a given map name
     *
     * @param mapName String Name of the searched for map.
     * @return ZoneRenderer The map/zone.
     * @throws net.rptools.maptool.client.functions.util.AnnotatedFunctionException if the map is not found
     */
    private ZoneRenderer getNamedMap(final String mapName)
            throws AnnotatedFunctionException {
        ZoneRenderer zr = MapTool.getFrame().getZoneRenderer(mapName);

        if (zr != null) return zr;

        throw new AnnotatedFunctionException("macro.function.moveTokenMap.unknownMap", mapName);
    }
}
