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
package net.rptools.maptool.client.ui.zone.vbl;

import com.google.common.base.Stopwatch;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.ImageManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.locationtech.jts.simplify.VWSimplifier;
import org.locationtech.jts.util.GeometricShapeFactory;

/**
 * A utility class that creates and returns an Area based on image pixels. A few convenience methods
 * to handle other Token VBL functions.
 *
 * @author Jamz
 */
public class TokenVBL {

  private static final Logger log = LogManager.getLogger();
  private static int sliceSize = 100;

  /**
   * A passed token will have it's image asset rendered into an Area based on pixels that have an
   * Alpha transparency level greater than or equal to the alphaSensitivity parameter.
   *
   * @param token the token
   * @param alphaSensitivity the alpha sensitivity of the topology area
   * @param inverseTopology match the ignoreColor or everything but ignoreColor
   * @param ignoreColor color to match for topology generation
   * @param distanceTolerance JTS distance tolerance
   * @param method JTS method to use for optimization
   * @return Area
   * @author Jamz
   * @since 1.6.0
   */
  public static Area createOptimizedTopologyArea(
      Token token,
      int alphaSensitivity,
      boolean inverseTopology,
      Color ignoreColor,
      int distanceTolerance,
      String method) {
    final Area topologyArea =
        createTopologyAreaFromToken(token, alphaSensitivity, inverseTopology, ignoreColor);
    final JTS_SimplifyMethodType jtsMethod = JTS_SimplifyMethodType.fromString(method);

    return simplifyArea(topologyArea, distanceTolerance, jtsMethod);
  }

  public static Area createTopologyAreaFromToken(
      Token token, int alphaSensitivity, boolean inverseTopology, Color ignoredColor) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    BufferedImage image = ImageManager.getImageAndWait(token.getImageAssetId());

