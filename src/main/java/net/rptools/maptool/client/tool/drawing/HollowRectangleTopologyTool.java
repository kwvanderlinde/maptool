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

import java.awt.Rectangle;
import java.awt.Shape;
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

/**
 * @author drice
 */
public class HollowRectangleTopologyTool extends AbstractTopologyDrawingTool {
  private static final long serialVersionUID = 3258413928311830323L;

  protected Rectangle rectangle;

  public HollowRectangleTopologyTool() {}

  @Override
  protected boolean isBackgroundFill() {
    return false;
  }

  @Override
  protected boolean isInProgress() {
    return rectangle != null;
  }

  @Override
  public String getInstructions() {
    return "tool.recttopology.instructions";
  }

  @Override
  public String getTooltip() {
    return "tool.recttopologyhollow.tooltip";
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    rectangle = new Rectangle(point.x, point.y, 0, 0);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    rectangle.width = point.x - rectangle.x;
    rectangle.height = point.y - rectangle.y;
  }

  @Override
  protected void reset() {
    rectangle = null;
  }

  @Override
  protected @Nullable Shape getShape() {
    var result = rectangle == null ? null : normalizedRectangle(rectangle);
    if (result != null && result.isEmpty()) {
      return null;
    }
    return result;
  }
}
