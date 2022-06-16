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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.json.JSONMacroFunctions;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.client.ui.zone.vbl.TokenVBL;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.parser.ParserException;

public class Topology_Functions_New {
  private static final String[] paramTranslate = new String[] {"tx", "ty"};
  private static final String[] paramScale = new String[] {"sx", "sy"};

  private enum Shape {
    RECTANGLE,
    POLYGON,
    CROSS,
    CIRCLE,
    AUTO,
    NONE
  }

  @MacroFunction(name = "drawVBL")
  @Trusted
  public void drawWallVBL(Object shapes) throws ParserException {
    drawEraseTopology("drawVBL", Zone.TopologyType.WALL_VBL, false, shapes);
  }

  @MacroFunction
  @Trusted
  public void drawHillVBL(Object shapes) throws ParserException {
    drawEraseTopology("drawHillVBL", Zone.TopologyType.HILL_VBL, false, shapes);
  }

  @MacroFunction
  @Trusted
  public void drawPitVBL(Object shapes) throws ParserException {
    drawEraseTopology("drawPitVBL", Zone.TopologyType.PIT_VBL, false, shapes);
  }

  @MacroFunction
  @Trusted
  public void drawMBL(Object shapes) throws ParserException {
    drawEraseTopology("drawMBL", Zone.TopologyType.MBL, false, shapes);
  }

  @MacroFunction(name = "eraseVBL")
  @Trusted
  public void eraseWallVBL(Object shapes) throws ParserException {
    drawEraseTopology("drawVBL", Zone.TopologyType.WALL_VBL, true, shapes);
  }

  @MacroFunction
  @Trusted
  public void eraseHillVBL(Object shapes) throws ParserException {
    drawEraseTopology("drawHillVBL", Zone.TopologyType.HILL_VBL, true, shapes);
  }

  @MacroFunction
  @Trusted
  public void erasePitVBL(Object shapes) throws ParserException {
    drawEraseTopology("drawPitVBL", Zone.TopologyType.PIT_VBL, true, shapes);
  }

  @MacroFunction
  @Trusted
  public void eraseMBL(Object shapes) throws ParserException {
    drawEraseTopology("drawMBL", Zone.TopologyType.MBL, false, shapes);
  }

  @MacroFunction(name = "getVBL")
  @Trusted
  public Object getWallVBL(Object shapes) throws ParserException {
    return getTopology("getVBL", Zone.TopologyType.WALL_VBL, shapes, false);
  }

  @MacroFunction(name = "getVBL")
  @Trusted
  public Object getWallVBL(Object shapes, boolean simpleJSON) throws ParserException {
    return getTopology("getVBL", Zone.TopologyType.WALL_VBL, shapes, simpleJSON);
  }

  @MacroFunction
  @Trusted
  public Object getHillVBL(Object shapes) throws ParserException {
    return getTopology("getHillVBL", Zone.TopologyType.HILL_VBL, shapes, false);
  }

  @MacroFunction
  @Trusted
  public Object getHillVBL(Object shapes, boolean simpleJSON) throws ParserException {
    return getTopology("getHillVBL", Zone.TopologyType.HILL_VBL, shapes, simpleJSON);
  }

  @MacroFunction
  @Trusted
  public Object getPitVBL(Object shapes) throws ParserException {
    return getTopology("getPitVBL", Zone.TopologyType.PIT_VBL, shapes, false);
  }

  @MacroFunction
  @Trusted
  public Object getPitVBL(Object shapes, boolean simpleJSON) throws ParserException {
    return getTopology("getPitVBL", Zone.TopologyType.PIT_VBL, shapes, simpleJSON);
  }

  @MacroFunction
  @Trusted
  public Object getMBL(Object shapes) throws ParserException {
    return getTopology("getMBL", Zone.TopologyType.MBL, shapes, false);
  }

  @MacroFunction
  @Trusted
  public Object getMBL(Object shapes, boolean simpleJSON) throws ParserException {
    return getTopology("getMBL", Zone.TopologyType.MBL, shapes, simpleJSON);
  }

