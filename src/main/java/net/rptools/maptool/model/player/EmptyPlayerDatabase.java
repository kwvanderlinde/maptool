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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.util.cipher.CipherUtil;

public class EmptyPlayerDatabase implements PlayerDatabase {
  @Override
  public boolean playerExists(String playerName) {
    return false;
  }

  @Override
  public Player getPlayer(String playerName) {
    return null;
  }

  @Override
  public boolean supportsAsymmetricalKeys() {
    return false;
  }

  @Override
  public boolean supportsRolePasswords() {
    return false;
  }

  @Override
  public AuthMethod getAuthMethod(Player player) {
    return AuthMethod.PASSWORD;
  }

  @Override
  public CompletableFuture<CipherUtil.Key> getPublicKey(Player player, MD5Key md5key)
      throws ExecutionException, InterruptedException {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public Set<String> getEncodedPublicKeys(String name) {
    return Set.of();
  }

  @Override
  public CompletableFuture<Boolean> hasPublicKey(Player player, MD5Key md5key) {
    return CompletableFuture.completedFuture(false);
  }

  @Override
  public boolean isPlayerRegistered(String name) {
    return false;
  }

  @Override
  public void playerSignedIn(Player player) {}

  @Override
  public void playerSignedOut(Player player) {}

  @Override
  public Set<Player> getOnlinePlayers() {
    return Set.of();
  }

  @Override
  public boolean isPlayerConnected(String name) {
    return false;
  }
}
