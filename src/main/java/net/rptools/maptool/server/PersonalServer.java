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
package net.rptools.maptool.server;

import javax.annotation.Nullable;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.LocalPlayerDatabase;

public class PersonalServer implements IMapToolServer {
  private final LocalPlayerDatabase playerDatabase;
  private final ServerPolicy policy;

  public PersonalServer(LocalPlayer player) {
    playerDatabase = new LocalPlayerDatabase(player);
    // TODO Populate server policy from preferences & defaults, so not everybody needs to check
    // isPersonalServer() first.
    this.policy = new ServerPolicy();
  }

  @Override
  public boolean isPersonalServer() {
    return true;
  }

  @Override
  public boolean isServerRegistered() {
    return false;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public LocalPlayerDatabase getPlayerDatabase() {
    return playerDatabase;
  }

  @Override
  public ServerPolicy getPolicy() {
    return policy;
  }

  @Nullable
  @Override
  public ServerConfig getConfig() {
    return null;
  }
}