  private void drawEraseTopology(
      final String functionName,
      Zone.TopologyType topologyType,
      final boolean erase,
      final Object shapes)
      throws ParserException {
    ZoneRenderer renderer = MapTool.getFrame().getCurrentZoneRenderer();
    JsonElement json = JSONMacroFunctions.getInstance().asJsonElement(shapes);

    JsonArray topologyArray;
    if (json.isJsonArray()) {
      topologyArray = json.getAsJsonArray();
    } else if (json.isJsonObject()) {
      topologyArray = new JsonArray();
      topologyArray.add(json.getAsJsonObject());
    } else {
      throw new ParserException(
          I18N.getText(
              "macro.function.json.unknownType",
              json == null ? shapes.toString() : json.toString(),
              functionName));
    }

    for (int i = 0; i < topologyArray.size(); i++) {
      JsonObject topologyObject = topologyArray.get(i).getAsJsonObject();

      final var topologyShape =
          Shape.valueOf(topologyObject.get("shape").getAsString().toUpperCase());

      Area newArea =
          switch (topologyShape) {
            case RECTANGLE -> makeRectangle(topologyObject, functionName);
            case POLYGON -> makePolygon(topologyObject, functionName);
            case CROSS -> makeCross(topologyObject, functionName);
            case CIRCLE -> makeCircle(topologyObject, functionName);
            case NONE -> null;
            default -> null;
          };
      if (newArea != null) {
        TokenVBL.renderTopology(renderer, newArea, erase, topologyType);
      }
    }
  }

  private JsonArray getTopology(
      final String functionName, Zone.TopologyType topologyType, Object shapes, boolean simpleJSON)
      throws ParserException {
    // region TODO This intro is identical to drawEraseTopology().
    ZoneRenderer renderer = MapTool.getFrame().getCurrentZoneRenderer();
    JsonElement json = JSONMacroFunctions.getInstance().asJsonElement(shapes);
    JsonArray topologyArray;
    if (json.isJsonArray()) {
      topologyArray = json.getAsJsonArray();
    } else if (json.isJsonObject()) {
      topologyArray = new JsonArray();
      topologyArray.add(json.getAsJsonObject());
    } else {
      throw new ParserException(
          I18N.getText(
              "macro.function.json.unknownType",
              json == null ? shapes.toString() : json.toString(),
              functionName));
    }
    // endregion

    Area topologyArea = new Area();
    for (int i = 0; i < topologyArray.size(); i++) {
      JsonObject topologyObject = topologyArray.get(i).getAsJsonObject();
      Area tempTopologyArea = getTopology(renderer, topologyObject, topologyType, functionName);
      topologyArea.add(tempTopologyArea);
    }

    if (simpleJSON) {
      // Build a single list of points for the area.
      return getAreaPoints(topologyArea);
    } else {
      // Build separate objects for each area.
      JsonArray allShapes = new JsonArray();
      var areaShape = getAreaShapeObject(topologyArea);
      if (areaShape != null) {
        allShapes.add(areaShape);
      }
      return allShapes;
    }
  }

  /**
   * Auto generate topology using token topology optimzation options
   *
   * @param token the token to generate topology from
   * @param jsonParms the parameters json passed in
   * @return the generated token topology as an Area
   * @throws ParserException
   */
  private Area autoGenerateTopology(Token token, JsonObject jsonParms, String functionName)
      throws ParserException {
    String loggedFunctionName = functionName + "[Auto]";

    final int sensitivity = getJSONint(jsonParms, "sensitivity", 10, 0, 255, loggedFunctionName);
    final int inverse = getJSONint(jsonParms, "inverse", 0, 0, 1, loggedFunctionName);
    final int r = getJSONint(jsonParms, "r", 0, 0, 255, loggedFunctionName);
    final int g = getJSONint(jsonParms, "g", 0, 0, 255, loggedFunctionName);
    final int b = getJSONint(jsonParms, "b", 0, 0, 255, loggedFunctionName);
    final int a = getJSONint(jsonParms, "a", 0, 0, 255, loggedFunctionName);
    final int level = getJSONint(jsonParms, "level", 2, 0, 100, loggedFunctionName);
    final String method =
        getJSONasString(
            jsonParms,
            "method",
            TokenVBL.JTS_SimplifyMethodType.getDefault().name(),
            loggedFunctionName);

    Color color = new Color(r, g, b, a);
    final boolean inverseTopology = inverse == 1;

    return TokenVBL.createOptimizedTopologyArea(
        token, sensitivity, inverseTopology, color, level, method);
  }

