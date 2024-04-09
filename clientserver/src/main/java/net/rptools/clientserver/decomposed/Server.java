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
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Can I call this MessageRouter, or will it end up being more than that/
public class Server {
  private static final Logger log = LogManager.getLogger(Server.class);

  private final MessageHandler messageHandler;
  // NB: Using a Function does not permit releasing the handshake. But that's fine since we can
  // expect the provider to in some way also listen to the handshake to know when to release it. We
  // don't have to take on that responsibility for them.
  private final Function<Connection, Handshake> handshakeProvider;
  // TODO Also track whether the connection is in the handshake state or not.
  //  Is that something I can push into the connection itself?
  private final Map<String, Connection> clientConnections;
  // Reduced observer for when a connection is still in its handshake.
  private final ConnectionObserver handshakeConnectionObserver;
  private final ConnectionObserver connectionObserver;

  public Server(MessageHandler messageHandler, Function<Connection, Handshake> handshakeProvider) {
    this.messageHandler = messageHandler;
    this.handshakeProvider = handshakeProvider;

    this.clientConnections = new ConcurrentHashMap<>();

    this.handshakeConnectionObserver =
        new ConnectionObserver() {
          @Override
          public void onMessageReceived(Connection connection, byte[] message) {
            // Nothing to to do. We expect the handshake itself to handle any messages
            // for the time being.
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

    this.connectionObserver =
        new ConnectionObserver() {
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

  // Only once a connection is set up do we add it to the server. Most importantly though, the
  // server is not the one responsible for deciding what kind of connections to use.
  public void addConnection(@Nonnull Connection connection) {
    log.debug("Adding connection {}", connection.getId());

    final var existingConnection =
        this.clientConnections.putIfAbsent(connection.getId(), connection);
    if (existingConnection != null) {
      log.error("Attempted to add a connection with an existing ID: {}", connection.getId());
      return;
    }

    final var handshake = handshakeProvider.apply(connection);
    handshake.addObserver(
        ignored -> {
          connection.addObserver(connectionObserver);
          connection.removeObserver(handshakeConnectionObserver);
        });
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

    connection.removeObserver(handshakeConnectionObserver);
    connection.removeObserver(connectionObserver);
  }
}
