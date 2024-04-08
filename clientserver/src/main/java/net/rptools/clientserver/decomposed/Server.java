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
package net.rptools.clientserver.decomposed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Can I call this MessageRouter, or will it end up being more than that/
public class Server {
  private static final Logger log = LogManager.getLogger(Server.class);

  private final Map<String, Connection> clients;
  private final ConnectionObserver connectionObserver;

  public Server() {
    this.clients = new ConcurrentHashMap<>();
    this.connectionObserver =
        new ConnectionObserver() {
          @Override
          public void onMessageReceived(Connection connection, byte[] message) {
            // TODO Forward to message handler.
            // TODO In the future, when server is more stateless, surely we can simply route to
            //  all other connections?
            // TODO During handshake, messages should not be rebroadcast. Only after handshake
            //  should the connection be considered ready-to-go.
          }

          @Override
          public void onDisconnected(Connection connection, String reason) {
            removeConnection(connection.getId(), reason);
          }

          @Override
          public void onActivity(
              Connection connection,
              Direction direction,
              State state,
              int totalTransferSize,
              int currentTransferSize) {
            // TODO I don't think the server cares about this. Ignore it. Clients should be sure
            //  to register an observer on their connections to capture activity.
          }
        };
  }

  // Only once a connection is set up do we add it to the server. Most importantly though, the
  // server is not the one responsible for deciding what kind of connections to use.
  public void addConnection(@Nonnull Connection connection) {
    log.debug("Adding connection {}", connection.getId());

    final var existingConnection = this.clients.putIfAbsent(connection.getId(), connection);
    if (existingConnection != null) {
      log.error("Attempted to add a connection with an existing ID: {}", connection.getId());
      return;
    }

    connection.addObserver(connectionObserver);

    // TODO Any connection follow-up here?
  }

  public void removeConnection(@Nonnull String connectionId, @Nullable String reason) {
    if (reason == null) {
      log.debug("Removing connection {}", connectionId);
    } else {
      log.debug("Removing connection {} for this reason: {}", connectionId, reason);
    }

    final var connection = clients.remove(connectionId);
    if (connection == null) {
      log.error("Attempted to remove unknown connection {}", connectionId);
      return;
    }

    connection.removeObserver(connectionObserver);
  }
}
