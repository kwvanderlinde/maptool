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
package net.rptools.clientserver.simple.server;

import java.io.IOException;
import net.rptools.clientserver.simple.channels.Channel;

/**
 * Waits for connections to come in, returning them to the caller.
 *
 * <p>Analogous to how a {@link java.net.ServerSocket} waits for {@link java.net.Socket}, a {@code
 * ConnectionReceiver} produces {@link net.rptools.clientserver.simple.connection.Connection}. The
 * only substantial difference is that {@code ConnectionReceiver} yields connections via a callback
 * rather than blocking.
 *
 * <p>In order to keep the receiver responsive, listeners should handle the connection on a separate
 * thread.
 */
public interface ChannelReceiver {
  void start() throws IOException;

  void close();

  void addListener(Observer observer);

  void removeListener(Observer observer);

  interface Observer {
    void onConnected(String id, Channel channel);

    void onError(Exception error);
  }
}
