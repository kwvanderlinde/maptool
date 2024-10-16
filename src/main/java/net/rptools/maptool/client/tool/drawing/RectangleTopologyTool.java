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
import java.awt.geom.Area;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.Rectangle;

/**
 * @author drice
 */
public class RectangleTopologyTool extends AbstractTopologyDrawingTool {
  private static final long serialVersionUID = 3258413928311830323L;

  protected Rectangle rectangle;

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
    paintTopologyOverlay(g, rectangle);
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    rectangle = new Rectangle(point.x, point.y, point.x, point.y);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    rectangle.getEndPoint().x = point.x;
    rectangle.getEndPoint().y = point.y;
  }

  @Override
  protected Area finish() {
    int x1 = Math.min(rectangle.getStartPoint().x, rectangle.getEndPoint().x);
    int x2 = Math.max(rectangle.getStartPoint().x, rectangle.getEndPoint().x);
    int y1 = Math.min(rectangle.getStartPoint().y, rectangle.getEndPoint().y);
    int y2 = Math.max(rectangle.getStartPoint().y, rectangle.getEndPoint().y);

    Area area = new Area(new java.awt.Rectangle(x1, y1, x2 - x1, y2 - y1));
    rectangle = null;
    return area;
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
