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
package net.rptools.maptool.client.tool;

import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;

/** Controls the toolbar button which drives if VBL will block movement or not when AI is used */
public class AI_UseVblTool extends Tool {

  public AI_UseVblTool() {
    addPropertyChangeListener(
        evt -> {
          switch (evt.getPropertyName()) {
            case "selected" -> {
              var selected = (boolean) evt.getNewValue();

              AppPreferences.pathfindingBlockedByVbl.set(selected);

              var client = MapTool.getClient();
              var policy = client.getServerPolicy();
              policy.setVblBlocksMove(selected);
              client.setServerPolicy(policy);
              client.getServerCommand().setServerPolicy(policy);
            }
          }
        });
  }

  @Override
  public void updateButtonState() {
    var serverPolicy = MapTool.getServerPolicy();
    boolean shouldBeSelected =
        serverPolicy == null
            ? AppPreferences.pathfindingBlockedByVbl.get()
            : serverPolicy.getVblBlocksMove();
    setSelected(shouldBeSelected);
  }

  @Override
  public String getTooltip() {
    return "tools.ignore_vbl_on_move.tooltip";
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM() && AppPreferences.pathfindingEnabled.get();
  }
}
