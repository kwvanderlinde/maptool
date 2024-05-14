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
package net.rptools.maptool.client;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.maptool.client.events.PlayerConnected;
import net.rptools.maptool.client.events.PlayerDisconnected;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.CampaignFactory;
import net.rptools.maptool.model.campaign.CampaignManager;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.PlayerDatabaseFactory;
import net.rptools.maptool.model.player.ServerSidePlayerDatabase;
import net.rptools.maptool.server.ClientHandshake;
import net.rptools.maptool.server.ServerCommand;
import net.rptools.maptool.server.ServerPolicy;
import net.rptools.maptool.server.proto.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The client side of a client-server channel.
 *
 * <p>This has nothing to do with the GUI, but represents those parts of the client that are needed
 * to interact with a server. Most of this used to exist as global state in {@link
 * net.rptools.maptool.client.MapTool} and elsewhere.
 */
public class MapToolClient {
  private static final Logger log = LogManager.getLogger(MapToolClient.class);

  public enum State {
    New,
    Started,
    Connected,
    Closed
  }

  private final LocalPlayer player;
  private final PlayerDatabase playerDatabase;

  /** Case-insensitive ordered set of player names. */
  private final List<Player> playerList;

  private final Connection connection;
  private final Handshake<Void> handshake;
  private final List<Consumer<Boolean>> onCompleted = new CopyOnWriteArrayList<>();
  private Campaign campaign;
  private ServerPolicy serverPolicy;
  private final ServerCommand serverCommand;
  private boolean disconnectExpected = false;
  private State currentState = State.New;

  private MapToolClient(
      Campaign campaign,
      boolean isForLocalServer,
      LocalPlayer player,
      PlayerDatabase playerDatabase,
      ServerPolicy serverPolicy,
      Connection connection) {
    this.campaign = campaign;
    this.player = player;
    this.playerDatabase = playerDatabase;
    this.playerList = new ArrayList<>();
    this.serverPolicy = new ServerPolicy(serverPolicy);
    this.serverCommand = new ServerCommandClientImpl(this);

    this.connection = connection;
    this.connection.addDisconnectHandler(conn -> onDisconnect(isForLocalServer, conn));

    // Only remote connections require a handshake.
    this.handshake = isForLocalServer ? null : new ClientHandshake(this, connection);
  }

  /**
   * Creates a client for use with a personal server or locally hosted.
   *
   * <p>This client is immediately connected to the server once started.
   *
   * @param connection The connection to the local server
   */
  public MapToolClient(
      Campaign campaign,
      LocalPlayer player,
      Connection connection,
      ServerPolicy policy,
      ServerSidePlayerDatabase playerDatabase) {
    this(campaign, true, player, playerDatabase, policy, connection);
  }

  /**
   * Creates a client for use with a remote hosted server.
   *
   * @param player The player connecting to the server.
   */
  public MapToolClient(Campaign campaign, LocalPlayer player, Connection connection) {
    this(
        campaign,
        false,
        player,
        PlayerDatabaseFactory.getLocalPlayerDatabase(player),
        new ServerPolicy(),
        connection);
  }

  public void onCompleted(Consumer<Boolean> onCompleted) {
    this.onCompleted.add(onCompleted);
  }

  /**
   * Transition from any state except {@code newState} to {@code newState}.
   *
   * @param newState The new state to set.
   */
  private boolean transitionToState(State newState) {
    if (currentState == newState) {
      log.warn(
          "Failed to transition to state {} because that is already the current state", newState);
      return false;
    } else {
      currentState = newState;
      return true;
    }
  }

  /**
   * Transition from {@code expectedState} to {@code newState}.
   *
   * @param expectedState The state to transition from
   * @param newState The new state to set.
   */
  private boolean transitionToState(State expectedState, State newState) {
    if (currentState != expectedState) {
      log.warn(
          "Failed to transition from state {} to state {} because the current state is actually {}",
          expectedState,
          newState,
          currentState);
      return false;
    } else {
      currentState = newState;
      return true;
    }
  }

  public State getState() {
    return currentState;
  }

  public void start() throws IOException {
    if (!transitionToState(State.New, State.Started)) {
      return;
    }

    connection.open();

    if (handshake == null) {
      // No handshake required. Jump straight to being connected.
      // We actually expect this case to have no callbacks, but let's be solid.
      for (final var callback : onCompleted) {
        callback.accept(true);
      }
      if (transitionToState(State.Started, State.Connected)) {
        this.connection.addMessageHandler(new ClientMessageHandler(this));
      }
    } else {
      handshake.whenComplete(
          (result, exception) -> {
            if (exception != null) {
              // For client side only show the error message as its more likely to make sense
              // for players, the exception is logged just in case more info is required
              log.warn("Handshake failed", exception);
              MapTool.showError(exception.getMessage());
              connection.close();
              for (final var callback : onCompleted) {
                callback.accept(false);
              }
              AppActions.disconnectFromServer();
              this.close();
            } else {
              for (final var callback : onCompleted) {
                callback.accept(true);
              }
              if (transitionToState(State.Started, State.Connected)) {
                this.connection.addMessageHandler(new ClientMessageHandler(this));
              }
            }
          });
      handshake.startHandshake();
    }
  }

