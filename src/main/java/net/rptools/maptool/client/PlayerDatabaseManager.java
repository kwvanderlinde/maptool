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
package net.rptools.maptool.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.PlayerDatabaseFactory;

public class PlayerDatabaseManager {
  private static final Map<PlayerDatabaseFactory.PlayerDatabaseType, PlayerDatabase>
      playerDatabaseMap = new ConcurrentHashMap<>();

  public PlayerDatabase getPlayerDatabaseOfType(PlayerDatabaseFactory.PlayerDatabaseType type) {
    // TODO Any reason _not_ to stash the default and personal server instances?
    //  On the other hand, any reason to stash the others?
    return switch (type) {
      case LOCAL_PLAYER, PASSWORD_FILE -> playerDatabaseMap.computeIfAbsent(
          type, PlayerDatabaseFactory::createPlayerDatabase);
      default -> PlayerDatabaseFactory.createPlayerDatabase(type);
    };
  }
}
