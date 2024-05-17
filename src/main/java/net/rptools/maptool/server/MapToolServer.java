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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.MessageHandler;
import net.rptools.clientserver.simple.channels.Channel;
import net.rptools.clientserver.simple.channels.ZstdChannel;
import net.rptools.clientserver.simple.connection.ChannelConnection;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.clientserver.simple.connection.DirectConnection;
import net.rptools.clientserver.simple.server.ChannelReceiver;
import net.rptools.clientserver.simple.server.NilChannelReceiver;
import net.rptools.clientserver.simple.server.Router;
import net.rptools.clientserver.simple.server.SocketChannelReceiver;
import net.rptools.clientserver.simple.server.WebRTCChannelReceiver;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolClient;
import net.rptools.maptool.client.MapToolRegistry;
import net.rptools.maptool.client.ui.connectioninfodialog.ConnectionInfoDialog;
import net.rptools.maptool.common.MapToolConstants;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.TextMessage;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.ServerSidePlayerDatabase;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.PlayerConnectedMsg;
import net.rptools.maptool.server.proto.PlayerDisconnectedMsg;
import net.rptools.maptool.server.proto.SetCampaignMsg;
import net.rptools.maptool.server.proto.UpdateAssetTransferMsg;
import net.rptools.maptool.transfer.AssetProducer;
import net.rptools.maptool.transfer.AssetTransferManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author drice
 */
public class MapToolServer {
  private static final Logger log = LogManager.getLogger(MapToolServer.class);
  private static final int ASSET_CHUNK_SIZE = 5 * 1024;

  private final ServerConfig config;
  private final ServerSidePlayerDatabase playerDatabase;

  private final Map<String, Player> playerMap = new ConcurrentHashMap<>();
  private final Map<String, AssetTransferManager> assetManagerMap =
      Collections.synchronizedMap(new HashMap<String, AssetTransferManager>());

  private final ChannelReceiver receiver;
  private final Router router;
  private final MessageHandler messageHandler;
  private final AssetProducerThread assetProducerThread;

  private Campaign campaign;
  private ServerPolicy policy;
  private HeartbeatThread heartbeatThread;
  private final DirectConnection.Pair localConnection;
  private final MapToolClient localClient;

  public MapToolServer(
      Campaign campaign,
      LocalPlayer localPlayer,
      ServerConfig config,
      ServerPolicy policy,
      ServerSidePlayerDatabase playerDb) {
    this.localConnection = DirectConnection.create("local"); // TODO Should have a real name

    this.config = config;
    this.policy = policy;
    playerDatabase = playerDb;

    // Server must have a different campaign than the client.
    this.campaign = new Campaign(campaign);

    assetProducerThread = new AssetProducerThread();

    this.localClient =
        new MapToolClient(
            campaign, localPlayer, localConnection.clientSide(), policy, playerDatabase);

    this.router = new Router();
    this.messageHandler = new ServerMessageHandler(this);

    if (config == null) {
      receiver = new NilChannelReceiver();
    } else if (config.getUseWebRTC()) {
      receiver = new WebRTCChannelReceiver(config.getServerName());
    } else {
      receiver = new SocketChannelReceiver(config.getPort());
    }
    receiver.addListener(new ReceiverObserver());
  }

  public MapToolClient getLocalClient() {
    return localClient;
  }

  public void sendMessage(String id, Message message) {
    log.debug("{} sent to {}: {}", getName(), id, message.getMessageTypeCase());
    router.sendMessage(id, message.toByteArray());
  }

  public void sendMessage(String id, Object channel, Message message) {
    log.debug(
        "{} sent to {}: {} ({})", getName(), id, message.getMessageTypeCase(), channel.toString());
    router.sendMessage(id, message.toByteArray());
  }

  public void broadcastMessage(Message message) {
    log.debug("{} broadcast: {}", getName(), message.getMessageTypeCase());
    router.broadcastMessage(message.toByteArray());
  }

  public void broadcastMessage(String[] exclude, Message message) {
    log.debug(
        "{} broadcast: {} except to {}",
        getName(),
        message.getMessageTypeCase(),
        String.join(",", exclude));
    router.broadcastMessage(exclude, message.toByteArray());
  }

  public Handshake<Player> createHandshake(Connection conn) {
    return new ServerHandshake(
        this, conn, playerDatabase, config == null ? false : config.getUseEasyConnect());
  }

  public boolean isPersonalServer() {
    return config == null;
  }

  public boolean isServerRegistered() {
    return config != null && config.isServerRegistered();
  }

  public String getName() {
    return config == null ? "" : config.getServerName();
  }

