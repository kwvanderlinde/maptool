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
package net.rptools.maptool.client.ui.zone.renderer;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import java.util.function.Consumer;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.ui.zone.BufferedImagePool;

/**
 * Transform graphics objects into world space to enable more convenient rendering for some layers.
 *
 * <p>Also optionally renders onto an intermediate buffer.
 */
public class RenderHelper {
  private final String timerPrefix;
  private final ZoneRenderer renderer;
  private final BufferedImagePool tempBufferPool;

  private RenderHelper(
      ZoneRenderer renderer, BufferedImagePool tempBufferPool, String timerPrefix) {
    this.renderer = renderer;
    this.tempBufferPool = tempBufferPool;
    this.timerPrefix = timerPrefix;
  }

  public RenderHelper(ZoneRenderer renderer, BufferedImagePool tempBufferPool) {
    this(renderer, tempBufferPool, "RenderHelper");
  }

  public ImageObserver getImageObserver() {
    return renderer;
  }

  public RenderHelper withTimerPrefix(String timerPrefix) {
    return new RenderHelper(renderer, tempBufferPool, timerPrefix);
  }

  private void doRender(Graphics2D g, Consumer<Graphics2D> render) {
    var timer = CodeTimer.get();

    timer.start("%s-useAA", timerPrefix);
    SwingUtil.useAntiAliasing(g);
    timer.stop("%s-useAA", timerPrefix);

    timer.start("%s-setTransform", timerPrefix);
    Scale scale = renderer.getZoneScale();
    AffineTransform af = new AffineTransform();
    af.translate(scale.getOffsetX(), scale.getOffsetY());
    af.scale(scale.getScale(), scale.getScale());
    g.setTransform(af);
    timer.stop("%s-setTransform", timerPrefix);

    timer.start("%s-render", timerPrefix);
    render.accept(g);
    timer.stop("%s-render", timerPrefix);
  }

  public void render(Graphics2D g, Consumer<Graphics2D> render) {
    var timer = CodeTimer.get();
    timer.start("%s-createContext", timerPrefix);
    g = (Graphics2D) g.create();
    timer.stop("%s-createContext", timerPrefix);
    try {
      timer.start("%s-doRender", timerPrefix);
      doRender(g, render);
    } finally {
      timer.stop("%s-doRender", timerPrefix);
      timer.start("%s-disposeContext", timerPrefix);
      g.dispose();
      timer.stop("%s-disposeContext", timerPrefix);
    }
  }

  public void bufferedRender(Graphics2D g, Composite blitComposite, Consumer<Graphics2D> render) {
    var timer = CodeTimer.get();

    timer.start("%s-acquireBuffer", timerPrefix);
    if (tempBufferPool.getWidth() == renderer.getWidth()
        && tempBufferPool.getHeight() == renderer.getHeight()) {
      timer.increment(String.format("%s-matching-dimensions", timerPrefix));
      // This case only holds during regular rendering. Other rendering, such as screenshots, may
      // have different dimensions.
      try (final var entry = tempBufferPool.acquire()) {
        timer.stop("%s-acquireBuffer", timerPrefix);

        bufferedRender(entry, g, blitComposite, render);
      }
    } else {
      timer.increment(String.format("%s-mismatched-dimensions", timerPrefix));
      var buffer =
          GraphicsEnvironment.getLocalGraphicsEnvironment()
              .getDefaultScreenDevice()
              .getDefaultConfiguration()
              .createCompatibleVolatileImage(
                  renderer.getWidth(), renderer.getHeight(), Transparency.TRANSLUCENT);
      timer.stop("%s-acquireBuffer", timerPrefix);

      var handle =
          new BufferedImagePool.Handle() {
            @Override
            public void close() {}

            @Override
            public Image get() {
              return buffer;
            }

            @Override
            public Graphics2D createGraphics() {
              return buffer.createGraphics();
            }
          };

      bufferedRender(handle, g, blitComposite, render);
    }
  }

  private void bufferedRender(
      BufferedImagePool.Handle bufferHandle,
      Graphics2D g,
      Composite blitComposite,
      Consumer<Graphics2D> render) {
    var timer = CodeTimer.get();

    Image buffer = bufferHandle.get();
    Graphics2D buffG = bufferHandle.createGraphics();
    try {
      buffG.setClip(new Rectangle(0, 0, buffer.getWidth(null), buffer.getHeight(null)));
      doRender(buffG, render);
    } finally {
      buffG.dispose();
    }

    timer.start("%s-blit", timerPrefix);
    g = (Graphics2D) g.create();
    try {
      g.setComposite(blitComposite);
      g.drawImage(buffer, null, null);
    } finally {
      g.dispose();
    }
    timer.stop("%s-blit", timerPrefix);
  }
}
