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
package net.rptools.maptool.client.ui.zone;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import net.rptools.lib.CodeTimer;

/**
 * At any given time, there are three {@link Image} instances of the same dimensions:
 *
 * <ol>
 *   <li>The results buffer, to which all results will be drawn.
 *   <li>The back buffer, to which temporary results will be drawn before being composited into the
 *       results buffer.
 * </ol>
 *
 * <p>Note: The GdxRenderer also has a "spare buffer" which is needed to blit the back buffer with
 * the results buffer, after which the spare buffer becomes the results buffer. By contrast, in
 * Swing, the back buffer can be directly composited with the results buffer.
 */
// TODO Direct compositing sounds inefficient. Can we avoid it by piping two inputs images to one
//  output?
public class ImageBufferManager {
  private int width;
  private int height;
  private GraphicsConfiguration configuration;

  private BufferedImage backBuffer;
  private BufferedImage resultsBuffer;

  private ImageBufferManager(int width, int height, GraphicsConfiguration configuration) {
    this.width = width;
    this.height = height;
    this.configuration = configuration;

    allocate();
  }

  public ImageBufferManager() {
    this(
        1,
        1,
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration());
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void update(int width, int height, GraphicsConfiguration configuration) {
    if (this.width != width || this.height != height || !this.configuration.equals(configuration)) {
      this.width = width;
      this.height = height;
      this.configuration = configuration;
      allocate();
    }
  }

  private void allocate() {
    this.backBuffer =
        this.configuration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    this.resultsBuffer =
        this.configuration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
  }

  public void drawToResultsBuffer(Consumer<Graphics2D> drawer) {
    var g = resultsBuffer.createGraphics();
    try {
      drawer.accept(g);
    } finally {
      g.dispose();
    }
  }

  public void blitResultsTo(Graphics2D target) {
    target.drawImage(resultsBuffer, 0, 0, null);
  }

  public void drawToBackBuffer(Composite blitComposite, Consumer<Graphics2D> drawer) {
    var backBufferG = backBuffer.createGraphics();
    try {
      drawer.accept(backBufferG);
    } finally {
      backBufferG.dispose();
    }

    var timer = CodeTimer.get();
    timer.start("ImageBufferManager-drawToBackBuffer:blit");
    drawToResultsBuffer(
        resultsBufferG -> {
          resultsBufferG.setComposite(blitComposite);
          resultsBufferG.drawImage(backBuffer, null, null);
        });
    timer.stop("ImageBufferManager-drawToBackBuffer:blit");
  }
}
