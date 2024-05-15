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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import net.rptools.lib.MD5Key;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.util.ImageManager;

/**
 * Support "asset://" in Swing components
 *
 * @author Azhrei
 */
public class AssetURLStreamHandler extends URLStreamHandler {

  @Override
  protected URLConnection openConnection(URL u) throws IOException {
    return new AssetURLConnection(u);
  }

  public static class AssetURLConnection extends URLConnection {
    private final boolean isRaw;
    private InputStream in;

    public AssetURLConnection(URL url) throws IOException {
      super(url);

      this.isRaw =
          (url.getQuery() != null)
              && Arrays.stream(url.getQuery().split("&"))
                  .anyMatch(q -> q.equalsIgnoreCase("raw=true"));
    }

    @Override
    public void connect() throws IOException {
      if (this.connected) {
        return;
      }

      if (isRaw) {
        // TODO I don't want to block.
        var asset = AssetManager.getAssetAndWait(new MD5Key(url.getHost()));
        this.in = new ByteArrayInputStream(asset.getData());
      } else {
        var out = new PipedOutputStream();
        var in = new PipedInputStream(out);

        BufferedImage img =
            ImageManager.getImageFromUrlAsync(
                url,
                (i) -> {
                  try {
                    out.write(ImageUtil.imageToBytes(i, "png"));
                    out.flush();
                    out.close();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
        if (img == ImageManager.TRANSFERING_IMAGE) {
          this.in = in;
        } else {
          // We already have the data. Push it right away.
          this.in = new ByteArrayInputStream(ImageUtil.imageToBytes(img, "png"));
        }
      }
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return this.in;
    }
  }
}
