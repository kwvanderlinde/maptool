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
package net.rptools.clientserver.decomposed;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public abstract class AbstractConnection implements Connection {
  // TODO Add channel management here as well?

  // NB: Adding and removing observers is a rare operation, meaning two things:
  // 1. This list will not be very large in practice.
  // 2. Even if it is large enough for iteration to be noticeable, it still won't matter.
  private final List<ConnectionObserver> observers = new ArrayList<>();
  private final String id;

  protected AbstractConnection(String id) {
    this.id = id;
  }

  @Nonnull
  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public void addObserver(ConnectionObserver observer) {
    observers.add(observer);
  }

  @Override
  public void removeObserver(ConnectionObserver observer) {
    observers.removeIf(element -> element == observer);
  }

  protected void onMessageReceived(byte[] message) {
    observers.forEach(observer -> observer.onMessageReceived(this, message));
  }

  protected void onDisconnected(String reason) {
    observers.forEach(observer -> observer.onDisconnected(this, reason));
  }

  protected void onActivity(
      ConnectionObserver.Direction direction,
      ConnectionObserver.State state,
      int totalTransferSize,
      int currentTransferSize) {
    observers.forEach(
        observer ->
            observer.onActivity(this, direction, state, totalTransferSize, currentTransferSize));
  }

  /**
   * Writes a message to the output stream using a simple protocol.
   *
   * <p>The protocol is that the message length is written in network byte order (big endian), then
   * the bytes of the message are written verbatim.
   *
   * <p>Note: if the message is to be compressed or otherwise transformed, that should already have
   * been done.
   *
   * @param out The output stream to send the message through.
   * @param message The serialized message to send.
   * @throws IOException
   */
  protected void writeMessage(OutputStream out, byte[] message) throws IOException {
    final var CHUNK_SIZE = 4 * 1024;
    final var length = message.length;

    onActivity(ConnectionObserver.Direction.Outbound, ConnectionObserver.State.Start, length, 0);

    // NB: If you're confused about this, remember the parameter should actually be a byte and will
    // will be masked to the lowest byte.
    out.write(length >> 24);
    out.write(length >> 16);
    out.write(length >> 8);
    out.write(length);

    // Write the message out in chunks. More efficient than writing out byte-by-byte and checks
    // whether we hit the chunk limit for reporting.
    int i = 0;
    while (i <= length - CHUNK_SIZE) {
      out.write(message, i, CHUNK_SIZE);
      i += CHUNK_SIZE;

      onActivity(
          ConnectionObserver.Direction.Outbound, ConnectionObserver.State.Progress, length, i);
    }
    if (i < length) {
      out.write(message, i, length - i);

      onActivity(
          ConnectionObserver.Direction.Outbound, ConnectionObserver.State.Progress, length, length);
    }
    out.flush();

    onActivity(
        ConnectionObserver.Direction.Outbound, ConnectionObserver.State.Complete, length, length);
  }
}
