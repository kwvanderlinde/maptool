package net.rptools.maptool.client.functions;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.util.Delimited;
import net.rptools.maptool.client.functions.util.MacroFunction;
import net.rptools.maptool.client.functions.util.Transitional;
import net.rptools.maptool.client.functions.util.Trusted;
import net.rptools.maptool.client.ui.zone.ZoneRenderer;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.ParserException;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class MapFunctions_New {
    @MacroFunction
    @Transitional(minParameters = 0, maxParameters = 0)
    public Object getCurrentMapName(List<Object> parameters) throws ParserException {
        ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
        if (currentZR == null) {
            throw new ParserException(I18N.getText("macro.function.map.none", "getCurrentMapName"));
        } else {
            return currentZR.getZone().getName();
        }
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 0, maxParameters = 1)
    public Object getMapDisplayName(List<Object> parameters) throws ParserException {
        final var functionName = "getMapDisplayName";
        if (parameters.size() == 0) {
            ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
            if (currentZR == null) {
                throw new ParserException(I18N.getText("macro.function.map.none", functionName));
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
                throw new ParserException(I18N.getText("macro.function.map.notFound", functionName));
            } else {
                return foundMap;
            }
        }
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 1)
    public Object setCurrentMap(List<Object> parameters) throws ParserException {
        final var functionName = "setCurrentMap";
        String mapName = parameters.get(0).toString();
        ZoneRenderer zr = getNamedMap(functionName, mapName);
        MapTool.getFrame().setCurrentZoneRenderer(zr);
        return mapName;
    }

    @MacroFunction
    @Transitional(minParameters = 0, maxParameters = 1)
    public Object getMapVisible(List<Object> parameters) throws ParserException {
        final var functionName = "getMapVisible";
        if (parameters.size() > 0) {
            String mapName = parameters.get(0).toString();
            return getNamedMap(functionName, mapName).getZone().isVisible()
                    ? BigDecimal.ONE
                    : BigDecimal.ZERO;
        } else {
            // Return the visibility of the current map/zone
            ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
            if (currentZR == null) {
                throw new ParserException(I18N.getText("macro.function.map.none", functionName));
            } else {
                return currentZR.getZone().isVisible() ? BigDecimal.ONE : BigDecimal.ZERO;
            }
        }
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 2)
    public Object setMapVisible(List<Object> parameters) throws ParserException {
        final var functionName = "setMapVisible";
        boolean visible = FunctionUtil.getBooleanValue(parameters.get(0).toString());
        Zone zone;
        if (parameters.size() > 1) {
            String mapName = parameters.get(1).toString();
            zone = getNamedMap(functionName, mapName).getZone();
        } else {
            ZoneRenderer currentZR = MapTool.getFrame().getCurrentZoneRenderer();
            if (currentZR == null) {
                throw new ParserException(I18N.getText("macro.function.map.none", functionName));
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
    public Object setMapName(List<Object> parameters) throws ParserException {
        final var functionName = "setMapName";
        String oldMapName = parameters.get(0).toString();
        String newMapName = parameters.get(1).toString();
        Zone zone = getNamedMap(functionName, oldMapName).getZone();
        zone.setName(newMapName);
        MapTool.serverCommand().renameZone(zone.getId(), newMapName);
        if (zone == MapTool.getFrame().getCurrentZoneRenderer().getZone())
            MapTool.getFrame().setCurrentZoneRenderer(MapTool.getFrame().getCurrentZoneRenderer());
        return zone.getName();
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 2, maxParameters = 2)
    public Object setMapDisplayName(List<Object> parameters) throws ParserException {
        final var functionName = "setMapDisplayName";
        String mapName = parameters.get(0).toString();
        String newMapDisplayName = parameters.get(1).toString();
        Zone zone = getNamedMap(functionName, mapName).getZone();
        String oldName;
        oldName = zone.getPlayerAlias();
        zone.setPlayerAlias(newMapDisplayName);
        if (oldName.equals(newMapDisplayName)) return zone.getPlayerAlias();
        MapTool.serverCommand().changeZoneDispName(zone.getId(), newMapDisplayName);
        if (zone == MapTool.getFrame().getCurrentZoneRenderer().getZone())
            MapTool.getFrame().setCurrentZoneRenderer(MapTool.getFrame().getCurrentZoneRenderer());
        if (oldName.equals(zone.getPlayerAlias()))
            throw new ParserException(
                    I18N.getText("macro.function.map.duplicateDisplay", functionName));
        return zone.getPlayerAlias();
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 2, maxParameters = 2)
    public Object copyMap(List<Object> parameters) throws ParserException {
        final var functionName = "copyMap";
        String oldName = parameters.get(0).toString();
        String newName = parameters.get(1).toString();
        Zone oldMap = getNamedMap(functionName, oldName).getZone();
        Zone newMap = new Zone(oldMap);
        newMap.setName(newName);
        MapTool.addZone(newMap, false);
        MapTool.serverCommand().putZone(newMap);
        return newMap.getName();
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 0, maxParameters = 1)
    @Delimited(parameterIndex = 0, ifMissing = ",")
    public List<String> getAllMapNames(List<Object> parameters) throws ParserException {
        return getMapAttributes(zone -> true, Zone::getName);
    }

    @MacroFunction
    @Transitional(minParameters = 0, maxParameters = 1)
    @Delimited(parameterIndex = 0, ifMissing = ",")
    public List<String> getVisibleMapNames(List<Object> parameters) throws ParserException {
        return getMapAttributes(Zone::isVisible, Zone::getName);
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 0, maxParameters = 1)
    @Delimited(parameterIndex = 0, ifMissing = ",")
    public List<String> getAllMapDisplayNames(List<Object> parameters) throws ParserException {
        return getMapAttributes(zone -> true, Zone::getPlayerAlias);
    }

    @MacroFunction
    @Transitional(minParameters = 0, maxParameters = 1)
    @Delimited(parameterIndex = 0, ifMissing = ",")
    public List<String> getVisibleMapDisplayNames(List<Object> parameters) throws ParserException {
        return getMapAttributes(Zone::isVisible, Zone::getPlayerAlias);
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 1)
    public Object getMapName(List<Object> parameters) throws ParserException {
        final var functionName = "getMapName";
        String dispName = parameters.get(0).toString();

        for (ZoneRenderer zr : MapTool.getFrame().getZoneRenderers()) {
            if (zr.getZone().getPlayerAlias().equals(dispName)) {
                return zr.getZone().getName();
            }
        }
        throw new ParserException(I18N.getText("macro.function.map.notFound", functionName));
    }

    @MacroFunction
    @Trusted
    @Transitional(minParameters = 1, maxParameters = 1)
    public Object setMapSelectButton(List<Object> parameters) throws ParserException {
        final var functionName = "setMapSelectButton";
        // this is kind of a map function? :)
        boolean vis = !parameters.get(0).toString().equals("0");
        if (MapTool.getFrame().getFullsZoneButton() != null)
            MapTool.getFrame().getFullsZoneButton().setVisible(vis);
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
     * @param functionName String Name of the calling function.
     * @param mapName String Name of the searched for map.
     * @return ZoneRenderer The map/zone.
     * @throws ParserException if the map is not found
     */
    private ZoneRenderer getNamedMap(final String functionName, final String mapName)
            throws ParserException {
        ZoneRenderer zr = MapTool.getFrame().getZoneRenderer(mapName);

        if (zr != null) return zr;

        throw new ParserException(
                I18N.getText("macro.function.moveTokenMap.unknownMap", functionName, mapName));
    }

    // region TODO These are useful for other types of functions too.

    private Object delimited(String delim, List<String> strings) {
        if ("json".equals(delim)) {
            JsonArray jarr = new JsonArray();
            strings.forEach(m -> jarr.add(new JsonPrimitive(m)));
            return jarr;
        } else {
            return StringFunctions.getInstance().join(strings, delim);
        }
    }

    // endregion
}