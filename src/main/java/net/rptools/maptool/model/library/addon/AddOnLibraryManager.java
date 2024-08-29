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
package net.rptools.maptool.model.library.addon;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.library.AddOnsAddedEvent;
import net.rptools.maptool.model.library.AddOnsRemovedEvent;
import net.rptools.maptool.model.library.Library;
import net.rptools.maptool.model.library.LibraryInfo;
import net.rptools.maptool.model.library.proto.AddOnLibraryDto;
import net.rptools.maptool.model.library.proto.AddOnLibraryListDto;
import net.rptools.maptool.model.library.proto.AddOnLibraryListDto.AddOnLibraryEntryDto;

/** Class for managing {@link AddOnLibrary} objects. */
public class AddOnLibraryManager {

  /** "Protocol" for add-on libraries. */
  private static final String LIBRARY_PROTOCOL = "lib";

  /** The add-on libraries that are registered. */
  private final Map<String, AddOnLibrary> namespaceLibraryMap = new ConcurrentHashMap<>();

  private ExternalAddOnLibraryManager externalAddOnLibraryManager;

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
  public void registerLibrary(AddOnLibrary library) {
    String namespace = library.getNamespace().join().toLowerCase();

    var registeredLib = namespaceLibraryMap.computeIfAbsent(namespace, k -> library);
    if (registeredLib != library) {
      throw new IllegalStateException("Library is already registered");
    }

    library.initialize();
    new MapToolEventBus()
        .getMainEventBus()
        .post(new AddOnsAddedEvent(Set.of(library.getLibraryInfo().join())));
  }

  /**
   * Checks to see if the specified namespace is registered.
   *
   * @param namespace the namespace to check.
   * @return {@code true} if the namespace is registered.
   */
  public boolean isNamespaceRegistered(String namespace) {
    return namespaceLibraryMap.containsKey(namespace.toLowerCase());
  }

  /**
   * Deregister the add-on library with the specified namespace.
   *
   * @param namespace the namespace of the library to deregister.
   */
  public void deregisterLibrary(String namespace) {
    var removed = namespaceLibraryMap.remove(namespace.toLowerCase());
    if (removed != null) {
      removed.cleanup();
      new MapToolEventBus()
          .getMainEventBus()
          .post(new AddOnsRemovedEvent(Set.of(removed.getLibraryInfo().join())));
    }
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

  /**
   * Returns the {@link AddOnLibraryListDto} containing all the add-on libraries.
   *
   * @return the {@link AddOnLibraryListDto} containing all the add-on libraries.
   */
  public CompletableFuture<AddOnLibraryListDto> toDto() {
    return CompletableFuture.supplyAsync(
        () -> {
          var dto = AddOnLibraryListDto.newBuilder();
          for (var library : namespaceLibraryMap.values()) {
            var detailDto = AddOnLibraryDto.newBuilder();
            detailDto.setName(library.getName().join());
            detailDto.setVersion(library.getVersion().join());
            detailDto.setWebsite(library.getWebsite().join());
            detailDto.setGitUrl(library.getGitUrl().join());
            detailDto.addAllAuthors(Arrays.asList(library.getAuthors().join()));
            detailDto.setLicense(library.getLicense().join());
            detailDto.setNamespace(library.getNamespace().join());
            detailDto.setDescription(library.getDescription().join());
            detailDto.setShortDescription(library.getShortDescription().join());
            var campDto = AddOnLibraryEntryDto.newBuilder();
            campDto.setDetails(detailDto);
            campDto.setMd5Hash(library.getAssetKey().toString());
            dto.addLibraries(campDto);
          }
          return dto.build();
        });
  }

  /** Remove all the add-on libraries. */
  public void removeAllLibraries() {
    var libs =
        namespaceLibraryMap.values().stream()
            .map(AddOnLibrary::getLibraryInfo)
            .map(CompletableFuture::join)
            .collect(Collectors.toSet());

    if (libs.size() > 0) {
      new MapToolEventBus().getMainEventBus().post(new AddOnsRemovedEvent(libs));
      for (var library : namespaceLibraryMap.values()) {
        library.cleanup();
      }
      namespaceLibraryMap.clear();
    }
  }

  /**
   * Returns the list of tokens that have handlers for the specified legacy token events.
   *
   * @param eventName the name of the event to match.
   * @return the list of tokens that have handlers for the specified legacy token events.
   */
  public CompletableFuture<Set<Library>> getLegacyEventTargets(String eventName) {
    return CompletableFuture.supplyAsync(
        () ->
            namespaceLibraryMap.values().stream()
                .filter(l -> l.getLegacyEvents().contains(eventName))
                .collect(Collectors.toSet()));
  }

  /** Initializes the add-on library manager. */
  public void init() {
    externalAddOnLibraryManager = new ExternalAddOnLibraryManager(this);
    String path = AppPreferences.getExternalAddOnLibrariesPath();
    if (path != null && !path.isEmpty()) {
      externalAddOnLibraryManager.setExternalLibraryPath(Path.of(path));
      externalAddOnLibraryManager.setEnabled(AppPreferences.getExternalLibraryManagerEnabled());
    }
  }

  /**
   * Replaces the add-on library with a newer version.
   *
   * @param library the library to replace the existing library with.
   */
  public void replaceLibrary(AddOnLibrary library) {
    library
        .getNamespace()
        .thenAccept(
            namespace -> {
              deregisterLibrary(namespace);
              registerLibrary(library);
            });
  }

  /**
   * Returns the information of external add-on libraries that are registered.
   *
   * @return the information of external add-on libraries that are registered.
   */
  public List<LibraryInfo> getExternalAddOnLibraries() {
    return externalAddOnLibraryManager.getLibraries();
  }

  /**
   * Returns if external add-on libraries are enabled.
   *
   * @return if external add-on libraries are enabled.
   */
  public boolean externalLibrariesEnabled() {
    return externalAddOnLibraryManager.isEnabled();
  }

  /**
   * Sets if external add-on libraries are enabled.
   *
   * @param enabled if external add-on libraries are enabled.
   */
  public void setExternalLibrariesEnabled(boolean enabled) {
    externalAddOnLibraryManager.setEnabled(enabled);
  }

  /**
   * Returns the path to the external add-on libraries.
   *
   * @return the path to the external add-on libraries.
   */
  public Path getExternalLibraryPath() {
    return externalAddOnLibraryManager.getExternalLibraryPath();
  }

  /**
   * Sets the path to the external add-on libraries.
   *
   * @param path the path to the external add-on libraries.
   */
  public void setExternalLibraryPath(Path path) {
    externalAddOnLibraryManager.setExternalLibraryPath(path);
  }

  /**
   * Registers the add-on library as an external library.
   *
   * @param addOnLibrary The add-on library to register.
   */
  public void registerExternalLibrary(AddOnLibrary addOnLibrary) {
    externalAddOnLibraryManager.registerExternalAddOnLibrary(addOnLibrary);
  }

  /**
   * De-registers the add-on library with the given namespace.
   *
   * @param namespace The namespace of the library.
   */
  public void deregisterExternalLibrary(String namespace) {
    externalAddOnLibraryManager.deregisterExternalAddOnLibrary(namespace);
  }

  /**
   * Makes the external library with the given namespace available to MapTool.
   * @param namespace The namespace of the library.
   */
  public void makeExternalLibraryAvailable(String namespace) {
    externalAddOnLibraryManager.makeAvailable(namespace);
  }
}
