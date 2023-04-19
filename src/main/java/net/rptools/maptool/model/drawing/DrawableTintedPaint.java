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
package net.rptools.maptool.model.drawing;

import java.awt.Color;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import net.rptools.maptool.server.proto.drawing.DrawablePaintDto;

public class DrawableTintedPaint extends DrawablePaint {
  private final DrawablePaint baseDrawablePaint;
  private final Color tint;

  public DrawableTintedPaint(DrawablePaint baseDrawablePaint, Color tint) {
    this.baseDrawablePaint = baseDrawablePaint;
    this.tint = tint;
  }

  @Override
  public Paint getPaint(double offsetX, double offsetY, double scale, ImageObserver... observers) {
    return new TintedPaint(
        this.baseDrawablePaint.getPaint(offsetX, offsetY, scale, observers), this.tint);
  }

  @Override
  public Paint getCenteredPaint(
      double centerX, double centerY, double width, double height, ImageObserver... observers) {
    return new TintedPaint(
        this.baseDrawablePaint.getCenteredPaint(centerX, centerY, width, height, observers),
        this.tint);
  }

  @Override
  public DrawablePaintDto toDto() {
    return null;
  }

  private static class TintedPaint implements Paint {
    private final Paint basePaint;
    private final Color tint;

    public TintedPaint(Paint basePaint, Color tint) {
      this.basePaint = basePaint;
      this.tint = tint;
    }

    @Override
    public PaintContext createContext(
        ColorModel cm,
        Rectangle deviceBounds,
        Rectangle2D userBounds,
        AffineTransform xform,
        RenderingHints hints) {
      return new Context(basePaint.createContext(cm, deviceBounds, userBounds, xform, hints), tint);
    }

    @Override
    public int getTransparency() {
      return basePaint.getTransparency();
    }
  }

  private static class Context implements PaintContext {
    private final PaintContext base;
    private final Color tint;

    public Context(PaintContext base, Color tint) {
      this.base = base;
      this.tint = tint;
    }

    @Override
    public void dispose() {
      base.dispose();
    }

    @Override
    public ColorModel getColorModel() {
      return base.getColorModel();
    }

    @Override
    public Raster getRaster(int x, int y, int w, int h) {
      final var baseRaster = (WritableRaster) this.base.getRaster(x, y, w, h);

      int[] basePixelBuffer = new int[4];
      for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
          basePixelBuffer = baseRaster.getPixel(j, i, basePixelBuffer);

          basePixelBuffer[0] = basePixelBuffer[0] * tint.getRed() / 255;
          basePixelBuffer[1] = basePixelBuffer[1] * tint.getGreen() / 255;
          basePixelBuffer[2] = basePixelBuffer[2] * tint.getBlue() / 255;

          baseRaster.setPixel(j, i, basePixelBuffer);
        }
      }

      return baseRaster;
    }
  }
}
