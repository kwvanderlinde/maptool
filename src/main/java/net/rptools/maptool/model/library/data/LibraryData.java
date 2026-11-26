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
package net.rptools.maptool.model.library.data;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.rptools.maptool.model.gamedata.data.DataValue;

/** Interface for a library data object. */
public interface LibraryData {

  /**
   * Returns the set of keys in the library.
   *
   * @return the set of keys in the library
   */
  CompletableFuture<Set<String>> getAllKeys();

  /**
   * Returns the data value of the specified key.
   *
   * @param key the key to get the data value of
   * @return the data value of the specified key
   */
  CompletableFuture<DataValue> getValue(String key);

  /**
   * Sets the data value of the specified key.
   *
   * @param value the data value to set.
   * @return a future that completes when the data value is set
   */
  CompletableFuture<Void> setData(DataValue value);

  /**
   * Returns the static data value of the specified path.
   *
   * @param path the path of the static data to get.
   * @return the static data value of the specified path.
   */
  DataValue getStaticData(String path);

  /**
   * Returns the public static data value of the specified path.
   *
   * @param path the path of the public static data to get.
   * @return the public static data value of the specified path.
   */
  DataValue getPublicStaticData(String path);
}
