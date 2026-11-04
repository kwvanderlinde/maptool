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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.tool.editing;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

public class NilToolMode implements ToolMode {
  @Override
  public void activate() {}

  @Override
  public void deactivate() {}

  @Override
  public boolean cancel() {
    return false;
  }

  @Override
  public void delete() {}

  @Override
  public void mouseMoved(Point2D point, Snap snapMode, MouseEvent event) {}

  @Override
  public boolean mouseDragged(Point2D point, Snap snapMode, MouseEvent event) {
    return false;
  }

  @Override
  public void mousePressed(Point2D point, Snap snapMode, MouseEvent event) {}

  @Override
  public void mouseReleased(Point2D point, Snap snapMode, MouseEvent event) {}

  @Override
  public void mouseClicked(Point2D point, Snap snapMode, MouseEvent event) {}

  @Override
  public void paint(Graphics2D g2) {}
}
