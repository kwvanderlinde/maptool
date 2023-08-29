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
package net.rptools.maptool.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.rptools.maptool.client.events.ResourceLibraryAdded;
import net.rptools.maptool.events.MapToolEventBus;

public class ResourceLibraryManager {
  /**
   * Describes a resource library.
   *
   * @param id An internal ephemeral identifier for the library. Unique.
   * @param name A human-readable name for the library. Not unique.
   * @param path The absolute path to the libray on the local system.
   */
  public record ResourceLibrary(int id, String name, Path path) {}

  private final Random random = new Random();

  // Number of resource libraries will always be "small", so a plain list suffices.
  private List<ResourceLibrary> libraries = null;

  public ResourceLibraryManager() {}

  public List<ResourceLibrary> getLibraries() {
    if (libraries == null) {
      final List<ResourceLibrary> libraries = new ArrayList<>();
      final List<Path> roots = AppPreferences.getAssetRootPaths();
      for (final var root : roots) {
        if (!root.toFile().exists()) {
          // No point including missing roots.
          continue;
        }

        // TODO Make sure it is definitely unique.
        final var id = random.nextInt();
        final var name = root.getFileName().toString();
        libraries.add(new ResourceLibrary(id, name, root));
      }
      this.libraries = libraries;
    }

    return this.libraries;
  }

  public ResourceLibrary addLibrary(Path root) {
    root = root.toAbsolutePath();

    // TODO Make sure it is definitely unique.
    final var id = random.nextInt();
    final var resourceLibrary = new ResourceLibrary(id, root.getFileName().toString(), root);
    this.getLibraries().add(resourceLibrary);

    AppPreferences.addAssetRootPath(root);

    new MapToolEventBus().getMainEventBus().post(new ResourceLibraryAdded(resourceLibrary));

    return resourceLibrary;
  }

  public void removeLibrary(Path root) {
    root = root.toAbsolutePath();

    final var iterator = libraries.iterator();
    while (iterator.hasNext()) {
      final var library = iterator.next();
      if (library.path.equals(root)) {
        iterator.remove();
      }
    }

    AppPreferences.removeAssetRootPath(root);
  }
}
