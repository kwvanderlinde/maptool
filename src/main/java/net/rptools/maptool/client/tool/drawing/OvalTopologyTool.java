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
import net.rptools.maptool.model.drawing.Oval;
import net.rptools.maptool.util.GraphicsUtil;

public class OvalTopologyTool extends AbstractTopologyDrawingTool {

  private static final long serialVersionUID = 3258413928311830321L;

  protected Oval oval;
  private ZonePoint originPoint;

  public OvalTopologyTool() {}

  @Override
  protected boolean isBackgroundFill() {
    return false;
  }

  @Override
  protected boolean isInProgress() {
    return oval != null;
  }

  @Override
  public String getInstructions() {
    return "tool.ovaltopology.instructions";
  }

  @Override
  public String getTooltip() {
    return "tool.ovaltopology.tooltip";
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    paintTopologyOverlay(g, oval);
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    originPoint = point;
    oval = new Oval(originPoint.x, originPoint.y, originPoint.x, originPoint.y);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    oval.getEndPoint().x = point.x;
    oval.getEndPoint().y = point.y;
    oval.getStartPoint().x = originPoint.x - (point.x - originPoint.x);
    oval.getStartPoint().y = originPoint.y - (point.y - originPoint.y);
  }

  @Override
  protected Area finish() {
    Area area =
        GraphicsUtil.createLineSegmentEllipse(
            oval.getStartPoint().x,
            oval.getStartPoint().y,
            oval.getEndPoint().x,
            oval.getEndPoint().y,
            10);
    oval = null;
    return area;
  }

  /** Stop drawing a rectangle and repaint the zone. */
  @Override
  public void resetTool() {
    if (oval != null) {
      oval = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }
}
