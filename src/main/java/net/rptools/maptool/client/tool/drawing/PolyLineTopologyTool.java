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
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.ZonePoint;

/** Tool for drawing freehand lines. */
public class PolyLineTopologyTool extends AbstractTopologyDrawingTool {
  private static final long serialVersionUID = 3258132466219627316L;

  private final LineBuilder lineBuilder = new LineBuilder();

  public PolyLineTopologyTool() {}

  @Override
  public String getTooltip() {
    return "tool.polylinetopo.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.poly.instructions";
  }

  protected boolean isBackgroundFill() {
    return false;
  }

  @Override
  protected boolean isInProgress() {
    return !lineBuilder.isEmpty();
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    paintTopologyOverlay(g, lineBuilder.asPath());
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
  protected @Nullable Shape finish() {
    lineBuilder.trim();
    var path = lineBuilder.asPath();
    lineBuilder.clear();
    return path;
  }
}
