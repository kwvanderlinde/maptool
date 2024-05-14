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
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.transfer.AssetProducer;

public interface IMapToolServer {
  MapToolServerConnection getConnection();

  Handshake<Player> createHandshake(Connection conn);

  void addAssetProducer(String connectionId, AssetProducer producer);

  Campaign getCampaign();

  void setCampaign(Campaign campaign);

  boolean isPersonalServer();

  boolean isServerRegistered();

  String getName();

  int getPort();

  ServerPolicy getPolicy();

  void updateServerPolicy(ServerPolicy policy);

  boolean isPlayerConnected(String id);

  void updatePlayerStatus(String playerName, GUID zoneId, boolean loaded);

  void start() throws IOException;

  void stop();

  Connection getClientConnection(String playerName);

  void configureClientConnection(Connection connection);

  /** Forceably disconnects a client and cleans up references to it */
  void releaseClientConnection(Connection connection);
}
