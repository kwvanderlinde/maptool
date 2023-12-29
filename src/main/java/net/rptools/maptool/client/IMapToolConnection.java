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
import java.util.concurrent.ExecutionException;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.maptool.client.ui.ActivityMonitorPanel;
import net.rptools.maptool.server.proto.Message;

public interface IMapToolConnection {
  void onCompleted(Runnable onCompleted);

  void start() throws IOException, ExecutionException, InterruptedException;

  void addMessageHandler(ClientMessageHandler handler);

  boolean isAlive();

  void close() throws IOException;

  void sendMessage(Message msg);

  // TODO These are very specific, and don't really have anything to do with MT connections per se.
  //  The callers just care about observing the underlying connection.

  void addActivityListener(ActivityMonitorPanel activityMonitor);

  void addDisconnectHandler(DisconnectHandler serverDisconnectHandler);
}
