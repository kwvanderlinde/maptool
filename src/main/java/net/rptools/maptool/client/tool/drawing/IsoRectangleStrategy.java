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
package net.rptools.maptool.client.tool.drawing;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

public class IsoRectangleStrategy implements Strategy<ZonePoint> {
  @Override
  public ZonePoint startNewAtPoint(ZonePoint point) {
    return point;
  }

  private Point2D.Double toPoint(ZonePoint point) {
    return new Point2D.Double(point.x, point.y);
  }

  @Override
  public @Nullable DrawingResult getShape(
      ZonePoint origin_, ZonePoint currentPoint_, boolean centerOnOrigin, boolean isFilled) {
    // Inversion check is not strictly needed, but simplifies some case work below.
    var invertedY = currentPoint_.y < origin_.y;
    var origin = toPoint(invertedY ? currentPoint_ : origin_);
    var currentPoint = toPoint(invertedY ? origin_ : currentPoint_);

    final double diffX = (currentPoint.x - origin.x) / 2;
    final double diffY = currentPoint.y - origin.y;
    assert diffY >= 0 : "diffY should be forced positive by the above inversion check";

    var p1 = new Point2D.Double(origin.x + diffX + diffY, origin.y + (diffY + diffX) / 2);
    var p2 = new Point2D.Double(origin.x + diffX - diffY, origin.y + (diffY - diffX) / 2);

    var points = new Point2D.Double[] {origin, p1, currentPoint, p2};
    // For the sake of measurements, we need to know which point is in each compass direction.
    // Check for edge cases, and force order of `points` to be north, east, south, west.
    if (diffY < Math.abs(diffX)) {
      // Inverted over the y = x / 2 axis. First rotate either p1 or p2 into north position...
      Collections.rotate(Arrays.asList(points), diffX > 0 ? 1 : -1);
      // ... then swap roles of origin and currentPoint due to the inversion.
      Collections.swap(Arrays.asList(points), 1, 3);
    }

    var north = points[0];
    var east = points[1];
    var south = points[2];
    var west = points[3];

    var path = new Path2D.Double();
    path.moveTo(north.x, north.y);
    path.lineTo(east.x, east.y);
    path.lineTo(south.x, south.y);
    path.lineTo(west.x, west.y);
    path.closePath();

    if (path.getBounds().isEmpty()) {
      return null;
    }

    return new DrawingResult(path, new Measurement.IsoRectangular(north, west, east));
  }
}
