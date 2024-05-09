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
package net.rptools.clientserver.simple.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A channel implementation that waits for a channel-producing future. */
public class DeferredChannel implements Channel {
  private static final Logger log = LogManager.getLogger(DeferredChannel.class);

  private record DeferredState(
      Channel channel, OutputStream outputStream, InputStream inputStream) {}

  private final CompletionStage<? extends Channel> futureChannel;
  private DeferredState state = null;

  public DeferredChannel(CompletionStage<? extends Channel> futureChannel) {
    this.futureChannel = futureChannel;
  }

  /**
   * Block until the underlying channel is produced by the future, then open it.
   *
   * @throws IOException If the underlying future produced an exception.
   */
  @Override
  public void open() throws IOException {
    while (state == null) {
      try {
        var channel = this.futureChannel.toCompletableFuture().get();
        channel.open();

        state = new DeferredState(channel, channel.getOutputStream(), channel.getInputStream());
      } catch (InterruptedException e) {
        // Just try again.
      } catch (ExecutionException e) {
        throw new IOException(e.getCause());
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (state == null) {
      // Future not complete yet.
      futureChannel.whenComplete(
          (channel, error) -> {
            if (channel != null) {
              try {
                channel.close();
              } catch (IOException e) {
                log.error("Failed to close deferred channel", e);
              }
            }
          });
    } else {
      // Note: we don't close the streams since the underlying channel with do that itself.
      state.channel.close();
    }
  }

  @Override
  public OutputStream getOutputStream() {
    return state.outputStream;
  }

  @Override
  public InputStream getInputStream() {
    return state.inputStream;
  }
}
