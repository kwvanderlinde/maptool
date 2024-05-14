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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.maptool.client.ui.ActivityMonitorPanel;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.server.ClientHandshake;
import net.rptools.maptool.server.proto.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author trevor
 */
public class MapToolConnection {

  /** Instance used for log messages. */
  private static final Logger log = LogManager.getLogger(MapToolConnection.class);

  private final LocalPlayer player;
  private final Connection connection;
  private final Handshake<Void> handshake;
  private final List<Runnable> onCompleted;

  public MapToolConnection(MapToolClient client, LocalPlayer player, Connection connection) {
    this.connection = connection;
    this.player = player;
    this.handshake = new ClientHandshake(client, connection);
    this.onCompleted = new CopyOnWriteArrayList<>();
  }

  public void onCompleted(Runnable onCompleted) {
    this.onCompleted.add(onCompleted);
  }

  public void start() throws IOException {
    handshake.whenComplete(
        (result, exception) -> {
          if (exception != null) {
            // For client side only show the error message as its more likely to make sense
            // for players, the exception is logged just in case more info is required
            log.warn(exception);
            MapTool.showError(exception.getMessage());
            connection.close();
            for (final var callback : onCompleted) {
              callback.run();
            }
            AppActions.disconnectFromServer();
          } else {
            for (final var callback : onCompleted) {
              callback.run();
            }
          }
        });

    // this triggers the handshake from the server side
    connection.open();
    handshake.startHandshake();
  }

  public void addMessageHandler(ClientMessageHandler handler) {
    connection.addMessageHandler(handler);
  }

  public void addActivityListener(ActivityMonitorPanel activityMonitor) {
    connection.addActivityListener(activityMonitor);
  }

  public void addDisconnectHandler(DisconnectHandler serverDisconnectHandler) {
    connection.addDisconnectHandler(serverDisconnectHandler);
  }

  public boolean isAlive() {
    return connection.isAlive();
  }

  public void close() throws IOException {
    connection.close();
  }

  public void sendMessage(Message msg) {
    log.debug("{} sent {}", player.getName(), msg.getMessageTypeCase());
    connection.sendMessage(msg.toByteArray());
  }
}
