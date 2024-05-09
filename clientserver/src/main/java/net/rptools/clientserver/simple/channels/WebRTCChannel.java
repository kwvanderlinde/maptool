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

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebRTCChannel implements Channel {
  private static final int MAXIMUM_CHUNK_SIZE = 16 * 1024;

  private final AtomicBoolean disconnected = new AtomicBoolean(false);
  private final RTCDataChannel rtcDataChannel;

  private OutputStream outputStream;
  private PipedInputStream inputStream;
  private PipedOutputStream inputStreamOutput;

  public WebRTCChannel(RTCDataChannel rtcDataChannel) {
    this.rtcDataChannel = rtcDataChannel;

    this.rtcDataChannel.registerObserver(
        new RTCDataChannelObserver() {
          @Override
          public void onBufferedAmountChange(long previousAmount) {}

          @Override
          public void onStateChange() {
            switch (rtcDataChannel.getState()) {
              case CLOSED, CLOSING -> disconnected.set(true);
            }
          }

          @Override
          public void onMessage(RTCDataChannelBuffer buffer) {
            // TODO Catch BufferUnderflowException and BufferOverflowExceptions and disconnect.

            final var byteBuffer = buffer.data;

            final int length = byteBuffer.remaining();
            final byte[] bytes;
            final int offset;

            if (byteBuffer.hasArray()) {
              offset = byteBuffer.capacity() - byteBuffer.remaining();
              bytes = byteBuffer.array();
            } else {
              offset = 0;
              bytes = new byte[length];
              byteBuffer.get(bytes, offset, length);
            }

            try {
              inputStreamOutput.write(bytes, offset, length);
            } catch (IOException e) {
              // TODO Fail and close down the channel.
            }
          }
        });
  }

  @Override
  public void open() throws IOException {
    outputStream = new RTCOutputStream();

    inputStream = new PipedInputStream();
    inputStreamOutput = new PipedOutputStream(inputStream);
  }

  @Override
  public void close() throws IOException {
    rtcDataChannel.close();
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  private IOException makeDisconnectedError() {
    return new IOException(
        String.format(
            "WebRTC data channel has disconnected. State is %s", rtcDataChannel.getState()));
  }

  private final class RTCOutputStream extends OutputStream {
    // TODO Somewhere, somehow, this class should implement disconnectedness.

    private final byte[] buffer = new byte[MAXIMUM_CHUNK_SIZE];
    private int position = 0;

    @Override
    public void write(int b) throws IOException {
      buffer[position++] = (byte) (b & 0xFF);

      if (position >= buffer.length) {
        flush();
      }
    }

    @Override
    public void flush() throws IOException {
      if (position <= 0) {
        return;
      }

      // I would rather use ByteBuffer.wrap(buffer, 0, position), but the RTC library just grabs the
      // raw array and does not respect the bounds.
      final var trimmed = ByteBuffer.allocate(position);
      trimmed.put(buffer, 0, position);
      position = 0;

      try {
        rtcDataChannel.send(new RTCDataChannelBuffer(trimmed, true));
      } catch (Exception e) {
        throw new IOException(e);
      }

      super.flush();
    }
  }
}
