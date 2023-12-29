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
package net.rptools.maptool.model.player;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.rptools.maptool.client.AppUtil;

public class PlayerDatabaseFactory {

  public enum PlayerDatabaseType {
    PASSWORD_FILE
  }

  private static final Map<PlayerDatabaseType, PlayerDatabase> playerDatabaseMap =
      new ConcurrentHashMap<>();

  private static final File PASSWORD_FILE =
      AppUtil.getAppHome("config").toPath().resolve("passwords.json").toFile();
  private static final File PASSWORD_ADDITION_FILE =
      AppUtil.getAppHome("config").toPath().resolve("passwords_add.json").toFile();

  public static PlayerDatabase getPlayerDatabase(PlayerDatabaseType databaseType) {
    return playerDatabaseMap.computeIfAbsent(
        databaseType, PlayerDatabaseFactory::createPlayerDatabase);
  }

  private static PlayerDatabase createPlayerDatabase(PlayerDatabaseType databaseType) {
    try {
      return switch (databaseType) {
        case PASSWORD_FILE -> new PasswordFilePlayerDatabase(PASSWORD_FILE, PASSWORD_ADDITION_FILE);
      };
    } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException(e);
    }
  }
}
