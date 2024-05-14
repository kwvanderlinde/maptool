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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import net.rptools.lib.FileUtil;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.assets.AssetCache;
import net.rptools.maptool.model.assets.AssetLinkCache;
import net.rptools.maptool.model.assets.InMemoryAssetCache;
import net.rptools.maptool.model.assets.LayeredAssetCache;
import net.rptools.maptool.model.assets.LazyAsset;
import net.rptools.maptool.model.assets.PersistentAssetCache;
import net.rptools.maptool.transfer.AssetHeader;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles the caching, loading, and downloading of assets. All assets are loaded through
 * this class.
 *
 * @author RPTools Team
 */
/*
 * TODO Support lazy asset loading. Rather, enforce it where applicable. The caches are already
 *  capable, but it should be the default behaviour that we can pass asset references around without
 *  having to load the data.
 */
public class AssetManager {

  /** {@link MD5Key} to use for assets trying to specify a location outside of asset cache. */
  public static final MD5Key BAD_ASSET_LOCATION_KEY = new MD5Key("bad-location");

  /** {@link Asset}s that are required and should never be removed. */
  private static final Set<MD5Key> REQUIRED_ASSETS = Set.of(BAD_ASSET_LOCATION_KEY);

  private static final Logger log = LogManager.getLogger(AssetManager.class);

  /** Location of the cache on the filesystem */
  private static final File cacheDir;

  private static final AssetCache inMemoryCache;
  private static final AssetCache persistentAssetCache;
  private static final AssetLinkCache linkCache;
  private static final AssetCache layeredCache;

  /** For assets coming to the server from clients, and which other clients may depend on. */
  private static Map<MD5Key, AssetHeader> pendingAssets = new ConcurrentHashMap<>();

  /**
   * A list of listeners which should be notified when the asset associated with a given MD5 sum has
   * finished downloading.
   */
  private static Map<MD5Key, List<AssetAvailableListener>> assetListenerListMap =
      new ConcurrentHashMap<>();

  /** Used to load assets from storage */
  private static AssetLoader assetLoader = new AssetLoader();

  private static ExecutorService assetLoaderThreadPool = Executors.newFixedThreadPool(1);

  static {
    inMemoryCache = new InMemoryAssetCache();

    // TODO Make sure cacheDir is absolute.
    cacheDir = AppUtil.getAppHome("assetcache");
    persistentAssetCache = new PersistentAssetCache(cacheDir.toPath());
    linkCache = new AssetLinkCache(cacheDir.toPath());
    // Ordered fastest to slowest.
    layeredCache = new LayeredAssetCache(inMemoryCache, persistentAssetCache, linkCache);
  }

  public static boolean pushPendingHeader(AssetHeader assetHeader) {
    if (layeredCache.has(assetHeader.getId())) {
      return false;
    }

    var previous = pendingAssets.putIfAbsent(assetHeader.getId(), assetHeader);
    return previous == null;
  }

  public static @Nullable AssetHeader getPendingHeader(MD5Key id) {
    return pendingAssets.get(id);
  }

  public static AssetCache getCache() {
    return layeredCache;
  }

  /**
   * Brute force clear asset cache... TODO: Create preferences and filter to clear cache
   * automatically by age of asset
   *
   * @author Jamz
   * @since 1.4.0.1
   */
  public static void clearCache() {
    try {
      if (cacheDir != null) {
        FileUtils.cleanDirectory(cacheDir);
      }
    } catch (IOException e) {
      log.error("Failed to clear the asset cache.", e);
    }
  }

  /**
   * Remove all existing repositories and load all the repositories from the currently loaded
   * campaign.
   */
  public static void updateRepositoryList() {
    List<String> invalidRepos = new ArrayList<>();
    assetLoader.removeAllRepositories();
    for (String repo : MapTool.getCampaign().getRemoteRepositoryList()) {
      if (!assetLoader.addRepository(repo)) {
        invalidRepos.add(repo);
      }
    }

    if (!invalidRepos.isEmpty()) {
      if (MapTool.isHostingServer()) {
        String tab = "    ";
        String repos = tab + String.join("\n" + tab, invalidRepos);
        MapTool.showError(I18N.getText("msg.error.host.inaccessibleRepo", repos));
      } else {
        invalidRepos.forEach(
            repo -> MapTool.addLocalMessage(I18N.getText("msg.error.inaccessibleRepo", repo)));
      }
    }
  }

  /**
   * Determine if the asset is currently being requested. While an asset is being loaded it will be
   * marked as requested and this function will return true. Once the asset is done loading this
   * function will return false and the asset will be available from the cache.
   *
   * @param key MD5Key of asset being requested
   * @return True if asset is currently being requested, false otherwise
   */
  public static boolean isAssetRequested(MD5Key key) {
    return assetLoader.isIdRequested(key);
  }

