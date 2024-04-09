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
import javax.annotation.Nonnull;
import net.rptools.clientserver.decomposed.AbstractConnection;
import net.rptools.clientserver.decomposed.Connection;
import net.rptools.clientserver.decomposed.MessageSpool;

// NB: Unlike our predecessar, we require the underlying socket to be created _before_ the
// Connection object itself exists.
public class SocketConnection extends AbstractConnection implements Connection {
  private final Socket socket;
  private final SendThread send;
  private final ReceiveThread receive;

  public SocketConnection(String id, Socket socket) {
    super(id);
    this.socket = socket;

    try {
      // TODO The create of the input and output streams here is incidental. It should in principle
      //  be internal to the thread itself, not requiring exception propagation here.
      this.send = new SendThread(this, socket);
      this.receive = new ReceiveThread(this, socket.getInputStream());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void sendMessage(@Nonnull Object channel, @Nonnull byte[] message) {}

  private static final class SendThread extends Thread {
    private final SocketConnection connection;
    private final Socket socket;
    private final MessageSpool sppol;

    public SendThread(SocketConnection connection, Socket socket) {
      super("SocketConnection.SendThread");
      this.connection = connection;
      this.socket = socket;
      this.pendingMessages = new ConcurrentLinkedQueue<>();
      this.sppol = new MessageSpool();
    }

    private void sendAllMessages(OutputStream out) {
      byte[] message;
      while ((message = sppol.nextMessage()) != null) {
        connection.writeMessage(out, message);
      }
      // TODO Original caught an IndexOutOfBoundsException somewhere here, and I don't see why.
    }

    @Override
    public void run() {
      try {
        // TODO Is there actually value in a buffered output stream? E.g., is it interruptible?
        final var out = new BufferedOutputStream(socket.getOutputStream());
        while (!socket.isClosed()) {
          try {
            sendAllMessages(out);
            synchronized (this) {
              if (!stopRequested) {
                this.wait();
              }
            }
          } catch (InterruptedException e) {
            // do nothing
          }
        }
      } catch (IOException e) {
        // Likely a socket closure, though could also be some unexpected thing.
        log.error(e);
        fireDisconnect();
      }
    }
  }

  private static final class ReceiveThread extends Thread {
    private final InputStream in;

    public ReceiveThread(SocketConnection connection, InputStream in) {
      setName("SocketConnection.ReceiveThread");
      this.in = in;
    }
  }
}
