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
import java.awt.image.VolatileImage;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A single-element pool of BufferedImages.
 *
 * <p>This is particularly useful for rendering the lighting overlays in ZoneRenderer.
 */
public class BufferedImagePool {
  private static final Logger log = LogManager.getLogger(BufferedImagePool.class);

  public interface Handle extends AutoCloseable {
    @Override
    void close();

    Image get();

    Graphics2D createGraphics();
  }

  private final class VolatileImageHandle implements Handle {
    private final VolatileImage image;

    private VolatileImageHandle(VolatileImage image) {
      this.image = image;
    }

    public Image get() {
      return image;
    }

    @Override
    public void close() {
      release(image);
    }

    public Graphics2D createGraphics() {
      return image.createGraphics();
    }
  }

  private int width;
  private int height;
  private @Nonnull GraphicsConfiguration configuration;

  private final int maxSize;
  private final Deque<VolatileImage> available = new ArrayDeque<>();
  private final Set<VolatileImage> checkedOut = Collections.newSetFromMap(new IdentityHashMap<>());

  public BufferedImagePool(int maxSize) {
    this.maxSize = maxSize;
    this.width = 0;
    this.height = 0;
    this.configuration =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration();
  }

  /** Removes all images from the pool. */
  private void clear() {
    this.available.clear();
    this.checkedOut.clear();
  }

  public int getWidth() {
    return this.width;
  }

  public void setWidth(int width) {
    if (width != this.width) {
      this.width = width;
      this.clear();
    }
  }

  public int getHeight() {
    return this.height;
  }

  public void setHeight(int height) {
    if (height != this.height) {
      this.height = height;
      this.clear();
    }
  }

  public void setConfiguration(GraphicsConfiguration configuration) {
    if (!this.configuration.equals(configuration)) {
      this.configuration = configuration;
      this.clear();
    }
  }

  public Handle acquire() {
    if (available.isEmpty()) {
      final var newInstance =
          this.configuration.createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT);

      // If there is still space available in the pool, start tracking the newly created image.
      // Otherwise, we can just return it and forget about it.
      if (available.size() + checkedOut.size() < maxSize) {
        checkedOut.add(newInstance);
      } else {
        log.info("Needed new instance but pool is full.");
      }

      return new VolatileImageHandle(newInstance);
    }

    // We've got an existing instance. We'll need to make sure it's cleared before yielding it.
    final var instance = available.removeLast();
    checkedOut.add(instance);

    return new VolatileImageHandle(instance);
  }

  private void release(VolatileImage image) {
    final var wasCheckedOut = checkedOut.remove(image);
    if (wasCheckedOut) {
      available.addLast(image);
    } else {
      log.info("Attempted to release an instance not in the pool.");
    }
  }
}
