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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Helper type to handle creating and moving temporary files. */
public class TempFileThenRenameOperation implements AutoCloseable {
  private final Path temporaryPath;

  // TODO Use paths instead.
  public TempFileThenRenameOperation(Path tempRoot) throws IOException {
    this.temporaryPath = Files.createTempFile(tempRoot, "tmp.", "");
  }

  /**
   * Move the temporary file to its final location.
   *
   * <p>The move will be done atomically if possible, but a non-atomic move may be used as a
   * fallback.
   *
   * @throws java.io.IOException If the move fails.
   */
  public void moveTo(Path finalPath) throws IOException {
    try {
      Files.move(temporaryPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporaryPath, finalPath);
    }
  }

  public void write(InputStream in) throws IOException {
    try (var temporaryFileStream = Files.newOutputStream(temporaryPath)) {
      in.transferTo(temporaryFileStream);
    }
  }

  /**
   * Clean up the temporary file.
   *
   * <p>If the operation has been committed, there is no longer a temporary file and this does
   * nothing.
   *
   * @throws java.io.IOException If the file could not be deleted.
   */
  @Override
  public void close() throws IOException {
    Files.deleteIfExists(temporaryPath);
  }
}
