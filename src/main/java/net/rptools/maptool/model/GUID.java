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
package net.rptools.maptool.model;

import java.io.Serializable;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/** Global unique identificator object. */
public class GUID implements Serializable, Comparable<GUID> {
  /** Serial version unique identifier. */
  private static final long serialVersionUID = 6361057925697403643L;

  /** GUIDs always have 16 bytes. */
  public static final int GUID_LENGTH = 16;

  // NOTE: THIS CAN NEVER BE CHANGED, OR IT WILL AFFECT ALL THINGS THAT PREVIOUSLY USED IT
  public static final int GUID_BUCKETS = 100;
  // NOTE: THIS CAN NEVER BE CHANGED, OR IT WILL AFFECT ALL THINGS THAT PREVIOUSLY USED IT

  private final byte[] baGUID;

  // Cache of the hashCode for a GUID
  private transient int hash;

  public GUID() {
    this.baGUID = generateGUID();
    validateGUID();
  }

  /**
   * Creates a new GUID based on the specified hexadecimal-code string.
   *
   * @param strGUID the guid as a hexadecimal-code string
   * @throws InvalidGUIDException if the GUID is invalid
   */
  public GUID(String strGUID) {
    if (strGUID == null) {
      throw new InvalidGUIDException("GUID is null");
    }

    try {
      this.baGUID = HexFormat.of().parseHex(strGUID);
    } catch (Exception e) {
      throw new InvalidGUIDException("Invalid format for GUID");
    }

    validateGUID();
  }

  /**
   * Ensures the GUID is legal.
   *
   * @throws InvalidGUIDException if the GUID is invalid
   */
  private void validateGUID() throws InvalidGUIDException {
    if (baGUID == null) throw new InvalidGUIDException("GUID is null");
    if (baGUID.length != GUID_LENGTH)
      throw new InvalidGUIDException("GUID length is invalid: " + baGUID.length);
  }

  /**
   * Returns the GUID representation of the {@link String} argument.
   *
   * @param s the guid as a hexadecimal-code string
   * @return a new GUID instance
   */
  public static GUID valueOf(String s) {
    if (s == null) return null;
    return new GUID(s);
  }

  /** Determines whether two GUIDs are equal. */
  @Override
  public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    Class<?> objClass = object.getClass();

    GUID guid;
    try {
      if (objClass == String.class) { // string
        guid = new GUID((String) object);
      } else { // try to cast to a GUID
        guid = (GUID) object;
      }
    } catch (ClassCastException e) { // not a GUID
      return false;
    }

    // Compare bytes.
    for (int i = 0; i < GUID_LENGTH; i++) {
      if (this.baGUID[i] != guid.baGUID[i]) return false;
    }

    // All tests pass.
    return true;
  }

  public byte[] getBytes() {
    return baGUID;
  }

  /** Returns a string for the GUID. */
  @Override
  public String toString() {
    return HexFormat.of().withUpperCase().formatHex(baGUID);
  }

  /**
   * Returns a hashcode for this GUID. This function is based on the algorithm that JDK 1.3 uses for
   * a String.
   *
   * @return a hash code value for this object.
   */
  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      byte val[] = baGUID;
      int len = GUID_LENGTH;

      for (int i = 0; i < len; i++) h = 31 * h + val[i];
      hash = h;
    }
    return h;
  }

  /**
   * Returns a new non-numeric GUID using UUID to generate a unique alphanumeric string
   *
   * @return a byte[]
   */
  public static byte[] generateGUID() throws InvalidGUIDException {
    String newGUID = UUID.randomUUID().toString().replaceAll("-", "");

    while (StringUtils.isNumeric(newGUID)) {
      newGUID = UUID.randomUUID().toString().replaceAll("-", "");
    }

    return new GUID(newGUID).getBytes();
  }

  /**
   * A fast check for whether a given argument is not a GUID.
   *
   * @param arg
   * @return whether arg is not GUID (returning false != isGUID)
   */
  public static boolean isNotGUID(String arg) {
    return arg.length() != GUID.GUID_LENGTH * 2;
  }

  public int compareTo(@NotNull GUID o) {
    if (o != this) {
      for (int i = 0; i < GUID_LENGTH; i++) {
        if (this.baGUID[i] != o.baGUID[i]) return this.baGUID[i] - o.baGUID[i];
      }
    }
    return 0;
  }
}