  /**
   * Get the required parameters needed from the JSON to draw a rectangle and render as topology.
   *
   * @param topologyObject The JsonObject containing all the coordinates and values to needed to
   *     draw a rectangle.
   * @return the topology area
   * @throws ParserException If the minimum required parameters are not present in the JSON.
   */
  private Area makeRectangle(JsonObject topologyObject, String funcname) throws ParserException {
    funcname += "[Rectangle]";
    // Required Parameters
    String[] requiredParms = {"x", "y", "w", "h"};
    if (!jsonKeysExist(topologyObject, requiredParms, funcname)) {
      throw new ParserException(
          I18N.getText("macro.function.general.argumentKeyTypeI", funcname, "{x,y,w,h}"));
    }

    int x = getJSONint(topologyObject, "x", funcname);
    int y = getJSONint(topologyObject, "y", funcname);
    int w = getJSONint(topologyObject, "w", funcname);
    int h = getJSONint(topologyObject, "h", funcname);

    // Optional Parameters
    int fill = getJSONint(topologyObject, "fill", funcname);
    double s = getJSONdouble(topologyObject, "scale", funcname);
    double r = getJSONdouble(topologyObject, "r", funcname);
    double facing = getJSONdouble(topologyObject, "facing", funcname);
    float t = (float) getJSONdouble(topologyObject, "thickness", funcname);
    boolean useFacing = topologyObject.has("facing");

    if (t < 2) {
      t = 2;
    } // Set default thickness to 2 if null or negative
    if (t % 2 != 0) {
      t -= 1;
    } // Set thickness an even number so we don't split .5 pixels on BasicStroke
    if (t > w - 2) {
      t = w - 2;
    } // Set thickness to width - 2 pixels if thicker
    if (t > h - 2) {
      t = h - 2;
    } // Set thickness to height -2 pixels if thicker
    if (w < 4) {
      w = 4;
    } // Set width to min of 4, as a 2 pixel thick rectangle as to be at least 4 pixels wide
    if (h < 4) {
      h = 4;
    } // Set height to min of 4, as a 2 pixel thick rectangle as to be at least 4 pixels high

    // Apply Scaling if requested
    double w2;
    double h2;
    if (s != 0) {
      // Subtracting "thickness" so drawing stays within "bounds"
      w2 = (w * s) - t;
      h2 = (h * s) - t;
    } else {
      // Subtracting "thickness" so drawing stays within "bounds"
      w2 = w - t;
      h2 = h - t;
    }
    x = (int) (x + (t / 2));
    y = (int) (y + (t / 2));
    w = (int) w2;
    h = (int) h2;
    // Apply Thickness, defaults to 2f
    BasicStroke stroke = new BasicStroke(t != 0f ? t : 2f);

    // Create the rectangle, unfilled
    Area area = new Area(stroke.createStrokedShape(new java.awt.Rectangle(x, y, w, h)));

    // Fill in the rectangle if requested
    if (fill != 0) {
      area.add(new Area(new java.awt.Rectangle(x, y, w, h)));
    }

    AffineTransform atArea = new AffineTransform();
    applyTranslate(funcname, atArea, topologyObject, paramTranslate);

    // Rotate the Polygon if requested
    if (useFacing || r != 0) {
      // Find the center x,y coords of the rectangle
      int rx = area.getBounds().x + (area.getBounds().width / 2);
      int ry = area.getBounds().y + (area.getBounds().height / 2);

      // Override rx,ry coords if supplied
      String rParms[] = {"rx", "ry"};
      if (jsonKeysExist(topologyObject, rParms, funcname)) {
        rx = getJSONint(topologyObject, "rx", funcname);
        ry = getJSONint(topologyObject, "ry", funcname);
      }
      if (useFacing) {
        r = -(facing + 90);
      }
      atArea.rotate(Math.toRadians(r), rx, ry);
    }
    applyScale(funcname, atArea, topologyObject, paramScale);

    if (!atArea.isIdentity()) {
      area.transform(atArea);
    }

    return area;
  }

