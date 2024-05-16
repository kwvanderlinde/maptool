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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.transfer.AssetHeader;
import net.rptools.maptool.util.TempFileThenRenameOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * On-disk asset cache.
 *
 * <p>Used as a single place for both local and remote assets to be remembered for future loading.
 *
 * <p>Each entry of the persistent cache is a file containing the asset, whose file name is the MD5
 * of the asset. An optional sibling file, whose name is the MD5 key with the ".info" extension,
 * contains properties about the asset, namely its original file name and file type. For now, the
 * info file is optional, but will be made required in the future to maximize robustness.
 */
// TODO An operation to clean the asset cache of any invalid files and files without .info.
// TODO Make sure this class is never serialized.
public class PersistentAssetCache implements AssetCache {
  private static final Logger log = LogManager.getLogger(PersistentAssetCache.class);
  private static final ExecutorService assetWriterThreadPool =
      Executors.newSingleThreadExecutor(
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setNameFormat("PersistentAssetCache.AssetWriterThread-%d")
              .build());

  /** Property string associated with asset name */
  private static final String INFO_PROPERTY_NAME = "name";

  /** Property string associated with asset type. */
  private static final String INFO_PROPERTY_TYPE = "type";

  private static final Pattern VALID_MD5_KEY_PATTERN = Pattern.compile("[A-Za-z0-9]{32}");

  // TODO Use Path instead.
  private final Path cacheDir;

  public PersistentAssetCache(Path cacheDir) {
    this.cacheDir = cacheDir;
  }

  /**
   * Determine if the asset is in the persistent cache.
   *
   * @param id The asset ID to search for
   * @return {@code true} if the asset is in the persistent cache, {@code false} otherwise.
   */
  @Override
  public boolean has(MD5Key id) {
    try {
      validateAssetId(id);
    } catch (IOException e) {
      log.error("Error resolving cache dir for id {}", id, e);
      return false;
    }

    return hasAssetFile(id);
  }

  @Override
  public Optional<LazyAsset> get(MD5Key id) {
    // TODO Fix untrustworthiness of MD5Key.
    try {
      validateAssetId(id);
    } catch (IOException e) {
      log.error("Error resolving cache dir for id {}", id, e);
      return Optional.empty();
    }

    if (!has(id)) {
      // 404 Not Found
      return Optional.empty();
    }

    Path assetPath = getAssetPath(id);
    Properties props = getAssetInfo(id);

    var propName = props.getProperty(INFO_PROPERTY_NAME);
    var propType = props.getProperty(INFO_PROPERTY_TYPE);

    long size;
    try {
      size = Files.size(assetPath);
    } catch (IOException e) {
      log.error("Failed to get file size for persistent cache entry", e);
      return Optional.empty();
    }
    // Should have been caught by `has(id)`, but better safe than sorry.
    if (size == 0) {
      log.error("Asset file is empty; not loading it");
      return Optional.empty();
    }

    var name = Objects.requireNonNullElse(propName, "");
    var type = propType == null ? Asset.Type.UNKNOWN : Asset.Type.valueOf(propType);
    return Optional.of(
        new LazyAsset(
            new AssetHeader(id, name, size),
            new VerifyingLoader(
                this, id, () -> type.getFactory().apply(name, Files.readAllBytes(assetPath)))));
  }

  @Override
  public void add(Asset asset) {
    // Invalid images are represented by empty assets. Don't persist those
    if (asset.getData().length == 0) {
      log.warn("Not adding invalid asset to the persistent cache.");
      return;
    }

    // TODO Fix untrustworthiness of MD5Key.
    final var id = asset.getMD5Key();
    try {
      validateAssetId(id);
    } catch (IOException e) {
      log.error("Error resolving cache dir for id {}", id, e);
      return;
    }

    final var mustWriteAsset = !hasAssetFile(id);
    final var mustWriteAssetInfo = mustWriteAsset || !hasAssetInfoFile(id);

    final Path assetPath = getAssetPath(id);
    final Path infoPath = getAssetInfoPath(id);

    if (mustWriteAsset || mustWriteAssetInfo) {
      assetWriterThreadPool.submit(
          () -> {
            try {
              Files.createDirectories(cacheDir);
            } catch (IOException e) {
              // Cache dir could not be made. Bail early.
              log.error("Unable to create cache directory");
              return;
            }

            if (mustWriteAsset) {
              // Placing the temp file under the cache directory means it will be on the same
              // filesystem in most cases.
              try (var operation = new TempFileThenRenameOperation(cacheDir)) {
                operation.write(new ByteArrayInputStream(asset.getData()));
                // Now that the data is in a file, we move it to its final resting place.
                operation.moveTo(assetPath);
              } catch (IOException ioe) {
                log.error("Could not persist asset file", ioe);
              }
              // TODO Original caught NPE here, but I don't think it is necessary.
            }
          });
    }
    if (mustWriteAssetInfo) {
      assetWriterThreadPool.submit(
          () -> {
            Properties props = new Properties();
            var name = asset.getName();
            if (name != null) {
              props.put(INFO_PROPERTY_NAME, name);
            }
            props.put(INFO_PROPERTY_TYPE, asset.getType().name());

            try (OutputStream out = Files.newOutputStream(infoPath)) {
              props.store(out, "Asset Info");
            } catch (IOException ioe) {
              log.error("Could not persist asset info file", ioe);
            }
          });
    }
  }

  @Override
  public void remove(MD5Key id) {
    // TODO Fix untrustworthiness of MD5Key.
    try {
      validateAssetId(id);
    } catch (IOException e) {
      log.error("Error resolving cache dir for id {}", id, e);
    }

    try {
      Files.delete(getAssetPath(id));
    } catch (IOException e) {
      log.error("Unable to delete asset file", e);
    }

    try {
      Files.delete(getAssetInfoPath(id));
    } catch (IOException e) {
      log.error("Unable to delete asset info file", e);
    }
  }

  private boolean hasAssetFile(MD5Key id) {
    var assetFile = getAssetPath(id).toFile();
    // Note: length == 0 indicates an invalid image. Nowadays these shouldn't be in the cache, but
    // there may be pre-existing entries.
    return assetFile.exists() && assetFile.length() > 0;
  }

  private boolean hasAssetInfoFile(MD5Key id) {
    return Files.exists(getAssetInfoPath(id));
  }

  /**
   * Return a set of properties associated with the asset.
   *
   * @param id MD5 of the asset
   * @return Properties object containing asset properties.
   */
  private Properties getAssetInfo(MD5Key id) {
    Path infoPath = getAssetInfoPath(id);
    Properties props = new Properties();
    try (InputStream is = Files.newInputStream(infoPath)) {
      props.load(is);
    } catch (IOException ioe) {
      // do nothing
    }
    return props;
  }

  private Path getAssetPath(MD5Key id) {
    return cacheDir.resolve(id.toString());
  }

  private Path getAssetInfoPath(MD5Key id) {
    return cacheDir.resolve(id.toString() + ".info");
  }

  /**
   * Checks the {@link Asset} id to ensure that the is {@link Asset} is valid.
   *
   * @param md5Key the {@link MD5Key} to check.
   * @throws java.io.IOException If the key could not be validated.
   */
  private void validateAssetId(MD5Key md5Key) throws IOException {
    if (md5Key == null) {
      throw new IOException("MD5 key is not null");
    }

    // Check that there are no strange characters that might escape the directory.
    if (!VALID_MD5_KEY_PATTERN.asMatchPredicate().test(md5Key.toString())) {
      throw new IOException("Key is not a valid MD5 key");
    }
  }
}
