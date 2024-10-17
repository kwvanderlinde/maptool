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
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

public class PolyLineStrategy implements Strategy<Path2D> {
  private final boolean isFreehand;

  public PolyLineStrategy(boolean isFreehand) {
    this.isFreehand = isFreehand;
  }

  @Override
  public boolean isFreehand() {
    return isFreehand;
  }

  @Override
  public boolean isLinear() {
    return true;
  }

  public ZonePoint getLastPoint(Path2D path) {
    var point = path.getCurrentPoint();
    return new ZonePoint((int) point.getX(), (int) point.getY());
  }

  @Override
  public Path2D startNewAtPoint(ZonePoint point) {
    var path = new Path2D.Double();
    path.moveTo(point.x, point.y);
    return path;
  }

  @Override
  public void pushPoint(Path2D state, ZonePoint point) {
    state.lineTo(point.x, point.y);
  }

  @Override
  public @Nullable DrawingResult getShape(
      Path2D state, ZonePoint currentPoint, boolean centerOnOrigin, boolean isFilled) {
    var newPath = new Path2D.Double(state);
    newPath.lineTo(currentPoint.x, currentPoint.y);
    if (isFilled) {
      newPath.closePath();
    }

    // TODO It's a bit jarring the moment we commit a point, the text jumps to 0 and the box moves.
    Measurement measurement = null;
    if (!isFreehand()) {
      measurement =
          new Measurement.LineSegment(
              state.getCurrentPoint(), new Point2D.Double(currentPoint.x, currentPoint.y));
    }

    return new DrawingResult(newPath, measurement);
  }
}
