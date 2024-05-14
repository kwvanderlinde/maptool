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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import net.rptools.maptool.server.proto.AssetChunkDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An asset producer that doesn't have all the data available but needs it to come from somewhere
 * else.
 */
public class StreamingAssetProducer implements IAssetProducer {
  private static final Logger log = LogManager.getLogger(StreamingAssetProducer.class);

  private final AssetHeader header;
  private final InputStream in;
  private final byte[] buffer = new byte[8 * 1024];
  private long position = 0;

  public StreamingAssetProducer(AssetHeader header, InputStream in) {
    this.header = header;
    this.in = in;
  }

  @Override
  public AssetHeader getHeader() {
    return header;
  }

  @Override
  public AssetChunkDto nextChunk(int size) throws IOException {
    log.info("We've been asked for a chunk of size {}", size);

    long remaining = header.getSize() - position;
    if (size > remaining) {
      size = (int) remaining;
    }

    var bytesRead = in.read(buffer, 0, size);
    if (bytesRead < 0) {
      throw new EOFException(
          String.format("Unexpected end of stream; expected %s more bytes", remaining));
    }
    if (bytesRead == 0) {
      // No chunk this time.
      return null;
    }
    position += bytesRead;

    var data = ByteString.copyFrom(buffer, 0, bytesRead);
    return AssetChunkDto.newBuilder().setId(header.getId().toString()).setData(data).build();
  }

  @Override
  public boolean isComplete() {
    return position >= header.getSize();
  }
}
