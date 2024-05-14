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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.server.proto.AssetChunkDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetTransferTest {

  @Test
  @DisplayName("Test Basic Transfer.")
  void testBasicTransfer() throws Exception {

    byte[] data = new byte[1024];
    for (int i = 0; i < 1024; i++) {
      data[i] = (byte) i;
    }

    var asset = mock(Asset.class);
    when(asset.getMD5Key()).thenReturn(new MD5Key("Testing"));
    when(asset.getName()).thenReturn("onetwo");
    when(asset.getType()).thenReturn(Asset.Type.TEXT);
    when(asset.getExtension()).thenReturn(Asset.Type.TEXT.getDefaultExtension());
    when(asset.getData()).thenReturn(data);

    // PRODUCER
    AssetProducer producer = new AssetProducer(asset);
    AssetHeader header = producer.getHeader();

    assertNotNull(header);
    assertEquals(data.length, header.getSize());
    assertFalse(producer.isComplete());

    // CONSUMER
    AssetConsumer consumer = new AssetConsumer(header);

    assertFalse(consumer.isComplete());

    // TEST
    while (!producer.isComplete()) {
      AssetChunkDto chunk = producer.nextChunk(10);
      consumer.update(chunk);
    }

    // CHECK
    assertTrue(consumer.isComplete());

    var dataOut = consumer.getData();
    assertEquals(header.getSize(), dataOut.length);
    assertArrayEquals(data, dataOut);
  }
}
