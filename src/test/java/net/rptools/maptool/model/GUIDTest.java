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
    var guid = new GUID("57CF2A35CF3441A28CD02BE5B9D6F56B");
    var dto = guid.toDto();

    assertEquals(
        0x57_CF_2A_35_CF_34_41_A2L, dto.getHigh(), "High bits must match the original GUID");
    assertEquals(0x8C_D0_2B_E5_B9_D6_F5_6BL, dto.getLow(), "Low bits must match the original GUID");

    var newGuid = GUID.fromDto(dto);

    assertEquals(guid, newGuid, "The reconstituted GUID must be the same as the original");
  }

  @Test
  void equalsSelf() {
    var guid = new GUID("5C6F833AF8804739AB6F52E2291951AC");

    assertTrue(guid.equals(guid), "The GUIDs should equal itself.");
  }

  @Test
  void equalsSuccess() {
    var guid1 = new GUID("66BDB6D4957F40A9B046911B6E1B4E9C");
    var guid2 = new GUID("66BDB6D4957F40A9B046911B6E1B4E9C");

    assertTrue(guid1.equals(guid2), "The GUIDs should be equal.");
  }

  @Test
  void equalsFailure() {
    var guid1 = new GUID("66BDB6D4957F40A9B046911B6E1B4E9C");
    var guid2 = new GUID("1948C3E4278F4961BD347BFCE105922F");

    assertFalse(guid1.equals(guid2), "The GUIDs should not be equal.");
  }

  @Test
  void equalGuidsHaveEqualHashCodes() {
    var guid1 = new GUID("EBE33C7570004309940A8351C6BA6188");
    var guid2 = new GUID("EBE33C7570004309940A8351C6BA6188");

    var hash1 = guid1.hashCode();
    var hash2 = guid2.hashCode();

    assertTrue(hash1 == hash2, "The GUID hashes should be equal.");
  }

  @Test
  void compareToSelf() {
    var guid = new GUID("8ED4E6212C4040559334F0228DE68814");

    assertTrue(guid.compareTo(guid) == 0, "The GUID should equal itself.");
  }

  @Test
  void compareToZero() {
    var guid1 = new GUID("ADF468A359CB4FE0A8574231B4BE0CCB");
    var guid2 = new GUID("ADF468A359CB4FE0A8574231B4BE0CCB");

    assertTrue(guid1.compareTo(guid2) == 0, "The GUIDs should be equal.");
  }

  @Test
  void compareToPositive() {
    var guid1 = new GUID("56A303480F654A95B6D53184277D4DD8");
    var guid2 = new GUID("4EB2378F7E4F4351B29B6678198EAA19");

    assertTrue(guid1.compareTo(guid2) > 0, "The GUIDs should be equal.");
  }

  @Test
  void compareToNegative() {
    var guid1 = new GUID("DA1AF31049524C4ABEDF17B7ED92FFF5");
    var guid2 = new GUID("432AFD8B8B6F4E6BBFD3E65AA7A0A755");

    assertTrue(guid1.compareTo(guid2) < 0, "The GUIDs should be equal.");
  }
}
