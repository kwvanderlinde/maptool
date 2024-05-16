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
package net.rptools.maptool.util;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.rptools.lib.MD5Key;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.AssetAvailableListener;
import net.rptools.maptool.model.AssetManager;
import org.apache.commons.collections4.map.AbstractReferenceMap;
import org.apache.commons.collections4.map.ReferenceMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * The ImageManager class keeps a cache of loaded images. This class can be used to load the raw
 * image data from an asset. The loading of the raw image data into a usable class is done in the
 * background by one of two threads. The ImageManager will return a "?" (UNKNOWN_IMAGE) if the asset
 * is still downloading or the asset image is still being loaded, and a "X" (BROKEN_IMAGE) if the
 * asset or image is invalid. Small images are loaded using a different thread pool from large
 * images, and allows small images to load quicker.
 *
 * @author RPTools Team.
 */
public class ImageManager {
  @FunctionalInterface
  public interface Observer {
    void imageLoaded(BufferedImage image);
  }

  private static final AtomicInteger skip = new AtomicInteger(0);

  public static void considerSkipping(int count) {
    skip.set(count);
  }

  public static boolean shouldSkip() {
    var remainingSkips = skip.decrementAndGet();
    log.debug("{} skips remaining", remainingSkips);
    return remainingSkips >= 0;
  }

  private static final Logger log = LogManager.getLogger(ImageManager.class);

  static {
    Configurator.setLevel(log, Level.DEBUG);
  }

  private static final ConcurrentMap<MD5Key, ImageEntry> imageMap = new ConcurrentHashMap<>();

  // TODO This map is not and may not be concurrent. It should be synchronized upon or wrapped.
  /** Additional Soft-reference Cache of images that allows best . */
  private static final Map<MD5Key, BufferedImage> backupImageMap =
      new ReferenceMap<>(
          AbstractReferenceMap.ReferenceStrength.HARD, AbstractReferenceMap.ReferenceStrength.SOFT);

  /**
   * The unknown image, a "?" is used for all situations where the image will eventually appear e.g.
   * asset download, and image loading.
   */
  /** The buffered "?" image to display while transferring the image. */
  public static BufferedImage TRANSFERING_IMAGE;

  /** The broken image, a "X" is used for all situations where the asset or image was invalid. */
  public static BufferedImage BROKEN_IMAGE;

  static {
    TRANSFERING_IMAGE = RessourceManager.getImage(Images.UNKNOWN);
    BROKEN_IMAGE = RessourceManager.getImage(Images.BROKEN);
  }

  /**
   * Remove all images from the image cache. The observers and image load hints are not flushed. The
   * same observers will be notified when the image is reloaded, and the same hints will be used for
   * loading.
   */
  public static void flush() {
    imageMap.clear();
  }

  /**
   * Loads the asset's raw image data into a buffered image, and waits for the image to load.
   *
   * @param assetId Load image data from this asset
   * @return BufferedImage Return the loaded image
   */
  public static BufferedImage getImageAndWait(MD5Key assetId) {
    return getImageAndWait(assetId, null);
  }

  /**
   * Flush all images that are <b>not</b> in the provided set. This presumes that the images in the
   * exception set will still be in use after the flush.
   *
   * @param exceptionSet a set of images not to be flushed
   */
  public static void flush(Set<MD5Key> exceptionSet) {
    imageMap.keySet().removeAll(exceptionSet);
  }

  /**
   * Loads the asset's raw image data into a buffered image, and waits for the image to load.
   *
   * @param assetId Load image data from this asset
   * @param hintMap Hints used when loading the image
   * @return BufferedImage Return the loaded image
   */
  public static BufferedImage getImageAndWait(final MD5Key assetId, Map<String, Object> hintMap) {
    if (assetId == null) {
      return BROKEN_IMAGE;
    }

    var future = new CompletableFuture<BufferedImage>();
    var image =
        getImage(
            assetId,
            (img) -> {
              // If we're here then the image has just finished loading. Release the blocked thread
              log.debug("Completed for asset: {}", assetId);
              future.complete(img);
            });
    if (image != TRANSFERING_IMAGE) {
      return image;
    }

    try {
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error waiting for image", e);
      return BROKEN_IMAGE;
    }
  }

