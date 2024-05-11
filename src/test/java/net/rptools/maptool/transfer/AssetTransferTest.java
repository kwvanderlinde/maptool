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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.assets.LazyAsset;
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

    var entry = mock(LazyAsset.class);
    when(entry.getId()).thenReturn(new MD5Key("Testing"));
    when(entry.getName()).thenReturn("onetwo");
    when(entry.getType()).thenReturn(Asset.Type.TEXT);
    when(entry.getSizeInBytes()).thenReturn((long) data.length);
    when(entry.getInputStream()).then(invocation -> new ByteArrayInputStream(data));
    // AssetProducer should not require loading the asset in the fashion of load(), but should read
    // from the stream.
    when(entry.load()).thenThrow(new RuntimeException("Unexpected load"));

    // PRODUCER
    AssetProducer producer = new AssetProducer(entry);
    AssetHeader header = producer.getHeader();

    assertNotNull(header);
    assertEquals(data.length, header.getSize());
    assertFalse(producer.isComplete());

    // CONSUMER
    AssetConsumer consumer = new AssetConsumer(new File("."), header);

    assertFalse(consumer.isComplete());

    // TEST
    while (!producer.isComplete()) {
      AssetChunkDto chunk = producer.nextChunk(10);

      consumer.update(chunk);
    }

    // CHECK
    assertTrue(consumer.isComplete());
    assertTrue(consumer.getFilename().exists());
    assertEquals(header.getSize(), consumer.getFilename().length());

    int count = 0;
    int val;
    FileInputStream in = new FileInputStream(consumer.getFilename());
    while ((val = in.read()) != -1) {
      assertEquals(data[count], (byte) val);
      count++;
    }
    in.close();
    assertEquals(data.length, count);

    // CLEANUP
    consumer.getFilename().delete();
  }
}
