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

import java.awt.event.MouseEvent;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.AbstractTemplate;
import net.rptools.maptool.model.drawing.BurstTemplate;

/**
 * Draw a template for an effect with a burst. Make the template show the squares that are effected,
 * not just draw a circle. Let the player choose the base hex with the mouse and then click again to
 * set the radius. The control key can be used to move the base hex.
 *
 * @author jgorrell
 * @version $Revision: $ $Date: $ $Author: $
 */
public class BurstTemplateTool extends RadiusTemplateTool {
  /*---------------------------------------------------------------------------------------------
   * Instance Variables
   *-------------------------------------------------------------------------------------------*/

  /*---------------------------------------------------------------------------------------------
   * Constructors
   *-------------------------------------------------------------------------------------------*/

  public BurstTemplateTool() {}

  /*---------------------------------------------------------------------------------------------
   * Overridden RadiusTemplateTool methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  protected AbstractTemplate createBaseTemplate() {
    return new BurstTemplate();
  }

  /**
   * This seems to be redundant and doesn't account for moving the mouse pointer to the nearest
   * vertex, only truncating to the nearest top/left vertex.
   */
  @Override
  protected ZonePoint getCellAtMouse(MouseEvent e) {
    ZonePoint mouse =
        new ScreenPoint(e.getX(), e.getY()).convertToZone(renderer.getViewModel().getZoneScale());
    CellPoint cp = renderer.getZone().getGrid().convert(mouse);
    return renderer.getZone().getGrid().convert(cp);
  }

  @Override
  protected int getRadiusAtMouse(MouseEvent e) {
    return super.getRadiusAtMouse(e);
  }

  @Override
  public String getTooltip() {
    return "tool.bursttemplate.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.bursttemplate.instructions";
  }
}
