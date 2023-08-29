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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

public class ResourceURLStreamHandler extends URLStreamHandler {
  @Override
  protected URLConnection openConnection(URL u) {
    return new ResourceURLConnection(u);
  }

  public static class ResourceURLConnection extends URLConnection {

    public ResourceURLConnection(URL url) {
      super(url);
    }

    @Override
    public void connect() {
      // Nothing to do
    }

    @Override
    public InputStream getInputStream() throws IOException {
      final var host = URLDecoder.decode(url.getHost(), StandardCharsets.UTF_8);
      if (MapTool.getClientId().equals(host)) {
        // Leading slash, then first part is the library name plus the library identifiers, then
        // path relative to the library root.
        final var parts = url.getPath().split("/", 3);
        final var libraryHandle = parts[1];
        final var path = parts[2];

        // TODO Could use a regex pattern for clarity, though it will be slower.
        final var index = libraryHandle.lastIndexOf('-');
        if (index < 0 || index >= libraryHandle.length() - 1) {
          throw new FileNotFoundException(url.toString());
        }

        final int id;
        try {
          id = Integer.parseInt(libraryHandle.substring(index + 1));
        } catch (NumberFormatException nfe) {
          throw new FileNotFoundException(url.toString());
        }

        final var library =
            MapTool.getResourceLibraryManager()
                .getLibraryById(id)
                .orElseThrow(() -> new FileNotFoundException(url.toString()));

        final var fullPath = library.path().resolve(path);
        if (!fullPath.startsWith(library.path())) {
          // Tried to escape the library root.
          throw new FileNotFoundException(url.toString());
        }

        return new FileInputStream(fullPath.toFile());
      } else {
        // TODO Pull and cache the resource from the identified client.
        // TODO Can we do it be direct URL to remote file, and tee it to a file? Not robust...
        // TODO Doesn't matter, this is a POC for now we can implement it later.
        throw new FileNotFoundException(url.toString());
      }
    }
  }
}
