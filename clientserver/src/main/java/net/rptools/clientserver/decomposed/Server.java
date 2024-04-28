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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.clientserver.simple.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Can I call this MessageRouter, or will it end up being more than that/
public class Server {
  private static final Logger log = LogManager.getLogger(Server.class);

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          new ThreadFactoryBuilder().setNameFormat("server-thread-%d").build());

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
            executor.execute(() -> messageHandler.handleMessage(connection.getId(), message));
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

  public ExecutorService getExecutor() {
    return executor;
  }

  /**
   * Sends a message to all clients.
   *
   * @param message
   */
  public void broadcast(byte[] message) {
    for (final var conn : this.clientConnections.values()) {
      conn.sendMessage(message);
    }
  }

  public void broadcast(String[] excludeIds, byte[] message) {
    // NB: excludeIds is in practice no longer than one, so no point building a set.
    // TODO Recipient inclusion/exclusion would be much more efficient if we could use a bitmask.
    for (final var conn : this.clientConnections.values()) {
      if (Arrays.stream(excludeIds).anyMatch(id -> conn.getId().equals(id))) {
        continue;
      }
      conn.sendMessage(message);
    }
  }

  public void sendMessage(String connectionId, byte[] message) {
    final var connection = this.clientConnections.get(connectionId);
    if (connection != null) {
      connection.sendMessage(message);
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

  public Optional<Connection> removeConnection(
      @Nonnull String connectionId, @Nullable String reason) {
    if (reason == null) {
      log.debug("Removing connection {}", connectionId);
    } else {
      log.debug("Removing connection {} for this reason: {}", connectionId, reason);
    }

    final var connection = clientConnections.remove(connectionId);
    if (connection == null) {
      log.error("Attempted to remove unknown connection {}", connectionId);
      return Optional.empty();
    }

    connection.removeObserver(connectionObserver);
    return Optional.of(connection);
  }
}
