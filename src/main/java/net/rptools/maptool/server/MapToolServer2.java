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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import net.rptools.clientserver.decomposed.Connection;
import net.rptools.clientserver.decomposed.ConnectionHandler;
import net.rptools.clientserver.decomposed.Server;
import net.rptools.clientserver.decomposed.socket.SocketConnectionHandler;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.PlayerConnectedMsg;
import net.rptools.maptool.server.proto.SetCampaignMsg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class MapToolServer2 {
  private static final Logger log = LogManager.getLogger(MapToolServer2.class);

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          new ThreadFactoryBuilder().setNameFormat("server-thread-%d").build());

  private @Nonnull Campaign campaign;
  private @Nonnull ServerConfig config;
  private @Nonnull ServerPolicy policy;
  private @Nonnull PlayerDatabase playerDatabase;

  private final Server server;
  // TODO Enforce invariants that playersByConnectionId only has keys present in the server. But how
  //  can we enforce that?
  private final Map<String, Player> playersByConnectionId = new HashMap<>();
  private final ConnectionHandler connectionHandler;
  private final Handshake2.Observer handshakeObserver;

  // TODO I feel like we should be the ones deciding the player database... no?

  public MapToolServer2(ServerConfig config, ServerPolicy policy, PlayerDatabase playerDb) {
    this.campaign = new Campaign();
    this.config = config;
    this.policy = policy;
    this.playerDatabase = playerDb;
    this.handshakeObserver = new HandshakeObserver();

    // TODO Obviously this casting will not work. We will have to change ServerMessageHandler
    //  to accept a MapToolServer2. There's not much about it, mostly the message handler just
    //  needs to look up the server's campaign, and sometimes send messages and
    //  connections.
    // TODO In my new approach, handshakes are decided entirely here.
    this.server = new Server(new ServerMessageHandler(null /* Should be `this` */));
    // TODO Create via ConnectionFactory so that ServerConfig is accounted for. This might even
    //  be feasible to create at the call site and injected instead of a hard dependency here).
    this.connectionHandler = new SocketConnectionHandler();

    this.connectionHandler.addListener(new ConnectionHandlerListener());
  }

  public @Nonnull Campaign getCampaign() {
    return this.campaign;
  }

  public void setCampaign(@Nonnull Campaign campaign) {
    this.campaign = campaign;
  }

  public @Nonnull ServerPolicy getPolicy() {
    return policy;
  }

  public void updateServerPolicy(@Nonnull ServerPolicy policy) {
    this.policy = policy;
  }

  private final class HandshakeObserver implements Handshake2.Observer {
    @Override
    public void onCompleted(Handshake2 handshake) {
      handshake.removeObserver(this);
      final var connection = handshake.getConnection();

      // TODO AbstractServer then released the handshake here. Unimportant since we do not
      //  maintain a set of active handshakes, but if we did we should clear it out here.

      if (!handshake.isSuccessful()) {
        log.error(
            "Handshake for connection {} failed with message: {}",
            connection.getId(),
            handshake.getErrorMessage(),
            handshake.getException());
        connection.close();
        return;
      }

      final var connectionAdded = server.addConnection(connection);
      if (!connectionAdded) {
        log.error("Another connection with ID {} already exists", connection.getId());
        connection.close();
        return;
      }

      // TODO AbstractServer.onCompleted() took the opportunity to reapClients() here. Let's
      //  add that to the decomposed server as well, through the message pump though.

      // TODO MapToolServer.configureClientConnection() would have added it to assetManagerMap

      final Player connectedPlayer = ((ServerHandshake2) handshake).getPlayer();

      // Send the connected player to each client.
      // Original in MapToolServer connection does this in the reverse order, but I like this.
      server.broadcast(
          null,
          Message.newBuilder()
              .setPlayerConnectedMsg(
                  PlayerConnectedMsg.newBuilder()
                      .setPlayer(connectedPlayer.getTransferablePlayer().toDto()))
              .build()
              .toByteArray());
      for (Player player : playersByConnectionId.values()) {
        connection.sendMessage(
            null,
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
          null,
          Message.newBuilder()
              .setSetCampaignMsg(SetCampaignMsg.newBuilder().setCampaign(campaign.toDto()))
              .build()
              .toByteArray());

      // Important that we do this here, otherwise the loop above will send a connection
      // messages to the new client for itself.
      playersByConnectionId.put(connection.getId(), connectedPlayer);
    }
  }

  private final class ConnectionHandlerListener implements ConnectionHandler.Listener {
    @Override
    public void onConnected(@NotNull Connection connection) {
      executor.execute(
          () -> {
            /*
             * TODO I feel like I ought to maintain a set of current handshakes, to prevent
             *  two from running for the same client. But that comes with two downsides:
             *  1. Both could be legit, so why prefer the one that got in a few ms earlier?
             *  2. it's extra state to juggle, so we need a really good reason for it.
             */

            // Start the handshake.
            final var handshake =
                new ServerHandshake2(connection, playerDatabase, config.getUseEasyConnect());
            handshake.addObserver(handshakeObserver);
            handshake.startHandshake();

            connection.start();
          });
    }

    @Override
    public void onConnectionClosed(@NotNull String connectionId) {
      // Cancel any pending handshake and remove from the server.
    }

    @Override
    public void onConnectionLost(@NotNull String connectionId, @NotNull String reason) {
      // Cancel any pending handshake and remove from the server. Possibly close it again?
    }
  }
}
