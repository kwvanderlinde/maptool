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

import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.MessageHandler;
import net.rptools.clientserver.simple.connection.Connection;

public class PersonalServerConnection implements Connection {
  private final String id;
  private boolean isAlive = true;

  public PersonalServerConnection(String id) {
    this.id = id;
  }

  @Override
  public void open() {}

  @Override
  public void close() {
    isAlive = false;
  }

  @Override
  public void sendMessage(Object channel, byte[] message) {}

  @Override
  public boolean isAlive() {
    return isAlive;
  }

  @Override
  public String getId() {
    return id;
  }

  // region There is no real server, so messages neither come nor go, and disconnection is
  //  impossible.

  @Override
  public void addMessageHandler(MessageHandler handler) {}

  @Override
  public void removeMessageHandler(MessageHandler handler) {}

  @Override
  public void addDisconnectHandler(DisconnectHandler handler) {}

  @Override
  public void removeDisconnectHandler(DisconnectHandler handler) {}

  @Override
  public void addActivityListener(ActivityListener listener) {}

  @Override
  public void removeActivityListener(ActivityListener listener) {}

  @Override
  public String getError() {
    return null;
  }

  // endregion
}
