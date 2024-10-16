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
import net.rptools.maptool.util.GraphicsUtil;

public class OvalTopologyTool extends AbstractTopologyDrawingTool {

  private static final long serialVersionUID = 3258413928311830321L;

  private Rectangle bounds;
  private ZonePoint originPoint;

  public OvalTopologyTool() {}

  @Override
  protected boolean isBackgroundFill() {
    return true;
  }

  @Override
  protected boolean isInProgress() {
    return bounds != null;
  }

  @Override
  public String getInstructions() {
    return "tool.ovaltopology.instructions";
  }

  @Override
  public String getTooltip() {
    return "tool.ovaltopology.tooltip";
  }

  private Shape toShape() {
    if (bounds == null) {
      return null;
    }

    var rect = normalizedRectangle(bounds);

    return GraphicsUtil.createLineSegmentEllipsePath(
        rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 10);
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    paintTopologyOverlay(g, toShape());
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    originPoint = point;
    bounds = new Rectangle(originPoint.x, originPoint.y, 0, 0);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    bounds.x = point.x;
    bounds.y = point.y;
    bounds.width = 2 * (originPoint.x - point.x);
    bounds.height = 2 * (originPoint.y - point.y);
  }

  @Override
  protected @Nullable Shape finish() {
    var result = toShape();
    bounds = null;
    return result;
  }

  /** Stop drawing a rectangle and repaint the zone. */
  @Override
  public void resetTool() {
    if (bounds != null) {
      bounds = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }
}
