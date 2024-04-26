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

import javax.annotation.Nonnull;

public interface ConnectionHandler {
  interface Listener {
    void onConnected(@Nonnull Connection connection);

    // TODO Callbacks for server-side error conditions.
  }

  void start();

  void stop();

  // TODO How should we represent start() to allow asynchronous failures?

  void addListener(@Nonnull Listener listener);

  void removeListener(@Nonnull Listener listener);

  // TODO start / stop() ?
}
