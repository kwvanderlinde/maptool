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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.clientserver.simple.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.MessageHandler;

public interface Connection extends AutoCloseable {
  record Streams(InputStream input, OutputStream output) {
    public Streams() {
      this(InputStream.nullInputStream(), OutputStream.nullOutputStream());
    }

    public void flush() throws IOException {
      // TODO Tells the underlying connection to send its pending data.
    }
  }

  Streams open() throws IOException;

  void close();

  default void sendMessage(byte[] message) {
    sendMessage(null, message);
  }

  void sendMessage(Object channel, byte[] message);

  boolean isAlive();

  String getId();

  void addMessageHandler(MessageHandler handler);

  void removeMessageHandler(MessageHandler handler);

  void addDisconnectHandler(DisconnectHandler handler);

  void removeDisconnectHandler(DisconnectHandler handler);

  void addActivityListener(ActivityListener listener);

  void removeActivityListener(ActivityListener listener);

  String getError();
}