    List<Geometry> geometryList =
        createTopologyGeometry(image, alphaSensitivity, inverseTopology, ignoredColor);
    log.debug(
        "Time to complete createTopologyGeometry(): {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));

    final Area area = createAreaFromGeometries(geometryList);
    log.debug(
        "Total time for createTopologyAreaFromToken(): {}",
        stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return area;
  }

  private static Area createAreaFromGeometries(List<Geometry> geometryList) {
    if (geometryList.isEmpty()) {
      return new Area();
    } else {
      Stopwatch stopwatch = Stopwatch.createStarted();
      GeometryCollection geometryCollection =
          (GeometryCollection) new GeometryFactory().buildGeometry(geometryList);

      final Area area = new Area(new ShapeWriter().toShape(geometryCollection));
      log.debug("Time to complete convert to Area: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
      return area;
    }
  }

  public static Area simplifyArea(
      Area topologyArea, double distanceTolerance, JTS_SimplifyMethodType simplifyMethod) {

    if (simplifyMethod.equals(JTS_SimplifyMethodType.NONE)) {
      return topologyArea;
    }

    final GeometryFactory geometryFactory = new GeometryFactory();
    ShapeReader shapeReader = new ShapeReader(geometryFactory);
    Geometry topologyGeometry = null;

    if (!topologyArea.isEmpty()) {
      try {
        topologyGeometry = shapeReader.read(topologyArea.getPathIterator(null));
        // .buffer(1); // helps creating valid geometry and prevent self-intersecting polygons
        if (!topologyGeometry.isValid()) {
          log.debug(
              "topology geometry is invalid! May cause issues. Check for self-intersecting polygons.");
        }
      } catch (Exception e) {
        log.error("There is a problem reading topology geometry: ", e);
      }
    } else {
      return topologyArea;
    }

    ShapeWriter sw = new ShapeWriter();
    Geometry simplifiedGeometry;

    switch (simplifyMethod) {
      case DOUGLAS_PEUCKER_SIMPLIFIER:
        DouglasPeuckerSimplifier dps = new DouglasPeuckerSimplifier(topologyGeometry);
        dps.setDistanceTolerance(distanceTolerance);
        dps.setEnsureValid(false);
        simplifiedGeometry = dps.getResultGeometry();
        break;
      case TOPOLOGY_PRESERVING_SIMPLIFIER:
        TopologyPreservingSimplifier tss = new TopologyPreservingSimplifier(topologyGeometry);
        tss.setDistanceTolerance(distanceTolerance);
        simplifiedGeometry = tss.getResultGeometry();
        break;
      case VW_SIMPLIFIER:
        VWSimplifier vws = new VWSimplifier(topologyGeometry);
        vws.setDistanceTolerance(distanceTolerance);
        vws.setEnsureValid(false);
        simplifiedGeometry = vws.getResultGeometry();
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + simplifyMethod);
    }

    final Area simplifiedArea = new Area(sw.toShape(simplifiedGeometry));

    if (!simplifiedGeometry.isValid()) {
      log.debug(
          "simplifiedGeometry is invalid! May cause issues. Check for self-intersecting polygons.");
    }

    return simplifiedArea;
  }

  public static Area getTopology_underToken(
      Zone zone, Token token, Zone.TopologyType topologyType) {
    Area topologyOnMap = zone.getLegacyTopology(topologyType);
    Rectangle footprintBounds = token.getBounds(zone);

    final AffineTransform captureArea = new AffineTransform();
    final Area topologyUnderToken;
    if (token.getShape() == Token.TokenShape.TOP_DOWN && token.getFacingInDegrees() != 0) {
      // Get the center of the token bounds
      double rx = footprintBounds.getCenterX() - token.getAnchor().x;
      double ry = footprintBounds.getCenterY() - token.getAnchor().y;

      // Rotate the area to match the token facing
      captureArea.rotate(Math.toRadians(token.getFacingInDegrees()), rx, ry);
      topologyUnderToken = new Area(captureArea.createTransformedShape(footprintBounds));
    } else {
      topologyUnderToken = new Area(footprintBounds);
    }

    // Capture the topology via intersection
    topologyUnderToken.intersect(topologyOnMap);
    return topologyUnderToken;
  }

  public static Area transformTopology_toToken(Zone zone, Token token, Area topologyUnderToken) {
    Rectangle footprintBounds = token.getBounds(zone);

    // Reverse all token transformations so we can store a raw untransformed version on the Token.
    AffineTransform atArea = new AffineTransform();
    // Let's account for flipped images...
    if (token.isFlippedX()) {
      atArea.scale(-1, 1);
      atArea.translate(-token.getWidth(), 0);
    }
    if (token.isFlippedY()) {
      atArea.scale(1, -1);
      atArea.translate(0, -token.getHeight());
    }
    // Translate the capture to zero out the x,y to store on the Token
    if (token.isSnapToScale()) {
      Dimension imgSize = new Dimension(token.getWidth(), token.getHeight());
      SwingUtil.constrainTo(imgSize, footprintBounds.width, footprintBounds.height);
      atArea.scale(token.getWidth() / imgSize.getWidth(), token.getHeight() / imgSize.getHeight());
      atArea.translate(
          -footprintBounds.getX() - (int) ((footprintBounds.getWidth() - imgSize.getWidth()) / 2),
          -footprintBounds.getY()
              - (int) ((footprintBounds.getHeight() - imgSize.getHeight()) / 2));
    } else {
      atArea.scale(1 / token.getScaleX(), 1 / token.getScaleY());
      atArea.translate(-footprintBounds.getX(), -footprintBounds.getY());
    }

    if (token.getShape() == Token.TokenShape.TOP_DOWN && token.getFacingInDegrees() != 0) {
      // Get the center of the token bounds
      double rx = footprintBounds.getCenterX() - token.getAnchor().x;
      double ry = footprintBounds.getCenterY() - token.getAnchor().y;

      // Rotate the area to match the token facing
      atArea.rotate(-Math.toRadians(token.getFacingInDegrees()), rx, ry);
    }

    return new Area(atArea.createTransformedShape(topologyUnderToken));
  }

  /**
   * Create a topology area from a bufferedImage and alphaSensitity. The area is created by
   * combining topology rectangle where the alpha of the image is greater or equal to the
   * sensitivity. Assumes all colors form the topology area, everything except transparent pixels
   * with alpha >= alphaSensitivity
   *
   * @param image the buffered image.
   * @param colorTolerance the alphaSensitivity.
   * @param inversePickColor choose to pick the chosen color or all colors not chosen
   * @param pickColor color to compare against pixel color
   * @return the area.
   */
  private static List<Geometry> createTopologyGeometry(
      BufferedImage image, int colorTolerance, boolean inversePickColor, Color pickColor) {

    if (image == null) {
      return Collections.emptyList();
    }

    ArrayList<Geometry> geometryList = new ArrayList<>();
    GeometricShapeFactory gsf = new GeometricShapeFactory();
    gsf.setNumPoints(4);
    gsf.setWidth(1);

    int y1, y2;
    boolean addArea = false;

    for (int x = 0; x < image.getWidth(); x++) {
      y1 = 99;
      y2 = -1;
      for (int y = 0; y < image.getHeight(); y++) {
        if (Thread.interrupted()) {
          log.info("Thread interrupted!");
          return geometryList;
        }

        Color pixelColor = new Color(image.getRGB(x, y), true);

        if (colorWithinTolerance(pickColor, pixelColor, colorTolerance, inversePickColor)) {
          if (y1 == 99) {
            y1 = y;
            y2 = y;
          }
          if (y > (y2 + 1)) {
            gsf.setHeight(y2 - y1);
            gsf.setBase(new Coordinate(x, y1));
            geometryList.add(gsf.createRectangle());
            y1 = y;
            y2 = y;
          }
          y2 = y;
        }
      }

      if ((y2 - y1) >= 0) {
        gsf.setHeight(y2 - y1 + 1);
        gsf.setBase(new Coordinate(x, y1));
        geometryList.add(gsf.createRectangle());
      }
    }

    return geometryList;
  }

  private static boolean colorWithinTolerance(
      Color pick, Color pixel, int tolerance, boolean inversePick) {

    double distance = distanceSquared(pick, pixel);

    if (distance > tolerance) {
      log.trace("Color Distance: {}", distance);
    }

    if (distance <= tolerance) {
      return !inversePick;
    } else {
      return inversePick;
    }
  }

  private static double distanceSquared(Color a, Color b) {
    int deltaR = a.getRed() - b.getRed();
    int deltaG = a.getGreen() - b.getGreen();
    int deltaB = a.getBlue() - b.getBlue();
    int deltaAlpha = a.getAlpha() - b.getAlpha();

    double rgbDistanceSquared = (deltaR * deltaR + deltaG * deltaG + deltaB * deltaB) / 3;

    double result =
        deltaAlpha * deltaAlpha / 2.0
            + rgbDistanceSquared * a.getAlpha() * b.getAlpha() / 65025; // 255^2 = 65025

    return Math.sqrt(result);
  }

  public enum JTS_SimplifyMethodType {
    DOUGLAS_PEUCKER_SIMPLIFIER(),
    TOPOLOGY_PRESERVING_SIMPLIFIER(),
    VW_SIMPLIFIER(),
    NONE();

    private final String displayName;

    JTS_SimplifyMethodType() {
      displayName = I18N.getString("TokenVBL.JTS_SimplifyMethodType." + name());
    }

    @Override
    public String toString() {
      return displayName;
    }

    public static JTS_SimplifyMethodType getDefault() {
      return DOUGLAS_PEUCKER_SIMPLIFIER;
    }

    public static JTS_SimplifyMethodType fromString(String label) {
      final JTS_SimplifyMethodType jts_simplifyMethod =
          Stream.of(JTS_SimplifyMethodType.values())
              .filter(e -> e.name().equalsIgnoreCase(label))
              .findAny()
              .orElse(getDefault());

      return jts_simplifyMethod;
    }
  }
}