  // TODO getImage() should itself support scaling to simplify some callers.

  public static BufferedImage getImage(MD5Key assetId) {
    return getImage(assetId, (Observer) null);
  }

  /**
   * Return the image corresponding to the assetId.
   *
   * @param assetId Load image data from this asset.
   * @param observer the observer to be notified when the image loads, if it hasn't already.
   * @return the image, or BROKEN_IMAGE if assetId null, or TRANSFERING_IMAGE if loading.
   */
  public static BufferedImage getImage(MD5Key assetId, Observer observer) {
    if (assetId == null) {
      return BROKEN_IMAGE;
    }

    var entry = imageMap.computeIfAbsent(assetId, ImageEntry::new);
    var image = entry.getIfAvailable(observer);
    return Objects.requireNonNullElse(image, TRANSFERING_IMAGE);
  }

  /**
   * Return the image corresponding to the assetId.
   *
   * <p>This is a convenience wrapper for {@link #getImage(net.rptools.lib.MD5Key,
   * net.rptools.maptool.util.ImageManager.Observer)}. Since many of the callers are swing
   * components, this allows more direct use without requiring non-swing component to think through
   * the meaning of {@code ImageObserver}. Each observer will be notified only when the full image
   * is loaded, with the flag {@link java.awt.image.ImageObserver#ALLBITS} and the dimensions of the
   * full image.
   *
   * @param assetId Load image data from this asset.
   * @param observers the observers to be notified when the image loads, if it hasn't already.
   * @return the image, or BROKEN_IMAGE if assetId null, or TRANSFERING_IMAGE if loading.
   */
  public static BufferedImage getImage(MD5Key assetId, ImageObserver... observers) {
    return getImage(
        assetId,
        (img) -> {
          for (var observer : observers) {
            observer.imageUpdate(img, ImageObserver.ALLBITS, 0, 0, img.getWidth(), img.getHeight());
          }
        });
  }

  /**
   * Returns an image from an asset:// URL.<br>
   * The returned image may be scaled based on parameters in the URL:<br>
   * <b>width</b> - Query parameter. Desired width in px.<br>
   * <b>height</b> - Query parameter. Desired height in px.<br>
   * <b>size</b> - A suffix added after the asset id in the form of "-size" where size is a value
   * indicating the size in px to scale the largest side of the image to, maintaining aspect ratio.
   * This parameter is ignored if a width or height are present.<br>
   * (ex. asset://9e9687c80a3c9796b328711df6bd67cf-50)<br>
   * All parameters expect an integer >0. Any invalid value (a value <= 0 or non-integer) will
   * result in {@code BROKEN_IMAGE} being returned. Images are only scaled down and if any parameter
   * exceeds the image's native size the image will be returned unscaled.
   *
   * @param url URL to an asset
   * @return the image, scaled if indicated by the URL, or {@code BROKEN_IMAGE} if url is null, the
   *     URL protocol is not asset://, or it has invalid parameter values.
   */
  public static BufferedImage getImageFromUrl(URL url) {
    if (url == null || !url.getProtocol().equals("asset")) {
      return BROKEN_IMAGE;
    }

    String id = url.getHost();
    String query = url.getQuery();
    BufferedImage image;
    int imageW, imageH, scaleW = -1, scaleH = -1, size = -1;

    // Get size parameter
    int szIndex = id.indexOf('-');
    if (szIndex != -1) {
      String szStr = id.substring(szIndex + 1);
      id = id.substring(0, szIndex);
      try {
        size = Integer.parseInt(szStr);
      } catch (NumberFormatException nfe) {
        // Do nothing
      }
      if (size <= 0) {
        return BROKEN_IMAGE;
      }
    }

    // Get query parameters
    if (query != null && !query.isEmpty()) {
      HashMap<String, String> params = new HashMap<>();

      for (String param : query.split("&")) {
        if (param.isBlank()) continue;

        int eqIndex = param.indexOf("=");
        if (eqIndex != -1) {
          String k, v;
          k = param.substring(0, eqIndex).trim();
          v = param.substring(eqIndex + 1).trim();
          params.put(k, v);
        } else {
          params.put(param.trim(), "");
        }
      }

      if (params.containsKey("width")) {
        size = -1; // Don't use size param if width is present
        try {
          scaleW = Integer.parseInt(params.get("width"));
        } catch (NumberFormatException nfe) {
          // Do nothing
        }
        if (scaleW <= 0) {
          return BROKEN_IMAGE;
        }
      }

      if (params.containsKey("height")) {
        size = -1; // Don't use size param if height is present
        try {
          scaleH = Integer.parseInt(params.get("height"));
        } catch (NumberFormatException nfe) {
          // Do nothing
        }
        if (scaleH <= 0) {
          return BROKEN_IMAGE;
        }
      }
    }

    image = getImageAndWait(new MD5Key(id), null);
    imageW = image.getWidth();
    imageH = image.getHeight();

    // We only want to scale down, so if scaleW or ScaleH are too large just return the image
    if (scaleW > imageW || scaleH > imageH) {
      return image;
    }

    // Note: size will never be >0 if height or width parameters are present
    if (size > 0) {
      if (imageW > imageH) {
        scaleW = size;
      } else if (imageH > imageW) {
        scaleH = size;
      } else {
        scaleW = scaleH = size;
      }
    }

    if ((scaleW > 0 && imageW > scaleW) || (scaleH > 0 && imageH > scaleH)) {
      // Maintain aspect ratio if one dimension isn't given
      if (scaleW <= 0) {
        scaleW = Math.max((int) ((double) scaleH / imageH * imageW), 1);
      } else if (scaleH <= 0) {
        scaleH = Math.max((int) ((double) scaleW / imageW * imageH), 1);
      }
      image = ImageUtil.scaleBufferedImage(image, scaleW, scaleH);
    }

    return image;
  }

