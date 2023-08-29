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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.rptools.maptool.client.events.ResourceLibraryAdded;
import net.rptools.maptool.events.MapToolEventBus;

public class ResourceLibraryManager {
  /**
   * Describes a resource library.
   *
   * @param id An internal ephemeral identifier for the library. Unique.
   * @param name A human-readable name for the library. Not unique.
   * @param path The absolute path to the library on the local system.
   */
  public record ResourceLibrary(int id, String name, Path path) {}

  private final Random random = new Random();

  // Number of resource libraries will always be "small", so a plain list suffices.
  private List<ResourceLibrary> libraries = null;

  public ResourceLibraryManager() {}

  private int newId() {
    // TODO Make sure it is definitely unique.
    // IDs are between 100,000,000 and 999,999,999, i.e., is a 9-digit number.
    return random.nextInt(100_000_000, 1_000_000_000);
  }

  public List<ResourceLibrary> getLibraries() {
    if (libraries == null) {
      final List<ResourceLibrary> libraries = new ArrayList<>();
      final Set<File> roots = AppPreferences.getAssetRoots();
      for (final var root : roots) {
        if (!root.exists()) {
          // No point including missing roots.
          continue;
        }

        final var id = newId();
        System.out.printf("Resource library: %d is %s%n", id, root);
        final var name = root.getName();
        libraries.add(new ResourceLibrary(id, name, root.toPath()));
      }
      this.libraries = libraries;
    }

    return this.libraries;
  }

  public void addLibrary(Path root) {
    root = root.toAbsolutePath();

    final var file = root.toFile();
    if (!file.exists()) {
      return;
    }

    final var id = newId();
    System.out.printf("Resource library: %d is %s%n", id, root);
    final var resourceLibrary = new ResourceLibrary(id, root.getFileName().toString(), root);
    this.getLibraries().add(resourceLibrary);

    AppPreferences.addAssetRoot(file);

    new MapToolEventBus().getMainEventBus().post(new ResourceLibraryAdded(resourceLibrary));
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

    AppPreferences.removeAssetRoot(root.toFile());
  }
}
