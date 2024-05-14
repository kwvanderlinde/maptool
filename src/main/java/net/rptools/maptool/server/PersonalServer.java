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
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.clientserver.simple.connection.DirectConnection;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.transfer.AssetProducer;

public class PersonalServer implements IMapToolServer {
  private final LocalPlayer player;
  private ServerPolicy serverPolicy;
  private Campaign campaign;

  private final DirectConnection serverSide;
  private final DirectConnection clientSide;
  private final MapToolServerConnection conn;

  public PersonalServer(LocalPlayer player, ServerPolicy serverPolicy, Campaign campaign) {
    this.player = player;
    this.serverPolicy = serverPolicy;
    this.campaign = campaign;

    var connections = DirectConnection.create("Personal Server");
    this.serverSide = connections.getRight();
    this.clientSide = connections.getLeft();

    conn = new MapToolServerConnection(this, new ServerMessageHandler(this));
  }

  public DirectConnection getClientSideConnection() {
    return clientSide;
  }

  public DirectConnection getServerSideConnection() {
    return serverSide;
  }

  @Override
  public Handshake<Player> createHandshake(Connection conn) {
    return new PersonalServerHandshake(this, conn, player);
  }

  @Override
  public void addAssetProducer(String connectionId, AssetProducer producer) {
    // Should never be needed.
  }

  @Override
  public MapToolServerConnection getConnection() {
    return conn;
  }

  @Override
  public Campaign getCampaign() {
    return campaign;
  }

  @Override
  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @Override
  public boolean isPersonalServer() {
    return true;
  }

  @Override
  public boolean isServerRegistered() {
    return false;
  }

  @Override
  public String getName() {
    return "";
  }

  @Override
  public int getPort() {
    return -1;
  }

  @Override
  public ServerPolicy getPolicy() {
    return serverPolicy;
  }

  @Override
  public void updateServerPolicy(ServerPolicy policy) {
    this.serverPolicy = new ServerPolicy(policy);
  }

  @Override
  public boolean isPlayerConnected(String id) {
    return false;
  }

  @Override
  public void updatePlayerStatus(String playerName, GUID zoneId, boolean loaded) {
    // We only have our local player, whose status is constant. So do nothing.
  }

  @Override
  public void start() throws IOException {
    conn.open();
  }

  @Override
  public void stop() {
    conn.close();
  }

  // We don't accept connections, so configure/release do nothing.

  @Override
  public Connection getClientConnection(String playerName) {
    return null;
  }

  @Override
  public void configureClientConnection(Connection connection) {}

  @Override
  public void releaseClientConnection(Connection connection) {}
}
