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

import com.google.common.eventbus.Subscribe;
import java.awt.EventQueue;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.maptool.client.events.PlayerConnected;
import net.rptools.maptool.client.events.PlayerDisconnected;
import net.rptools.maptool.client.events.ZoneSwitched;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.CampaignFactory;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.campaign.CampaignManager;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.PlayerDatabaseFactory;
import net.rptools.maptool.model.zones.TokensAdded;
import net.rptools.maptool.model.zones.TokensRemoved;
import net.rptools.maptool.model.zones.ZoneAdded;
import net.rptools.maptool.model.zones.ZoneRemoved;
import net.rptools.maptool.model.zones.ZoneVisibilityChanged;
import net.rptools.maptool.server.ClientHandshake;
import net.rptools.maptool.server.MapToolServer;
import net.rptools.maptool.server.ServerCommand;
import net.rptools.maptool.server.ServerPolicy;
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

  private final MapToolServer localServer;
  private final LocalPlayer player;
  private final PlayerDatabase playerDatabase;

  /** Case-insensitive ordered set of player names. */
  private final List<Player> playerList;

  private final MapToolConnection conn;
  private Campaign campaign;
  // null means no zone should be shown to the user.
  // Note: we don't just track the GUID because if the zone is directly removed from the campaign we
  // would no longer know about it for the sake of events and such.
  private @Nullable Zone currentZone;
  private ServerPolicy serverPolicy;
  private final ServerCommand serverCommand;
  private State currentState = State.New;

  private MapToolClient(
      @Nullable MapToolServer localServer,
      Campaign campaign,
      LocalPlayer player,
      Connection connection,
      ServerPolicy policy,
      PlayerDatabase playerDatabase) {
    this.localServer = localServer;
    this.campaign = campaign;
    this.player = player;
    this.playerDatabase = playerDatabase;
    this.playerList = new ArrayList<>();
    this.serverPolicy = new ServerPolicy(policy);

    this.conn =
        new MapToolConnection(
            connection, player, localServer == null ? new ClientHandshake(this, connection) : null);

    this.serverCommand = new ServerCommandClientImpl(this);

    this.conn.addDisconnectHandler(this::onDisconnect);
    this.conn.onCompleted(
        (success) -> {
          if (!success) {
            // Failed handshake. Disconnect from the server, but treat it as unexpected.
            this.conn.close();
            return;
          }

          if (transitionToState(State.Started, State.Connected)) {
            this.conn.addMessageHandler(new ClientMessageHandler(this));
          }
        });
  }

  /** Creates a client for a local server, whether personal or hosted. */
  public MapToolClient(
      MapToolServer localServer, Campaign campaign, LocalPlayer player, Connection connection) {
    this(
        localServer,
        campaign,
        player,
        connection,
        localServer.getPolicy(),
        localServer.getPlayerDatabase());
  }

  /**
   * Creates a client for use with a remote hosted server.
   *
   * @param player The player connecting to the server.
   */
  public MapToolClient(LocalPlayer player, Connection connection) {
    this(
        null,
        new Campaign(),
        player,
        connection,
        new ServerPolicy(),
        PlayerDatabaseFactory.getLocalPlayerDatabase(player));
  }

  /**
   * Transition from any state except {@code newState} to {@code newState}.
   *
   * @param newState The new state to set.
   * @return The previous state set. If not equal to {@code newState}, the transition was
   *     successful.
   */
  private State transitionToState(State newState) {
    var previousState = currentState;
    currentState = newState;

    if (previousState == newState) {
      log.warn(
          "Failed to transition to state {} because that is already the current state", newState);
    }

    return previousState;
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
    if (transitionToState(State.New, State.Started)) {
      new MapToolEventBus().getMainEventBus().register(this);

      try {
        conn.start();
      } catch (IOException e) {
        // Make sure we're in a reasonable state before propagating.
        log.error("Failed to start client", e);
        transitionToState(State.Closed);
        throw e;
      }
    }
  }

  public void close() {
    State previousState = transitionToState(State.Closed);
    if (previousState != State.Closed) {
      if (conn.isAlive()) {
        conn.close();
      }

      playerList.clear();

      if (previousState != State.New) {
        new MapToolEventBus().getMainEventBus().unregister(this);
      }
    }
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

  public MapToolConnection getConnection() {
    return conn;
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
    // First remove the old campaign + zones.
    for (var zone : campaign.getZones()) {
      new MapToolEventBus().getMainEventBus().post(new TokensRemoved(zone, zone.getAllTokens()));
      new MapToolEventBus().getMainEventBus().post(new ZoneRemoved(zone));
    }

    this.campaign = campaign;

    for (var zone : campaign.getZones()) {
      new MapToolEventBus().getMainEventBus().post(new ZoneAdded(zone));
      new MapToolEventBus().getMainEventBus().post(new TokensAdded(zone, zone.getAllTokens()));
    }

    var intiialZone = findFirstVisibleZone();
    setCurrentZoneId(intiialZone == null ? null : intiialZone.getId());
  }

  /**
   * Set the current zone.
   *
   * <p>Note: if {@code zoneId} does not exist in the current campaign, this call will set it to
   * {@code null} instead.
   *
   * @param zoneId The ID of the zone to make current, or {@code null} if no zone should be current.
   */
  public void setCurrentZoneId(@Nullable GUID zoneId) {
    // TODO Also subscribe to ZoneRemoved or some other event so we can clear automatically.

    var previousZone = currentZone;

    var newZone = zoneId == null ? null : campaign.getZone(zoneId);
    if (zoneId != null && newZone == null) {
      log.warn("Attempted to switch to zone {} but it's not in the campaign", zoneId);
    }
    if (newZone != null && !player.isGM() && !newZone.isVisible()) {
      log.warn("Attempted to switch to zone {} but it's not visible to this player", zoneId);
      newZone = null;
    }

    currentZone = newZone;
    new MapToolEventBus().getMainEventBus().post(new ZoneSwitched(previousZone, newZone));
  }

  /**
   * @return The current zone ID, or {@code null} if there is no current zone.
   */
  public @Nullable GUID getCurrentZoneId() {
    return currentZone == null ? null : currentZone.getId();
  }

  /**
   * @return The current zone, or {@code null} if there is no current zone.
   */
  public @Nullable Zone getCurrentZone() {
    return currentZone == null ? null : currentZone;
  }

  @Subscribe
  private void onZoneAdded(ZoneAdded event) {
    var visibleToPlayer = player.isGM() || event.zone().isVisible();
    if (currentZone == null && visibleToPlayer) {
      setCurrentZoneId(event.zone().getId());
    }
  }

  @Subscribe
  private void onZoneRemoved(ZoneRemoved event) {
    if (currentZone != null && currentZone.getId().equals(event.zone().getId())) {
      var switchTo = findFirstVisibleZone();
      setCurrentZoneId(switchTo == null ? null : switchTo.getId());
    }
  }

  @Subscribe
  private void onZoneVisibilityChanged(ZoneVisibilityChanged event) {
    var visibleToPlayer = player.isGM() || event.zone().isVisible();
    if (currentZone == null) {
      if (visibleToPlayer) {
        setCurrentZoneId(event.zone().getId());
      }
    } else if (currentZone.getId().equals(event.zone().getId())) {
      if (!visibleToPlayer) {
        // TODO Switch to another zone if available.
        setCurrentZoneId(null);
      }
    }
  }

  private @Nullable Zone findFirstVisibleZone() {
    for (var zone : campaign.getZones()) {
      if (player.isGM() || zone.isVisible()) {
        return zone;
      }
    }
    return null;
  }

  private void onDisconnect(Connection connection) {
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
    var disconnectExpected = currentState == State.Closed;

    if (!disconnectExpected) {
      // Keep any local server campaign around in the new personal server.
      final var newPersonalServerCampaign =
          localServer == null ? CampaignFactory.createBasicCampaign() : localServer.getCampaign();

      // Make sure the connection state is cleaned up since we can't count on it having been done.
      MapTool.disconnect();
      MapTool.stopServer();

      EventQueue.invokeLater(
          () -> {
            var errorText = I18N.getText("msg.error.server.disconnected");
            var connectionError = connection.getError();
            var errorMessage =
                errorText + (connectionError != null ? (": " + connectionError) : "");
            MapTool.showError(errorMessage);

            // hide map so player doesn't get a brief GM view
            setCurrentZoneId(null);
            MapTool.getFrame().getToolbarPanel().getMapselect().setVisible(true);
            MapTool.getFrame().getAssetPanel().enableAssets();
            new CampaignManager().clearCampaignData();
            MapTool.getFrame().getToolbarPanel().setTokenSelectionGroupEnabled(true);

            try {
              MapTool.startPersonalServer(newPersonalServerCampaign);
            } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
              MapTool.showError(I18N.getText("msg.error.server.cantrestart"), e);
            }
          });
    }
  }
}
