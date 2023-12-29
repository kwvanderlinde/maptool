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

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import net.rptools.maptool.util.cipher.CipherUtil;

/**
 * A {@link net.rptools.maptool.model.player.PlayerDatabase} augmented with functinoality only
 * needed by servers (password validation, etc).
 */
public interface ServerSidePlayerDatabase extends PlayerDatabase {
  /**
   * Returns the {@link net.rptools.maptool.util.cipher.CipherUtil.Key} for the player. If the
   * database only supports role based passwords the returned value will be empty.
   *
   * @param playerName The name of the player to check.
   * @return the {@link net.rptools.maptool.util.cipher.CipherUtil.Key} to use.
   */
  Optional<CipherUtil.Key> getPlayerPassword(String playerName);

  /**
   * Returns the salt used for the player's password.
   *
   * @param playerName the name of the player to get the password salt for.
   * @return the salt used for the password.
   */
  byte[] getPlayerPasswordSalt(String playerName);

  // TODO getPlayerWithRole should only be called for password-based auth, so there should be no
  //  need for PasswordFilePlayerDatabase to implement it.

  /**
   * Returns the player overriding the role in the database with the specified role.
   *
   * @param playerName The name of the player to retrieve.
   * @param role The role for the player.
   * @return The player.
   */
  Player getPlayerWithRole(String playerName, Player.Role role)
      throws NoSuchAlgorithmException, InvalidKeySpecException;

  /**
   * Returns the password required for the role. If role authentication is not supported this will
   * be empty.
   *
   * @param role The role to retrieve the password for.
   * @return The password for the role.
   */
  Optional<CipherUtil.Key> getRolePassword(Player.Role role);

  /**
   * Returns the reason tha the player has been disabled, if the player has not been disabled then
   * an empty string is returned.
   *
   * @param player the player to get the disabled reason for.
   * @return the reason that the player has been disabled, or empty string if they have not.
   */
  String getBlockedReason(Player player);
}
