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

/** Represents a duplex channel. */
public interface Channel {
  /**
   * Opens the channel.
   *
   * <p>Only when this method completes successfully is it possible to read and write to the
   * channel.
   *
   * @throws IOException If the channel could not be initialized.
   */
  void open() throws IOException;

  /**
   * Closes the channel.
   *
   * <p>This method may block until any pending reads or writes are complete. So to be safe, make
   * sure all readers and writers are cleaned up before calling this method.
   *
   * @throws IOException If any underlying component could not be closed.
   */
  void close() throws IOException;

  /**
   * Get a stream for reading from the channel.
   *
   * <p>The channel must have previously been opened. Only one thread should read from the channel
   * at any given time.
   *
   * @return The channel's output stream.
   */
  OutputStream getOutputStream();

  /**
   * Get a stream for writing to the channel.
   *
   * <p>The channel must have previously been opened. Only one thread should write to the channel at
   * any given time.
   *
   * @return The channel's input stream.
   */
  InputStream getInputStream();
}
