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

/** Controls the toolbar button which drives if AI is used or not to move tokens */
public class AI_Tool extends Tool {

  public AI_Tool() {
    addPropertyChangeListener(
        evt -> {
          switch (evt.getPropertyName()) {
            case "selected" -> {
              var selected = (boolean) evt.getNewValue();

              AppPreferences.pathfindingEnabled.set(selected);

              var client = MapTool.getClient();
              var policy = client.getServerPolicy();
              policy.setUsingAstarPathfinding(selected);
              client.setServerPolicy(policy);
              client.getServerCommand().setServerPolicy(policy);
            }
          }
        });
  }

  public void updateState() {
    var serverPolicy = MapTool.getServerPolicy();
    boolean shouldBeSelected =
        serverPolicy == null
            ? AppPreferences.pathfindingEnabled.get()
            : serverPolicy.isUsingAstarPathfinding();
    setSelected(shouldBeSelected);
  }

  @Override
  public String getTooltip() {
    return "tools.ai_selector.tooltip";
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }
}
