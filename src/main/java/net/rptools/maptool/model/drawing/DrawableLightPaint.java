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
import java.awt.GraphicsEnvironment;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.TexturePaint;
import java.awt.Transparency;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import javax.annotation.Nullable;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.server.proto.drawing.DrawableLightPaintDto;
import net.rptools.maptool.server.proto.drawing.DrawablePaintDto;
import net.rptools.maptool.util.ImageManager;

/**
 * A paint that is similar to {@link net.rptools.maptool.model.drawing.DrawableTexturePaint}, but
 * stretches the texture to a given size (rather than a scale) and centers it around the given point
 * (instead of positioning the top-left corner).
 */
public class DrawableLightPaint extends DrawablePaint implements Serializable {
  // TODO Make dependent on shape.
  // TODO Cache results statically since they are only one-per-shape.
  private static BufferedImage getDefaultTexture() {
    final var width = 512;
    final var height = 512;
    final var configuration =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration();
    final var img = configuration.createCompatibleImage(width, height, Transparency.OPAQUE);
    final var g = img.createGraphics();
    g.setPaint(Color.black);
    g.fillRect(0, 0, width, height);

    g.setPaint(
        new RadialGradientPaint(
            width / 2.f,
            height / 2.f,
            256.f,
            new float[] {0.0f, 1.0f},
            new Color[] {Color.white, Color.black}));
    g.fillRect(0, 0, width, height);
    return img;
  }

  private record HSL(float hue, float saturation, float lightness) {}

  private static HSL RGBtoHSL(int r, int g, int b) {
    final var hsb = new float[3];
    Color.RGBtoHSB(r, g, b, hsb);
    final var saturationHsb = hsb[1];
    final var brightness = hsb[2];

    final var lightness = brightness * (1 - saturationHsb / 2);
    final float saturation;
    if (lightness == 0 || lightness == 1) {
      saturation = 0;
    } else {
      saturation = (brightness - lightness) / Math.min(lightness, 1 - lightness);
    }

    return new HSL(hsb[0], saturation, lightness);
  }

  private static int HSLtoRGB(HSL hsl) {
    final var value =
        hsl.lightness() + hsl.saturation() * Math.min(hsl.lightness(), 1 - hsl.lightness());
    final var saturationHsb = (value == 0) ? 0 : 2 * (1 - hsl.lightness() / value);
    return Color.HSBtoRGB(hsl.hue(), saturationHsb, value);
  }

  private @Nullable MD5Key assetId;
  private Color tint;
  private transient @Nullable BufferedImage image;
  private transient @Nullable BufferedImage defaultedImage;

  public DrawableLightPaint(@Nullable MD5Key id, @Nullable Color tint) {
    assetId = id;
    this.tint = Objects.requireNonNullElse(tint, new Color(0xFF_FF_FF_FF, true));
  }

  @Serial
  private Object readResolve() {
    if (this.tint == null) {
      this.tint = new Color(0xFF_FF_FF_FF, true);
    }
    return this;
  }

  private BufferedImage getTexture(ImageObserver... observers) {
    if (image != null) {
      return image;
    }

    final BufferedImage texture;
    final boolean isTemporaryResult;
    if (assetId == null) {
      isTemporaryResult = false;
      texture = getDefaultTexture();
    } else {
      final var tmpTexture = ImageManager.getImage(assetId, observers);
      if (tmpTexture == ImageManager.TRANSFERING_IMAGE) {
        if (defaultedImage != null) {
          // We've already calculated a temporary result.
          return defaultedImage;
        }

        isTemporaryResult = true;
        texture = getDefaultTexture();
      } else {
        isTemporaryResult = false;
        texture = tmpTexture;
      }
    }

    final var configuration =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration();
    final var img =
        configuration.createCompatibleImage(
            texture.getWidth(), texture.getHeight(), Transparency.OPAQUE);
    final var graphics = img.createGraphics();
    try {
      final var tintHsl = RGBtoHSL(this.tint.getRed(), this.tint.getGreen(), this.tint.getBlue());
      for (int y = 0; y < img.getHeight(); ++y) {
        for (int x = 0; x < img.getWidth(); ++x) {
          final int ax =
              texture.getColorModel().getAlpha(texture.getRaster().getDataElements(x, y, null));
          if (ax == 0) {
            // Transparently black, nothing to tint.
            img.setRGB(x, y, 0);
            continue;
          }
          int rx =
              ax
                  * texture.getColorModel().getRed(texture.getRaster().getDataElements(x, y, null))
                  / 255;
          int gx =
              ax
                  * texture
                      .getColorModel()
                      .getGreen(texture.getRaster().getDataElements(x, y, null))
                  / 255;
          int bx =
              ax
                  * texture.getColorModel().getBlue(texture.getRaster().getDataElements(x, y, null))
                  / 255;

          final var textureHsl = RGBtoHSL(rx, gx, bx);
          // We want to keep the hue (and saturation I guess) of the tint, while using the
          // brightness of the texture.
          final var resultHsl =
              new HSL(
                  tintHsl.hue(),
                  tintHsl.saturation(),
                  // TODO The multiplier here could be a configurable intensity value.
                  tintHsl.lightness() * textureHsl.lightness() // * 0.75f
                  );
          final var rgb = HSLtoRGB(resultHsl);
          img.setRGB(x, y, (ax << 24) | (rgb & 0x00_ff_ff_ff));
        }
      }
    } finally {
      graphics.dispose();
    }

    if (isTemporaryResult) {
      defaultedImage = img;
    } else {
      image = img;
    }

    return img;
  }

  @Override
  public Paint getPaint(double offsetX, double offsetY, double scale, ImageObserver... observers) {
    BufferedImage texture = getTexture(observers);

    // We center the texture around the offset.
    return new TexturePaint(
        texture, new Rectangle2D.Double(-scale / 2 - offsetX, -scale / 2 - offsetY, scale, scale));
  }

  @Override
  public DrawablePaintDto toDto() {
    var dto = DrawablePaintDto.newBuilder();
    var textureDto = DrawableLightPaintDto.newBuilder().setTint(tint.getRGB());
    if (assetId != null) {
      textureDto.setAssetId(assetId.toString());
    }
    return dto.setLightPaint(textureDto).build();
  }
}
