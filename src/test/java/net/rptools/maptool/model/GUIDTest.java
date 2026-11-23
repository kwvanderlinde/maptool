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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class GUIDTest {
  @Test
  void testDto() {
    var guid = new GUID(0x57CF2A35CF3441A2L, 0x8CD02BE5B9D6F56BL);
    var dto = guid.toDto();

    assertEquals(
        0x57_CF_2A_35_CF_34_41_A2L, dto.getHigh(), "High bits must match the original GUID");
    assertEquals(0x8C_D0_2B_E5_B9_D6_F5_6BL, dto.getLow(), "Low bits must match the original GUID");

    var newGuid = GUID.fromDto(dto);

    assertEquals(guid, newGuid, "The reconstituted GUID must be the same as the original");
  }

  @Test
  void testStringParse() {
    var guidFromString = new GUID("A663192153894D92AD2CBEB84F7698FD");
    var guidFromBits = new GUID(0xA663192153894D92L, 0xAD2CBEB84F7698FDL);

    assertTrue(
        guidFromString.equals(guidFromBits),
        "Parsed GUID should be the same as when built from high/low bits");
  }

  @Test
  void equalsSelf() {
    var guid = new GUID(0x5C6F833AF8804739L, 0xAB6F52E2291951ACL);

    assertTrue(guid.equals(guid), "The GUIDs should equal itself.");
  }

  @Test
  void equalsSuccess() {
    var guid1 = new GUID(0x66BDB6D4957F40A9L, 0xB046911B6E1B4E9CL);
    var guid2 = new GUID(0x66BDB6D4957F40A9L, 0xB046911B6E1B4E9CL);

    assertTrue(guid1.equals(guid2), "The GUIDs should be equal.");
  }

  @Test
  void equalsFailure() {
    var guid1 = new GUID(0x66BDB6D4957F40A9L, 0xB046911B6E1B4E9CL);
    var guid2 = new GUID(0x1948C3E4278F4961L, 0xBD347BFCE105922FL);

    assertFalse(guid1.equals(guid2), "The GUIDs should not be equal.");
  }

  @Test
  void equalGuidsHaveEqualHashCodes() {
    var guid1 = new GUID(0xEBE33C7570004309L, 0x940A8351C6BA6188L);
    var guid2 = new GUID(0xEBE33C7570004309L, 0x940A8351C6BA6188L);

    var hash1 = guid1.hashCode();
    var hash2 = guid2.hashCode();

    assertTrue(hash1 == hash2, "The GUID hashes should be equal.");
  }

  @Test
  void compareToSelf() {
    var guid = new GUID(0x8ED4E6212C404055L, 0x9334F0228DE68814L);

    assertTrue(guid.compareTo(guid) == 0, "The GUID should equal itself.");
  }

  @Test
  void compareToZero() {
    var guid1 = new GUID(0xADF468A359CB4FE0L, 0xA8574231B4BE0CCBL);
    var guid2 = new GUID(0xADF468A359CB4FE0L, 0xA8574231B4BE0CCBL);

    assertTrue(guid1.compareTo(guid2) == 0, "The GUIDs should be equal.");
  }

  @Test
  void compareToPositive() {
    var guid1 = new GUID(0x56A303480F654A95L, 0xB6D53184277D4DD8L);
    var guid2 = new GUID(0x4EB2378F7E4F4351L, 0xB29B6678198EAA19L);

    assertTrue(guid1.compareTo(guid2) > 0, "The GUIDs should be equal.");
  }

  @Test
  void compareToNegative() {
    var guid1 = new GUID(0xDA1AF31049524C4AL, 0xBEDF17B7ED92FFF5L);
    var guid2 = new GUID(0x432AFD8B8B6F4E6BL, 0xBFD3E65AA7A0A755L);

    assertTrue(guid1.compareTo(guid2) < 0, "The GUIDs should be equal.");
  }
}
