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

import static net.rptools.maptool.model.player.PlayerDatabaseFactory.PlayerDatabaseType.LOCAL_PLAYER;
import static net.rptools.maptool.model.player.PlayerDatabaseFactory.PlayerDatabaseType.PERSONAL_SERVER;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.CampaignFactory;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.PlayerDatabaseFactory;
import net.rptools.maptool.server.ServerCommand;
import net.rptools.maptool.server.ServerConfig;
import net.rptools.maptool.server.ServerPolicy;

/**
 * The client side of a client-server channel.
 *
 * <p>This isn't the GUI per se, but something that can be swapped out as different servers are
 * connected to. It compiles a few things that used to be static state of {@link
 * net.rptools.maptool.client.MapTool} and elsewhere.
 */
public class MapToolClient {
  private final LocalPlayer player;
  private final PlayerDatabase playerDatabase;
  private final IMapToolConnection conn;
  private Campaign campaign;
  private ServerPolicy serverPolicy;
  private final ServerCommand serverCommand;
  private final DisconnectHandler disconnectHandler;

  /** Creates a client for a personal server. */
  public MapToolClient() {
    this.campaign = CampaignFactory.createBasicCampaign();

    try {
      PlayerDatabaseFactory.setCurrentPlayerDatabase(PERSONAL_SERVER);
      playerDatabase = PlayerDatabaseFactory.getCurrentPlayerDatabase();

      String username = AppPreferences.getDefaultUserName();
      player = (LocalPlayer) playerDatabase.getPlayer(username);

      serverPolicy = new ServerPolicy();

      this.disconnectHandler = conn -> {};

      conn = new NilMapToolConnection();
      conn.onCompleted(
          () -> {
            conn.addMessageHandler(new ClientMessageHandler(this));
          });
      this.serverCommand = new ServerCommandClientImpl(conn);

    } catch (Exception e) {
      throw new RuntimeException("Unable to start personal server", e);
    }
  }

  public MapToolClient(LocalPlayer player, ServerConfig config) throws IOException {
    this.campaign = CampaignFactory.createBasicCampaign();

    this.player = player;
    this.serverPolicy = new ServerPolicy();

    PlayerDatabaseFactory.setCurrentPlayerDatabase(LOCAL_PLAYER);
    playerDatabase = PlayerDatabaseFactory.getCurrentPlayerDatabase();

    this.disconnectHandler = new ServerDisconnectHandler();

    conn = new MapToolConnection(config, player);
    conn.addDisconnectHandler(disconnectHandler);
    this.serverCommand = new ServerCommandClientImpl(conn);
    conn.onCompleted(
        () -> {
          conn.addMessageHandler(new ClientMessageHandler(this));
        });
  }

  public void start() throws IOException, ExecutionException, InterruptedException {
    conn.start();
  }

  public void close() throws IOException {
    // TODO WHy not just .close()? Surely if it's not alive that would be a no-op.
    if (conn.isAlive()) {
      conn.close();
    }
  }

  public void expectDisconnection() {
    if (disconnectHandler instanceof ServerDisconnectHandler serverDisconnectHandler) {
      serverDisconnectHandler.disconnectExpected = true;
    }
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

  public IMapToolConnection getConnection() {
    return conn;
  }

  public ServerPolicy getServerPolicy() {
    return serverPolicy;
  }

  public Campaign getCampaign() {
    return this.campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  public void setServerPolicy(ServerPolicy serverPolicy, boolean sendToServer) {
    this.serverPolicy = serverPolicy;
    if (sendToServer) {
      this.serverCommand.setServerPolicy(serverPolicy);
    }
  }
}
