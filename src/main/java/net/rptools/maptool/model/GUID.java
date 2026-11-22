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

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.rptools.maptool.server.proto.GuidDto;
import org.apache.commons.lang3.StringUtils;

/** Global unique identificator object. */
public class GUID implements Serializable, Comparable<GUID> {
  /** Serial version unique identifier. */
  private static final long serialVersionUID = 6361057925697403643L;

  /** GUIDs always have 16 bytes. */
  private static final int GUID_LENGTH = 16;

  private final byte[] baGUID;

  // Cache of the hashCode for a GUID
  private transient int hash;

  private GUID(long high, long low) {
    baGUID = new byte[GUID_LENGTH];
    var buffer = ByteBuffer.wrap(baGUID);
    buffer.putLong(high).putLong(low);
  }

  private GUID(UUID uuid) {
    this(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
  }

  public GUID() {
    this(generateUUID());
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
    if (!(object instanceof GUID guid)) {
      return false;
    }

    // Compare bytes.
    return Arrays.equals(this.baGUID, guid.baGUID);
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
    if (hash == 0) {
      hash = Arrays.hashCode(baGUID);
    }
    return hash;
  }

  /**
   * Returns a new non-numeric GUID using UUID to generate a unique alphanumeric string
   *
   * @return a byte[]
   */
  private static UUID generateUUID() {
    UUID uuid;
    String idString;
    do {
      uuid = UUID.randomUUID();
      idString = uuid.toString().replace("-", "");
    } while (StringUtils.isNumeric(idString));

    return uuid;
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

  @Override
  public int compareTo(@Nonnull GUID o) {
    return Arrays.compare(this.baGUID, o.baGUID);
  }

  public GuidDto toDto() {
    var buffer = ByteBuffer.wrap(baGUID);
    long high = buffer.getLong();
    long low = buffer.getLong();
    return GuidDto.newBuilder().setHigh(high).setLow(low).build();
  }

  public static GUID fromDto(GuidDto dto) {
    return new GUID(dto.getHigh(), dto.getLow());
  }
}
