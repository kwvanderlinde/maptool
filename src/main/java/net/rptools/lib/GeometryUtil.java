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
package net.rptools.lib;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.rptools.maptool.model.ZonePoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.noding.NodableSegmentString;
import org.locationtech.jts.noding.NodedSegmentString;
import org.locationtech.jts.noding.SegmentString;
import org.locationtech.jts.noding.snapround.SnapRoundingNoder;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.valid.IsValidOp;

public class GeometryUtil {
  private static final Logger log = LogManager.getLogger(GeometryUtil.class);

  private static final PrecisionModel precisionModel = new PrecisionModel(100_000.0);

  private static final GeometryFactory geometryFactory = new GeometryFactory(precisionModel);

  public static double getAngle(Point2D origin, Point2D target) {
    double angle =
        Math.toDegrees(
            Math.atan2((origin.getY() - target.getY()), (target.getX() - origin.getX())));
    if (angle < 0) {
      angle += 360;
    }
    return angle;
  }

  public static double getAngleDelta(double sourceAngle, double targetAngle) {
    // Normalize
    targetAngle -= sourceAngle;

    if (targetAngle > 180) {
      targetAngle -= 360;
    }
    if (targetAngle < -180) {
      targetAngle += 360;
    }
    return targetAngle;
  }

  public static Path2D createIsoRectangle(ZonePoint point1, ZonePoint point2) {
    final double diffX = point2.x - point1.x;
    final double diffY = point2.y - point1.y;
    final double avgY = (point1.y + point2.y) / 2.;

    // Points are in counterclockwise order.
    var path = new Path2D.Double();
    path.moveTo(point1.x, point1.y);
    path.lineTo(point1.x - diffY + diffX / 2, avgY - diffX / 4);
    path.lineTo(point2.x, point2.y);
    path.lineTo(point1.x + diffY + diffX / 2, avgY + diffX / 4);
    path.closePath();
    return path;
  }

  public static Shape createDiamond(ZonePoint originPoint, ZonePoint newPoint) {
    int ox = originPoint.x;
    int oy = originPoint.y;
    int nx = newPoint.x;
    int ny = newPoint.y;
    int x1 = ox - (ny - oy) + ((nx - ox) / 2);
    int y1 = ((oy + ny) / 2) - ((nx - ox) / 4);
    int x2 = ox + (ny - oy) + ((nx - ox) / 2);
    int y2 = ((oy + ny) / 2) + ((nx - ox) / 4);
    int[] x = {originPoint.x, x1, nx, x2};
    int[] y = {originPoint.y, y1, ny, y2};
    return new java.awt.Polygon(x, y, 4);
  }

  public static Rectangle createRect(ZonePoint originPoint, ZonePoint newPoint) {
    int x = Math.min(originPoint.x, newPoint.x);
    int y = Math.min(originPoint.y, newPoint.y);

    int w = Math.max(originPoint.x, newPoint.x) - x;
    int h = Math.max(originPoint.y, newPoint.y) - y;

    return new Rectangle(x, y, w, h);
  }

  /**
   * Unions several areas into one.
   *
   * <p>The results are the same as progressively `.add()`ing all areas in the collection, but
   * performs much better when the result is complicated.
   *
   * @param areas The areas to union.
   * @return The union of {@code areas}
   */
  public static Area union(Collection<Area> areas) {
    final var copy = new ArrayList<>(areas);
    copy.replaceAll(Area::new);

    return destructiveUnion(copy);
  }

  /**
   * Like {@link #union(java.util.Collection)}, but will modify the areas and collection for
   * performance gains.
   *
   * @param areas The areas to union.
   * @return The union of {@code areas}
   */
  public static Area destructiveUnion(List<Area> areas) {
    areas.removeIf(Area::isEmpty);

    // Union two-by-two, on repeat until only one is left.
    while (areas.size() >= 2) {
      for (int i = 0; i + 1 < areas.size(); i += 2) {
        final var a = areas.get(i);
        final var b = areas.get(i + 1);

        a.add(b);
        areas.set(i + 1, null);
      }
      areas.removeIf(Objects::isNull);
    }

    if (areas.isEmpty()) {
      // Just in case, maybe it's possible for Area() to have edge cases the produce empty unions?
      return new Area();
    }

    return areas.getFirst();
  }

  public static PrecisionModel getPrecisionModel() {
    return precisionModel;
  }

  public static GeometryFactory getGeometryFactory() {
    return geometryFactory;
  }

  private static Polygonizer toPolygonizer(Area area) {
    final var pathIterator = area.getPathIterator(null, 1. / precisionModel.getScale());

    // Make sure the geometry is noded and precise before polygonizing.
    final var coords = (List<Coordinate[]>) ShapeReader.toCoordinates(pathIterator);
    final var strings = new ArrayList<NodableSegmentString>(coords.size());
    for (var string : coords) {
      strings.add(new NodedSegmentString(string, null));
    }

    final var noder = new SnapRoundingNoder(precisionModel);
    noder.computeNodes(strings);
    final Collection<? extends SegmentString> nodedStrings = noder.getNodedSubstrings();

    // Now build the polygons from our corrected geometry.
    final var polygonizer = new Polygonizer(true);
    for (var string : nodedStrings) {
      final var lineString = geometryFactory.createLineString(string.getCoordinates());
      polygonizer.add(lineString);
    }

    final var danglingEdges = polygonizer.getDangles().size();
    final var cutEdges = polygonizer.getCutEdges().size();
    final var invalidRings = polygonizer.getInvalidRingLines().size();
    if (danglingEdges != 0 || cutEdges != 0 || invalidRings != 0) {
      log.error(
          "Found invalid geometry: {} dangling edges; {} cut edges; {} invalid rings",
          danglingEdges,
          cutEdges,
          invalidRings);
    }

    return polygonizer;
  }

  public static MultiPolygon toJts(Area area) {
    final var polygons = toJtsPolygons(area);
    final var geometry = geometryFactory.createMultiPolygon(polygons.toArray(Polygon[]::new));
    assert geometry.isValid()
        : "Returned geometry must be valid, but found this error: "
            + new IsValidOp(geometry).getValidationError();
    return geometry;
  }

  public static Collection<Polygon> toJtsPolygons(Area area) {
    if (area.isEmpty()) {
      return Collections.emptyList();
    }

    return toPolygonizer(area).getPolygons();
  }
}
