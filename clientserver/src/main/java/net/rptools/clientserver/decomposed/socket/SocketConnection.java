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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

  private static final ChannelId SOCKET_OPERATION_CHANNEL = new ChannelId();

  private final Socket socket;
  private final SendThread send;
  private final ReceiveThread receive;

  public SocketConnection(String id, Socket socket) {
    super(id);
    this.socket = socket;

    this.send = new SendThread(this, socket);
    this.receive = new ReceiveThread(this, socket);
  }

  @Override
  public void sendMessage(@Nonnull ChannelId channelId, @Nonnull byte[] message) {
    this.send.addMessage(channelId, message);
  }

  @Override
  public void start() {
    this.send.start();
    this.receive.start();
  }

  @Override
  public void close() {
    // Immediately stop listening for new messages.
    this.receive.requestStop();

    // Ask the sender to stop sending messages, but only after existing messages have been handled.
    this.send.addMessage(SOCKET_OPERATION_CHANNEL, new byte[0]);

    onDisconnected("closed");
  }

  private void doDisconnect(@Nullable Exception exception) {
    if (exception != null) {
      log.error(exception);
      onDisconnected(exception.getMessage());
    } else {
      onDisconnected(null);
    }

    if (!socket.isClosed()) {
      try {
        socket.close();
      } catch (IOException ioe) {
        log.error("Unable to close socket", ioe);
      }
    }
  }

  private static final class SendThread extends Thread {
    private static final int SPOOL_AMOUNT = 1000;

    private record PendingMessage(ChannelId channelId, byte[] message) {}

    private final SocketConnection connection;
    private final Socket socket;
    private final MessageSpool spool;
    private final ConcurrentLinkedQueue<PendingMessage> pendingMessages;
    private final AtomicBoolean isClosed;

    public SendThread(SocketConnection connection, Socket socket) {
      super("SocketConnection.SendThread");
      this.connection = connection;
      this.socket = socket;
      this.pendingMessages = new ConcurrentLinkedQueue<>();
      this.spool = new MessageSpool();
      this.isClosed = new AtomicBoolean(false);
    }

    private void close() {
      isClosed.set(true);
      pendingMessages.clear();
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

    private void spoolPendingMessages(int maxPendingMessagesToProcess) {
      assert Thread.currentThread() == this : "Spooling must be done on the send thread";

      int count = 0;

      while (!isClosed.get() && count++ < maxPendingMessagesToProcess) {
        final var pendingMessage = pendingMessages.poll();
        if (pendingMessage == null) {
          // No message available.
          break;
        }

        if (SOCKET_OPERATION_CHANNEL.equals(pendingMessage.channelId())) {
          // This message is meant to manipulate the connection, not to be sent to clients.
          if (pendingMessage.message.length == 0) {
            // Indicates a closure request.
            try {
              socket.close();
            } catch (IOException e) {
              log.error("Error when attempting to close the socket", e);
            }
            isClosed.set(true);
          }

          continue;
        }

        spool.addMessage(pendingMessage.channelId(), pendingMessage.message());
      }
      log.info("Spooled {} messages", count);
    }

    private void sendAllSpooledMessages(OutputStream out) throws IOException {
      assert Thread.currentThread() == this : "Message sending must be done on the send thread";

      byte[] message;
      while ((message = spool.nextMessage()) != null) {
        connection.writeMessage(out, message);
      }
      // TODO Original caught an IndexOutOfBoundsException somewhere here, and I don't see why.
    }

    @Override
    public void run() {
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

      int count = 0;
      do {
        spoolPendingMessages(SPOOL_AMOUNT);

        if (socket.isClosed()) {}

        try {
          sendAllSpooledMessages(out);
        } catch (IOException e) {
          log.error("Error while trying to send messages", e);
          connection.doDisconnect(e);
          return;
        }

        try {
          synchronized (this) {
            this.wait();
          }
        } catch (InterruptedException e) {
          // Totally normal, do nothing.
        }
      } while (true);

      try {
        while (!socket.isClosed()) {
          try {
            spoolPendingMessages(SPOOL_AMOUNT);
            sendAllSpooledMessages(out);
            synchronized (this) {
              if (!socket.isClosed()) {
                this.wait();
              }
            }
          } catch (InterruptedException e) {
            // do nothing
          }
        }
      } catch (IOException e) {
        // Likely a socket closure, though could also be some unexpected thing.
        connection.doDisconnect(e);
      }
    }
  }

  private static final class ReceiveThread extends Thread {
    private final SocketConnection connection;
    private final Socket socket;
    private final AtomicBoolean requestStop;

    public ReceiveThread(SocketConnection connection, Socket socket) {
      super("SocketConnection.ReceiveThread");
      this.connection = connection;
      this.socket = socket;
      this.requestStop = new AtomicBoolean(false);
    }

    public void requestStop() {
      requestStop.set(true);
    }

    @Override
    public void run() {
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
