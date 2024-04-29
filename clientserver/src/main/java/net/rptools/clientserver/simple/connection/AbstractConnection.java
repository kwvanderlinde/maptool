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
package net.rptools.clientserver.simple.connection;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.MessageHandler;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractConnection implements Connection {
  /**
   * The maximum amount of time we want to wait for blocking operations in loops.
   *
   * <p>Without such a bound, a thread will wait potentially forever for a result. This isn't bad
   * per se, but it means we need to be very careful to wake or interrupt blocked threads on certain
   * state changes (e.g., if the connection is lost). If we miss such a case, the thread could be
   * parked for much longer than we expect.
   *
   * <p>By using this upper bound on wait time, we can get an opportunity to recheck loop conditions
   * even if the result is not ready yet. So, for example, we know that at least every 10ms, we
   * checked whether the connection is still alive and handled a lost connection as needed.
   *
   * <p>The exact value is not important. It is small enough that it should not cause user-visible
   * delays, but plenty long enough to be a win over explicit busy waiting.
   */
  protected static final int WAIT_TIME_MS = 10;

  private static final Logger log = LogManager.getLogger(AbstractConnection.class);

  private final BlockingQueue<byte[]> outQueue = new LinkedBlockingQueue<>();

  private final List<DisconnectHandler> disconnectHandlers = new CopyOnWriteArrayList<>();
  private final List<ActivityListener> listeners = new CopyOnWriteArrayList<>();
  private final List<MessageHandler> messageHandlers = new CopyOnWriteArrayList<>();

  private byte[] compress(byte[] message) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(message.length);
      OutputStream ios = new ZstdCompressorOutputStream(baos);
      ios.write(message);
      ios.close();
      var compressedMessage = baos.toByteArray();
      return compressedMessage;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] inflate(byte[] compressedMessage) {
    InputStream bytesIn = new ByteArrayInputStream(compressedMessage);
    try {
      InputStream ios = new ZstdCompressorInputStream(bytesIn);
      var decompressed = ios.readAllBytes();
      ios.close();
      return decompressed;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void addMessage(byte[] message) {
    outQueue.add(compress(message));
  }

  protected byte[] nextMessage() {
    try {
      return outQueue.poll(WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      return null;
    }
  }

  public final void addMessageHandler(MessageHandler handler) {
    messageHandlers.add(handler);
  }

  public final void removeMessageHandler(MessageHandler handler) {
    messageHandlers.remove(handler);
  }

  protected void dispatchMessage(String id, byte[] message) {
    if (messageHandlers.isEmpty()) {
      log.warn("message received but not messageHandlers registered.");
    }

    for (MessageHandler handler : messageHandlers) {
      handler.handleMessage(id, message);
    }
  }

  protected final void dispatchCompressedMessage(String id, byte[] compressedMessage) {
    var message = inflate(compressedMessage);
    dispatchMessage(id, message);
  }

  protected final void writeMessage(OutputStream out, byte[] message) throws IOException {
    int length = message.length;

    notifyListeners(ActivityListener.Direction.Outbound, ActivityListener.State.Start, length, 0);

    out.write(length >> 24);
    out.write(length >> 16);
    out.write(length >> 8);
    out.write(length);

    for (int i = 0; i < message.length; i++) {
      out.write(message[i]);

      if (i != 0 && i % ActivityListener.CHUNK_SIZE == 0) {
        notifyListeners(
            ActivityListener.Direction.Outbound, ActivityListener.State.Progress, length, i);
      }
    }
    out.flush();
    notifyListeners(
        ActivityListener.Direction.Outbound, ActivityListener.State.Complete, length, length);
  }

  protected final byte[] readMessage(InputStream in) throws IOException {
    int b32 = in.read();
    int b24 = in.read();
    int b16 = in.read();
    int b8 = in.read();

    if (b32 < 0) {
      throw new IOException("Stream closed");
    }
    int length = (b32 << 24) + (b24 << 16) + (b16 << 8) + b8;

    notifyListeners(ActivityListener.Direction.Inbound, ActivityListener.State.Start, length, 0);

    byte[] ret = new byte[length];
    for (int i = 0; i < length; i++) {
      ret[i] = (byte) in.read();

      if (i != 0 && i % ActivityListener.CHUNK_SIZE == 0) {
        notifyListeners(
            ActivityListener.Direction.Inbound, ActivityListener.State.Progress, length, i);
      }
    }
    notifyListeners(
        ActivityListener.Direction.Inbound, ActivityListener.State.Complete, length, length);
    return ret;
  }

  private ByteBuffer messageBuffer = null;

  protected final byte[] readMessage(ByteBuffer part) {
    if (messageBuffer == null) {
      int length = part.getInt();
      notifyListeners(ActivityListener.Direction.Inbound, ActivityListener.State.Start, length, 0);

      if (part.remaining() == length) {
        var ret = new byte[length];
        part.get(ret);
        notifyListeners(
            ActivityListener.Direction.Inbound, ActivityListener.State.Complete, length, length);
        return ret;
      }

      messageBuffer = ByteBuffer.allocate(length);
    }

    messageBuffer.put(part);
    notifyListeners(
        ActivityListener.Direction.Inbound,
        ActivityListener.State.Progress,
        messageBuffer.capacity(),
        messageBuffer.position());

    if (messageBuffer.capacity() == messageBuffer.position()) {
      notifyListeners(
          ActivityListener.Direction.Inbound,
          ActivityListener.State.Complete,
          messageBuffer.capacity(),
          messageBuffer.capacity());
      var ret = messageBuffer.array();
      messageBuffer = null;
      return ret;
    }

    return null;
  }

  protected final void fireDisconnect() {
    for (DisconnectHandler handler : disconnectHandlers) {
      handler.handleDisconnect(this);
    }
  }

  public final void addDisconnectHandler(DisconnectHandler handler) {
    disconnectHandlers.add(handler);
  }

  public final void removeDisconnectHandler(DisconnectHandler handler) {
    disconnectHandlers.remove(handler);
  }

  public final void addActivityListener(ActivityListener listener) {
    listeners.add(listener);
  }

  public final void removeActivityListener(ActivityListener listener) {
    listeners.remove(listener);
  }

  private void notifyListeners(
      ActivityListener.Direction direction,
      ActivityListener.State state,
      int totalTransferSize,
      int currentTransferSize) {
    for (ActivityListener listener : listeners) {
      listener.notify(direction, state, totalTransferSize, currentTransferSize);
    }
  }
}