  /**
   * Remove the image associated this MD5Key from the cache.
   *
   * @param assetId MD5Key associated with this image
   */
  public static void flushImage(MD5Key assetId) {
    // LATER: investigate how this effects images that are already in progress
    // TODO I _think_ this has no effect on in-progress images since the loader should have the
    //  entry itself. If not, I should make sure the listener does so.
    if (assetId != null) {
      imageMap.remove(assetId);
    }
  }

  /** Represents an image that is either being loaded or has been loaded. */
  private static class ImageEntry {
    /** The ID of the asset that the image will be created from. */
    private final MD5Key key;

    /**
     * The loaded image, or {@code null} if still in progress.
     *
     * <p>Note: This field must only be accessed under {@code synchronized (this)}.
     */
    private @Nullable BufferedImage image;

    // TODO Make this ordered. I see no reason to introduce uncertainty.
    /**
     * The observers that need to be notified when the image is available.
     *
     * <p>Note: This field must only be accessed under {@code synchronized (this)}.
     */
    private final Set<Observer> observers;

    public ImageEntry(MD5Key key) {
      this.key = key;
      this.image = null;
      this.observers = new HashSet<>();
    }

    public MD5Key getKey() {
      return key;
    }

    public synchronized @Nullable BufferedImage getIfAvailable(Observer observer) {
      var shouldSkip = ImageManager.shouldSkip();
      if (shouldSkip) {
        log.debug("Skipping the checks; assuming the image is not loaded");
        if (observer != null) {
          this.observers.add(observer);
        }
        // TODO I realize now that my testing methodology is more than a bit flawed.
        AssetManager.getAssetAsynchronously(key, new AssetListener(this, null));
        return null;
      }

      if (image == null) {
        // Check if the soft reference still resolves image
        var backupImage = backupImageMap.get(key);
        if (backupImage != null) {
          resolve(backupImage);
        } else {
          // Entry still not resolved.
          if (observer != null) {
            this.observers.add(observer);
          }
          // TODO Do I need a different listener now?
          AssetManager.getAssetAsynchronously(key, new AssetListener(this, null));
        }
      }
      return image;
    }

    /**
     * Resolves the entry with an image.
     *
     * <p>Any observers that have not been notified will will be notified.
     *
     * <p>After this method completes, the entry is <emph>resolved</emph>.
     *
     * @param image The image to resolve.
     */
    public void resolve(BufferedImage image) {
      List<Observer> observers;
      synchronized (this) {
        if (this.image != null) {
          log.debug("Image wasn't in transit: {}", key);
          // Stick with the existing image. We still need to notify any pending observers.
          image = this.image;
        } else {
          this.image = image;
        }
        // At this point the entry is resolved.

        observers = new ArrayList<>(this.observers);
        this.observers.clear();
      }

      // Note: if a call to getIfAvailable() happens at this point in time or later, those new
      // observers do not need to be included since the caller will have the image already.

      // Help out with future lookups - even after flushes - by remembering a soft reference.
      if (image != ImageManager.BROKEN_IMAGE) {
        backupImageMap.putIfAbsent(key, image);
      }

      // Notify outside of any mutex.
      notify(key, image, observers);
    }

