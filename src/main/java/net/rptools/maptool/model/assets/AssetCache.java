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
import java.util.Optional;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface AssetCache {
  boolean has(MD5Key id);

  Optional<LazyAsset> get(MD5Key id);

  void add(Asset asset);

  void remove(MD5Key id);

  final class VerifyingLoader implements LazyAsset.Loader {
    private static final Logger log = LogManager.getLogger(VerifyingLoader.class);

    private final AssetCache cache;
    private final MD5Key expected;
    private final LazyAsset.Loader decorated;

    public VerifyingLoader(AssetCache cache, MD5Key expected, LazyAsset.Loader decorated) {
      this.cache = cache;
      this.expected = expected;
      this.decorated = decorated;
    }

    @Override
    public Asset load() throws IOException {
      var asset = decorated.load();
      if (!expected.equals(asset.getMD5Key())) {
        log.error(
            "Failed to validate asset; expected key {} but got {}. Removing from cache.",
            expected,
            asset.getMD5Key());
        cache.remove(expected);
        throw new IOException(String.format("Failed to validate asset %s", expected));
      }
      return asset;
    }
  }
}
