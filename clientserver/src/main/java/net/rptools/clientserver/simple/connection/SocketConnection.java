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
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author drice
 */
public class SocketConnection extends AbstractConnection implements Connection {
  /** Instance used for log messages. */
  private static final Logger log = LogManager.getLogger(SocketConnection.class);

  private final String id;
  private SendThread send;
  private ReceiveThread receive;
  private Socket socket;
  private String hostName;
  private int port;

  public SocketConnection(String id, String hostName, int port) {
    this.id = id;
    this.hostName = hostName;
    this.port = port;
  }

  public SocketConnection(String id, Socket socket) {
    this.id = id;
    this.socket = socket;

    initialize(socket);
  }

  public String getId() {
    return id;
  }

  private void initialize(Socket socket) {
    this.socket = socket;
    this.send = new SendThread(socket);
    this.receive = new ReceiveThread(socket);

    this.send.start();
    this.receive.start();
  }

  @Override
  public void open() throws IOException {
    initialize(new Socket(hostName, port));
  }

  public void sendMessage(byte[] message) {
    addMessage(message);
  }

  protected boolean isStopRequested() {
    return send.stopRequested.get();
  }

  public synchronized void close() {
    if (isStopRequested()) {
      return;
    }
    send.requestStop();
    receive.requestStop();

    try {
      socket.close();
    } catch (IOException e) {
      log.warn(e.toString());
    }
  }

  public boolean isAlive() {
    return !socket.isClosed();
  }

  @Override
  public String getError() {
    return null;
  }

  // /////////////////////////////////////////////////////////////////////////
  // send thread
  // /////////////////////////////////////////////////////////////////////////
  private class SendThread extends Thread {
    private final Socket socket;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public SendThread(Socket socket) {
      setName("SocketConnection.SendThread");
      this.socket = socket;
    }

    public void requestStop() {
      this.stopRequested.set(true);
      this.interrupt();
    }

    @Override
    public void run() {
      final OutputStream out;
      try {
        out = new BufferedOutputStream(socket.getOutputStream());
      } catch (IOException e) {
        log.error("Unable to get socket output stream", e);
        fireDisconnect();
        return;
      }

      while (!stopRequested.get() && SocketConnection.this.isAlive()) {
        // Blocks for a time until a message is received.
        byte[] message = SocketConnection.this.nextMessage();
        if (message == null) {
          // No message available. Thread may also have been interrupted as part of stopping.
          continue;
        }

        try {
          SocketConnection.this.writeMessage(out, message);
        } catch (IOException e) {
          log.error(e);
          fireDisconnect();
          return;
        }
      }
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // receive thread
  // /////////////////////////////////////////////////////////////////////////
  private class ReceiveThread extends Thread {
    private final Socket socket;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public ReceiveThread(Socket socket) {
      setName("SocketConnection.ReceiveThread");
      this.socket = socket;
    }

    public void requestStop() {
      stopRequested.set(true);
    }

    @Override
    public void run() {
      final InputStream in;
      try {
        in = socket.getInputStream();
      } catch (IOException e) {
        log.error("Unable to get socket input stream", e);
        SocketConnection.this.close();
        return;
      }

      while (!stopRequested.get() && SocketConnection.this.isAlive()) {
        try {
          byte[] message = SocketConnection.this.readMessage(in);
          SocketConnection.this.dispatchCompressedMessage(SocketConnection.this.id, message);
        } catch (IOException e) {
          log.error(e);
          fireDisconnect();
          break;
        } catch (Throwable t) {
          log.error(t);
          // don't let anything kill this thread via exception
          t.printStackTrace();
        }
      }
    }
  }
}
