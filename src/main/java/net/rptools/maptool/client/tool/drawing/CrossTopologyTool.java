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
import java.awt.geom.Path2D;
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

/**
 * @author CoveredInFish
 */
public class CrossTopologyTool extends AbstractTopologyDrawingTool {
  private static final long serialVersionUID = 3258413928311830323L;

  private Rectangle bounds;

  public CrossTopologyTool() {}

  @Override
  protected boolean isBackgroundFill() {
    return false;
  }

  @Override
  protected boolean isInProgress() {
    return bounds != null;
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
  protected void startNewAtPoint(ZonePoint point) {
    bounds = new Rectangle(point.x, point.y, 0, 0);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    bounds.width = point.x - bounds.x;
    bounds.height = point.y - bounds.y;
  }

  @Override
  protected void reset() {
    bounds = null;
  }

  @Override
  protected @Nullable Path2D getShape() {
    if (bounds == null) {
      return null;
    }

    var path = new Path2D.Double();
    path.moveTo(bounds.x, bounds.y);
    path.lineTo(bounds.x + bounds.width, bounds.y + bounds.height);
    path.moveTo(bounds.x, bounds.y + bounds.height);
    path.lineTo(bounds.x + bounds.width, bounds.y);
    return path;
  }
}
