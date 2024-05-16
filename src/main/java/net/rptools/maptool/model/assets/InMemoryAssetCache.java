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
package net.rptools.maptool.model.assets;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;

public class InMemoryAssetCache implements AssetCache {
  private final ConcurrentMap<MD5Key, Asset> assetMap = new ConcurrentHashMap<>();

  @Override
  public boolean has(MD5Key id) {
    return assetMap.containsKey(id);
  }

  @Override
  public Optional<LazyAsset> get(MD5Key id) {
    var asset = assetMap.get(id);
    return Optional.ofNullable(asset).map(LazyAsset::new);
  }

  @Override
  public void add(Asset asset) {
    assetMap.putIfAbsent(asset.getMD5Key(), asset);
  }

  @Override
  public void remove(MD5Key id) {
    assetMap.remove(id);
  }
}
