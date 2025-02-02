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

import com.google.common.primitives.Floats;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.json.JSONMacroFunctions;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.DrawnElement;
import net.rptools.maptool.model.drawing.Pen;
import net.rptools.maptool.model.drawing.ShapeDrawable;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;
import org.apache.batik.ext.awt.geom.ExtendedGeneralPath;
import org.apache.batik.ext.awt.geom.Polygon2D;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.ParseException;
import org.apache.batik.parser.PathParser;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;

public class ShapeFunctions extends AbstractFunction {

  protected static final Map<String, ShapeDrawable> CACHED_SHAPES;
  private static final ShapeFunctions instance = new ShapeFunctions();
  private static final AWTPathProducer AWT_PATH_PRODUCER = new AWTPathProducer();
  private static final PathParser PATH_PARSER = new PathParser();
  private static final String UNKNOWN_LAYER = "macro.function.tokenProperty.unknownLayer";
  private static final String OBJECT_NOT_FOUND = "macro.function.general.objectNotFound";
  private static final String INDEX_OUT_OF_BOUNDS =
          "macro.function.general.indexOutOfBoundsVerbose";
  private static final String UNABLE_TO_PARSE = "macro.function.general.unableToParse";
  private static final String UNSUPPORTED_OPERATION = "macro.function.general.unsupportedOperation";
  private static final String WRONG_NUMBER_OF_ARGUMENTS_FOR_OPERATION =
          "macro.function.general.wrongNumberArgumentsForOperation";

  static {
    PATH_PARSER.setPathHandler(AWT_PATH_PRODUCER);
    CACHED_SHAPES = new TreeMap<>(String::compareToIgnoreCase);
  }

  private ShapeFunctions() {
    super(
            0,
            UNLIMITED_PARAMETERS,
            "shape.areaAdd",
            "shape.areaExclusiveOr",
            "shape.areaIntersect",
            "shape.areaSubtract",
            "shape.clearAll",
            "shape.combinePaths",
            "shape.copy",
            "shape.create",
            "shape.delete",
            "shape.draw",
            "shape.getProperties",
            "shape.list",
            "shape.transform");
  }

  public static ShapeFunctions getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
          Parser parser, VariableResolver resolver, String functionName, List<Object> parameters)
          throws ParserException {
    if (functionName.equalsIgnoreCase("shape.areaAdd")) {
      return areaBoolean("add", parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.areaExclusiveOr")) {
      return areaBoolean("exclusiveOr", parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.areaIntersect")) {
      return areaBoolean("intersect", parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.areaSubtract")) {
      return areaBoolean("subtract", parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.clearAll")) {
      CACHED_SHAPES.clear();
      return true;
    } else if (functionName.equalsIgnoreCase("shape.combinePaths")) {
      return combinePaths(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.copy")) {
      return copyShape(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.create")) {
      return createShape(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.delete")) {
      return deleteShape(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.draw")) {
      return drawShape(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.getProperties")) {
      return getProperties(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.list")) {
      return shapeList(parser, resolver, functionName, parameters);
    } else if (functionName.equalsIgnoreCase("shape.transform")) {
      return transformShape(parser, resolver, functionName, parameters);
    } else {
      throw new ParserException(
              I18N.getText("macro.function.general.unknownFunction", functionName));
    }
  }

}
