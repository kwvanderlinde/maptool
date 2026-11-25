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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.model.library.builtin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.AppConstants;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolMacroContext;
import net.rptools.maptool.client.macro.MacroManager.MacroDetails;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.library.Library;
import net.rptools.maptool.model.library.LibraryInfo;
import net.rptools.maptool.model.library.MTScriptMacroInfo;
import net.rptools.maptool.model.library.builtin.themecss.ThemeCssContext;
import net.rptools.maptool.model.library.data.LibraryData;
import net.rptools.maptool.util.HandlebarsUtil;

public class MapToolBuiltInLibrary implements Library {

  private record ResourceDetails(
      String name, String path, Function<ResourceDetails, String> supplier, boolean cache) {}

  private final Map<String, ResourceDetails> resourcesMap =
      Map.of(
          "css/mt-theme.css",
          new ResourceDetails(
              "MT Theme",
              "/net/rptools/maptool/library/builtin/mt-theme-css.hbs",
              rd -> processHandlebarsTemplate(rd),
              true),
          "css/mt-stat-sheet.css",
          new ResourceDetails(
              "MT Theme",
              "/net/rptools/maptool/library/builtin/mt-stat-sheet-css.hbs",
              rd -> processHandlebarsTemplate(rd),
              true));

  private String processHandlebarsTemplate(ResourceDetails rd) {
    try (var cssTemplateIs = MapToolBuiltInLibrary.class.getResourceAsStream(rd.path())) {
      var cssTemplate = new String(cssTemplateIs.readAllBytes());
      return new HandlebarsUtil<>(cssTemplate).apply(new ThemeCssContext());
    } catch (IOException e) {
      MapTool.showError(I18N.getText("msg.error.parsing.handlebars", rd.path()), e);
      return "";
    }
  }

  private static final Map<String, String> cache = new ConcurrentHashMap<>();

  private static final LibraryInfo libraryInfo =
      new LibraryInfo(
          "MapTool Built-In Library",
          AppConstants.MT_BUILTIN_ADD_ON_NAMESPACE,
          "1.0.0",
          "https://www.rptools.net",
          "https://github.com/RPTools/maptool",
          List.of("RPTools Team"),
          "AGPLv3",
          "MapTool Built-In Library",
          "MapTool Built-In Library",
          true,
          "",
          "");

  @Override
  public CompletableFuture<Boolean> locationExists(URL location) {
    var key = location.getPath().replaceFirst("^/", "");
    return CompletableFuture.completedFuture(resourcesMap.containsKey(key));
  }

  @Override
  public CompletableFuture<Optional<MD5Key>> getAssetKey(URL location) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<String> readAsString(URL location) throws IOException {
    var path = location.getPath().replaceFirst("^/", "");
    if (resourcesMap.containsKey(path)) {
      return CompletableFuture.completedFuture(readAsString(resourcesMap.get(path)));
    } else {
      throw new IOException("Resource not found: " + location);
    }
  }

  @Override
  public CompletableFuture<InputStream> read(URL location) throws IOException {
    var path = location.getPath().replaceFirst("^/", "");
    if (resourcesMap.containsKey(path)) {
      return CompletableFuture.completedFuture(read(resourcesMap.get(path)));
    } else {
      throw new IOException("Resource not found: " + location);
    }
  }

  private String readAsString(ResourceDetails details) throws IOException {
    if (details.supplier != null) {
      if (details.cache) {
        return cache.computeIfAbsent(details.path, k -> details.supplier.apply(details));
      } else {
        return details.supplier.apply(details);
      }
    } else {
      return new String(read(details).readAllBytes());
    }
  }

  public InputStream read(ResourceDetails details) throws IOException {
    if (details.supplier != null) {
      return new ByteArrayInputStream(readAsString(details).getBytes());
    } else {
      return MapToolBuiltInLibrary.class.getResourceAsStream(details.path());
    }
  }

  @Override
  public LibraryInfo getLibraryInfo() {
    return libraryInfo;
  }

  @Override
  public CompletableFuture<Optional<MTScriptMacroInfo>> getMTScriptMacroInfoForUriPath(
      String macroPath) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<Optional<MTScriptMacroInfo>> getMTScriptMacroInfo(String macroName) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<Optional<MTScriptMacroInfo>> getPrivateMacroInfo(String macroName) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<List<String>> getAllFiles() {
    return CompletableFuture.completedFuture(resourcesMap.keySet().stream().toList());
  }

  @Override
  public LibraryData getLibraryData() {
    return new BuiltInLibraryData(this);
  }

  @Override
  public CompletableFuture<Optional<String>> getLegacyEventHandlerName(String eventName) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public boolean canMTScriptAccessPrivate(MapToolMacroContext context) {
    return false;
  }

  @Override
  public CompletableFuture<Optional<Asset>> getReadMeAsset() {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<Optional<Asset>> getLicenseAsset() {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public void cleanup() {}

  @Override
  public Set<MacroDetails> getSlashCommands() {
    return Set.of();
  }
}
