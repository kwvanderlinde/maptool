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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;
import net.rptools.maptool.server.proto.drawing.DrawablePaintDto;

public class DrawableTintedPaint extends DrawablePaint {
  private final DrawablePaint baseDrawablePaint;
  private final RasterOp tintOp;

  public DrawableTintedPaint(DrawablePaint baseDrawablePaint, Color tint) {
    this.baseDrawablePaint = baseDrawablePaint;
    this.tintOp = new TintOp(tint);
  }

  @Override
  public Paint getPaint(double offsetX, double offsetY, double scale, ImageObserver... observers) {
    return new TintedPaint(
        this.baseDrawablePaint.getPaint(offsetX, offsetY, scale, observers), tintOp);
  }

  @Override
  public Paint getCenteredPaint(
      double centerX, double centerY, double width, double height, ImageObserver... observers) {
    return new TintedPaint(
        this.baseDrawablePaint.getCenteredPaint(centerX, centerY, width, height, observers),
        tintOp);
  }

  @Override
  public DrawablePaintDto toDto() {
    return null;
  }

  private static class TintedPaint implements Paint {
    private final Paint basePaint;
    private final RasterOp rasterOp;

    public TintedPaint(Paint basePaint, RasterOp rasterOp) {
      this.basePaint = basePaint;
      this.rasterOp = rasterOp;
    }

    @Override
    public PaintContext createContext(
        ColorModel cm,
        Rectangle deviceBounds,
        Rectangle2D userBounds,
        AffineTransform xform,
        RenderingHints hints) {
      return new TransformPaintContext(
          basePaint.createContext(cm, deviceBounds, userBounds, xform, hints), rasterOp);
    }

    @Override
    public int getTransparency() {
      return basePaint.getTransparency();
    }
  }

  private static class TintOp implements RasterOp {
    private int[] buffer = null;
    private final int[] premultipliedRed = new int[256];
    private final int[] premultipliedGreen = new int[256];
    private final int[] premultipliedBlue = new int[256];

    public TintOp(Color tint) {
      final var tr = tint.getRed();
      final var tg = tint.getGreen();
      final var tb = tint.getBlue();
      for (int i = 0; i < 256; ++i) {
        premultipliedRed[i] = (i * tr) / 255;
        premultipliedGreen[i] = (i * tg) / 255;
        premultipliedBlue[i] = (i * tb) / 255;
      }
    }

    private static int[] getPixels(Raster src, int x, int y, int w, int h, int[] buffer) {
      if (w == 0 || h == 0) {
        return new int[0];
      }
      if (buffer == null || buffer.length < w * h) {
        // In practice I only need buffers up to ~2000, so I don't need exponential progression.
        // I just need to avoid incrementing by 1 at a time.
        final var bufferIncrement = 256;
        final var newSize = bufferIncrement * (1 + (w * h) / bufferIncrement);
        buffer = new int[newSize];
      }

      return (int[]) src.getDataElements(x, y, w, h, buffer);
    }

    public static void setPixels(WritableRaster dest, int x, int y, int w, int h, int[] pixels) {
      if (pixels == null || w == 0 || h == 0) {
        return;
      }
      if (pixels.length < w * h) {
        throw new IllegalArgumentException("pixels array must have a length >= w*h");
      }

      dest.setDataElements(x, y, w, h, pixels);
    }

    @Override
    public WritableRaster filter(Raster src, WritableRaster dest) {
      final var width = src.getWidth();
      final var height = src.getHeight();

      buffer = getPixels(src, src.getMinX(), src.getMinY(), width, height, buffer);
      for (int i = 0; i < (width * height); ++i) {
        final var argb = buffer[i];
        buffer[i] =
            (argb & 0xFF_00_00_00)
                | premultipliedRed[(argb >> 16) & 0xFF] << 16
                | premultipliedGreen[(argb >> 8) & 0xFF] << 8
                | premultipliedBlue[(argb >> 0) & 0xFF] << 0;
      }
      setPixels(dest, dest.getMinX(), dest.getMinY(), width, height, buffer);

      return dest;
    }

    @Override
    public Rectangle2D getBounds2D(Raster src) {
      return src.getBounds();
    }

    @Override
    public WritableRaster createCompatibleDestRaster(Raster src) {
      return src.createCompatibleWritableRaster();
    }

    @Override
    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
      if (dstPt == null) {
        dstPt = new Point2D.Float();
      }
      dstPt.setLocation(srcPt.getX(), srcPt.getY());

      return dstPt;
    }

    @Override
    public RenderingHints getRenderingHints() {
      return null;
    }
  }

  private static class TransformPaintContext implements PaintContext {
    private final PaintContext base;
    private final RasterOp rasterOp;

    public TransformPaintContext(PaintContext base, RasterOp rasterOp) {
      this.base = base;
      this.rasterOp = rasterOp;
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
      final var baseRaster = this.base.getRaster(x, y, w, h);
      if (baseRaster instanceof WritableRaster writableRaster) {
        return rasterOp.filter(baseRaster, writableRaster);
      } else {
        return rasterOp.filter(baseRaster, rasterOp.createCompatibleDestRaster(baseRaster));
      }
    }
  }
}
