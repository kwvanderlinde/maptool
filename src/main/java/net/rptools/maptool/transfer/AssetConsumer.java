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
package net.rptools.maptool.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.server.proto.AssetChunkDto;

/**
 * Receiving end of AssetProducer
 *
 * @author trevor
 */
public class AssetConsumer {
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(8 * 1024);
  private final AssetHeader header;

  /**
   * Create a new asset consumer, it will prepare a place to receive the incoming data chunks. When
   * complete the resulting file can be found at getFilename()
   *
   * @param header - from the corresponding AssetProducer
   */
  public AssetConsumer(AssetHeader header) {
    if (header == null) {
      throw new IllegalArgumentException("Header cannot be null");
    }
    this.header = header;
  }

  /**
   * @return the ID of the incoming asset
   */
  public MD5Key getId() {
    return header.getId();
  }

  public String getName() {
    return header.getName();
  }

  /**
   * Add the next chunk of data to this consumer
   *
   * @param chunk produced from the corresponding AssetProducer
   * @throws IOException if the file exists but is a directory rather than a regular file, does not
   *     exist but cannot be created, or cannot be opened for any other reason
   */
  public void update(AssetChunkDto chunk) throws IOException {
    byte[] data = chunk.getData().toByteArray();
    buffer.writeBytes(data);
  }

  /**
   * Whether all the data has been transferred
   *
   * @return true if all data been transferred
   */
  public boolean isComplete() {
    return buffer.size() >= header.getSize();
  }

  public double getPercentComplete() {
    return buffer.size() / (double) header.getSize();
  }

  public long getSize() {
    return header.getSize();
  }

  /**
   * When complete this will have all the data for the asset.
   *
   * @return The asset data
   */
  public byte[] getData() {
    return buffer.toByteArray();
  }
}
