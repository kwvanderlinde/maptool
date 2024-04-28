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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import net.rptools.clientserver.decomposed.AbstractConnectionHandler;
import net.rptools.clientserver.decomposed.ConnectionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SocketConnectionHandler extends AbstractConnectionHandler
    implements ConnectionHandler {
  private static final Logger log = LogManager.getLogger(SocketConnectionHandler.class);

  private final int port;
  private final ListeningThread listeningThread;

  public SocketConnectionHandler(int port) {
    this.port = port;
    this.listeningThread = new ListeningThread();
  }

  @Override
  public void start() {
    listeningThread.start();
  }

  @Override
  public void stop() {
    listeningThread.requestStop();
  }

  private final class ListeningThread {
    private final Thread thread;
    private int nextConnectionId = 0;

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public ListeningThread() {
      this.thread = new Thread(this::run, "SocketConnectionHandler.ListeningThread");
    }

    public void start() {
      thread.start();
    }

    public void requestStop() {
      stopRequested.set(true);
    }

    private String nextClientId(Socket socket) {
      final var id = nextConnectionId++;
      return socket.getInetAddress().getHostAddress() + "-" + id;
    }

    private void run() {
      final var suppressErrors = false;

      try (final var serverSocket = new ServerSocket(port)) {
        while (!stopRequested.get()) {
          Socket socket;
          try {
            socket = serverSocket.accept();
          } catch (IOException e) {
            // TODO Can't I stop the thread first, then shutdown the socket?
            if (!suppressErrors) {
              log.error(e.getMessage(), e);
            }
            continue;
          }

          log.debug("Client connecting ...");

          String id = nextClientId(socket);
          final var conn = new SocketConnection(id, socket);
          onConnected(conn);
        }
      } catch (IOException e) {
        // How to propagate to the server proper for cleanup?
        onError(e);
      }
    }
  }
}
