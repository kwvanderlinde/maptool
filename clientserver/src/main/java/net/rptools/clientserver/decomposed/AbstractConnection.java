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
package net.rptools.clientserver.decomposed;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public abstract class AbstractConnection implements Connection {
  // TODO Add channel management here as well?

  // NB: Adding and removing observers is a rare operation, meaning two things:
  // 1. This list will not be very large in practice.
  // 2. Even if it is large enough for iteration to be noticeable, it still won't matter.
  private final List<ConnectionObserver> observers = new ArrayList<>();
  private final String id;

  protected AbstractConnection(String id) {
    this.id = id;
  }

  @Nonnull
  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public void addObserver(ConnectionObserver observer) {
    observers.add(observer);
  }

  @Override
  public void removeObserver(ConnectionObserver observer) {
    observers.removeIf(element -> element == observer);
  }
}
