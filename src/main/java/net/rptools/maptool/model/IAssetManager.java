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

import java.util.Optional;
import java.util.function.Supplier;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.assets.LazyAsset;

public interface IAssetManager {
  void add(MD5Key id, Supplier<LazyAsset> supplier);

  boolean hasAsset(MD5Key key);

  Optional<Asset> getAsset(MD5Key key);

  void putAsset(Asset asset);
}
