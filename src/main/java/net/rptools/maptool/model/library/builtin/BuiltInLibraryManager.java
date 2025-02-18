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
package net.rptools.maptool.model.library.builtin;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.library.Library;
import net.rptools.maptool.model.library.addon.AddOnLibrary;
import net.rptools.maptool.model.library.addon.AddOnLibraryImporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Class for managing {@link AddOnLibrary} objects. */
public class BuiltInLibraryManager {

  /** "Protocol" for built in add-on libraries. */
  private static final String LIBRARY_PROTOCOL = "lib";

  private static final Logger log = LogManager.getLogger(BuiltInLibraryManager.class);

  /** The add-on libraries that are registered. */
  private final Map<String, Library> namespaceLibraryMap = new ConcurrentHashMap<>();

  public BuiltInLibraryManager() {
    registerLibrary(new MapToolBuiltInLibrary());
  }

  /**
   * Is there a add-on library that would handle this path. This just checks the protocol and
   * namespace, it won't check that the full path actually exists.
   *
   * @param path the path for the library (can be full path or just part of path).
   * @return if the library at the path is handled by a add-on library.
   */
  public boolean handles(URL path) {
    if (path.getProtocol().toLowerCase().startsWith(LIBRARY_PROTOCOL)) {
      return namespaceRegistered(path.getHost());
    } else {
      return false;
    }
  }

  /**
   * Checks to see if this namespace is already registered.
   *
   * @param namespace the namespace to check.
   * @return {@code true} if the namespace is registered.
   */
  public boolean namespaceRegistered(String namespace) {
    return namespaceLibraryMap.containsKey(namespace.toLowerCase());
  }

  /**
   * Registers the specified add-on library.
   *
   * @param library The add-on library to register.
   * @throws IllegalStateException if there is already a add-on library with the same namespace.
   */
  public void registerLibrary(BuiltInLibrary library) {
    String namespace = library.getNamespace().join().toLowerCase();

    var registeredLib = namespaceLibraryMap.computeIfAbsent(namespace, k -> library);
    if (registeredLib != library) {
      throw new IllegalStateException("Library is already registered");
    }
  }

  /**
   * Deregister the add-on library with the specified namespace.
   *
   * @param namespace the namespace of the library to deregister.
   */
  public void deregisterLibrary(String namespace) {
    // We never deregister the built in libraries
  }

  /**
   * Returns a list of the registered add-on libraries.
   *
   * @return list of the registered add-on libraries.
   */
  public List<Library> getLibraries() {
    return new ArrayList<>(namespaceLibraryMap.values());
  }

  /**
   * Returns the library with the specified namespace. If no library exists for this namespace then
   * null is returned.
   *
   * @param namespace the namespace of the library.
   * @return the library for the namespace.
   */
  public Library getLibrary(String namespace) {
    return namespaceLibraryMap.getOrDefault(namespace.toLowerCase(), null);
  }

  /**
   * Returns the {@link Library} that will handle the lib:// uri that is passed in.
   *
   * @param path the path of the add-on library.
   * @return the {@link Library} representing the lib:// uri .
   */
  public Library getLibrary(URL path) {
    if (path.getProtocol().toLowerCase().startsWith(LIBRARY_PROTOCOL)) {
      return getLibrary(path.getHost());
    } else {
      return null;
    }
  }

  /** Initializes the built in libraries. */
  public void loadBuiltIns() {
    var classLoader = Thread.currentThread().getContextClassLoader();

    URI uri;
    try {
      uri = classLoader.getResource(ClassPathAddOnLibrary.BUILTIN_LIB_CLASSPATH_DIR).toURI();
    } catch (URISyntaxException e) {
      MapTool.showError("msg.error.library.builtin.path", e);
      return;
    }

    try (var fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
      var resourcePath = fs.getPath(ClassPathAddOnLibrary.BUILTIN_LIB_CLASSPATH_DIR);
      var libs =
          Files.walk(resourcePath, 1)
              .filter(p -> p.toString().endsWith(AddOnLibraryImporter.DROP_IN_LIBRARY_EXTENSION))
              .toList();

      var importer = new AddOnLibraryImporter();
      libs.stream()
          .forEach(
              l -> {
                try {
                  var lib = importer.importFromClassPath(l.toString());
                  var clib = new ClassPathAddOnLibrary(l.toString(), lib);
                  registerLibrary(clib);
                  clib.initialize();

                } catch (Exception e) {
                  MapTool.showError("msg.error.library.builtin.load", e);
                }
              });
    } catch (IOException e) {
      MapTool.showError("msg.error.library.builtin.load", e);
    }
  }
}
