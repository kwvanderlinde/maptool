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

import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.PlayerDatabaseFactory;

public class PersonalServer implements IMapToolServer {
  private final PlayerDatabase playerDatabase;

  public PersonalServer() {
    playerDatabase =
        PlayerDatabaseFactory.getPlayerDatabase(
            PlayerDatabaseFactory.PlayerDatabaseType.PERSONAL_SERVER);
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
  public void stop() {}

  @Override
  public PlayerDatabase getPlayerDatabase() {
    return playerDatabase;
  }
}
