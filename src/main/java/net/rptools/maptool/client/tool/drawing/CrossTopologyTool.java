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
import java.awt.geom.Point2D;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.Cross;
import net.rptools.maptool.util.GraphicsUtil;

/**
 * @author CoveredInFish
 */
public class CrossTopologyTool extends AbstractTopologyDrawingTool {
  private static final long serialVersionUID = 3258413928311830323L;

  protected Cross cross;

  public CrossTopologyTool() {}

  @Override
  protected boolean isBackgroundFill() {
    return false;
  }

  @Override
  protected boolean isInProgress() {
    return cross != null;
  }

  @Override
  public String getInstructions() {
    return "tool.crosstopology.instructions";
  }

  @Override
  public String getTooltip() {
    return "tool.crosstopology.tooltip";
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    paintTopologyOverlay(g, cross);
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    cross = new Cross(point.x, point.y, point.x, point.y);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    cross.getEndPoint().x = point.x;
    cross.getEndPoint().y = point.y;
  }

  @Override
  protected Area finish() {
    int x1 = Math.min(cross.getStartPoint().x, cross.getEndPoint().x);
    int x2 = Math.max(cross.getStartPoint().x, cross.getEndPoint().x);
    int y1 = Math.min(cross.getStartPoint().y, cross.getEndPoint().y);
    int y2 = Math.max(cross.getStartPoint().y, cross.getEndPoint().y);

    Area area = GraphicsUtil.createLine(1, new Point2D.Double(x1, y1), new Point2D.Double(x2, y2));
    area.add(GraphicsUtil.createLine(1, new Point2D.Double(x1, y2), new Point2D.Double(x2, y1)));

    cross = null;

    return area;
  }

  /** Stop drawing a cross and repaint the zone. */
  @Override
  public void resetTool() {
    if (cross != null) {
      cross = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }
}
