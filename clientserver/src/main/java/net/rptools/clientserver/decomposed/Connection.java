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
import javax.annotation.Nullable;

public interface Connection {
  @Nonnull
  String getId();

  void start();

  void close();

  void addObserver(ConnectionObserver observer);

  void removeObserver(ConnectionObserver observer);

  // TODO I would prefer some constraint on channels, though in this generic library no option comes
  //  to mind. It seems prudent, though, that channels could be strings, or even integers. Perhaps
  //  they could even be registered somehow so as to provide an indicator to the receiver which
  //  channel is being used.
  //  Current thought is that channels should be communicated through the handshake. Channels would
  //  have names, integer IDs, and configurable compression parameters (e.g., algorithm, level,
  //  etc).
  void sendMessage(@Nullable Object channel, @Nonnull byte[] message);
}