  /**
   * Register a listener with the asset manager. The listener will be notified when the asset is
   * done loading.
   *
   * @param key MD5Key of the asset
   * @param listeners Listener to notify when the asset is done loading
   */
  public static void addAssetListener(MD5Key key, AssetAvailableListener... listeners) {

    if (listeners == null || listeners.length == 0) {
      return;
    }

    List<AssetAvailableListener> listenerList =
        assetListenerListMap.computeIfAbsent(key, k -> new LinkedList<AssetAvailableListener>());

    for (AssetAvailableListener listener : listeners) {
      if (!listenerList.contains(listener)) {
        listenerList.add(listener);
      }
    }
  }

  public static void removeAssetListener(MD5Key key, AssetAvailableListener... listeners) {

    if (listeners == null || listeners.length == 0) {
      return;
    }

    List<AssetAvailableListener> listenerList = assetListenerListMap.get(key);
    if (listenerList == null) {
      // Nothing to do
      return;
    }

    for (AssetAvailableListener listener : listeners) {
      listenerList.remove(listener);
    }
  }

  /**
   * Determine if the asset manager has the asset. This does not tell you if the asset is done
   * downloading.
   *
   * @param asset Asset to look for
   * @return True if the asset exists, false otherwise
   */
  public static boolean hasAsset(Asset asset) {
    return hasAsset(asset.getMD5Key());
  }

  /**
   * Determine if the asset manager has the asset. This does not tell you if the asset is done
   * downloading.
   *
   * @param key the key
   * @return true if the asset manager has the key
   */
  public static boolean hasAsset(MD5Key key) {
    return layeredCache.has(key);
  }

  /**
   * Add the asset to the asset cache. Listeners for this asset are notified.
   *
   * @param asset Asset to add to cache
   */
  public static void putAsset(Asset asset) {

    if (asset == null || asset.getMD5Key().equals(BAD_ASSET_LOCATION_KEY)) {
      return;
    }

    try {
      if (sanitizeAssetId(asset.getMD5Key()) != asset.getMD5Key()) {
        // If a different asset is returned we know this asset is invalid so dont add it
        return;
      }
    } catch (IOException e) {
      if (!asset.getMD5Key().equals(BAD_ASSET_LOCATION_KEY)) {
        log.error(I18N.getText("msg.error.errorResolvingCacheDir", asset.getMD5Key(), e));
      }
    }

    layeredCache.add(asset);

    // Clear the waiting status
    assetLoader.completeRequest(asset.getMD5Key());

    // Listeners
    List<AssetAvailableListener> listenerList = assetListenerListMap.get(asset.getMD5Key());
    if (listenerList != null) {
      for (AssetAvailableListener listener : listenerList) {
        listener.assetAvailable(asset.getMD5Key());
      }

      assetListenerListMap.remove(asset.getMD5Key());
    }
  }

  /**
   * Similar to getAsset(), but does not block. It will always use the listeners to pass the data
   *
   * @param id MD5 of the asset requested
   * @param listeners instances of {@link AssetAvailableListener} that will be notified when the
   *     asset is available
   */
  public static void getAssetAsynchronously(
      final MD5Key id, final AssetAvailableListener... listeners) {

    assetLoaderThreadPool.submit(
        () -> {
          Asset asset = getAsset(id);

          // Simplest case, we already have it
          if (asset != null && asset.getData() != null && asset.getData().length > 0) {
            for (AssetAvailableListener listener : listeners) {
              listener.assetAvailable(id);
            }

            return;
          }

          // Let's get it from the server
          // As a last resort we request the asset from the server
          if (!isAssetRequested(id)) {
            requestAssetFromServer(id, listeners);
          }
        });
  }

  /**
   * Get the asset from the cache. If the asset is not currently available, will return null. Does
   * not request the asset from the server
   *
   * @param id MD5 of the asset requested
   * @return Asset object for the MD5 sum
   */
  public static Asset getAsset(MD5Key id) {
    if (id == null) {
      return null;
    }

    MD5Key assetId;
    try {
      assetId = sanitizeAssetId(id);
    } catch (IOException e) {
      log.error(I18N.getText("msg.error.errorResolvingCacheDir", id, e));
      return null;
    }

    // TODO LayeredCache should have a load(MD5Key) operation that returns an asset and can write
    //  back to faster caches.
    return layeredCache
        .get(assetId)
        .flatMap(
            lazyAsset -> {
              try {
                return Optional.of(lazyAsset.loader().load());
              } catch (IOException e) {
                log.error("Failed to load asset", e);
                return Optional.empty();
              }
            })
        .orElse(null);
  }

