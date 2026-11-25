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
package net.rptools.maptool.model.library.addon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.library.AddOnsAddedEvent;
import net.rptools.maptool.model.library.AddOnsRemovedEvent;
import net.rptools.maptool.model.library.Library;
import net.rptools.maptool.model.library.proto.AddOnLibraryDto;
import net.rptools.maptool.model.library.proto.AddOnLibraryListDto;
import net.rptools.maptool.model.library.proto.AddOnLibraryListDto.AddOnLibraryEntryDto;

/** Class for managing {@link AddOnLibrary} objects. */
public class AddOnLibraryManager {

  /** "Protocol" for add-on libraries. */
  private static final String LIBRARY_PROTOCOL = "lib";

  /** The add-on libraries that are registered. */
  private final Map<String, AddOnLibrary> namespaceLibraryMap = new ConcurrentHashMap<>();

  /**
   * Is there an add-on library that would handle this path. This just checks the protocol and
   * namespace, it won't check that the full path actually exists.
   *
   * @param path the path for the library (can be full path or just part of path).
   * @return if the library at the path is handled by an add-on library.
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
   * @throws IllegalStateException if there is already an add-on library with the same namespace.
   */
  public void registerLibrary(AddOnLibrary library) {
    String namespace = library.getNamespace().toLowerCase();

    var registeredLib = namespaceLibraryMap.computeIfAbsent(namespace, k -> library);
    if (registeredLib != library) {
      throw new IllegalStateException("Library is already registered");
    }

    library.initialize();
    new MapToolEventBus()
        .getMainEventBus()
        .post(new AddOnsAddedEvent(Set.of(library.getLibraryInfo())));
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
          .post(new AddOnsRemovedEvent(Set.of(removed.getLibraryInfo())));
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
            detailDto.setName(library.getName());
            detailDto.setVersion(library.getVersion());
            detailDto.setWebsite(library.getWebsite());
            detailDto.setGitUrl(library.getGitUrl());
            detailDto.addAllAuthors(library.getAuthors());
            detailDto.setLicense(library.getLicense());
            detailDto.setNamespace(library.getNamespace());
            detailDto.setDescription(library.getDescription());
            detailDto.setShortDescription(library.getShortDescription());
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
            .collect(Collectors.toSet());

    if (!libs.isEmpty()) {
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
}
