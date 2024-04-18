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

import java.util.concurrent.atomic.AtomicInteger;

/*
 * TODO We could really expand on the concept of channels, potentially for great gain. Although
 *  today channels are merely a balancing mechanism for a message queue, they could contain various
 *  parameters controlling details of the communication. Especially compression could be
 *  interesting, by apply different algorithms or compression levels for text vs images vs other
 *  assets.
 */

public final class ChannelId {
  private static final AtomicInteger nextId = new AtomicInteger(0);

  private final int id;

  public ChannelId() {
    this.id = nextId.getAndIncrement();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ChannelId channelId && this.id == channelId.id;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(id);
  }
}
