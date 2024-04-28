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
import net.rptools.clientserver.decomposed.Connection;
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
  public void sendMessage(@Nonnull byte[] message) {
    if (message.length == 0) {
      // No point sending empty messages. Also, internally we use an empty message as a sentinel to
      // signal a close request, so we don't want anyone else to mess with that.
      return;
    }

    this.sender.addMessage(message);
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
    private final Thread thread;
    private final SocketConnection connection;
    private final Socket socket;
    private final BlockingQueue<byte[]> pendingMessages;
    private final AtomicBoolean isClosed;

    public Sender(SocketConnection connection, Socket socket) {
      this.thread = new Thread(this::run, "SocketConnection.SendThread");
      this.connection = connection;
      this.socket = socket;
      this.pendingMessages = new LinkedBlockingQueue<>();
      this.isClosed = new AtomicBoolean(false);
    }

    public void start() {
      thread.start();
    }

    public void requestStop() {
      this.addMessage(new byte[0]);
    }

    public void addMessage(byte[] message) {
      if (isClosed.get()) {
        // It's not the end of the world if we don't 100% discard all these messages, we just don't
        // want them to build up unnecessarily.
        log.warn("Discarding message sent after connection closure");
        return;
      }

      pendingMessages.add(message);

      synchronized (this) {
        this.notify();
      }
    }

    private void sendMessages(OutputStream out, Collection<byte[]> messages) throws IOException {
      int count = 0;

      // Write out the batch of messages.
      for (final var message : messages) {
        if (message.length == 0) {
          // This is a special close request. We still want to action preceding messages, but do not
          // spool any more messages.
          isClosed.set(true);
          break;
        }

        connection.writeMessage(out, message);
        ++count;
      }

      log.info("Sent {} messages", count);
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
      final var messageBuffer = new ArrayList<byte[]>(bufferCapacity);
      while (!isClosed.get()) {
        try {
          synchronized (this) {
            this.wait();
          }
        } catch (InterruptedException e) {
          // Totally normal, do nothing.
        }

        // TODO If I do .take(), then .drainTo(), I could rely on the queue to do my thread blocking
        //  & signaling for me.
        pendingMessages.drainTo(messageBuffer, bufferCapacity);

        // It's possible the isClosed flag just got set, but we still need to process any messages
        // that may have come in before that.

        try {
          // Write out the batch of messages.
          sendMessages(out, messageBuffer);
        } catch (IOException e) {
          log.error("Error while trying to send messages", e);
          connection.doDisconnect(e);
          return;
        }

        // All buffered messages have been sent, so clear the buffer for the next loop.
        messageBuffer.clear();
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
