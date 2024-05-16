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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import javax.annotation.Nullable;
import net.rptools.lib.FileUtil;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.transfer.AssetHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Not really a "cache" per se, but we know where to find these assets! */
public class AssetLinkCache implements AssetCache {
  private static final Logger log = LogManager.getLogger(AssetLinkCache.class);

  private final Path cacheDir;

  public AssetLinkCache(Path cacheDir) {
    this.cacheDir = cacheDir;
  }

  @Override
  public boolean has(MD5Key id) {
    return getLocalReference(id) != null;
  }

  @Override
  public Optional<LazyAsset> get(MD5Key id) {
    final var path = getLocalReference(id);
    if (path == null) {
      return Optional.empty();
    }

    String name = FileUtil.getNameWithoutExtension(path.getFileName().toString());

    long size;
    try {
      size = Files.size(path);
    } catch (IOException e) {
      log.error("Failed to get the size of the asset file", e);
      return Optional.empty();
    }

    // TODO We don't know the type until we read the file, potentially the entire thing. What we
    //  ought to do is note the type in the link file so we can establish an expectation.
    return Optional.of(
        new LazyAsset(
            new AssetHeader(id, name, size),
            new VerifyingLoader(
                this, id, () -> Asset.createAssetDetectType(name, Files.readAllBytes(path)))));
  }

  @Override
  public void add(Asset asset) {
    // We don't support directly adding assets. Doing nothing is perfectly reasonable, no need for
    // the caller to do anything different.
  }

  @Override
  public void remove(MD5Key id) {
    // Don't remove the asset file itself, just the lnk that points to it.
    final var path = getAssetLinkPath(id);
    try {
      Files.delete(path);
    } catch (IOException e) {
      log.error("Failed to delete asset link file", e);
    }
  }

  /**
   * Store an absolute path to where this asset exists. Perhaps this should be saved in a single
   * data structure that is read/written when it's modified? This would allow the fileFilterText
   * field from the AssetPanel the option of searching through all directories and not just the
   * current one. FJE
   *
   * @param imagePath the file to be stored
   * @throws IOException in case of an I/O error
   */
  public void reference(Path imagePath) throws IOException {
    MD5Key id = new MD5Key(Files.newInputStream(imagePath));
    final Path lnkPath = getAssetLinkPath(id);

    // See if we know about this one already
    if (Files.exists(lnkPath)) {
      try (var lineStream = Files.lines(lnkPath)) {
        if (lineStream.anyMatch(ref -> ref.equals(id.toString()))) {
          // We already know about this one
          return;
        }
      }
    }

    // We don't know about this one yet. Remember the reference.
    try (OutputStream out =
        Files.newOutputStream(lnkPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      out.write((imagePath.toAbsolutePath() + "\n").getBytes());
    }
  }

  private @Nullable Path getLocalReference(MD5Key id) {
    Path lnkPath = getAssetLinkPath(id);
    if (!Files.exists(lnkPath)) {
      return null;
    }
    try (var refList = Files.lines(lnkPath)) {
      return refList.map(Path::of).filter(Files::exists).findFirst().orElse(null);
    } catch (IOException e) {
      log.error("Error while reading asset link file", e);
      return null;
    }
  }

  private Path getAssetLinkPath(MD5Key id) {
    return cacheDir.resolve(id + ".lnk");
  }
}
