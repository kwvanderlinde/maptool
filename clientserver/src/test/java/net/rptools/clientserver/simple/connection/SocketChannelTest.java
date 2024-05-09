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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.rptools.clientserver.simple.channels.SocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SocketChannelTest {
  private ServerSocketChannel serverSocket;
  // Channels don't actually care about server-side vs client-side, but I find these names easier
  // to think through.
  private SocketChannel serverSide;
  private SocketChannel clientSide;

  @BeforeEach
  public void setUp() throws IOException {
    serverSocket = ServerSocketChannel.open();
    serverSocket.bind(new InetSocketAddress(0));

    clientSide =
        new SocketChannel(
            java.nio.channels.SocketChannel.open(
                new InetSocketAddress("127.0.0.1", serverSocket.socket().getLocalPort())));
    serverSide = new SocketChannel(serverSocket.accept());

    clientSide.open();
    serverSide.open();
  }

  @AfterEach
  public void tearDown() throws IOException {
    serverSocket.close();
  }

  @Test
  public void testSendFromClient() throws IOException {
    byte[] message = "this is a message".getBytes(StandardCharsets.UTF_8);

    var out = clientSide.getOutputStream();
    out.write(message);
    out.flush();

    var in = serverSide.getInputStream();
    byte[] received = new byte[message.length];
    var totalBytesRead = in.readNBytes(received, 0, received.length);

    assertEquals(
        totalBytesRead,
        message.length,
        "End-of-file must not be reached partway through the message");
    assertArrayEquals(
        message, received, "The received message should be identical to the sent message");
  }

  @Test
  public void testSendFromServer() throws IOException {
    byte[] message = "this is a message".getBytes(StandardCharsets.UTF_8);

    var out = serverSide.getOutputStream();
    out.write(message);
    out.flush();

    var in = clientSide.getInputStream();
    byte[] received = new byte[message.length];
    var totalBytesRead = in.readNBytes(received, 0, received.length);

    assertEquals(
        totalBytesRead,
        message.length,
        "End-of-file must not be reached partway through the message");
    assertArrayEquals(
        message, received, "The received message should be identical to the sent message");
  }

  @Test
  public void receiveWaits() throws IOException {
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      final byte[] message = "this is a message".getBytes(StandardCharsets.UTF_8);

      // Let's not make too much of a habit of testing with delay.
      executorService.schedule(
          () -> {
            try {
              var out = serverSide.getOutputStream();
              out.write(message);
              out.flush();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          },
          500,
          TimeUnit.MILLISECONDS);

      var in = clientSide.getInputStream();
      byte[] received = new byte[message.length];
      var totalBytesRead = in.readNBytes(received, 0, received.length);

      // Impossible to have the same message unless we waited for the result.
      assertEquals(
          totalBytesRead,
          message.length,
          "End-of-file must not be reached partway through the message");
      assertArrayEquals(
          message, received, "The received message should be identical to the sent message");
    }
  }
}
