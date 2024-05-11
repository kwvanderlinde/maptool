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
package net.rptools.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the MD5 key for a certain set of data. Can be used in maps as keys.
 *
 * <p>This class is thread safe if a couple of simple rules are followed.
 *
 * <ul>
 *   <li>If creating the instance with {@link #MD5Key(byte[])} the passed in array must not be
 *       modified in a separate thread until the constructor returns
 *   <li>If creating the instance with {@link #MD5Key(InputStream)} the input stream must not be
 *       used by another thread.
 * </ul>
 *
 * If either of the above two rules are violated then the state of {@link MD5Key} will be
 * inconsistent for the same data.
 */
@SuppressWarnings("serial")
public final class MD5Key implements Serializable {
  private static final Logger log = LogManager.getLogger(MD5Key.class);

  /** The {@link MessageDigest} used for calculation of the md5 sum. */
  private static final ThreadLocal<MessageDigest> md5Digest =
      ThreadLocal.withInitial(
          () -> {
            try {
              return MessageDigest.getInstance("md5");
            } catch (NoSuchAlgorithmException e) {
              // Shouldn't happen, but if it does let it bubble up as its really bad mojo if it does
              // happen
              throw new AssertionError(e);
            }
          });

  /** The {@code String} representation of the MD5key. */
  private final String id;

  /**
   * Creates a new {@code MD5Key} using the value in the {@code String} as the id.
   *
   * @param id the id of the key.
   */
  public MD5Key(String id) {
    this.id = id;
  }

  /**
   * Creates an {@code MD5Key} representing the supplied data.
   *
   * @param data The data to perform an md5 sum over.
   * @note Do not modify the data in the passed in array in another thread before this constructor
   *     completes, doing so will result in an inconsistent state for the sale input data.
   */
  public MD5Key(byte[] data) {
    id = encodeToHex(digestData(data));
  }

  /**
   * Creates an {@code MD5Key} representing the supplied data.
   *
   * @param data The data to perform an md5 sum over.
   * @throws IOException if an error occurs reading the data.
   * @note Do not use the {@link InputStream} provided in another thread (why would you do that in
   *     any case?) before this constructor completes, doing so will result in an inconsistent state
   *     for the sale input data.
   */
  public MD5Key(InputStream data) throws IOException {
    id = encodeToHex(digestData(data));
  }

  /**
   * Returns the {@code String} representation of this {@code MD5Key}. This method is guaranteed to
   * return a format that can be understood by the {@link #MD5Key(String)} constructor.
   *
   * @return the {@code String} representation of the {@code MD5Key}.
   */
  public String toString() {
    return id;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MD5Key)) {
      return false;
    }

    return id.equals(((MD5Key) obj).id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Returns the md5 sum of the provided data.
   *
   * @param data The data to calculate the md5 sum of.
   * @return the md5 sum of the data.
   */
  private static byte[] digestData(byte[] data) {
    MessageDigest digest = md5Digest.get();
    digest.reset();
    digest.update(data);

    log.info("Digested {} bytes from array", data.length);

    if (data.length > 1_300_000) {
      log.info("That's a lot!");
    }

    return digest.digest();
  }

  /**
   * Returns the md5 sum of the data provided by the {@link InputStream}.
   *
   * @param is The {@code InputStream} providing the data to calculate the md5 sum of.
   * @return the md5 sum of the data from the {@link InputStream}.
   */
  private static byte[] digestData(InputStream is) throws IOException {
    MessageDigest digest = md5Digest.get();
    digest.reset();

    var buffer = new byte[8 * 1024];
    int count = 0;
    while (true) {
      var bytesRead = is.read(buffer, 0, buffer.length);
      if (bytesRead < 0) {
        break;
      }

      digest.update(buffer, 0, bytesRead);
      count += bytesRead;
    }
    log.info("Digested {} bytes from stream", count);

    return digest.digest();
  }

  /**
   * Encode a byte array into a hexadecimal string.
   *
   * @param data the byte array to encode.
   * @return a {@code String} containing the encoded hexadecimal value of the passed in data.
   */
  private static String encodeToHex(byte[] data) {
    StringBuilder strbuild = new StringBuilder();
    for (byte datum : data) {
      String hex = Integer.toHexString(datum);
      if (hex.length() < 2) {
        strbuild.append("0");
      }
      if (hex.length() > 2) {
        hex = hex.substring(hex.length() - 2);
      }
      strbuild.append(hex);
    }
    return strbuild.toString();
  }
}