  /**
   * Checks the {@link Asset} id to ensure that the is {@link Asset} is valid.
   *
   * @param md5Key the {@link MD5Key} to check.
   * @return The passed in {@code md5Key} if it is ok, otherwise the key of an {@link Asset} in the
   *     asset cache to use in its place.
   */
  private static MD5Key sanitizeAssetId(MD5Key md5Key) throws IOException {
    if (md5Key == null) {
      return null;
    }

    // Check to see that the asset path wont escape the asset cache directory.
    File inCache = cacheDir.getCanonicalFile().toPath().resolve(md5Key.toString()).toFile();
    File toCheck = cacheDir.toPath().resolve(md5Key.toString()).toFile().getCanonicalFile();

    if (!inCache.equals(toCheck)) {
      return BAD_ASSET_LOCATION_KEY;
    }

    return md5Key;
  }

  /**
   * Remove the asset from the asset cache.
   *
   * @param id MD5 of the asset to remove
   */
  public static void flushAssetFromMemory(MD5Key id) {
    if (!REQUIRED_ASSETS.contains(id)) {
      inMemoryCache.remove(id);
    }
  }

  /**
   * Request that the asset be loaded from the server
   *
   * @param id MD5 of the asset to load from the server
   */
  private static void requestAssetFromServer(MD5Key id, AssetAvailableListener... listeners) {

    if (id != null) {
      addAssetListener(id, listeners);
      assetLoader.requestAsset(id);
    }
  }

  /**
   * Request that the asset be loaded from the server, blocks access while loading, use with
   * caution!
   *
   * @param id MD5 of the asset to load from the server
   * @return Asset from the server
   */
  public static Asset requestAssetFromServer(MD5Key id) {

    if (id != null) {
      assetLoader.requestAsset(id);
      return getAsset(id);
    }

    return null;
  }

  /**
   * Create an asset from a file.
   *
   * @param file File to use for asset
   * @return Asset associated with the file
   * @throws IOException in case of an I/O error
   */
  public static Asset createAsset(File file) throws IOException {
    return Asset.createAssetDetectType(
        FileUtil.getNameWithoutExtension(file), FileUtils.readFileToByteArray(file));
  }

  /**
   * Create an asset from a file.
   *
   * @param url File to use for asset
   * @return Asset associated with the file
   * @throws IOException in case of an I/O error
   */
  public static Asset createAsset(URL url) throws IOException {
    return createAsset(url, null);
  }

  public static Asset createAsset(URL url, Asset.Type assetType) throws IOException {
    // Create a temporary file from the downloaded URL
    File newFile = File.createTempFile("remote", null, null);
    try {
      FileUtils.copyURLToFile(url, newFile);
      if (!newFile.exists() || newFile.length() < 20) return null;
      if (assetType != null) {
        return Asset.createAsset(
            FileUtil.getNameWithoutExtension(url),
            FileUtils.readFileToByteArray(newFile),
            assetType);
      } else {
        return Asset.createAssetDetectType(
            FileUtil.getNameWithoutExtension(url), FileUtils.readFileToByteArray(newFile));
      }
    } finally {
      newFile.delete();
    }
  }

  /**
   * Loads the asset, and waits for the asset to load.
   *
   * @param assetId Load image data from this asset
   * @return Asset Return the loaded asset
   */
  public static Asset getAssetAndWait(final MD5Key assetId) {
    if (assetId == null) {
      return null;
    }
    Asset asset = null;
    final CountDownLatch loadLatch = new CountDownLatch(1);
    getAssetAsynchronously(
        assetId,
        (key) -> {
          // If we're here then the image has just finished loading
          // release the blocked thread
          log.debug("Countdown: " + assetId);
          loadLatch.countDown();
        });
    if (asset == null) {
      try {
        log.debug("Wait for:  " + assetId);
        loadLatch.await();
        // This time we'll get the cached version
        asset = getAsset(assetId);
      } catch (InterruptedException ie) {
        log.error(
            "getAssetAndWait(" + assetId + "):  asset not resolved; InterruptedException", ie);
        asset = null;
      }
    }
    return asset;
  }

  public static Optional<LazyAsset> getLazyAsset(MD5Key id) {
    return layeredCache.get(id);
  }

  /**
   * Recursively search from the rootDir, filtering files based on fileFilter, and store a reference
   * to every file seen.
   *
   * @param rootDir Starting directory to recurse from
   * @param fileFilter Only add references to image files that are allowed by the filter
   */
  public static void searchForImageReferences(File rootDir, FilenameFilter fileFilter) {
    for (File file : rootDir.listFiles()) {
      if (file.isDirectory()) {
        searchForImageReferences(file, fileFilter);
        continue;
      }
      try {
        if (fileFilter.accept(rootDir, file.getName())) {
          if (MapTool.getFrame() != null) {
            MapTool.getFrame().setStatusMessage("Caching image reference: " + file.getName());
          }

          linkCache.reference(file.toPath());
        }
      } catch (IOException ioe) {
        log.error("Failed to write to asset link cache", ioe);
      }
    }
    // Done
    if (MapTool.getFrame() != null) {
      MapTool.getFrame().setStatusMessage("");
    }
  }
}