    // Core of the resolve() method, but static so we don't accidentally access fields we shouldn't
    private static void notify(MD5Key key, BufferedImage image, Collection<Observer> observers) {
      log.debug("Notifying {} observers of image availability: {}", observers.size(), key);
      for (var observer : observers) {
        observer.imageLoaded(image);
      }
    }
  }

  /**
   * Load the asset's raw image data into a BufferedImage.
   *
   * @author RPTools Team.
   */
  private static class BackgroundImageLoader implements Runnable {
    private final ImageEntry entry;
    private final Asset asset;
    private final Map<String, Object> hints;

    /**
     * Create a background image loader to load the asset image using the hints provided.
     *
     * @param asset Asset to load
     * @param hints Hints to use for image loading
     */
    public BackgroundImageLoader(ImageEntry entry, Asset asset, Map<String, Object> hints) {
      this.entry = entry;
      this.asset = asset;
      this.hints = hints;
    }

    /** Load the asset raw image data and notify observers that the image is loaded. */
    public void run() {
      log.debug("Loading asset: {}", asset.getMD5Key());

      BufferedImage image;
      if (asset.getExtension().equals(Asset.DATA_EXTENSION)) {
        log.debug(
            "BackgroundImageLoader.run({}, {}, {}): looks like data and skipped",
            asset.getName(),
            asset.getExtension(),
            asset.getMD5Key());
        image = ImageManager.BROKEN_IMAGE; // we should never see this
      } else {
        try {
          assert asset.getData() != null
              : "asset.getImage() for " + asset.toString() + "returns null?!";
          image =
              ImageUtil.createCompatibleImage(
                  ImageUtil.bytesToImage(asset.getData(), asset.getName()), hints);
        } catch (Throwable t) {
          if (!AssetManager.BAD_ASSET_LOCATION_KEY.equals(asset.getMD5Key())) {
            // Don't bother logging cache miss of internal bad location asset
            log.error(
                "BackgroundImageLoader.run({}, {}, {}): image not resolved",
                asset.getName(),
                asset.getExtension(),
                asset.getMD5Key(),
                t);
          }
          image = ImageManager.BROKEN_IMAGE;
        }
      }

      entry.resolve(image);
    }
  }

  private static class AssetListener implements AssetAvailableListener {
    /** Small and large thread pools for background processing of asset raw image data. */
    private static final ExecutorService smallImageLoader = Executors.newFixedThreadPool(1);

    private static final ExecutorService largeImageLoader = Executors.newFixedThreadPool(1);

    private final ImageEntry entry;
    private final Map<String, Object> hints;

    public AssetListener(ImageEntry entry, Map<String, Object> hints) {
      this.entry = entry;
      this.hints = hints;
    }

    @Override
    public void assetAvailable(MD5Key key) {
      if (!key.equals(entry.getKey())) {
        return;
      }
      // No longer need to be notified when this asset is available
      AssetManager.removeAssetListener(entry.getKey(), this);

      // Image is now available for loading
      log.debug("Asset available: {}", entry.getKey());
      backgroundLoadImage(entry, AssetManager.getAsset(entry.getKey()), hints);
    }

    /**
     * Run a thread to load the asset raw image data in the background using the provided hints.
     *
     * @param asset Load raw image data from this asset
     * @param hints Hints used when loading image data
     */
    private static void backgroundLoadImage(
        ImageEntry entry, Asset asset, Map<String, Object> hints) {
      // Use large image loader if the image is larger than 128kb.
      if (asset.getData().length > 128 * 1024) {
        largeImageLoader.execute(new BackgroundImageLoader(entry, asset, hints));
      } else {
        smallImageLoader.execute(new BackgroundImageLoader(entry, asset, hints));
      }
    }
  }
}
