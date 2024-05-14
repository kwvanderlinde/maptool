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
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.clientserver.simple.server.HandshakeProvider;
import net.rptools.clientserver.simple.server.LocalServer;
import net.rptools.clientserver.simple.server.Server;
import net.rptools.clientserver.simple.server.ServerObserver;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.PlayerConnectedMsg;
import net.rptools.maptool.server.proto.PlayerDisconnectedMsg;
import net.rptools.maptool.server.proto.SetCampaignMsg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author trevor
 */
public class MapToolServerConnection implements ServerObserver, HandshakeProvider<Player> {
  private static final Logger log = LogManager.getLogger(MapToolServerConnection.class);
  private final Map<String, Player> playerMap = new ConcurrentHashMap<>();
  private final IMapToolServer server;
  private final Server connection;

  public MapToolServerConnection(PersonalServer server, ServerMessageHandler handler) {
    this.connection = new LocalServer(server.getServerSideConnection(), this, handler);
    this.server = server;
    addObserver(this);
  }

  public MapToolServerConnection(MapToolServer server, ServerMessageHandler handler) {
    this.connection =
        ConnectionFactory.getInstance().createServer(server.getConfig(), this, handler);
    this.server = server;
    addObserver(this);
  }

  /*
   * (non-Javadoc)
   *
   * @see net.rptools.clientserver.simple.server.ServerConnection# handleConnectionHandshake(java.net.Socket)
   */
  public Handshake<Player> getConnectionHandshake(Connection conn) {
    var handshake = server.createHandshake(conn);

    handshake.whenComplete(
        (player, ex) -> {
          if (ex != null) {
            log.error("Handshake failure", ex);
          } else {
            playerMap.put(conn.getId().toUpperCase(), player);
          }
        });

    return handshake;
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

  ////
  // SERVER OBSERVER

  /** Handle late connections */
  public void connectionAdded(Connection conn) {
    server.configureClientConnection(conn);

    Player connectedPlayer = playerMap.get(conn.getId().toUpperCase());
    for (Player player : playerMap.values()) {
      var msg = PlayerConnectedMsg.newBuilder().setPlayer(player.toDto());
      sendMessage(conn.getId(), Message.newBuilder().setPlayerConnectedMsg(msg).build());
    }
    var msg =
        PlayerConnectedMsg.newBuilder().setPlayer(connectedPlayer.getTransferablePlayer().toDto());
    broadcastMessage(Message.newBuilder().setPlayerConnectedMsg(msg).build());

    var msg2 = SetCampaignMsg.newBuilder().setCampaign(server.getCampaign().toDto());
    sendMessage(conn.getId(), Message.newBuilder().setSetCampaignMsg(msg2).build());
  }

  public void connectionRemoved(Connection conn) {
    server.releaseClientConnection(conn);
    var player = playerMap.get(conn.getId().toUpperCase()).getTransferablePlayer();
    var msg = PlayerDisconnectedMsg.newBuilder().setPlayer(player.toDto());
    broadcastMessage(
        new String[] {conn.getId()}, Message.newBuilder().setPlayerDisconnectedMsg(msg).build());
    playerMap.remove(conn.getId().toUpperCase());
  }

  public void sendMessage(String id, Message message) {
    log.debug("{} sent to {}: {}", server.getName(), id, message.getMessageTypeCase());
    connection.sendMessage(id, message.toByteArray());
  }

  public void sendMessage(String id, Object channel, Message message) {
    log.debug(
        "{} sent to {}: {} ({})",
        server.getName(),
        id,
        message.getMessageTypeCase(),
        channel.toString());
    connection.sendMessage(id, channel, message.toByteArray());
  }

  public void broadcastMessage(Message message) {
    log.debug("{} broadcast: {}", server.getName(), message.getMessageTypeCase());
    connection.broadcastMessage(message.toByteArray());
  }

  public void broadcastMessage(String[] exclude, Message message) {
    log.debug(
        "{} broadcast: {} except to {}",
        server.getName(),
        message.getMessageTypeCase(),
        String.join(",", exclude));
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
}