  public void close() {
    if (transitionToState(State.Closed)) {
      if (connection.isAlive()) {
        connection.close();
      }
      playerList.clear();
    }
  }

  public void addActivityListener(ActivityListener listener) {
    this.connection.addActivityListener(listener);
  }

  public void expectDisconnection() {
    disconnectExpected = true;
  }

  public ServerCommand getServerCommand() {
    return serverCommand;
  }

  public LocalPlayer getPlayer() {
    return player;
  }

  public List<Player> getPlayerList() {
    return Collections.unmodifiableList(playerList);
  }

  public void addPlayer(Player player) {
    if (!playerList.contains(player)) {
      playerList.add(player);
      new MapToolEventBus().getMainEventBus().post(new PlayerConnected(player));
      playerDatabase.playerSignedIn(player);

      playerList.sort((arg0, arg1) -> arg0.getName().compareToIgnoreCase(arg1.getName()));
    }
  }

  public void removePlayer(Player player) {
    playerList.remove(player);
    new MapToolEventBus().getMainEventBus().post(new PlayerDisconnected(player));
    playerDatabase.playerSignedOut(player);
  }

  public boolean isPlayerConnected(String playerName) {
    return playerList.stream().anyMatch(p -> p.getName().equalsIgnoreCase(playerName));
  }

  public PlayerDatabase getPlayerDatabase() {
    return playerDatabase;
  }

  public void sendMessage(Message msg) {
    log.debug("{} sent {}", player.getName(), msg.getMessageTypeCase());
    connection.sendMessage(msg.toByteArray());
  }

  /**
   * @return A copy of the client's server policy.
   */
  public ServerPolicy getServerPolicy() {
    return new ServerPolicy(serverPolicy);
  }

  /**
   * Sets the client's server policy.
   *
   * <p>If this also needs to be updated remotely, call {@link
   * net.rptools.maptool.server.ServerCommand#setServerPolicy(net.rptools.maptool.server.ServerPolicy)}
   * as well.
   *
   * @param serverPolicy The new policy to set.
   */
  public void setServerPolicy(ServerPolicy serverPolicy) {
    this.serverPolicy = new ServerPolicy(serverPolicy);
  }

  public Campaign getCampaign() {
    return this.campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  private void onDisconnect(boolean isLocalServer, Connection connection) {
    /*
     * Three main cases:
     * 1. Expected disconnect. This will be part of a broader shutdown sequence and we don't need to
     *    do anything to clean up client or server state.
     * 2. Unexpected disconnect for remote server. Common case due to remote server shutdown or
     *    other lost connection. We need to clean up the connection, show an error to the user, and
     *    start a new personal server with a blank campaign.
     * 3. Unexpected disconnect for local server. A rare case where we lost connection without
     *    shutting down the server. We need to clean up the connection, stop the server, show an
     *    error to the user, and start a new personal server with the current campaign.
     */

    if (!disconnectExpected) {
      // Make sure the connection state is cleaned up since we can't count on it having been done.
      MapTool.disconnect();
      if (isLocalServer) {
        MapTool.stopServer();
      }

      var errorText = I18N.getText("msg.error.server.disconnected");
      var connectionError = connection.getError();
      var errorMessage = errorText + (connectionError != null ? (": " + connectionError) : "");
      MapTool.showError(errorMessage);

      // hide map so player doesn't get a brief GM view
      MapTool.getFrame().setCurrentZoneRenderer(null);
      MapTool.getFrame().getToolbarPanel().getMapselect().setVisible(true);
      MapTool.getFrame().getAssetPanel().enableAssets();
      new CampaignManager().clearCampaignData();
      MapTool.getFrame().getToolbarPanel().setTokenSelectionGroupEnabled(true);

      // Keep any local campaign around in the new personal server.
      final var campaign = isLocalServer ? getCampaign() : CampaignFactory.createBasicCampaign();
      try {
        MapTool.startPersonalServer(campaign);
      } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
        MapTool.showError(I18N.getText("msg.error.server.cantrestart"), e);
      }
    } else if (!isLocalServer) {
      // expected disconnect from someone else's server
      // hide map so player doesn't get a brief GM view
      MapTool.getFrame().setCurrentZoneRenderer(null);
    }
  }
}
