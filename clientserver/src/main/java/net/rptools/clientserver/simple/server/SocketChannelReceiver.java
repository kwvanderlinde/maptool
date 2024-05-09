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
package net.rptools.clientserver.simple.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.rptools.clientserver.simple.channels.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wraps a {@link java.nio.channels.ServerSocketChannel} to produce {@link
 * net.rptools.clientserver.simple.channels.SocketChannel}.
 */
public class SocketChannelReceiver extends AbstractChannelReceiver {
  private static final Logger log = LogManager.getLogger(SocketChannelReceiver.class);

  // Shared state.
  private final AtomicBoolean stopRequested;

  // Receiver thread only (run() method).
  private final int port;
  private int nextConnectionId;

  private ServerSocketChannel serverSocket;
  private Thread receiverThread;

  public SocketChannelReceiver(int port) {
    super();

    this.stopRequested = new AtomicBoolean(false);
    this.port = port;
    this.nextConnectionId = 0;
  }

  @Override
  public void start() throws IOException {
    // Note: it is important that we create the server socket right away rather than pushing that
    // into the thread. Otherwise there could be unexpected delay in setting up the socket, during
    // which time clients may try to connect and illegitimately be rejected.
    serverSocket = ServerSocketChannel.open();
    serverSocket.bind(new InetSocketAddress(port));
    this.receiverThread =
        Thread.ofPlatform()
            .daemon()
            .name("SocketChannelReceiver.ReceivingThread")
            .unstarted(() -> this.run(serverSocket));
    this.receiverThread.start();
  }

  @Override
  public void close() {
    stopRequested.set(true);

    // Now that we're no longer trying to wait on the server socket, close it as well.
    try {
      serverSocket.close();
    } catch (IOException e) {
      log.error("Unable to close server socket", e);
      onError(e);
    }

    // Wait to stop listening on the server socket.
    // TODO Set a timeout to wait for.
    while (true) {
      try {
        receiverThread.join();
        break;
      } catch (InterruptedException e) {
        // Try again.
      }
    }
  }

  private void run(ServerSocketChannel serverSocket) {
    while (!stopRequested.get()) {
      // Right here, in this gap, it is possible that the socket is closed before we can .accept()
      // So it is important that we catch that case via exception below.

      java.nio.channels.SocketChannel socket;
      try {
        socket = serverSocket.accept();
      } catch (ClosedChannelException e) {
        // Can either be already closed, closed async, or closed by interrupt. Either way, our
        // course of action is the same.
        if (stopRequested.get()) {
          // Expected closure.
          log.info("Server socket closed. Shutting down.");
        } else {
          // Unexpected error.
          onError(e);
        }
        return;
      } catch (IOException e) {
        // Unexpected error.
        onError(e);
        return;
      }

      log.debug("Client connecting ...");

      final var id = socket.socket().getInetAddress().getHostAddress() + "-" + nextConnectionId++;
      final var channel = new SocketChannel(socket);
      onConnected(id, channel);
    }
  }
}
