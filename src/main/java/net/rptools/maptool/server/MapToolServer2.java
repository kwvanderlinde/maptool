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

import javax.annotation.Nonnull;
import net.rptools.clientserver.decomposed.ConnectionHandler;
import net.rptools.clientserver.decomposed.Server;
import net.rptools.clientserver.decomposed.socket.SocketConnectionHandler;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.player.PlayerDatabase;

public class MapToolServer2 {
  private final Server server;
  private final ConnectionHandler connectionHandler;
  private @Nonnull Campaign campaign;
  private @Nonnull ServerPolicy policy;

  // TODO I feel like we should be the ones deciding the player database... no?
  public MapToolServer2(ServerConfig config, ServerPolicy policy, PlayerDatabase playerDb) {
    // TODO Obviously this casting will not work. We will have to change ServerMessageHandler
    //  to accept a MapToolServer2. There's not much about it, mostly the message handler just
    //  needs to look up the server's campaign, and sometimes send messages and
    //  connections.
    // TODO In my new approach, handshakes are decided entirely here.
    this.server = new Server(new ServerMessageHandler((MapToolServer) (Object) this), null);
    // TODO Create via ConnectionFactory so that ServerConfig is accounted for. This might even
    //  be feasible to create at the call site and injected instead of a hard dependency here).
    this.connectionHandler = new SocketConnectionHandler();

    this.campaign = new Campaign();
    this.policy = policy;

    // TODO Hook up observers thusly:
    //  1. When a client connects, begin the handshake.
    //  2. When the handshake completes, install the connection to `this.server`. When done,
    //     send the handshake acknowledgement.
    //  3. Only report activity for connections with completed handshakes.
    //  4. When a message is received from a connection, send it to the message handler.

    // TODO Modify servers so they don't need a campaign of their own. Leave that entirely to
    //  clients.
    //  Oh... I can't actually do that. It's true that the server itself doesn't really need a
    //  campaign, however when a new client connections the server does need to be able to send
    //  the campaign over.
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
}
