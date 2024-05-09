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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.rptools.clientserver.ActivityListener;
import net.rptools.clientserver.simple.DisconnectHandler;
import net.rptools.clientserver.simple.MessageHandler;
import net.rptools.clientserver.simple.channels.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChannelConnection implements Connection {
  private static final Logger log = LogManager.getLogger(ChannelConnection.class);
  private static final int MAX_CHUNK_SIZE = 4 * 1024; // Feel like this could be way smaller.

  private final String id;
  private final Channel channel;
  private final BlockingQueue<byte[]> outgoingQueue = new LinkedBlockingQueue<>();

  private final List<DisconnectHandler> disconnectHandlers = new CopyOnWriteArrayList<>();
  private final List<MessageHandler> messageHandlers = new CopyOnWriteArrayList<>();

  private final List<ActivityListener> activityListeners = new CopyOnWriteArrayList<>();
  private final ActivityListener delegatingActivityListener;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final SendThread sendThread;
  private final ReceiveThread receiveThread;

  public ChannelConnection(String id, Channel channel) {
    this.id = id;
    this.channel = channel;

    this.sendThread = new SendThread();
    this.sendThread.setName("ChannelConnection.SendThread[" + id + "]");

    this.receiveThread = new ReceiveThread();
    this.sendThread.setName("ChannelConnection.ReceiveThread[" + id + "]");

    this.delegatingActivityListener =
        new ActivityListener() {
          @Override
          public void notify(
              Direction direction, State state, int totalTransferSize, int currentTransferSize) {
            activityListeners.forEach(
                listener ->
                    listener.notify(direction, state, totalTransferSize, currentTransferSize));
          }
        };
  }

  @Override
  public void open() throws IOException {
    channel.open();
    sendThread.start();
    receiveThread.start();
  }

  @Override
  public void close() {
    // Clean close.

    closeMaybeUnexpectedly(null);
  }

  private void closeMaybeUnexpectedly(@Nullable Throwable t) {
    // Compare and set so we only close once.
    if (closed.compareAndSet(false, true)) {
      if (t != null) {
        if (t instanceof EOFException e) {
          log.info("End of input reached", e);
        } else if (t instanceof IOException e) {
          log.error("Lost the channel", e);
        } else {
          log.error("Unexpected failure", t);
        }
      }

      // TODO Have a join timeout. No sense hanging here indefinitely if something went awry.
      if (!Thread.currentThread().equals(receiveThread)) {
        while (receiveThread.isAlive()) {
          try {
            // Because our streams are based on interruptible channels, this will unblock any
            // currently blocked reads.
            receiveThread.interrupt();
            receiveThread.join(10);
          } catch (InterruptedException e) {
            // Just try again.
          }
        }
      }
      if (!Thread.currentThread().equals(sendThread)) {
        while (true) {
          try {
            // We don't expect our send thread to be blocked like the receive thread. But this can
            // at least stop any pending queue pool leading to an earlier exit.
            sendThread.interrupt();
            sendThread.join();
            break;
          } catch (InterruptedException e) {
            // Just try again.
          }
        }
      }

      try {
        // TODO Allow our close() method to bubble this exception up.
        channel.close();
      } catch (IOException e) {
        log.error("Failed to close channel", e);
      }

      for (DisconnectHandler handler : disconnectHandlers) {
        handler.handleDisconnect(this);
      }
    }
  }

  @Override
  public void sendMessage(byte[] message) {
    outgoingQueue.add(message);
  }

  @Override
  public boolean isAlive() {
    return !closed.get();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void addMessageHandler(MessageHandler handler) {
    messageHandlers.add(handler);
  }

  @Override
  public void removeMessageHandler(MessageHandler handler) {
    messageHandlers.removeIf(handler::equals);
  }

  private void fireMessageReceived(byte[] message) {
    if (messageHandlers.isEmpty()) {
      log.warn("message received but no messageHandlers registered.");
    }

    for (MessageHandler handler : messageHandlers) {
      handler.handleMessage(id, message);
    }
  }

  @Override
  public void addDisconnectHandler(DisconnectHandler handler) {
    disconnectHandlers.add(handler);
  }

  @Override
  public void removeDisconnectHandler(DisconnectHandler handler) {
    disconnectHandlers.removeIf(handler::equals);
  }

  @Override
  public void addActivityListener(ActivityListener listener) {
    activityListeners.add(listener);
  }

  @Override
  public void removeActivityListener(ActivityListener listener) {
    activityListeners.removeIf(listener::equals);
  }

  @Override
  public String getError() {
    return "";
  }

  private final class SendThread extends Thread {
    @Override
    public void run() {
      // A big-endian stream.
      final var stream = new DataOutputStream(channel.getOutputStream());

      while (!closed.get()) {
        try {
          byte[] nextMessage;
          try {
            nextMessage = outgoingQueue.poll(10, TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            // Just try again on interruption.
            continue;
          }

          if (nextMessage == null) {
            // Try again if there was nothing to receive.
            continue;
          }

          // We have a message!
          try {
            delegatingActivityListener.notify(
                ActivityListener.Direction.Outbound,
                ActivityListener.State.Start,
                nextMessage.length,
                0);

            stream.writeInt(nextMessage.length);

            // Chunked writes so we can keep the activity listener updated.
            int offset = 0;
            while (offset < nextMessage.length) {
              var chunkSize = Math.min(MAX_CHUNK_SIZE, nextMessage.length - offset);
              stream.write(nextMessage, offset, chunkSize);
              offset += chunkSize;

              delegatingActivityListener.notify(
                  ActivityListener.Direction.Outbound,
                  ActivityListener.State.Progress,
                  nextMessage.length,
                  offset);
            }

            stream.flush();
            delegatingActivityListener.notify(
                ActivityListener.Direction.Outbound,
                ActivityListener.State.Complete,
                nextMessage.length,
                nextMessage.length);
          } catch (IOException e) {
            // Channel is broken, kill the thread.
            closeMaybeUnexpectedly(e);
            break;
          }
        } catch (Throwable t) {
          closeMaybeUnexpectedly(t);
          break;
        }
      }
    }
  }

  private final class ReceiveThread extends Thread {
    @Override
    public void run() {
      // A big-endian stream.
      final var stream = new DataInputStream(channel.getInputStream());

      while (!closed.get()) {
        try {
          byte[] message;
          try {
            // First read the length word.
            final var length = stream.readInt();
            message = new byte[length];

            // The following loop is equivalent to stream.readNBytes(...) except that we can inform
            // the activity listeners about our progress.

            delegatingActivityListener.notify(
                ActivityListener.Direction.Inbound, ActivityListener.State.Start, length, 0);
            int totalBytesRead = 0;
            while (totalBytesRead < length) {
              var bytesRead = stream.read(message, totalBytesRead, message.length - totalBytesRead);
              if (bytesRead < 0) {
                break;
              }
              totalBytesRead += bytesRead;
              delegatingActivityListener.notify(
                  ActivityListener.Direction.Inbound,
                  ActivityListener.State.Progress,
                  length,
                  totalBytesRead);
            }
            delegatingActivityListener.notify(
                ActivityListener.Direction.Inbound,
                ActivityListener.State.Complete,
                length,
                totalBytesRead);

            if (totalBytesRead < message.length) {
              throw new EOFException(
                  String.format(
                      "End of input while reading message. Got %d of %d bytes",
                      totalBytesRead, message.length));
            }

          } catch (IOException e) {
            closeMaybeUnexpectedly(e);
            break;
          }

          fireMessageReceived(message);
        } catch (Throwable t) {
          closeMaybeUnexpectedly(t);
          break;
        }
      }
    }
  }
}
