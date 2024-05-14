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

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.assets.AssetCache;
import net.rptools.maptool.model.assets.LazyAsset;

// TODO What name should this be? It is restricted only to those things in the campaign, but is
//  backed by caches.
public class CampaignAssetManager implements IAssetManager {
  private final AssetCache cache;
  private final ConcurrentMap<MD5Key, LazyAsset> knownAssets = new ConcurrentHashMap<>();

  public CampaignAssetManager(AssetCache cache) {
    this.cache = cache;
  }

  @Override
  public void add(MD5Key id, Supplier<Optional<LazyAsset>> supplier) {
    // We assume supplier is slow (e.g., reading from PackedFile) so prefer cache.
    // Note: if the value is null, no entry is added.
    knownAssets.computeIfAbsent(id, id2 -> cache.get(id).or(supplier).orElse(null));
  }

  public Set<MD5Key> getAllKeys() {
    return knownAssets.keySet();
  }

  @Override
  public boolean hasAsset(MD5Key key) {
    return knownAssets.containsKey(key);
  }

  @Override
  public Optional<Asset> getAsset(MD5Key key) {
    var known = knownAssets.get(key);
    if (known == null) {
      return Optional.empty();
    }

    Asset asset;
    try {
      asset = known.loader().load();
    } catch (IOException e) {
      return Optional.empty();
    }

    return Optional.of(asset);
  }

  @Override
  public void putAsset(Asset asset) {
    cache.add(asset);
    knownAssets.put(asset.getMD5Key(), LazyAsset.of(asset));
  }
}
