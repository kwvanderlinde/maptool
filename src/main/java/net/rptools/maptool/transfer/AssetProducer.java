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
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import net.rptools.maptool.model.assets.LazyAsset;
import net.rptools.maptool.server.proto.AssetChunkDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates data chunks for transferring binary data. Assumes large datasets (otherwise it would be a
 * direct transfer) so expects the data to be streamed from a file
 *
 * @author trevor
 */
public class AssetProducer implements Closeable {
  private static final Logger log = LogManager.getLogger(AssetProducer.class);

  private final LazyAsset asset;

  private long remaining;
  private InputStream in = null;
  private long startTimeMs;

  public AssetProducer(LazyAsset asset) {
    this.asset = asset;
    this.remaining = asset.header().getSize();
  }

  @Override
  public void close() throws IOException {
    if (in != null) {
      in.close();
    }
  }

  /**
   * @return the header needed to create the corresponding AssetConsumer
   */
  public AssetHeader getHeader() {
    return new AssetHeader(
        asset.header().getId(), asset.header().getName(), asset.header().getSize());
  }

  /**
   * Get the next chunk of data
   *
   * @param size how many bytes to grab, may end up being less if there isn't enough data
   * @throws IOException if an I/O error occurs or current position in the file is wrong
   * @return an {@link AssetChunkDto} with the next chunk of data
   */
  public AssetChunkDto nextChunk(int size) throws IOException {
    log.info("We've been asked for a chunk of size {}", size);

    if (in == null) {
      startTimeMs = System.nanoTime();
      in = new ByteArrayInputStream(asset.loader().load().getData());
    }

    if (remaining < size) {
      size = (int) remaining;
    }
    byte[] data = new byte[size];

    var read = in.readNBytes(data, 0, size);
    if (read < size) {
      throw new EOFException("Unexpected end of file.");
    }
    remaining -= read;

    if (remaining <= 0) {
      final var endTimeMs = System.nanoTime();
      log.warn(
          "AssetProducer[{}] time: {} ms",
          asset.header().getId(),
          (endTimeMs - startTimeMs) / 1_000_000.);
    }

    return AssetChunkDto.newBuilder()
        .setId(asset.header().getId().toString())
        .setData(ByteString.copyFrom(data))
        .build();
  }

  /**
   * Whether all the data has been transferred
   *
   * @return true if all data been transferred
   */
  public boolean isComplete() {
    return remaining <= 0;
  }
}
