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
package net.rptools.maptool.server;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.rptools.clientserver.ConnectionFactory;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.clientserver.simple.server.HandshakeProvider;
import net.rptools.clientserver.simple.server.Server;
import net.rptools.clientserver.simple.server.ServerObserver;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.ServerSidePlayerDatabase;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.PlayerConnectedMsg;
import net.rptools.maptool.server.proto.PlayerDisconnectedMsg;
import net.rptools.maptool.server.proto.SetCampaignMsg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author trevor
 */
public class MapToolServerConnection {
  private static final Logger log = LogManager.getLogger(MapToolServerConnection.class);
  private final Map<String, Player> playerMap = new ConcurrentHashMap<>();
  private final MapToolServer server;
  private final Server connection;
  private final ServerSidePlayerDatabase playerDatabase;
  private final boolean useEasyConnect;

  // TODO It seems to me that the only reason for MapToolServerConnection to be a HandshakeProvider
  //  is so that it can register a completion handler on the Handshake. But surely that can be done
  //  just as well by adding handshake observation to ServerObserver, allowing registration?
  // TODO It is similarly wierd that MapToolServerConnection maintains references to both the
  //  MapToolServer and the Server. I would expect some directionality here. Worse than that,
  //  MapToolServerConnection maintains some server state and logic, (player map, player database)
  //  and connection logic (handhsakeMap), so what is being gained by separating MapToolServer and
  //  MapToolServerConnection?
  // TODO Okay, with the observers split out, it is starting to look to me like most of this
  //  rigamorole is about updating the player map... and that's it. What confuses me now is that I
  //  would have expected the player map to be the responsibility of the player database, given that
  //  it maintains the set of connected players.
  // TODO My next thinkado is that AbstractServer should actually have no knowledge of handshakes.
  //  AbstractServer should be a general implementations of a server, and should only care about
  //  connection-level handshakes (WEbRTC, TCP, etc). The MapTool-specific code should - once a
  //  connection is established - then execute a handshake _over_ the connection. I.e., the flow
  //  would be more akin to this:
  //  1. AbstractServer implementation is started and listens for connections.
  //  2. Request to connect comes in (via socket or WebRTC). Network-level handshake is performed to
  //     establish the connection.
  //  3. AbstractServer sends `connectionAdded` events to listeners (including MapToolServer).
  //  4. MapToolServer registers a `ServerHandshake` as a `MessageHandler` on the connection.
  //  5. Once the hashshake completes, MapToolServer removes the `ServerHandshake` `MessageHandler`
  //     and replaces it with a `ServerMessageHandler`.
  //  Only after (5) would MapTool consider the player connected, even though an underlying
  //  connection was established in (2).

  public MapToolServerConnection(
      MapToolServer server, ServerSidePlayerDatabase playerDatabase, ServerMessageHandler handler)
      throws IOException {
    this.connection =
        ConnectionFactory.getInstance()
            .createServer(server.getConfig(), new MapToolHandshakeProvider(), handler);
    this.server = server;
    this.playerDatabase = playerDatabase;
    this.useEasyConnect = server.getConfig().getUseEasyConnect();
    addObserver(new MapToolServerObserver());
  }

  public Player getPlayer(String id) {
    for (Player player : playerMap.values()) {
      if (player.getName().equalsIgnoreCase(id)) {
        return player;
      }
    }
    return null;
  }

  public String getConnectionId(String playerId) {
    for (Map.Entry<String, Player> entry : playerMap.entrySet()) {
      if (entry.getValue().getName().equalsIgnoreCase(playerId)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public void sendMessage(String id, Message message) {
    log.debug(
        server.getConfig().getServerName()
            + " sent to "
            + id
            + ": "
            + message.getMessageTypeCase());
    connection.sendMessage(id, message.toByteArray());
  }

  public void sendMessage(String id, Object channel, Message message) {
    log.debug(
        server.getConfig().getServerName()
            + " sent to "
            + id
            + ":"
            + message.getMessageTypeCase()
            + " ("
            + channel.toString()
            + ")");
    connection.sendMessage(id, channel, message.toByteArray());
  }

  public void broadcastMessage(Message message) {
    log.debug(server.getConfig().getServerName() + " broadcast: " + message.getMessageTypeCase());
    connection.broadcastMessage(message.toByteArray());
  }

  public void broadcastMessage(String[] exclude, Message message) {
    log.debug(
        server.getConfig().getServerName()
            + " broadcast: "
            + message.getMessageTypeCase()
            + " except to "
            + String.join(",", exclude));
    connection.broadcastMessage(exclude, message.toByteArray());
  }

  public void open() throws IOException {
    connection.start();
  }

  public void close() {
    connection.close();
  }

  public void addObserver(ServerObserver observer) {
    connection.addObserver(observer);
  }

  public void removeObserver(ServerObserver observer) {
    connection.removeObserver(observer);
  }

  private final class MapToolHandshakeProvider implements HandshakeProvider {
    @Override
    public ServerHandshake getConnectionHandshake(Connection conn) {
      var handshake = new ServerHandshake(server, conn, playerDatabase, useEasyConnect);
      handshake.addObserver(new MapToolHandshakeObserver());
      conn.addMessageHandler(handshake);
      return handshake;
    }

    @Override
    public void releaseHandshake(ServerHandshake handshake) {
      handshake.getConnection().removeMessageHandler(handshake);
    }
  }

  private final class MapToolHandshakeObserver implements HandshakeObserver<ServerHandshake> {
    @Override
    public void onCompleted(ServerHandshake handshake) {
      handshake.removeObserver(this);
      if (handshake.isSuccessful()) {
        Player player = handshake.getPlayer();

        if (player != null) {
          playerMap.put(handshake.getConnection().getId().toUpperCase(), player);
        }
      } else {
        var exception = handshake.getException();
        if (exception != null) log.error("Handshake failure: " + exception, exception);
      }
    }
  }

  private final class MapToolServerObserver implements ServerObserver {
    /** Handle late connections */
    public void connectionAdded(Connection conn) {
      server.configureClientConnection(conn);

      Player connectedPlayer = playerMap.get(conn.getId().toUpperCase());
      for (Player player : playerMap.values()) {
        var msg = PlayerConnectedMsg.newBuilder().setPlayer(player.toDto());
        server
            .getConnection()
            .sendMessage(conn.getId(), Message.newBuilder().setPlayerConnectedMsg(msg).build());
      }
      var msg =
          PlayerConnectedMsg.newBuilder()
              .setPlayer(connectedPlayer.getTransferablePlayer().toDto());
      server
          .getConnection()
          .broadcastMessage(Message.newBuilder().setPlayerConnectedMsg(msg).build());

      var msg2 = SetCampaignMsg.newBuilder().setCampaign(server.getCampaign().toDto());
      server
          .getConnection()
          .sendMessage(conn.getId(), Message.newBuilder().setSetCampaignMsg(msg2).build());
    }

    public void connectionRemoved(Connection conn) {
      server.releaseClientConnection(conn.getId());
      var player = playerMap.get(conn.getId().toUpperCase()).getTransferablePlayer();
      var msg = PlayerDisconnectedMsg.newBuilder().setPlayer(player.toDto());
      server
          .getConnection()
          .broadcastMessage(
              new String[] {conn.getId()},
              Message.newBuilder().setPlayerDisconnectedMsg(msg).build());
      playerMap.remove(conn.getId().toUpperCase());
    }
  }
}