  public int getPort() {
    return config == null ? -1 : config.getPort();
  }

  private String getConnectionId(String playerId) {
    for (Map.Entry<String, Player> entry : playerMap.entrySet()) {
      if (entry.getValue().getName().equalsIgnoreCase(playerId)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public @Nullable Connection getClientConnection(String playerName) {
    var connectionId = getConnectionId(playerName);
    if (connectionId == null) {
      return null;
    }
    return router.getConnection(connectionId);
  }

  /** Forceably disconnects a client and cleans up references to it */
  public void releaseClientConnection(Connection connection) {
    router.removeConnection(connection);
    assetManagerMap.remove(connection.getId());
  }

  public void addAssetProducer(String connectionId, AssetProducer producer) {
    AssetTransferManager manager = assetManagerMap.get(connectionId);
    manager.addProducer(producer);
  }

  private @Nullable Player getPlayer(String id) {
    for (Player player : playerMap.values()) {
      if (player.getName().equalsIgnoreCase(id)) {
        return player;
      }
    }
    return null;
  }

  public boolean isPlayerConnected(String id) {
    return getPlayer(id) != null;
  }

  public void updatePlayerStatus(String playerName, GUID zoneId, boolean loaded) {
    var player = getPlayer(playerName);
    if (player != null) {
      player.setLoaded(loaded);
      player.setZoneId(zoneId);
    }
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public ServerPolicy getPolicy() {
    return policy;
  }

  public void updateServerPolicy(ServerPolicy policy) {
    this.policy = new ServerPolicy(policy);
  }

  public void stop() {
    receiver.close();

    for (var connection : router.removeAll()) {
      connection.close();
    }

    if (heartbeatThread != null) {
      heartbeatThread.shutdown();
    }
    if (assetProducerThread != null) {
      assetProducerThread.shutdown();
    }
  }

  private static final Random random = new Random();

  public void start() throws IOException {
    localClient.start();

    // Adopt the local connection right away.
    localConnection.serverSide().open();
    connectionAdded(localConnection.serverSide(), localClient.getPlayer());

    assetProducerThread.start();

    // Start a heartbeat if requested
    if (config != null && config.isServerRegistered()) {
      heartbeatThread = new HeartbeatThread(config.getPort());
      heartbeatThread.start();
    }
    receiver.start();
  }

  private class HeartbeatThread extends Thread {
    private final int port;
    private boolean stop = false;
    private static final int HEARTBEAT_DELAY = 10 * 60 * 1000; // 10 minutes
    private static final int HEARTBEAT_FLUX = 20 * 1000; // 20 seconds

    private boolean ever_had_an_error = false;

    public HeartbeatThread(int port) {
      this.port = port;
    }

    @Override
    public void run() {
      int WARNING_TIME = 2; // number of heartbeats before popup warning
      int errors = 0;
      String IP_addr = ConnectionInfoDialog.getExternalAddress();

      while (!stop) {
        try {
          Thread.sleep(HEARTBEAT_DELAY + (int) (HEARTBEAT_FLUX * random.nextFloat()));
          // Pulse
          MapToolRegistry.getInstance().heartBeat();
          // If the heartbeat worked, reset the counter if the last one failed
          if (errors != 0) {
            String msg = I18N.getText("msg.info.heartbeat.registrySuccess", errors);
            SwingUtilities.invokeLater(
                () -> {
                  // Write to the GM's console. (Code taken from client.functions.ChatFunction)
                  MapTool.serverCommand().message(TextMessage.gm(null, msg));
                  // Write to our console. (Code taken from client.functions.ChatFunction)
                  MapTool.addServerMessage(TextMessage.me(null, msg));
                });
            errors = 0;
            WARNING_TIME = 2;
          }
        } catch (InterruptedException ie) {
          // This means we are being stopped from the outside, between heartbeats
          break;
        } catch (Exception e) {
          // Any other exception is a problem with the Hessian protocol and/or a network issue
          // Regardless, we will count the number of consecutive errors and display a dialog
          // at appropriate times, but otherwise ignore it. The purpose of the heartbeat is
          // to let the website registry know this server is still running so that clients can
          // easily connect. If it breaks in the middle of a game, the clients are already connected
          // so it's not *that* terrible. However, our dialog to the user should tell them where
          // the connection info can be found so they can give it to a client, if needed.
          errors++;
          if ((errors % WARNING_TIME) == 0) {
            WARNING_TIME = Math.min(WARNING_TIME * 3, 10);
            // It's been X heartbeats since we last talked to the registry successfully. Let
            // the user know we'll keep trying, but there may be an unrecoverable problem.
            // We use a linear backoff so we don't inundate the user with popups!

            String msg = I18N.getText("msg.info.heartbeat.registryFailure", IP_addr, port, errors);
            SwingUtilities.invokeLater(
                () -> {
                  // Write to the GM's console. (Code taken from client.functions.ChatFunction)
                  MapTool.serverCommand().message(TextMessage.gm(null, msg));
                  // Write to our console. (Code taken from client.functions.ChatFunction)
                  MapTool.addServerMessage(TextMessage.me(null, msg));

                  // This is the first time the heartbeat has failed in this stretch of time.
                  // Only writes to the log on the first error. Should it always add an entry?
                  if (!ever_had_an_error) {
                    ever_had_an_error = true;
                    // Uses a popup to tell the user what's going on. Includes a 'Logger.warn()'
                    // message.
                    MapTool.showWarning(msg, e);
                  }
                });
          }
        }
      }
    }

    public void shutdown() {
      stop = true;
      interrupt();
    }
  }

  ////
  // CLASSES
  private class AssetProducerThread extends Thread {
    private boolean stop = false;

    public AssetProducerThread() {
      setName("AssetProducerThread");
    }

    @Override
    public void run() {
      while (!stop) {
        Entry<String, AssetTransferManager> entryForException = null;
        try {
          boolean lookForMore = false;
          for (Entry<String, AssetTransferManager> entry : assetManagerMap.entrySet()) {
            entryForException = entry;
            var chunk = entry.getValue().nextChunk(ASSET_CHUNK_SIZE);
            if (chunk != null) {
              lookForMore = true;
              var msg = UpdateAssetTransferMsg.newBuilder().setChunk(chunk);
              sendMessage(
                  entry.getKey(),
                  MapToolConstants.Channel.IMAGE,
                  Message.newBuilder().setUpdateAssetTransferMsg(msg).build());
            }
          }
          if (lookForMore) {
            continue;
          }
          // Sleep for a bit
          synchronized (this) {
            Thread.sleep(500);
          }
        } catch (Exception e) {
          log.warn("Couldn't retrieve AssetChunk for {}", entryForException.getKey(), e);
          // keep on going
        }
      }
    }

    public void shutdown() {
      stop = true;
    }
  }

  /** Handle late connections */
  private void connectionAdded(Connection conn, Player player) {
    playerMap.put(conn.getId().toUpperCase(), player);

    conn.addMessageHandler(messageHandler);
    conn.addDisconnectHandler(this::connectionDisconnected);

    log.debug("About to add new client");
    router.reapClients();
    router.addConnection(conn);

    assetManagerMap.put(conn.getId(), new AssetTransferManager());

    Player connectedPlayer = playerMap.get(conn.getId().toUpperCase());
    for (Player existingPlayer : playerMap.values()) {
      var msg = PlayerConnectedMsg.newBuilder().setPlayer(existingPlayer.toDto());
      sendMessage(conn.getId(), Message.newBuilder().setPlayerConnectedMsg(msg).build());
    }
    var msg =
        PlayerConnectedMsg.newBuilder().setPlayer(connectedPlayer.getTransferablePlayer().toDto());
    broadcastMessage(Message.newBuilder().setPlayerConnectedMsg(msg).build());

    var msg2 = SetCampaignMsg.newBuilder().setCampaign(campaign.toDto());
    sendMessage(conn.getId(), Message.newBuilder().setSetCampaignMsg(msg2).build());
  }

  private void connectionDisconnected(Connection conn) {
    releaseClientConnection(conn);

    var player = playerMap.get(conn.getId().toUpperCase()).getTransferablePlayer();
    var msg = PlayerDisconnectedMsg.newBuilder().setPlayer(player.toDto());
    broadcastMessage(
        new String[] {conn.getId()}, Message.newBuilder().setPlayerDisconnectedMsg(msg).build());
    playerMap.remove(conn.getId().toUpperCase());
  }

  private final class ReceiverObserver implements ChannelReceiver.Observer {
    @Override
    public void onConnected(String id, Channel channel) {
      channel = new ZstdChannel(channel);

      final var connection = new ChannelConnection(id, channel);

      var handshake = createHandshake(connection);
      handshake.whenComplete(
          (result, error) -> {
            if (error != null) {
              log.error("Client closing: bad handshake", error);
              connection.close();
            } else {
              connectionAdded(connection, result);
            }
          });

      try {
        connection.open();
      } catch (IOException e) {
        log.error("Failed to open connection", e);
        return;
      }

      // Make sure the client is allowed
      handshake.startHandshake();
    }

    @Override
    public void onError(Exception error) {
      log.error(error.getMessage(), error);
    }
  }
}
