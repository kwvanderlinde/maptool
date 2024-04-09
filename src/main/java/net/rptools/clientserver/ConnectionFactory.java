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
package net.rptools.clientserver;

import java.io.IOException;
import java.util.function.Function;
import net.rptools.clientserver.decomposed.ConnectionHandler;
import net.rptools.clientserver.decomposed.socket.SocketConnectionHandler;
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.MessageHandler;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.clientserver.simple.connection.SocketConnection;
import net.rptools.clientserver.simple.connection.WebRTCConnection;
import net.rptools.clientserver.simple.server.HandshakeProvider;
import net.rptools.clientserver.simple.server.Server;
import net.rptools.clientserver.simple.server.SocketServer;
import net.rptools.clientserver.simple.server.WebRTCServer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.server.ServerConfig;
import org.jetbrains.annotations.NotNull;

public class ConnectionFactory {
  private static ConnectionFactory instance = new ConnectionFactory();

  public static ConnectionFactory getInstance() {
    return instance;
  }

  public Connection createConnection(String id, ServerConfig config) throws IOException {
    if (!config.getUseWebRTC() || config.isPersonalServer())
      return new SocketConnection(id, config.getHostName(), config.getPort());

    return new WebRTCConnection(
        id,
        config.getServerName(),
        new WebRTCConnection.Listener() {
          @Override
          public void onLoginError() {
            MapTool.showError("Handshake.msg.playerAlreadyConnected");
          }
        });
  }

  public Server createServer(
      ServerConfig config, HandshakeProvider handshake, MessageHandler messageHandler)
      throws IOException {
    if (!config.getUseWebRTC() || config.isPersonalServer()) {
      return new SocketServer(config.getPort(), handshake, messageHandler);
    }

    return new WebRTCServer(
        config.getServerName(),
        handshake,
        messageHandler,
        new WebRTCServer.Listener() {
          @Override
          public void onLoginError() {
            MapTool.showError("ServerDialog.error.serverAlreadyExists");
          }

          @Override
          public void onUnexpectedClose() {
            MapTool.stopServer();
          }
        });
  }

  public net.rptools.clientserver.decomposed.Server createDecomposedServer(
      ServerConfig config,
      Function<net.rptools.clientserver.decomposed.Connection, Handshake> handshakeProvider,
      MessageHandler messageHandler) {
    final var server =
        new net.rptools.clientserver.decomposed.Server(messageHandler, handshakeProvider);

    // TODO Based on server config, optionally instantiate a SocketConnectionHandler or
    //  WebRTCConnectionHandler.
    // TODO ConnectionHandler needs a way to signal when it's ready or if it encoutnered a startup
    //  error. I imagine this will be necessary for good user-facing message, or if the application
    //  wants to wait until it is started. Although if it just ends up being for messages, then is
    //  it really any different from any unexpected failure?
    final var connectionHandler = new SocketConnectionHandler();
    final var listener =
        new ConnectionHandler.Listener() {
          @Override
          public void onConnected(
              @NotNull net.rptools.clientserver.decomposed.Connection connection) {
            server.addConnection(connection);
          }

          @Override
          public void onConnectionClosed(@NotNull String connectionId) {
            server.removeConnection(connectionId, null);
          }

          @Override
          public void onConnectionLost(@NotNull String connectionId, @NotNull String reason) {
            server.removeConnection(connectionId, reason);
          }
        };
    connectionHandler.addListener(listener);

    return server;
  }
}
