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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;

/**
 * Layers multiple caches on top of each other.
 *
 * <p>When adding an asset, the asset is added to all caches, starting with the fast and finishing
 * with the slow. When reading an asset, the fastest caches is checked first, then the next fastest,
 * etc.
 */
public class LayeredAssetCache implements AssetCache {
  private final List<AssetCache> caches;

  /**
   * Create a layered cache.
   *
   * @param caches The caches to layer. Put fast caches before slow caches.
   */
  public LayeredAssetCache(AssetCache... caches) {
    this.caches = Arrays.asList(caches);
  }

  @Override
  public boolean has(MD5Key id) {
    return caches.stream().anyMatch(cache -> cache.has(id));
  }

  @Override
  public Optional<LazyAsset> get(MD5Key id) {
    for (var i = 0; i < caches.size(); ++i) {
      var cache = caches.get(i);
      final var entry = cache.get(id);
      if (entry.isPresent()) {
        return Optional.of(
            new LazyAsset(
                entry.get().header(),
                new WriteUpLoader(entry.get().loader(), caches.subList(0, i).reversed())));
      }
    }

    return Optional.empty();
  }

  @Override
  public void add(Asset asset) {
    caches.forEach(cache -> cache.add(asset));
  }

  @Override
  public void remove(MD5Key id) {
    caches.forEach(cache -> cache.remove(id));
  }

  private static final class WriteUpLoader implements LazyAsset.Loader {
    private final LazyAsset.Loader decorated;
    private final List<AssetCache> cachesToUpdate;

    public WriteUpLoader(LazyAsset.Loader decorated, List<AssetCache> cachesToUpdate) {
      this.decorated = decorated;
      this.cachesToUpdate = cachesToUpdate;
    }

    @Override
    public Asset load() throws IOException {
      var asset = decorated.load();

      for (var cache : cachesToUpdate) {
        cache.add(asset);
      }

      return asset;
    }
  }
}
