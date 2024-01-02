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

import com.google.common.eventbus.EventBus;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.ConnectionFactory;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.maptool.client.events.CampaignChanged;
import net.rptools.maptool.client.events.PlayerConnected;
import net.rptools.maptool.client.events.PlayerDisconnected;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.CampaignFactory;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.player.EmptyPlayerDatabase;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.LocalPlayerDatabase;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.Players;
import net.rptools.maptool.server.ClientHandshake;
import net.rptools.maptool.server.MapToolServer;
import net.rptools.maptool.server.PersonalServer;
import net.rptools.maptool.server.ServerCommand;
import net.rptools.maptool.server.ServerConfig;
import net.rptools.maptool.server.ServerPolicy;
import net.rptools.maptool.util.MessageUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The client side of a client-server channel.
 *
 * <p>This isn't the GUI per se, but something that can be swapped out as different servers are
 * connected to. It compiles a few things that used to be static state of {@link
 * net.rptools.maptool.client.MapTool} and elsewhere.
 */
public class MapToolClient {
  private static final Logger log = LogManager.getLogger(MapToolClient.class);

  private final List<Runnable> onConnectionCompleted = new ArrayList<>();
  private final EventBus eventBus;
  private final LocalPlayer player;
  private PlayerDatabase playerDatabase;
  private final Connection connection;
  private Campaign campaign;
  private ServerPolicy serverPolicy;
  private final ServerCommand serverCommand;
  private final DisconnectHandler disconnectHandler;
  private boolean closed = false;

  private MapToolClient(
      LocalPlayer player,
      PlayerDatabase playerDatabase,
      ServerPolicy serverPolicy,
      @Nullable ServerConfig serverConfig) {
    this.eventBus = new MapToolEventBus().getMainEventBus();
    this.campaign = CampaignFactory.createBasicCampaign();
    this.player = player;
    this.playerDatabase = playerDatabase;
    this.serverPolicy = serverPolicy;

    this.connection =
        serverConfig == null
            ? new PersonalServerConnection(player.getName())
            : ConnectionFactory.getInstance().createConnection(player.getName(), serverConfig);

    this.serverCommand = new ServerCommandClientImpl(connection);

    // TODO Should we use a dummy disconnect handler for personal servers?
    this.disconnectHandler = new ServerDisconnectHandler(this);
    this.connection.addDisconnectHandler(disconnectHandler);
    this.onConnectionCompleted.add(
        () -> {
          this.connection.addMessageHandler(new ClientMessageHandler(this));
        });
  }

  /** Creates a client for a personal server. */
  public MapToolClient(PersonalServer server) {
    this(server.getLocalPlayer(), server.getPlayerDatabase(), new ServerPolicy(), null);
  }

  public MapToolClient(LocalPlayer player, ServerConfig config) {
    this(player, new LocalPlayerDatabase(player), new ServerPolicy(), config);
  }

  public MapToolClient(LocalPlayer player, MapToolServer server) {
    this(player, server.getPlayerDatabase(), server.getPolicy(), server.getConfig());
  }

  public void onConnectionCompleted(Runnable callback) {
    this.onConnectionCompleted.add(callback);
  }

  public void addActivityListener(ActivityListener listener) {
    connection.addActivityListener(listener);
  }

  public void start() throws IOException, ExecutionException, InterruptedException {
    final var handshake = new ClientHandshake(this.connection, player);

    connection.addMessageHandler(handshake);
    handshake.addObserver(
        (ignore) -> {
          connection.removeMessageHandler(handshake);
          if (handshake.isSuccessful()) {
            for (final var callback : onConnectionCompleted) {
              callback.run();
            }
          } else {
            // For client side only show the error message as its more likely to make sense
            // for players, the exception is logged just in case more info is required
            var exception = handshake.getException();
            if (exception != null) {
              log.warn(exception);
            }
            MapTool.showError(handshake.getErrorMessage());
            connection.close();
            for (final var callback : onConnectionCompleted) {
              callback.run();
            }
            AppActions.disconnectFromServer();
          }
        });
    // this triggers the handshake from the server side
    connection.open();
    handshake.startHandshake();
  }

  public void close() throws IOException {
    closed = true;
    playerDatabase = new EmptyPlayerDatabase();

    // TODO WHy not just .close()? Surely if it's not alive that would be a no-op.
    if (connection.isAlive()) {
      connection.close();
    }
  }

  public boolean isClosed() {
    return closed;
  }

  public ServerCommand getServerCommand() {
    return serverCommand;
  }

  public LocalPlayer getPlayer() {
    return player;
  }

  public PlayerDatabase getPlayerDatabase() {
    return playerDatabase;
  }

  public ServerPolicy getServerPolicy() {
    return serverPolicy;
  }

  public Campaign getCampaign() {
    return this.campaign;
  }

  public void setCampaign(Campaign campaign) {
    setCampaign(campaign, null);
  }

  public void setCampaign(Campaign campaign, GUID defaultZoneId) {
    this.campaign = Objects.requireNonNullElseGet(campaign, Campaign::new);
    eventBus.post(new CampaignChanged(this, this.campaign, defaultZoneId));
  }

  public void setServerPolicy(ServerPolicy serverPolicy) {
    this.serverPolicy = serverPolicy;
  }

  public void addPlayer(Player player) {
    if (playerDatabase.isPlayerConnected(player.getName())) {
      // Already know about them.
      return;
    }

    new MapToolEventBus().getMainEventBus().post(new PlayerConnected(player));
    new Players().playerSignedIn(player);

    if (!player.getName().equalsIgnoreCase(this.player.getName())) {
      String msg = MessageFormat.format(I18N.getText("msg.info.playerConnected"), player.getName());
      MapTool.addLocalMessage(MessageUtil.getFormattedSystemMsg(msg));
    }
  }

  public void removePlayer(Player player) {
    new MapToolEventBus().getMainEventBus().post(new PlayerDisconnected(player));
    new Players().playerSignedOut(player);

    if (MapTool.getPlayer() != null && !player.equals(MapTool.getPlayer())) {
      String msg =
          MessageFormat.format(I18N.getText("msg.info.playerDisconnected"), player.getName());
      MapTool.addLocalMessage(MessageUtil.getFormattedSystemMsg(msg));
    }
  }

  /**
   * Checks if a specific player is connected.
   *
   * @param name The case-insensitive name of the player to check.
   * @return {@code true} if the player is connected otherwise {@code false}.
   */
  public boolean isPlayerConnected(String name) {
    return playerDatabase.isPlayerConnected(name);
  }

  public Collection<Player> getPlayers() {
    return playerDatabase.getOnlinePlayers();
  }

  public Collection<String> getNonGmNames() {
    return getPlayers().stream().filter(player -> !player.isGM()).map(Player::getName).toList();
  }
}
