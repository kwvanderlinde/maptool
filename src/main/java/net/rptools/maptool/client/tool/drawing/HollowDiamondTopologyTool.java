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
import javax.annotation.Nullable;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.model.ZonePoint;

public class HollowDiamondTopologyTool extends AbstractTopologyDrawingTool {
  protected Shape diamond;
  protected ZonePoint originPoint;

  public HollowDiamondTopologyTool() {
    super(
        "tool.isorectangletopology.instructions", "tool.isorectangletopologyhollow.tooltip", false);
  }

  @Override
  protected boolean isInProgress() {
    return diamond != null;
  }

  @Override
  protected void startNewAtPoint(ZonePoint point) {
    originPoint = point;
    diamond = GeometryUtil.createIsoRectangle(originPoint, originPoint);
  }

  @Override
  protected void updateLastPoint(ZonePoint point) {
    diamond = GeometryUtil.createIsoRectangle(originPoint, point);
  }

  @Override
  protected void reset() {
    diamond = null;
  }

  @Override
  protected @Nullable Shape getShape() {
    return diamond.getBounds().isEmpty() ? null : diamond;
  }
}
