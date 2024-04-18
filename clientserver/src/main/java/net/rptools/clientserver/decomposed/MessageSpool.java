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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Spools messages onto one of any number of channels, then allows reading them back in a
 * round-robin manner.
 *
 * <p>The idea behind this approach is that we don't need certain types of messages to be sequenced
 * relative to other types of messages, and we also don't want a large number of the one type to
 * starve out the other type. By using round-robin scheduling, we ensure that at most one message of
 * a given type is sent before looking for messages of a different type.
 *
 * <p>This class is not thread safe.
 */
/*
 * TODO Even better would be chunked transmission so that large assets (maps) don't starve out other
 *  messages. In this approach, a "channel" is really a totally ordered sequence of messages, and
 *  different channels have no ordering guarantees.
 */
public class MessageSpool {
  private static final Logger log = LogManager.getLogger(MessageSpool.class);

  /**
   * Associates each channel ID to the associated channel. The first time a channel ID is seen, a
   * new channel will be created for it.
   *
   * <p>A channel is just an independent list of messages. Message within the channel maintain their
   * sequence relative to one another, but not relative to messages in other channels.
   */
  private final Map<ChannelId, List<byte[]>> channelsById = new HashMap<>();

  /**
   * Maintains an ordering among active channels.
   *
   * <p>The first element if this list is the channel that will be read for messages next.
   *
   * <p>Invariant: this list contains as elements exactly those channels in {@link #channelsById}
   * that are not empty.
   */
  // TODO Even better, just keep a list of Object channel IDs?
  private final List<List<byte[]>> queueOfChannels = new LinkedList<>();

  private List<byte[]> getChannel(ChannelId channelId) {
    return channelsById.computeIfAbsent(channelId, ch -> new ArrayList<>());
  }

  public void addMessage(@Nonnull ChannelId channelId, @Nonnull byte[] message) {
    // message is compressed already.
    List<byte[]> channel = getChannel(channelId);

    if (channel.isEmpty()) {
      // Empty channel won't already be activated.
      queueOfChannels.add(channel);
    }

    channel.add(message);
  }

  /** Gets the next message in the spool, or {@code null} if the spool is empty. */
  public @Nullable byte[] nextMessage() {
    while (!queueOfChannels.isEmpty()) {
      final var channel = queueOfChannels.removeFirst();
      if (channel.isEmpty()) {
        log.error("Found empty channel in active state");
        continue;
      }

      final var message = channel.removeFirst();
      if (!channel.isEmpty()) {
        // Schedule this channel again.
        queueOfChannels.add(channel);
      }

      return message;
    }

    return null;
  }
}
