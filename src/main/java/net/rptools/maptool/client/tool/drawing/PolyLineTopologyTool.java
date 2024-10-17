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
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

/** Tool for drawing freehand lines. */
public class PolyLineTopologyTool extends AbstractTopologyDrawingTool<Path2D> {
  public PolyLineTopologyTool() {
    super("tool.poly.instructions", "tool.polylinetopo.tooltip", false);
  }

  @Override
  protected Path2D startNewAtPoint(ZonePoint point) {
    var path = new Path2D.Double();
    path.moveTo(point.x, point.y);
    return path;
  }

  @Override
  protected void pushPoint(Path2D state, ZonePoint point) {
    state.lineTo(point.x, point.y);
  }

  @Override
  protected @Nullable Path2D getShape(Path2D state, ZonePoint currentPoint) {
    var newPath = new Path2D.Double(state);
    newPath.lineTo(currentPoint.x, currentPoint.y);
    return newPath;
  }
}
