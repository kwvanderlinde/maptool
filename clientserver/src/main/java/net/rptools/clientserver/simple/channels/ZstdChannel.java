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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;

public class ZstdChannel implements Channel {
  private final Channel decorated;
  private ZstdCompressorInputStream inputStream;
  private ZstdCompressorOutputStream outputStream;

  public ZstdChannel(Channel channel) {
    decorated = channel;
  }

  @Override
  public void open() throws IOException {
    decorated.open();

    // Since we send messages, it is desirable that each message can be sent independently. This
    // allows the client to cleanly disconnect at any point in time without requiring the server to
    // close its final frame. If we do not set `closeFrameOnFlush`, clients will end up with
    // "Truncated source" errors as they disconnect.
    outputStream =
        new ZstdCompressorOutputStream(
            new NoCloseOutputStream(decorated.getOutputStream()),
            // 3 is the default compression level.
            3,
            true);
    inputStream = new ZstdCompressorInputStream(new NoCloseInputStream(decorated.getInputStream()));
  }

  @Override
  public void close() throws IOException {
    IOException lastFailure = null;

    try {
      inputStream.close();
    } catch (IOException e) {
      lastFailure = e;
    }
    try {
      outputStream.close();
    } catch (IOException e) {
      lastFailure = e;
    }
    try {
      decorated.close();
    } catch (IOException e) {
      lastFailure = e;
    }

    if (lastFailure != null) {
      throw lastFailure;
    }
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  /**
   * Wraps an input stream in a non-owning way. I.e., closing this stream will not close the
   * underlying stream.
   */
  private static final class NoCloseInputStream extends FilterInputStream {
    public NoCloseInputStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() {
      // Do nothing. The underlying decorator can close its own streams.
    }
  }

  /**
   * Wraps an output stream in a non-owning way. I.e., closing this stream will not close the
   * underlying stream.
   */
  private static final class NoCloseOutputStream extends FilterOutputStream {
    public NoCloseOutputStream(OutputStream out) {
      super(out);
    }

    @Override
    public void close() {
      // Do nothing. The underlying decorator can close its own streams.
    }
  }
}
