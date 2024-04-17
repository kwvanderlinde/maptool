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

import net.rptools.clientserver.ActivityListener;

public interface ConnectionObserver {
  enum Direction {
    Inbound,
    Outbound
  };

  enum State {
    Start,
    Progress,
    Complete
  };

  void onMessageReceived(Connection connection, byte[] message);

  void onDisconnected(Connection connection, String reason);

  // For client-side tracking. TODO Why? Cna't the MessageHandler simply log this?
  void onActivity(
      Connection connection,
      ActivityListener.Direction direction,
      ActivityListener.State state,
      int totalTransferSize,
      int currentTransferSize);
}