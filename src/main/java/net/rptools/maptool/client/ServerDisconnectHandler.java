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
import java.util.concurrent.ExecutionException;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.maptool.client.ui.ConnectionStatusPanel;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.CampaignFactory;
import net.rptools.maptool.model.campaign.CampaignManager;
import net.rptools.maptool.util.MessageUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** This class handles when the server inexplicably disconnects */
public class ServerDisconnectHandler implements DisconnectHandler {
  private static final Logger log = LogManager.getLogger(ServerDisconnectHandler.class);

  private final MapToolConnection client;

  public ServerDisconnectHandler(MapToolConnection client) {
    this.client = client;
  }

  public void handleDisconnect(Connection connection) {
    MapTool.getFrame()
        .getConnectionStatusPanel()
        .setStatus(ConnectionStatusPanel.Status.disconnected);

    // TODO: attempt to reconnect if this was unexpected
    if (!client.isClosed()) {
      // Unexpected disconnection.
      try {
        client.close();
      } catch (IOException ioe) {
        // This isn't critical, we're closing it anyway
        log.error("While closing connection", ioe);
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
      try {
        MapTool.startPersonalServer(CampaignFactory.createBasicCampaign());
      } catch (IOException
          | NoSuchAlgorithmException
          | InvalidKeySpecException
          | ExecutionException
          | InterruptedException e) {
        MapTool.showError(I18N.getText("msg.error.server.cantrestart"), e);
      }
    } else if (!MapTool.isPersonalServer()) {
      MapTool.addLocalMessage(
          MessageUtil.getFormattedSystemMsg(I18N.getText("msg.info.disconnected")));
      if (!MapTool.isHostingServer()) {
        // Disconnected from someone else's server. Hide map so player doesn't get a brief GM view
        MapTool.getFrame().setCurrentZoneRenderer(null);
      }
    }
  }
}
