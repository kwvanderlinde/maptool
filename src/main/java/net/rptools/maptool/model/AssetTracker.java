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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.campaign.AssetsAdded;
import net.rptools.maptool.model.drawing.DrawablePaint;
import net.rptools.maptool.model.drawing.DrawableTexturePaint;

/**
 * Keeps track of which assets a campaign owns.
 *
 * <p>Unlike the {@link AssetManager}, this is not a global set of assets, nor does it understand
 * the asset cache. It merely holds loaded assets specific to a campaign. It will, however, ensure
 * that all of its assets are added to the {@link AssetManager}.
 *
 * <p>When adding new assets, an {@link AssetsAdded} event is emitted. This is handled elsewhere to
 * upload the assets to the server.
 *
 * <p>Eventually, this should act as the source of truth for which assets belong in the campaign.
 */
public class AssetTracker {
  /** Assets are identified by the MD5 hash of their raw data. */
  private static final Map<MD5Key, Asset> assetMap = new ConcurrentHashMap<>();

  /** The parent campaign if this tracker. Needed for robust event handling. */
  private final Campaign campaign;

  public AssetTracker(Campaign campaign) {
    this.campaign = campaign;
  }

  public boolean hasAsset(MD5Key key) {
    return assetMap.containsKey(key);
  }

  public @Nullable Asset getAsset(MD5Key key) {
    return assetMap.getOrDefault(key, null);
  }

  public void putAsset(@Nullable Asset asset) {
    if (asset != null) {
      putAllAssets(List.of(asset));
    }
  }

  /**
   * A convenience method for calling {@link #putAsset(Asset)} with the assets from a {@link
   * DrawablePaint}.
   *
   * @param paint The paint to pull assets from.
   */
  public void putAssetsFrom(@Nullable DrawablePaint paint) {
    if (paint == null) {
      return;
    }

    if (paint instanceof DrawableTexturePaint texturePaint) {
      putAsset(texturePaint.getAsset());
    }
  }

  public void putAllAssets(Collection<Asset> assets) {
    var newAssets = new ArrayList<Asset>();
    for (var asset : assets) {
      if (asset == null) {
        continue;
      }

      var previous = assetMap.putIfAbsent(asset.getMD5Key(), asset);
      if (previous != null) {
        // Asset is already known.
        continue;
      }

      // This is a new asset.
      // Make sure the global asset manager knows about it.
      // TODO If the asset manager does know about, use the asset manager's version (deduplicate)?
      if (!AssetManager.hasAsset(asset.getMD5Key())) {
        AssetManager.putAsset(asset);
      }

      newAssets.add(asset);
    }

    // TODO Handled in MapToolClient#onAssetAdded() for uploading to the server if needed. Can we
    //  tighten this up by not requiring the event bus?
    //  Honestly, I'd like to adopt reactive streams.
    new MapToolEventBus().getMainEventBus().post(new AssetsAdded(campaign, newAssets));
  }

  public void garbageCollect(Collection<MD5Key> liveAssets) {
    assetMap.keySet().retainAll(liveAssets);
  }
}
