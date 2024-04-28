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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import net.rptools.clientserver.decomposed.Connection;
import net.rptools.clientserver.decomposed.ConnectionHandler;
import net.rptools.clientserver.decomposed.Server;
import net.rptools.clientserver.decomposed.socket.SocketConnectionHandler;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.PlayerConnectedMsg;
import net.rptools.maptool.server.proto.SetCampaignMsg;
import net.rptools.maptool.server.proto.StartAssetTransferMsg;
import net.rptools.maptool.server.proto.UpdateAssetTransferMsg;
import net.rptools.maptool.transfer.AssetProducer;
import net.rptools.maptool.transfer.AssetTransferManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class MapToolServer2 implements IMapToolServer {
  private static final Logger log = LogManager.getLogger(MapToolServer2.class);

  private @Nonnull Campaign campaign;
  private @Nonnull ServerConfig config;
  private @Nonnull ServerPolicy policy;
  private @Nonnull PlayerDatabase playerDatabase;

  private final Server server;
  // TODO Enforce invariants that playersByConnectionId only has keys present in the server. But how
  //  can we enforce that?
  // TODO The original uppercased the connection IDs. Why? Should we do so to?
  private final Map<String, Player> playersByConnectionId = new HashMap<>();
  private final ConnectionHandler connectionHandler;

  private final AssetProducerThread assetProducerThread;

  // TODO I feel like we should be the ones deciding the player database... no?

  public MapToolServer2(ServerConfig config, ServerPolicy policy, PlayerDatabase playerDb) {
    this.campaign = new Campaign();
    this.config = config;
    this.policy = policy;
    this.playerDatabase = playerDb;

    // TODO Obviously this casting will not work. We will have to change ServerMessageHandler
    //  to accept a MapToolServer2. There's not much about it, mostly the message handler just
    //  needs to look up the server's campaign, and sometimes send messages and
    //  connections.
    // TODO In my new approach, handshakes are decided entirely here.
    this.server = new Server(new ServerMessageHandler(this));
    // TODO Create via ConnectionFactory so that ServerConfig is accounted for. This might even
    //  be feasible to create at the call site and injected instead of a hard dependency here).
    this.connectionHandler = new SocketConnectionHandler(config.getPort());

    // TODO Right now old-school connections to decomposed server won't work because we don't LZMA
    //  compress. Will need to add that in here or hack it out there in order to test. This is just
    //  one more benefit that decomposition will bring.

    this.connectionHandler.addListener(new ConnectionHandlerListener());

    this.assetProducerThread = new AssetProducerThread();
  }

  // TODO Eliminate IOException.
  @Override
  public void start() throws IOException {
    this.connectionHandler.start();
  }

  @Override
  public void stop() {
    this.connectionHandler.stop();
    // TODO Heartbeart thread, not that I like the implementation.
    this.assetProducerThread.shutdown();
  }

  public @Nonnull Campaign getCampaign() {
    return this.campaign;
  }

  public void setCampaign(@Nonnull Campaign campaign) {
    this.campaign = campaign;
  }

  @Override
  public @Nonnull ServerConfig getConfig() {
    return config;
  }

  @Override
  public @Nonnull ServerPolicy getPolicy() {
    return policy;
  }

  public void updateServerPolicy(@Nonnull ServerPolicy policy) {
    this.policy = policy;
  }

  @Override
  public String getConnectionId(String playerId) {
    for (Map.Entry<String, Player> entry : playersByConnectionId.entrySet()) {
      if (entry.getValue().getName().equalsIgnoreCase(playerId)) {
        return entry.getKey();
      }
    }
    return null;
  }

  @Override
  public boolean isPlayerConnected(String id) {
    return getPlayer(id) != null;
  }

  private Player getPlayer(String playerId) {
    for (Map.Entry<String, Player> entry : playersByConnectionId.entrySet()) {
      if (entry.getValue().getName().equalsIgnoreCase(playerId)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Override
  public void bootPlayer(String playerId) {
    // TODO A translated and informative reason.
    server
        .removeConnection(playerId, "booted")
        .ifPresent(
            connection -> {
              connection.close();
              assetProducerThread.removeConnection(connection.getId());
              playersByConnectionId.remove(connection.getId());
            });
    // TODO Right now, ServerMessageHandler sends a BootPlayerMsg to all clients, by simply
    //  forwarding an incoming message. However, it may make sense to instead build such a message
    //  here as a matter of Single Responsibility. On the other hand, I like the model
    //  rebroadcasting incoming messages with each peer keeping things in sync.
  }

  @Override
  public void addAssetProducer(String connectionId, AssetProducer producer) {
    var msg = StartAssetTransferMsg.newBuilder().setHeader(producer.getHeader().toDto());
    server.sendMessage(
        connectionId, Message.newBuilder().setStartAssetTransferMsg(msg).build().toByteArray());

    // TODO An AssetProducer yields chunks of a single asset via AssetChunkDto. In my vision for the
    //  future, this would not be necessary, since we could instead stream the entire asset to the
    //  connection, using connection-level message chunking. This would also not be asset-specific.
    assetProducerThread.addAsset(connectionId, producer);
  }

  @Override
  public void updatePlayerStatus(String playerName, GUID zoneId, boolean loaded) {
    final var player = getPlayer(playerName);
    player.setLoaded(loaded);
    player.setZoneId(zoneId);
  }

  @Override
  public void broadcastMessage(String[] exclude, Message message) {
    server.broadcast(exclude, message.toByteArray());
  }

  @Override
  public void sendMessage(String id, Message message) {
    server.sendMessage(id, message.toByteArray());
  }

  private final class ConnectionHandlerListener implements ConnectionHandler.Listener {
    @Override
    public void onError(@NotNull Exception exception) {
      // TODO Shutdown the server or something?
    }

    @Override
    public void onConnected(@NotNull Connection connection) {
      // Start the handshake.
      final var handshake =
          new ServerHandshake2(
              server.getExecutor(), connection, playerDatabase, config.getUseEasyConnect());
      handshake
          .run()
          .whenComplete(
              (player, exception) -> {
                if (exception != null) {
                  onHandshakeError(connection, exception);
                }
                if (player != null) {
                  onHandshakeSuccess(connection, player);
                }
              });

      connection.start();
    }
  }

  private void onHandshakeError(Connection connection, Throwable exception) {
    log.error("Handshake for connection {} failed with message: {}", connection.getId(), exception);
    connection.close();
  }

  private void onHandshakeSuccess(Connection connection, Player connectedPlayer) {
    final var connectionAdded = server.addConnection(connection);
    if (!connectionAdded) {
      log.error("Another connection with ID {} already exists", connection.getId());
      connection.close();
      return;
    }

    // TODO AbstractServer.onCompleted() took the opportunity to reapClients() here. Let's
    //  add that to the decomposed server as well, through the message pump though.

    assetProducerThread.addConnection(connection.getId());

    // Send the connected player to each client.
    // Original in MapToolServer connection does this in the reverse order, but I like this.
    server.broadcast(
        Message.newBuilder()
            .setPlayerConnectedMsg(
                PlayerConnectedMsg.newBuilder()
                    .setPlayer(connectedPlayer.getTransferablePlayer().toDto()))
            .build()
            .toByteArray());
    for (Player player : playersByConnectionId.values()) {
      connection.sendMessage(
          Message.newBuilder()
              .setPlayerConnectedMsg(
                  // TODO Why do we not send the transferable player as above? Actually it
                  //  does not matter since the DTO only contains transferrable properties.
                  PlayerConnectedMsg.newBuilder().setPlayer(player.toDto()))
              .build()
              .toByteArray());
    }

    // Send the campaign to the new client.
    connection.sendMessage(
        Message.newBuilder()
            .setSetCampaignMsg(SetCampaignMsg.newBuilder().setCampaign(campaign.toDto()))
            .build()
            .toByteArray());

    // Important that we do this here, otherwise the loop above will send a connection
    // messages to the new client for itself.
    playersByConnectionId.put(connection.getId(), connectedPlayer);
  }

  private final class AssetProducerThread {
    /*
     * TODO If I'm reading this right, we just keep associating asset producers with connections.
     *  Even once the asset has been fully sent, we don't remove it from the AssetTransferManager.
     *     Hmm, okay, it actually looks like the AssetTransferManager does that itself.
     */

    private static final int ASSET_CHUNK_SIZE = 5 * 1024;

    private final Thread thread;
    private final AtomicBoolean stop;
    // TODO Why synchronize a hashmap instead of using a concurrent hash map?
    private final Map<String, AssetTransferManager> assetManagerMap;

    public AssetProducerThread() {
      this.thread = new Thread(this::run, "AssetProducerThread");
      this.stop = new AtomicBoolean(false);
      this.assetManagerMap = Collections.synchronizedMap(new HashMap<>());

      this.thread.start();
    }

    public void shutdown() {
      stop.set(true);
    }

    public void addConnection(String connectionId) {
      assetManagerMap.computeIfAbsent(connectionId, c -> new AssetTransferManager());
    }

    public void removeConnection(String connectionId) {
      assetManagerMap.remove(connectionId);
    }

    public void addAsset(String connectionId, AssetProducer producer) {
      final var manager = assetManagerMap.get(connectionId);
      if (manager != null) {
        manager.addProducer(producer);
      }
    }

    private void run() {
      while (!stop.get()) {
        Map.Entry<String, AssetTransferManager> entryForException = null;
        try {
          boolean lookForMore = false;
          for (Map.Entry<String, AssetTransferManager> entry : assetManagerMap.entrySet()) {
            entryForException = entry;
            var chunk = entry.getValue().nextChunk(ASSET_CHUNK_SIZE);
            if (chunk != null) {
              lookForMore = true;
              var msg = UpdateAssetTransferMsg.newBuilder().setChunk(chunk);
              server.sendMessage(
                  entry.getKey(),
                  Message.newBuilder().setUpdateAssetTransferMsg(msg).build().toByteArray());
            }
          }
          if (lookForMore) {
            continue;
          }
          // Sleep for a bit
          synchronized (this) {
            /*
             * TODO What does this even accomplish? The above loop burns through pending assets, so
             *  we are just waiting for the sake of allowing more assets to show up. This means we
             *  also have a half-second delay before transmitting assets, which is unnecessary.
             *      What we should be doing is waiting on a condition variable, which is signalled
             *  any time an asset is added. This also implies we need proper synchronization to
             *  ensure we can't miss a signal (although we can also just wait with a max 500 delay).
             *      Also an edge case: shutdown should also be checked after being signalled.
             */
            Thread.sleep(500);
          }
        } catch (Exception e) {
          // TODO Gross. It's quite fragile relying on this entry being set. Why not catch-all
          //  inside the `for ()` loop.
          log.warn("Couldn't retrieve AssetChunk for " + entryForException.getKey(), e);
          // keep on going
        }
      }
    }
  }
}
