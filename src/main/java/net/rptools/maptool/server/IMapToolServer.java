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
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.transfer.AssetProducer;

/**
 * This is a temporary interface to support the migration from MapToolServer to MapToolServer2.
 * Remove it once MapToolServer2 is renamed to MapToolServer.
 */
// TODO I think working with connection IDs is a mistake. Instead, player names makes sense, or else
//  `Connection` objects.
public interface IMapToolServer {
  Campaign getCampaign();

  String getConnectionId(String playerId);

  void bootPlayer(String playerName);

  void addAssetProducer(String connectionId, AssetProducer producer);

  boolean isPlayerConnected(String id);

  void updatePlayerStatus(String playerName, GUID zoneId, boolean loaded);

  void setCampaign(Campaign campaign);

  ServerPolicy getPolicy();

  void updateServerPolicy(ServerPolicy policy);

  ServerConfig getConfig();

  void stop();

  void start() throws IOException;

  // TODO Exclude by player ID.
  void broadcastMessage(String[] exclude, Message message);

  // TODO By player ID.
  void sendMessage(String id, Message message);
}
