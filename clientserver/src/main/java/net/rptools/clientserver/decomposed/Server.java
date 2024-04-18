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
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.clientserver.simple.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Can I call this MessageRouter, or will it end up being more than that/
public class Server {
  private static final Logger log = LogManager.getLogger(Server.class);

  private final MessageHandler messageHandler;
  private final Map<String, Connection> clientConnections;
  private final ConnectionObserver connectionObserver;

  public Server(MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
    this.clientConnections = new ConcurrentHashMap<>();

    this.connectionObserver =
        new ConnectionObserver() {
          @Override
          public void onStarted(Connection connection) {
            // Nothing special to do I guess.
          }

          @Override
          public void onMessageReceived(Connection connection, byte[] message) {
            messageHandler.handleMessage(connection.getId(), message);
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
            // No one cares about this server-side right now.
          }
        };
  }

  /**
   * Sends a message to all clients.
   *
   * @param channel
   * @param message
   */
  public void broadcast(Object channel, byte[] message) {
    for (final var conn : this.clientConnections.values()) {
      conn.sendMessage(channel, message);
    }
  }

  // Only once a connection is set up do we add it to the server. Most importantly though, the
  // server is not the one responsible for deciding what kind of connections to use.

  /**
   * Add an initialized connection to the server
   *
   * @param connection the connection to add
   * @return {@code true} if the connection was added; {@code false} if a connection with that ID
   *     already exists.
   */
  @CheckReturnValue
  public boolean addConnection(@Nonnull Connection connection) {
    log.debug("Adding connection {}", connection.getId());

    final var existingConnection =
        this.clientConnections.putIfAbsent(connection.getId(), connection);
    if (existingConnection != null) {
      return false;
    }

    connection.addObserver(connectionObserver);
    return true;
  }

  public void removeConnection(@Nonnull String connectionId, @Nullable String reason) {
    if (reason == null) {
      log.debug("Removing connection {}", connectionId);
    } else {
      log.debug("Removing connection {} for this reason: {}", connectionId, reason);
    }

    final var connection = clientConnections.remove(connectionId);
    if (connection == null) {
      log.error("Attempted to remove unknown connection {}", connectionId);
      return;
    }

    connection.removeObserver(connectionObserver);
  }
}
