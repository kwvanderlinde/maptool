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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.annotation.Nullable;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.ZonePoint;

/**
 * @author drice
 */
public class RectangleTopologyTool extends AbstractTopologyDrawingTool {
  private static final long serialVersionUID = 3258413928311830323L;

  private Rectangle rectangle;

  public RectangleTopologyTool() {}

  @Override
  public String getInstructions() {
    return "tool.recttopology.instructions";
  }

  @Override
  public String getTooltip() {
    return "tool.recttopology.tooltip";
  }

  @Override
  protected boolean isBackgroundFill() {
    return true;
  }

  @Override
  protected boolean isInProgress() {
    return rectangle != null;
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    paintTopologyOverlay(g, rectangle == null ? null : normalizedRectangle(rectangle));
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
  protected @Nullable Shape finish() {
    var result = normalizedRectangle(rectangle);
    if (result.isEmpty()) {
      result = null;
    }
    rectangle = null;
    return result;
  }

  /** Stop drawing a rectangle and repaint the zone. */
  @Override
  public void resetTool() {
    if (rectangle != null) {
      rectangle = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }
}
