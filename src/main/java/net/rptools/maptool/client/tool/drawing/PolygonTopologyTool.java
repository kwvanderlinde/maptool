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
import java.awt.geom.Area;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
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
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    // TODO Why do we only render the lines and not the fill? I have arbitrarily decided otherwise
    // now.
    var shape = lineBuilder.asPolygon();
    paintTopologyOverlay(g, shape);
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
  protected Area finish() {
    lineBuilder.trim();
    var polygon = lineBuilder.asPolygon();

    lineBuilder.clear();

    return new Area(polygon);
  }
}
