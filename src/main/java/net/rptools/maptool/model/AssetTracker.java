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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.campaign.AssetAdded;

/**
 * Keeps track of which assets a campaign owns.
 *
 * <p>When adding new assets, the assets are pushed to the server (unless we are running on the
 * server).
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

  public void putAsset(Asset asset) {
    var previous = assetMap.putIfAbsent(asset.getMD5Key(), asset);
    if (previous == null) {
      // Make sure the global asset manager knows about it.
      if (!AssetManager.hasAsset(asset.getMD5Key())) {
        AssetManager.putAsset(asset);
      }

      // TODO Handled in MapToolClient#onAssetAdded() for uploading to the server if needed. Can we
      //  tighten this up by not requiring the event bus?
      //  Honestly, I'd like to adopt reactive streams.
      new MapToolEventBus().getMainEventBus().post(new AssetAdded(campaign, asset));
    }
  }

  public void garbageCollect(Collection<MD5Key> liveAssets) {
    assetMap.keySet().retainAll(liveAssets);
  }
}
