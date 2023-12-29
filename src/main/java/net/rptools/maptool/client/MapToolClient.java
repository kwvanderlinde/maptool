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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutionException;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.server.ServerConfig;

/**
 * The client side of a client-server channel.
 *
 * <p>This isn't the GUI per se, but something that can be swapped out as different servers are
 * connected to. It compiles a few things that used to be static state of {@link
 * net.rptools.maptool.client.MapTool} and elsewhere.
 */
public class MapToolClient {
  private final LocalPlayer player;
  private MapToolConnection conn;
  private final ClientMessageHandler handler;

  public MapToolClient(LocalPlayer player, ServerConfig config, ClientMessageHandler messageHandler)
      throws IOException {
    this.player = player;
    this.handler = messageHandler;

    conn = new MapToolConnection(config, player);
    conn.onCompleted(
        () -> {
          conn.addMessageHandler(messageHandler);
        });
  }

  public void start() throws IOException, ExecutionException, InterruptedException {
    conn.start();
  }

  public void close() throws IOException {
    // TODO WHy not just .close()? Surely if it's not alive that would be a no-op.
    if (conn.isAlive()) {
      conn.close();
    }
  }

  public void connect(MapToolConnection conn) {
    this.conn = conn;
  }

  public LocalPlayer getPlayer() {
    return player;
  }

  public MapToolConnection getConnection() {
    return conn;
  }

  public static MapToolClient createDefault(ClientMessageHandler messageHandler) {
    try {
      return new MapToolClient(
          new LocalPlayer("", Player.Role.GM, ServerConfig.getPersonalServerGMPassword()),
          ServerConfig.createPersonalServerConfig(),
          messageHandler);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
      throw new RuntimeException("Unable to create default client", e);
    }
  }
}
