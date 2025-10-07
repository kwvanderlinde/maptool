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
package net.rptools.maptool.client.ui.zone.renderer;

import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneViewModel;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionBuilder;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSet;
import net.rptools.maptool.model.Zone;

/**
 * The Zone Compositor is responsible for providing the Zone Renderer with what needs to be
 * rendered. Within a given map region what objects exist that need to be drawn. Basically "What's
 * on screen?"
 */
public class ZoneCompositor {
  private final ZoneRenderer renderer;
  private final ZoneViewModel viewModel;
  private final Zone zone;

  public ZoneCompositor(ZoneRenderer renderer) {
    this.renderer = renderer;
    this.viewModel = renderer.getViewModel();
    this.zone = renderer.getZone();
  }

  /**
   * @param view
   * @return
   */
  public InstructionSet produceInstructions(PlayerView view) {
    // TODO Actually keep the same builder around each time, clearing in between.
    //  Would save on allocations.
    // TODO Respect bounds. E.g., don't add drawings or tokens that are completely outside of the
    //  bounds.
    var instructionBuilder = new InstructionBuilder();
    return instructionBuilder.snapshot();
  }
}
