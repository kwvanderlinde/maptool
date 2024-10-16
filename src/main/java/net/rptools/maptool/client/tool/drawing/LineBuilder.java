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

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.LineSegment;

/** Helper class for building a sequence of points. */
public class LineBuilder {
  private final List<ZonePoint> points = new ArrayList<>();

  public LineBuilder() {}

  public List<ZonePoint> getPoints() {
    return Collections.unmodifiableList(this.points);
  }

  public boolean isEmpty() {
    return points.isEmpty();
  }

  public int size() {
    return points.size();
  }

  public void clear() {
    points.clear();
  }

  public void addPoint(ZonePoint point) {
    points.addLast(point);
  }

  public ZonePoint getLastPoint() {
    return points.getLast();
  }

  public void replaceLastPoint(ZonePoint point) {
    if (!points.isEmpty()) {
      points.removeLast();
    }
    points.addLast(point);
  }

  public LineSegment asLineSegment(float width, boolean squareCap) {
    var segment = new LineSegment(width, squareCap);
    var segmentPoints = segment.getPoints();
    for (var point : points) {
      segmentPoints.addLast(new Point(point.x, point.y));
    }
    return segment;
  }

  public Path2D asPath() {
    var path = new Path2D.Double();
    for (var point : points) {
      if (path.getCurrentPoint() == null) {
        path.moveTo(point.x, point.y);
      } else {
        path.lineTo(point.x, point.y);
      }
    }
    return path;
  }

  public Polygon asPolygon() {
    Polygon polygon = new Polygon();
    for (var point : points) {
      polygon.addPoint(point.x, point.y);
    }
    return polygon;
  }

  /**
   * Due to mouse movement, a user drawn line often has duplicated points, especially at the end. To
   * draw a clean line with miter joints these duplicates should be removed.
   *
   * <p>TODO JTS / GeometryUtil could help us out here.
   */
  protected void trim() {
    ZonePoint lastPoint = null;
    var iterator = points.iterator();
    while (iterator.hasNext()) {
      var point = iterator.next();

      if (point.equals(lastPoint)) {
        iterator.remove();
      }
      lastPoint = point;
    }
  }
}
