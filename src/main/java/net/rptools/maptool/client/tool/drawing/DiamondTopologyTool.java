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

import java.awt.Shape;
import javax.annotation.Nullable;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.model.ZonePoint;

public class DiamondTopologyTool extends AbstractTopologyDrawingTool<ZonePoint> {
  public DiamondTopologyTool() {
    super("tool.isorectangletopology.instructions", "tool.isorectangletopology.tooltip", true);
  }

  @Override
  protected ZonePoint startNewAtPoint(ZonePoint point) {
    return point;
  }

  @Override
  protected @Nullable Shape getShape(ZonePoint state, ZonePoint currentPoint) {
    var diamond = GeometryUtil.createIsoRectangle(state, currentPoint);
    return diamond.getBounds().isEmpty() ? null : diamond;
  }
}
