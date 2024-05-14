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

import com.google.protobuf.ByteString;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.server.proto.AssetChunkDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates data chunks for transferring binary data. Assumes large datasets (otherwise it would be a
 * direct transfer) so expects the data to be streamed from a file
 *
 * @author trevor
 */
public class AssetProducer implements IAssetProducer {
  private static final Logger log = LogManager.getLogger(AssetProducer.class);

  private final AssetHeader header;
  private final byte[] data;

  private int position;

  public AssetProducer(Asset asset) {
    this.data = asset.getData();
    this.header =
        new AssetHeader(asset.getMD5Key(), asset.getName(), this.data.length, asset.getType());
    this.position = 0;
  }

  /**
   * @return the header needed to create the corresponding AssetConsumer
   */
  @Override
  public AssetHeader getHeader() {
    return header;
  }

  /**
   * Get the next chunk of data
   *
   * @param size how many bytes to grab, may end up being less if there isn't enough data
   * @return an {@link AssetChunkDto} with the next chunk of data
   */
  @Override
  public AssetChunkDto nextChunk(int size) {
    log.info("We've been asked for a chunk of size {}", size);

    size = Math.min(size, data.length - position);
    var data = ByteString.copyFrom(this.data, position, size);
    position += size;

    return AssetChunkDto.newBuilder().setId(header.getId().toString()).setData(data).build();
  }

  /**
   * Whether all the data has been transferred
   *
   * @return true if all data been transferred
   */
  @Override
  public boolean isComplete() {
    return position >= data.length;
  }
}
