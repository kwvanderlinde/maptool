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
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

// TODO These aren't freehand lines. Also it's not worth inheriting from LineTool.
/** Tool for drawing freehand lines. */
public class PolygonTopologyTool extends AbstractTopologyDrawingTool {

  private static final long serialVersionUID = 3258132466219627316L;
  protected final LineBuilder lineBuilder = new LineBuilder();

  public PolygonTopologyTool() {}

  @Override
  public String getTooltip() {
    return "tool.polytopo.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.poly.instructions";
  }

  @Override
  protected boolean isBackgroundFill() {
    return true;
  }

  @Override
  protected boolean isInProgress() {
    return !lineBuilder.isEmpty();
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    // Yes, add the point twice. The first is to commit the first point, the second is as the
    // temporary point that can be updated as we go.
    lineBuilder.addPoint(point);
    lineBuilder.addPoint(point);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    lineBuilder.replaceLastPoint(point);
  }

  @Override
  protected void pushPoint() {
    // Create a joint
    lineBuilder.addPoint(lineBuilder.getLastPoint());
  }

  @Override
  protected void reset() {
    lineBuilder.clear();
  }

  @Override
  protected @Nullable Path2D getShape() {
    // TODO Trim points. Current structure of lineBuilder does not distinguish the temporary point,
    //  so it is not possible. On the other hand, maybe we shouldn't care? It only eliminates
    //  identical points, we could do that in real-time as they are pushed.
    var path = lineBuilder.asPath();
    if (path.getCurrentPoint() == null) {
      return null;
    }
    // Can only close non-empty paths.
    path.closePath();
    return path;
  }
}
