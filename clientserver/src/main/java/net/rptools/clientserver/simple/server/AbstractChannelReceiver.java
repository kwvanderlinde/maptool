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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.rptools.clientserver.simple.channels.Channel;

/** Provides listener operations common to all connection receivers. */
public abstract class AbstractChannelReceiver implements ChannelReceiver {
  private final List<Observer> observers = new CopyOnWriteArrayList<>();

  @Override
  public void addListener(Observer observer) {
    observers.add(observer);
  }

  @Override
  public void removeListener(Observer observer) {
    observers.removeIf(l -> l == observer);
  }

  protected void onConnected(String id, Channel channel) {
    observers.forEach(l -> l.onConnected(id, channel));
  }

  protected void onError(Exception exception) {
    observers.forEach(l -> l.onError(exception));
  }
}