  /**
   * Get the required parameters needed from the JSON to draw a Polygon and render as topology.
   *
   * @param topologyObject The JsonObject containing all the coordinates and values to needed to
   *     draw a polygon.
   * @return the topology area
   * @throws ParserException If the minimum required parameters are not present in the JSON.
   */
  private Area makePolygon(JsonObject topologyObject, String funcname) throws ParserException {
    funcname += "[Polygon]";
    String requiredParms[] = {"points"};
    if (!jsonKeysExist(topologyObject, requiredParms, funcname)) {
      throw new ParserException(
          I18N.getText("macro.function.general.argumentKeyTypeA", funcname, "points"));
    }

    // Get all the x,y coords for the Polygon, must have at least 2
    JsonArray points = topologyObject.get("points").getAsJsonArray();
    if (points.size() < 2) {
      throw new ParserException(
          I18N.getText("macro.function.json.getInvalidEndIndex", funcname, 2, points.size()));
    }
    // Optional Parameters
    int fill = getJSONint(topologyObject, "fill", funcname);
    int close = getJSONint(topologyObject, "close", funcname);
    double r = getJSONdouble(topologyObject, "r", funcname);
    double facing = getJSONdouble(topologyObject, "facing", funcname);
    float t = (float) getJSONdouble(topologyObject, "thickness", funcname);
    boolean useFacing = topologyObject.has("facing");

    if (!topologyObject.has("thickness")) {
      t = 2; // Set default thickness if no value is passed.
    }

    Area area = null;

    if (close == 0) {
      // User requests for polygon to not be closed, so a Path is used
      Path2D path = new Path2D.Double();
      double lastX = 0;
      double lastY = 0;

      for (int i = 0; i < points.size(); i++) {
        JsonObject point = points.get(i).getAsJsonObject();

        String requiredPointParms[] = {"x", "y"};
        if (!jsonKeysExist(point, requiredPointParms, funcname)) {
          throw new ParserException(
              I18N.getText("macro.function.general.argumentKeyTypeI", funcname, "{x,y}"));
        }

        double x = getJSONdouble(point, "x", funcname);
        double y = getJSONdouble(point, "y", funcname);

        if (path.getCurrentPoint() == null) {
          path.moveTo(x, y);
        } else if (!(lastX == x && lastY == y)) {
          path.lineTo(x, y);
          lastX = x;
          lastY = y;
        }
      }
      BasicStroke stroke =
          new BasicStroke(Math.max(t, 0f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
      area = new Area(stroke.createStrokedShape(path));
    } else {
      // User requests for polygon to be closed, so a Polygon is used which is automatically
      // closed
      Polygon poly = new Polygon();

      for (int i = 0; i < points.size(); i++) {
        JsonObject point = points.get(i).getAsJsonObject();

        String requiredPointParms[] = {"x", "y"};
        if (!jsonKeysExist(point, requiredPointParms, funcname)) {
          throw new ParserException(
              I18N.getText("macro.function.general.argumentKeyTypeI", funcname, "{x,y}"));
        }

        int x = getJSONint(point, "x", funcname);
        int y = getJSONint(point, "y", funcname);

        poly.addPoint(x, y);
      }
      // A strokedShape will not be filled in and have a defined thickness.
      if (fill == 0) {
        BasicStroke stroke =
            new BasicStroke(Math.max(t, 0f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        area = new Area(stroke.createStrokedShape(poly));
      } else {
        area = new Area(poly);
      }
    }
    AffineTransform atArea = new AffineTransform();
    applyTranslate(funcname, atArea, topologyObject, paramTranslate);

    // Rotate the Polygon if requested
    if (useFacing || r != 0) {
      // Find the center x,y coords of the rectangle
      int rx = area.getBounds().x + (area.getBounds().width / 2);
      int ry = area.getBounds().y + (area.getBounds().height / 2);

      // Override rx,ry coords if supplied
      String rParms[] = {"rx", "ry"};
      if (jsonKeysExist(topologyObject, rParms, funcname)) {
        rx = getJSONint(topologyObject, "rx", funcname);
        ry = getJSONint(topologyObject, "ry", funcname);
      }
      if (useFacing) {
        r = -(facing + 90);
      }

      atArea.rotate(Math.toRadians(r), rx, ry);
    }
    applyScale(funcname, atArea, topologyObject, paramScale);

    if (!atArea.isIdentity()) {
      area.transform(atArea);
    }

    return area;
  }

  /**
   * Get the required parameters needed from the JSON to draw two Polygon 'lines' and render as
   * topology. This is a convenience function to draw two lines perpendicular to each other to form
   * a "cross" commonly used to block LOS for objects like Trees but still show most of the image.
   *
   * @param topologyObject The JsonObject containing all the coordinates and values to needed to
   *     draw a cross.
   * @return the topology area
   * @throws ParserException If the minimum required parameters are not present in the JSON.
   */
  private Area makeCross(JsonObject topologyObject, String funcname) throws ParserException {
    funcname += "[Cross]";
    // Required Parameters
    String requiredParms[] = {"x", "y", "w", "h"};
    if (!jsonKeysExist(topologyObject, requiredParms, funcname)) {
      throw new ParserException(
          I18N.getText("macro.function.general.argumentKeyTypeI", funcname, "{x,y,w,h}"));
    }

    int x = getJSONint(topologyObject, "x", funcname);
    int y = getJSONint(topologyObject, "y", funcname);
    int w = getJSONint(topologyObject, "w", funcname);
    int h = getJSONint(topologyObject, "h", funcname);

    // Optional Parameters
    double s = getJSONdouble(topologyObject, "scale", funcname);
    double r = getJSONdouble(topologyObject, "r", funcname);
    double facing = getJSONdouble(topologyObject, "facing", funcname);
    float t = (float) getJSONdouble(topologyObject, "thickness", funcname);
    boolean useFacing = topologyObject.has("facing");

    // Apply Scaling if requested
    if (s != 0) {
      double w2 = w * s;
      double h2 = h * s;
      x = (int) (x - ((w2 - w) / 2));
      y = (int) (y - ((h2 - h) / 2));
      w = (int) w2;
      h = (int) h2;
    }
    // Apply Thickness, defaults to 2f
    BasicStroke stroke = new BasicStroke(t != 0f ? t : 2f);

    // Create the first line
    Polygon line = new Polygon();
    line.addPoint(x, y);
    line.addPoint(x + w, y + h);
    Area area = new Area(stroke.createStrokedShape(line));

    // Create the second line
    line.reset();
    line.addPoint(x, y + h);
    line.addPoint(x + w, y);
    area.add(new Area(stroke.createStrokedShape(line)));

    AffineTransform atArea = new AffineTransform();
    applyTranslate(funcname, atArea, topologyObject, paramTranslate);

    // Rotate the Polygon if requested
    if (useFacing || r != 0) {
      // Find the center x,y coords of the rectangle
      int rx = area.getBounds().x + (area.getBounds().width / 2);
      int ry = area.getBounds().y + (area.getBounds().height / 2);

      // Override rx,ry coords if supplied
      String rParms[] = {"rx", "ry"};
      if (jsonKeysExist(topologyObject, rParms, funcname)) {
        rx = getJSONint(topologyObject, "rx", funcname);
        ry = getJSONint(topologyObject, "ry", funcname);
      }
      if (useFacing) {
        r = -(facing + 90);
      }

      atArea.rotate(Math.toRadians(r), rx, ry);
    }
    applyScale(funcname, atArea, topologyObject, paramScale);

    if (!atArea.isIdentity()) {
      area.transform(atArea);
    }

    return area;
  }

  /**
   * Get the required parameters needed from the JSON to draw an approximate circle and render as
   * topology.
   *
   * @param topologyObject The JsonObject containing all the coordinates and values to needed to
   *     draw a circle.
   * @return the topology area
   * @throws ParserException If the minimum required parameters are not present in the JSON.
   */
  private Area makeCircle(JsonObject topologyObject, String funcname) throws ParserException {
    funcname += "[Circle]";
    // Required Parameters
    String requiredParms[] = {"x", "y", "radius", "sides"};
    if (!jsonKeysExist(topologyObject, requiredParms, funcname)) {
      throw new ParserException(
          I18N.getText("macro.function.general.argumentKeyTypeI", funcname, "{x,y,radius,sides}"));
    }

    int x = getJSONint(topologyObject, "x", funcname);
    int y = getJSONint(topologyObject, "y", funcname);
    double radius = getJSONdouble(topologyObject, "radius", funcname);
    double sides = getJSONdouble(topologyObject, "sides", funcname);

    // Optional Parameters
    int fill = getJSONint(topologyObject, "fill", funcname);
    double rotation = getJSONdouble(topologyObject, "r", funcname);
    double facing = getJSONdouble(topologyObject, "facing", funcname);
    double scale = getJSONdouble(topologyObject, "scale", funcname);
    float t = (float) getJSONdouble(topologyObject, "thickness", funcname);
    boolean useFacing = topologyObject.has("facing");

    // Lets set some sanity limits
    if (sides < 3) {
      sides = 3;
    }
    if (sides > 100) {
      sides = 100;
    }

    // Apply Scaling if requested
    if (scale != 0) {
      radius = radius * scale;
    }

    // Subtracting "thickness" so drawing stays within "bounds"
    radius -= ((t / 2));
    x -= 1;
    y -= 1;

    // Apply Thickness, defaults to 2f
    BasicStroke stroke = new BasicStroke(t != 0f ? t : 2f);
    Polygon fakeCircle = new Polygon();

    double PI = Math.PI;

    for (int i = 0; i < sides; i++) {
      int Xi = (int) (x + radius * Math.cos(2.0 * PI * i / sides));
      int Yi = (int) (y + radius * Math.sin(2.0 * PI * i / sides));
      fakeCircle.addPoint(Xi, Yi);
    }
    // Create the circle, unfilled
    Area area = new Area(stroke.createStrokedShape(fakeCircle));

    // Fill in the circle if requested
    if (fill != 0) {
      area.add(new Area(fakeCircle));
    }

    AffineTransform atArea = new AffineTransform();
    applyTranslate(funcname, atArea, topologyObject, paramTranslate);

    // Rotate the Polygon if requested
    if (useFacing || rotation != 0) {
      // Find the center x,y coords of the rectangle
      int rx = area.getBounds().x + (area.getBounds().width / 2);
      int ry = area.getBounds().y + (area.getBounds().height / 2);

      // Override rx,ry coords if supplied
      String rParms[] = {"rx", "ry"};
      if (jsonKeysExist(topologyObject, rParms, funcname)) {
        rx = getJSONint(topologyObject, "rx", funcname);
        ry = getJSONint(topologyObject, "ry", funcname);
      }
      if (useFacing) {
        rotation = -(facing + 90);
      }

      atArea.rotate(Math.toRadians(rotation), rx, ry);
    }
    applyScale(funcname, atArea, topologyObject, paramScale);

    if (!atArea.isIdentity()) {
      area.transform(atArea);
    }

    return area;
  }

  private void applyTranslate(
      String funcname, AffineTransform at, JsonObject topologyObject, String[] params)
      throws ParserException {
    if (jsonKeysExist(topologyObject, params, funcname)) {
      double tx = getJSONdouble(topologyObject, "tx", funcname);
      double ty = getJSONdouble(topologyObject, "ty", funcname);
      at.translate(tx, ty);
    }
  }

  private void applyScale(
      String funcname, AffineTransform at, JsonObject topologyObject, String[] params)
      throws ParserException {
    if (jsonKeysExist(topologyObject, params, funcname)) {
      double sx = getJSONdouble(topologyObject, "sx", funcname);
      double sy = getJSONdouble(topologyObject, "sy", funcname);
      at.scale(sx, sy);
    }
  }

  /**
   * Get the required parameters needed from the JSON to get/set topology within a defined
   * rectangle.
   *
   * @param renderer Reference to the ZoneRenderer
   * @param topologyObject JsonObject containing all the coordinates and values needed to draw a
   *     rectangle.
   * @param topologyType The topology type to operate on.
   * @return the topology area.
   * @throws ParserException If the minimum required parameters are not present in the JSON.
   */
  private Area getTopology(
      ZoneRenderer renderer,
      JsonObject topologyObject,
      Zone.TopologyType topologyType,
      String funcname)
      throws ParserException {
    // Required Parameters
    String requiredParms[] = {"x", "y", "w", "h"};
    if (!jsonKeysExist(topologyObject, requiredParms, funcname)) {
      throw new ParserException(
          I18N.getText("macro.function.general.argumentKeyTypeI", "{x,y,w,h}", funcname));
    }

    int x = getJSONint(topologyObject, "x", funcname);
    int y = getJSONint(topologyObject, "y", funcname);
    int w = getJSONint(topologyObject, "w", funcname);
    int h = getJSONint(topologyObject, "h", funcname);

    // Optional Parameters
    int fill = getJSONint(topologyObject, "fill", funcname);
    double s = getJSONdouble(topologyObject, "scale", funcname);
    double r = getJSONdouble(topologyObject, "r", funcname);
    double facing = getJSONdouble(topologyObject, "facing", funcname);
    float t = (float) getJSONdouble(topologyObject, "thickness", funcname);
    boolean useFacing = topologyObject.has("facing");

    // Allow thickness of 0 and default to 0 to allow complete capture of topology under a token.
    if (t < 0) {
      t = 0; // Set default thickness to 0 if null or negative
    }
    if (w < 4) {
      w = 4; // Set width to min of 4, as a 2 pixel thick rectangle as to
    }
    // be at least 4 pixels wide
    if (h < 4) {
      h = 4; // Set height to min of 4, as a 2 pixel thick rectangle as to
    }
    // be at least 4 pixels high

    // Apply Scaling if requested
    double w2;
    double h2;
    if (s != 0) {
      // Subtracting "thickness" so drawing stays within "bounds"
      w2 = (w * s) - t;
      h2 = (h * s) - t;
    } else {
      // Subtracting "thickness" so drawing stays within "bounds"
      w2 = w - t;
      h2 = h - t;
    }
    x = (int) (x + (t / 2));
    y = (int) (y + (t / 2));
    w = (int) w2;
    h = (int) h2;
    // Apply Thickness, defaults handled above
    BasicStroke stroke = new BasicStroke(t);

    // Create the rectangle, unfilled
    Area area = new Area(stroke.createStrokedShape(new java.awt.Rectangle(x, y, w, h)));

    // Fill in the rectangle if requested
    if (fill != 0) {
      area.add(new Area(new java.awt.Rectangle(x, y, w, h)));
    }

    // Rotate the rectangle if requested
    if (useFacing || r != 0) {
      // Find the center x,y coords of the rectangle
      int rx = x + (w / 2);
      int ry = y + (h / 2);

      // Override rx,ry coords if supplied
      String[] rParms = {"rx", "ry"};
      if (jsonKeysExist(topologyObject, rParms, funcname)) {
        rx = getJSONint(topologyObject, "rx", funcname);
        ry = getJSONint(topologyObject, "ry", funcname);
      }
      if (useFacing) {
        r = -(facing + 90);
      }

      AffineTransform atArea = new AffineTransform();
      atArea.rotate(Math.toRadians(r), rx, ry);
      area.transform(atArea);
    }

    // Note: when multiple modes are requested, the overlap between each topology is returned.
    var zone = renderer.getZone();
    var topology = zone.getTopology(topologyType);
    area.intersect(topology);

    return area;
  }

  private JsonObject getAreaShapeObject(Area area) {
    // Each shape will be its own json object which each object contains an  array of x,y coords
    JsonObject polygon = new JsonObject();

    polygon.addProperty("generated", 1);
    polygon.addProperty("shape", "polygon");
    polygon.addProperty("fill", 1);
    polygon.addProperty("close", 1);
    polygon.addProperty("thickness", 0);

    JsonArray points = new JsonArray();
    consumeAreaPoints(
        area,
        (x, y) -> {
          var point = new JsonObject();
          point.addProperty("x", x);
          point.addProperty("y", y);
          points.add(point);
        });
    if (points.isEmpty()) {
      return null;
    }
    polygon.add("points", points);

    return polygon;
  }

  private JsonArray getAreaPoints(Area area) {
    JsonArray allPoints = new JsonArray();
    consumeAreaPoints(
        area,
        (x, y) -> {
          allPoints.add(x);
          allPoints.add(y);
        });
    return allPoints;
  }

  private void consumeAreaPoints(Area area, BiConsumer<Double, Double> pointConsumer) {
    ArrayList<double[]> areaPoints = new ArrayList<>();
    double[] coords = new double[6];

    for (PathIterator pi = area.getPathIterator(null); !pi.isDone(); pi.next()) {
      // The type will be SEG_LINETO, SEG_MOVETO, or SEG_CLOSE
      // Because the Area is composed of straight lines
      int type = pi.currentSegment(coords);

      // We record a double array of {segment type, x coord, y coord}
      double[] pathIteratorCoords = {type, coords[0], coords[1]};
      areaPoints.add(pathIteratorCoords);
    }
    // Now that we have the Area defined as commands, lets record the points

    double[] defaultPos = null;
    double[] moveTo = null;

    for (double[] currentElement : areaPoints) {
      // 2 decimals is precise enough, we will deal in .5 pixels mostly.
      currentElement[1] = Math.floor(currentElement[1] * 100) / 100;
      currentElement[2] = Math.floor(currentElement[2] * 100) / 100;

      // Make the lines
      if (currentElement[0] == PathIterator.SEG_MOVETO) {
        if (defaultPos == null) {
          defaultPos = currentElement;
        } else {
          pointConsumer.accept(defaultPos[1], defaultPos[2]);
        }
        moveTo = currentElement;

        pointConsumer.accept(currentElement[1], currentElement[2]);
      } else if (currentElement[0] == PathIterator.SEG_LINETO) {
        pointConsumer.accept(currentElement[1], currentElement[2]);
      } else if (currentElement[0] == PathIterator.SEG_CLOSE) {
        pointConsumer.accept(moveTo[1], moveTo[2]);
      } else {
        // System.out.println("in getAreaPoints(): found a curve, ignoring");
      }
    }
  }

  /**
   * Check to see if all needed parameters/keys in the JSON exist.
   *
   * @param jsonObject The JsonObject to validate.
   * @param parmList A String array of keys to look up.
   * @return boolean Return true only if all keys exist, otherwise return false if any key is
   *     missing.
   */
  private boolean jsonKeysExist(JsonObject jsonObject, String[] parmList, String funcname) {
    for (String parm : parmList) {
      if (!jsonObject.has(parm)) {
        return false;
      }
    }
    return true;
  }

  /**
   * This is a convenience method to fetch and return an int value from the JSON if key exists,
   * otherwise return 0.
   *
   * @param jsonObject The JsonObject to get key from.
   * @param key The string value to look for in the JSON.
   * @param defaultVal The default value to return if key not found
   * @return A String
   */
  private String getJSONasString(
      JsonObject jsonObject, String key, String defaultVal, String funcname)
      throws ParserException {
    String value = defaultVal;

    if (jsonObject.has(key)) {
      JsonElement v = jsonObject.get(key);
      if (v.getAsJsonPrimitive().isString()) {
        return v.getAsJsonPrimitive().getAsString();
      } else {
        // Is this even possible?
        throw new ParserException(
            I18N.getText("macro.function.general.argumentKeyTypeD", funcname, key));
      }
    }
    return value;
  }

  /**
   * This is a convenience method to fetch and return an int value from the JSON if key exists,
   * otherwise return 0.
   *
   * @param jsonObject The JsonObject to get key from.
   * @param key The string value to look for in the JSON.
   * @return An int
   */
  private int getJSONint(JsonObject jsonObject, String key, String funcname)
      throws ParserException {

    int value = 0;

    if (jsonObject.has(key)) {
      JsonElement v = jsonObject.get(key);
      if (v.getAsJsonPrimitive().isNumber()) {
        return v.getAsJsonPrimitive().getAsInt();
      } else {
        // Is this even possible?
        throw new ParserException(
            I18N.getText("macro.function.general.argumentKeyTypeD", funcname, key));
      }
    }
    return value;
  }

  /**
   * This is a convenience method to fetch and return an int value from the JSON if key exists,
   * otherwise return 0.
   *
   * @param jsonObject The JsonObject to get key from.
   * @param key The string value to look for in the JSON.
   * @param defaultVal The default value to return if key not found
   * @param min minimal acceptable value
   * @param max maximum acceptable value
   * @return An int
   */
  private int getJSONint(
      JsonObject jsonObject, String key, int defaultVal, int min, int max, String funcname)
      throws ParserException {
    int value = defaultVal;

    if (jsonObject.has(key)) {
      JsonElement v = jsonObject.get(key);
      if (v.getAsJsonPrimitive().isNumber()) {
        value = v.getAsJsonPrimitive().getAsInt();

        if (value < min || value > max) {
          throw new ParserException(
              I18N.getText(
                  "macro.function.input.illegalArgumentType",
                  value,
                  min + "-" + max + " for " + key));
        }

        return value;
      } else {
        // Is this even possible?
        throw new ParserException(
            I18N.getText("macro.function.general.argumentKeyTypeD", funcname, key));
      }
    }
    return value;
  }

  /**
   * This is a convenience method to fetch and return a double value from the JSON if key exists,
   * otherwise return 0.
   *
   * @param jsonObject The JsonObject to get key from.
   * @param key The string value to look for in the JSON.
   * @return A double
   */
  private double getJSONdouble(JsonObject jsonObject, String key, String funcname)
      throws ParserException {
    double value = key.equals("facing") ? -90 : 0;
    if (jsonObject.has(key)) {
      JsonElement v = jsonObject.get(key);
      if (v.getAsJsonPrimitive().isNumber()) {
        return v.getAsJsonPrimitive().getAsDouble();
      }
      // Is this even possible?
      throw new ParserException(
          I18N.getText("macro.function.general.argumentKeyTypeD", funcname, key));
    }
    return value;
  }
}
