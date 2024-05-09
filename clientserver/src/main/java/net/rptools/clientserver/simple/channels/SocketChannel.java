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
package net.rptools.clientserver.simple.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import javax.annotation.Nullable;

public class SocketChannel implements Channel {
  private java.nio.channels.SocketChannel socket;
  private final @Nullable SocketAddress addressToConnectTo;
  private InputStream inputStream;
  private OutputStream outputStream;

  public SocketChannel(String hostName, int port) {
    this.addressToConnectTo = new InetSocketAddress(hostName, port);
  }

  public SocketChannel(java.nio.channels.SocketChannel socket) {
    this.socket = socket;
    this.addressToConnectTo = null;
  }

  @Override
  public void open() throws IOException {
    if (addressToConnectTo != null) {
      socket = java.nio.channels.SocketChannel.open();
      socket.configureBlocking(true);
      socket.connect(addressToConnectTo);
    }

    outputStream = Channels.newOutputStream(socket);
    inputStream = Channels.newInputStream(socket);
  }

  @Override
  public void close() throws IOException {
    this.socket.close();
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }
}
