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
package net.rptools.clientserver.decomposed.socket;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import net.rptools.clientserver.decomposed.AbstractConnection;
import net.rptools.clientserver.decomposed.ChannelId;
import net.rptools.clientserver.decomposed.Connection;
import net.rptools.clientserver.decomposed.MessageSpool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// NB: Unlike our predecessar, we require the underlying socket to be created _before_ the
// Connection object itself exists.
public class SocketConnection extends AbstractConnection implements Connection {
  // TODO It would be nice to add compression or other transforms on the read and write
  //  threads without AbstractConnection hardcoding the exact method.

  private static final Logger log = LogManager.getLogger(SocketConnection.class);

  private final Socket socket;
  private final Sender sender;
  private final Receiver receiver;

  public SocketConnection(String id, Socket socket) {
    super(id);
    this.socket = socket;

    this.sender = new Sender(this, socket);
    this.receiver = new Receiver(this, socket);
  }

  @Override
  public void sendMessage(@Nonnull ChannelId channelId, @Nonnull byte[] message) {
    this.sender.addMessage(channelId, message);
  }

  @Override
  public void start() {
    this.sender.start();
    this.receiver.start();
  }

  @Override
  public void close() {
    // Immediately stop listening for new messages.
    this.receiver.requestStop();
    // Ask the sender to stop sending messages, but only after existing messages have been handled.
    this.sender.requestStop();

    onDisconnected("closed");
  }

  private void doDisconnect(@Nullable Exception exception) {
    if (!socket.isClosed()) {
      try {
        socket.close();
      } catch (IOException ioe) {
        log.error("Unable to close socket", ioe);
      }
    }

    if (exception != null) {
      log.error(exception);
      onDisconnected(exception.getMessage());
    } else {
      onDisconnected(null);
    }
  }

  @ThreadSafe
  private static final class Sender {
    private record PendingMessage(ChannelId channelId, byte[] message) {}

    private final Thread thread;
    private final SocketConnection connection;
    private final Socket socket;
    /*
     * TODO
     *      This is the wrong layer to apply spooling. In principle, the server should be aware of
     *  the order that messages are actioned, to decide on a canonical ordering that - in the
     *  future - we could require clients to replicate. Imagine TCP resend, or even more accurately,
     *  GGPO rollback / client-side prediction.
     *      Even leaving aside future improvements like the above, message spooling is a bane to
     *  connection composition. At its most basic level, a connection is just a way to stream bytes
     *  in and out. This remains true with spooling -- since messages are interleaved in the output
     *  -- but the interjection of spooling means we cannot simply "nest" connections in order to
     *  compose them, because every layer is technically responsible for spooling / interleaving.
     *  Additionally, spooling is a time-sensitive operations, so different layers may not even
     *  agree about the ultimate order of messages, which could be a problem if certain sequences
     *  need to be maintained.
     */
    private final MessageSpool spool;
    private final BlockingQueue<PendingMessage> pendingMessages;
    private final AtomicBoolean isClosed;
    private final ChannelId controlChannel;

    public Sender(SocketConnection connection, Socket socket) {
      this.thread = new Thread(this::run, "SocketConnection.SendThread");
      this.connection = connection;
      this.socket = socket;
      this.pendingMessages = new LinkedBlockingQueue<>();
      this.spool = new MessageSpool();
      this.isClosed = new AtomicBoolean(false);
      this.controlChannel = new ChannelId();
    }

    public void start() {
      thread.start();
    }

    public void requestStop() {
      this.addMessage(controlChannel, new byte[0]);
    }

    public void addMessage(ChannelId channelId, byte[] message) {
      if (isClosed.get()) {
        // It's not the end of the world if we don't 100% discard all these messages, we just don't
        // want them to build up unnecessarily.
        log.warn("Discarding message sent after connection closure");
        return;
      }

      pendingMessages.add(new PendingMessage(channelId, message));

      synchronized (this) {
        this.notify();
      }
    }

    private void spoolMessages(Collection<PendingMessage> messages) {
      assert Thread.currentThread() == thread : "Spooling must be done on the send thread";

      int count = 0;
      for (final var pendingMessage : messages) {
        if (controlChannel.equals(pendingMessage.channelId())
            && pendingMessage.message.length == 0) {
          // This is a special close request. We still want to action preceding messages, but do not
          // spool any more messages.
          isClosed.set(true);
          break;
        }

        spool.addMessage(pendingMessage.channelId(), pendingMessage.message());
        ++count;
      }

      log.info("Spooled {} messages", count);
    }

    private void sendAllSpooledMessages(OutputStream out) throws IOException {
      assert Thread.currentThread() == thread : "Message sending must be done on the send thread";

      byte[] message;
      while ((message = spool.nextMessage()) != null) {
        connection.writeMessage(out, message);
      }
      // TODO Original caught an IndexOutOfBoundsException somewhere here, and I don't see why.
    }

    private void run() {
      final OutputStream out;
      try {
        // TODO Is there actually value in a buffered output stream? E.g., is it interruptible?
        out = new BufferedOutputStream(socket.getOutputStream());
      } catch (IOException e) {
        log.error("Unable to get socket output stream", e);
        connection.doDisconnect(e);
        return;
      }

      // TODO This thread controls socket closure. So encountering a closed socket in the loop is
      //  in fact an unexpected scenario.

      final var bufferCapacity = 1024;
      final var messageBuffer = new ArrayList<PendingMessage>(bufferCapacity);
      while (!isClosed.get()) {
        try {
          synchronized (this) {
            this.wait();
          }
        } catch (InterruptedException e) {
          // Totally normal, do nothing.
        }

        pendingMessages.drainTo(messageBuffer, bufferCapacity);
        spoolMessages(messageBuffer);
        // All buffered messages are now in the spool, so clear the buffer for the next loop.
        messageBuffer.clear();

        // It's possible the isClosed flag just got set, but we still need to process any messages
        // that may have come in before that.

        try {
          sendAllSpooledMessages(out);
        } catch (IOException e) {
          log.error("Error while trying to send messages", e);
          connection.doDisconnect(e);
          return;
        }
      }

      // Clean exit. Clean up the socket and notify observers.
      connection.doDisconnect(null);
      // No point keeping stale messages around.
      pendingMessages.clear();
    }
  }

  @ThreadSafe
  private static final class Receiver {
    private final Thread thread;
    private final SocketConnection connection;
    private final Socket socket;
    private final AtomicBoolean requestStop;

    public Receiver(SocketConnection connection, Socket socket) {
      this.thread = new Thread(this::run, "SocketConnection.ReceiveThread");
      this.connection = connection;
      this.socket = socket;
      this.requestStop = new AtomicBoolean(false);
    }

    public void start() {
      thread.start();
    }

    public void requestStop() {
      requestStop.set(true);
    }

    private void run() {
      final InputStream in;
      try {
        in = socket.getInputStream();
      } catch (IOException e) {
        log.error("Unable to get socket input stream", e);
        connection.doDisconnect(e);
        return;
      }

      while (!requestStop.get() && !socket.isClosed()) {
        try {
          byte[] message = connection.readMessage(in);
          connection.onMessageReceived(message);
        } catch (IOException e) {
          // Likely a socket closure, but could also be something unexpected.
          connection.doDisconnect(e);
          return;
        } catch (Throwable t) {
          log.error(t);
        }
      }
    }
  }
}
