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
package net.rptools.clientserver.simple.connection;

import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractConnection implements Connection {
  private static final Logger log = LogManager.getLogger(AbstractConnection.class);

  private final List<DisconnectHandler> disconnectHandlers = new CopyOnWriteArrayList<>();
  private final List<ActivityListener> listeners = new CopyOnWriteArrayList<>();
  private final List<MessageHandler> messageHandlers = new CopyOnWriteArrayList<>();

  public final void addMessageHandler(MessageHandler handler) {
    messageHandlers.add(handler);
  }

  public final void removeMessageHandler(MessageHandler handler) {
    messageHandlers.remove(handler);
  }

  protected void dispatchMessage(String id, byte[] message) {
    if (messageHandlers.isEmpty()) {
      log.warn("message received but not messageHandlers registered.");
    }

    for (MessageHandler handler : messageHandlers) {
      handler.handleMessage(id, message);
    }
  }

  public final void addDisconnectHandler(DisconnectHandler handler) {
    disconnectHandlers.add(handler);
  }

  public final void removeDisconnectHandler(DisconnectHandler handler) {
    disconnectHandlers.remove(handler);
  }

  public final void addActivityListener(ActivityListener listener) {
    listeners.add(listener);
  }

  public final void removeActivityListener(ActivityListener listener) {
    listeners.remove(listener);
  }
}
