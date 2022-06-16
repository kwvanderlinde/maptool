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
package net.rptools.maptool.client.functions;

import annotatedmacros.annotations.MacroFunction;
import annotatedmacros.annotations.Trusted;
import java.util.function.Function;
import java.util.function.Predicate;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.ParserException;

public class MapFunctions_New {
  @MacroFunction
  public String getCurrentMapId() {
    return MapTool.getFrame().getCurrentZoneRenderer().getZone().getId().toString();
  }

  @MacroFunction
  public String getCurrentMapName() {
    return MapTool.getFrame().getCurrentZoneRenderer().getZone().getName();
  }

  @MacroFunction
  @Trusted
  public String getMapDisplayName() {
    return getMapDisplayName(MapTool.getFrame().getCurrentZoneRenderer().getZone());
  }

  @MacroFunction
  @Trusted
  public String getMapDisplayName(Zone zone) {
    return zone.getPlayerAlias();
  }

  @MacroFunction
  @Trusted
  public String setCurrentMap(Zone zone) {
    ZoneRenderer zr = MapTool.getFrame().getZoneRenderer(zone);
    MapTool.getFrame().setCurrentZoneRenderer(zr);
    return zone.getName();
  }

  @MacroFunction
  public boolean getMapVisible() {
    return getMapVisible(MapTool.getFrame().getCurrentZoneRenderer().getZone());
  }

  @MacroFunction
  public boolean getMapVisible(Zone zone) {
    // TODO Is this not trusted? Doesn't this let players query for other maps?
    return zone.isVisible();
  }

  @MacroFunction
  @Trusted
  public boolean setMapVisible(boolean visible) {
    return setMapVisible(visible, MapTool.getFrame().getCurrentZoneRenderer().getZone());
  }

  @MacroFunction
  @Trusted
  public boolean setMapVisible(boolean visible, Zone zone) {
    // Set the zone and return the visibility of the current map/zone
    zone.setVisible(visible);
    MapTool.serverCommand().setZoneVisibility(zone.getId(), zone.isVisible());
    MapTool.getFrame().getZoneMiniMapPanel().flush();
    MapTool.getFrame().repaint();
    return zone.isVisible();
  }

  @MacroFunction
  @Trusted
  public String setMapName(Zone zone, String newMapName) {
    zone.setName(newMapName);
    MapTool.serverCommand().renameZone(zone.getId(), newMapName);
    if (zone == MapTool.getFrame().getCurrentZoneRenderer().getZone()) {
      MapTool.getFrame().setCurrentZoneRenderer(MapTool.getFrame().getCurrentZoneRenderer());
    }
    return zone.getName();
  }

  @MacroFunction
  @Trusted
  public String setMapDisplayName(Zone zone, String newMapDisplayName) throws ParserException {
    String oldName = zone.getPlayerAlias();
    zone.setPlayerAlias(newMapDisplayName);
    if (oldName.equals(newMapDisplayName)) {
      return zone.getPlayerAlias();
    }
    MapTool.serverCommand().changeZoneDispName(zone.getId(), newMapDisplayName);
    if (zone == MapTool.getFrame().getCurrentZoneRenderer().getZone()) {
      MapTool.getFrame().setCurrentZoneRenderer(MapTool.getFrame().getCurrentZoneRenderer());
    }
    if (oldName.equals(zone.getPlayerAlias())) {
      throw new ParserException(
          I18N.getText("macro.function.map.duplicateDisplay", "setMapDisplayName"));
    }
    return zone.getPlayerAlias();
  }

  @MacroFunction
  @Trusted
  public String copyMap(Zone oldMap, String newName) {
    Zone newMap = new Zone(oldMap);
    newMap.setName(newName);
    MapTool.addZone(newMap, false);
    MapTool.serverCommand().putZone(newMap);
    return newMap.getName();
  }

  @MacroFunction
  public Object getVisibleMapNames() {
    return getVisibleMapNames(",");
  }

  @MacroFunction
  public Object getVisibleMapNames(String delim) {
    return getZoneNames(Zone::isVisible, Zone::getName, delim);
  }

  @MacroFunction
  @Trusted
  public Object getAllMapNames() {
    return getAllMapNames(",");
  }

  @MacroFunction
  @Trusted
  public Object getAllMapNames(String delim) {
    return getZoneNames(z -> true, Zone::getName, delim);
  }

  private Object getZoneNames(Predicate<Zone> filter, Function<Zone, String> getter, String delim) {
    final var zoneNames =
        MapTool.getFrame().getZoneRenderers().stream()
            .map(ZoneRenderer::getZone)
            .filter(filter)
            .map(getter);
    return FunctionUtil.delimited(zoneNames.toList(), delim);
  }

  @MacroFunction
  @Trusted
  public String getMapName(String displayName) throws ParserException {
    // Note: no use of `Zone` parameter here since it *must* be the display name passed in, not a
    // name or GUID.
    for (ZoneRenderer zr : MapTool.getFrame().getZoneRenderers()) {
      if (zr.getZone().getPlayerAlias().equals(displayName)) {
        return zr.getZone().getName();
      }
    }
    throw new ParserException(
        I18N.getText("macro.function.moveTokenMap.unknownMap", "getMapName", displayName));
  }

  @MacroFunction
  @Trusted
  public boolean setMapSelectButton(String param0) {
    // this is kind of a map function? :)/
    // TODO Should we generalize this via FunctionUtil.getBooleanValue()?
    boolean vis = !param0.equals("0");
    if (MapTool.getFrame().getFullsZoneButton() != null) {
      MapTool.getFrame().getFullsZoneButton().setVisible(vis);
    }
    MapTool.getFrame().getToolbarPanel().getMapselect().setVisible(vis);
    return MapTool.getFrame().getToolbarPanel().getMapselect().isVisible();
  }
}
