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

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;
import java.awt.image.WritableRaster;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

/**
 * A custom Composite class to replace AlphaComposite for the purposes of mixing lights, auras, and
 * other colored effects. <a
 * href="http://www.java2s.com/Code/Java/2D-Graphics-GUI/BlendCompositeDemo.htm">...</a>
 */
public class LightingComposite implements Composite {
  private static final VectorSpecies<Short> SHORT_SPECIES = ShortVector.SPECIES_PREFERRED;
  private static final VectorSpecies<Integer> INT_SPECIES =
      VectorSpecies.of(int.class, VectorShape.forBitSize(SHORT_SPECIES.vectorBitSize() / 2));
  private static final VectorSpecies<Byte> BYTE_SPECIES = INT_SPECIES.withLanes(byte.class);
  // Note that we deliberately want sign extension in this case.
  private static final Vector<Short> NO_ALPHA_MASK =
      IntVector.zero(INT_SPECIES)
          .add(0x00_FF_FF_FF)
          .reinterpretAsBytes()
          .castShape(SHORT_SPECIES, 0);

  /**
   * Used to blend lights together to give an additive effect.
   *
   * <p>To use to good effect, the initial image should be black (when used together with {@link
   * #OverlaidLights}) or clear (when used with {@link java.awt.AlphaComposite}) and then lights
   * should be added to it one-by-one.
   */
  public static final Composite BlendedLights = new LightingComposite(new ScreenBlender());

  /** Used to blend lighting results with an underlying image. */
  public static final Composite OverlaidLights =
      new LightingComposite(new ConstrainedBrightenBlender());

  // Blenders are stateless, so no point making new ones all the time.
  private final Blender blender;

  public LightingComposite(Blender blender) {
    this.blender = blender;
  }

  private static void checkComponentsOrder(ColorModel cm) throws RasterFormatException {
    if (cm.getTransferType() != DataBuffer.TYPE_INT) {
      throw new RasterFormatException("Color model must be represented as an int array.");
    }
    if (cm instanceof DirectColorModel directCM) {
      if (directCM.getRedMask() != 0x00FF0000
          || directCM.getGreenMask() != 0x0000FF00
          || directCM.getBlueMask() != 0x000000FF
          || (directCM.getNumComponents() == 4 && directCM.getAlphaMask() != 0xFF000000)) {
        throw new RasterFormatException("Color model must be RGB or ARGB");
      }
    } else {
      throw new RasterFormatException(
          "Color model must be a DirectColorModel so that each pixel is one int");
    }
  }

  @Override
  public CompositeContext createContext(
      ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
    checkComponentsOrder(srcColorModel);
    checkComponentsOrder(dstColorModel);

    return new BlenderContext(blender);
  }

  private static final class BlenderContext implements CompositeContext {
    private final Blender blender;

    public BlenderContext(Blender blender) {
      this.blender = blender;
    }

    @Override
    public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
      final int w = Math.min(src.getWidth(), dstIn.getWidth());
      final int h = Math.min(src.getHeight(), dstIn.getHeight());

      // Make the buffers a multiple of the vector width so we don't have to do any scalar touch-up.
      final int bufferSize = -Math.floorDiv(-w, INT_SPECIES.length()) * INT_SPECIES.length();

      final int[] srcPixels = new int[bufferSize];
      final int[] dstPixels = new int[bufferSize];
      final int[] dstOutPixels = new int[bufferSize];

      for (int y = 0; y < h; y++) {
        src.getDataElements(src.getMinX(), y + src.getMinY(), w, 1, srcPixels);
        dstIn.getDataElements(dstIn.getMinX(), y + dstIn.getMinY(), w, 1, dstPixels);

        blender.blendRow(dstPixels, srcPixels, dstOutPixels, bufferSize);

        dstOut.setDataElements(dstOut.getMinX(), y + dstOut.getMinY(), w, 1, dstOutPixels);
      }
    }

    @Override
    public void dispose() {}
  }

  /**
   * Magical division by 255.
   *
   * <p>Rather than literally dividing by 255, we do bit hack that prefer multiplication and bit
   * shifts to arrive at the same result.
   *
   * <p>Example: if x = 6888, x / 255 = 27. In this method: <code>
   *     6888 * 65793   = 453182184
   *          + 1 << 23 = 461570792
   *          >>> 24    = 27
   * </code>
   *
   * @param x The number to renormalize.
   * @return x divided by 255.
   */
  private static int renorm1(int x) {
    return (x * 65793 + (1 << 23)) >>> 24;
  }

  // renrom1, but decomposed a little differently.
  private static int renorm2(int x) {
    return ((x + 128)) * 257 >>> 16;
  }

  // renorm2, but where x * 257 is instead (x * 256) + x, as bit hacks.
  private static int renorm3(int x) {
    x += 128;
    return ((x << 8) + x) >>> 16;
  }

  // renorm3, but avoids left shifts.
  private static int renorm4(int x) {
    x += 128;
    return (x + (x >>> 8)) >>> 8;
  }

  // renorm4, but not rounding addition.
  private static int renorm5(int x) {
    return (x + (x >>> 8)) >>> 8;
  }

  private static ShortVector expand(IntVector vector) {
    // Note: converting to short preserves the sign. So, e.g., (byte) 0x96 becomes (short)
    // 0xFF96. So to get the correct positive value back, we just mask off the upper bits.
    return ((ShortVector) vector.reinterpretAsBytes().castShape(SHORT_SPECIES, 0))
        .and((short) 0x00FF);
  }

  private static IntVector contract(ShortVector vector) {
    return vector.castShape(BYTE_SPECIES, 0).reinterpretAsInts();
  }

  private static ShortVector renormalize(ShortVector vector) {
    return vector.lanewise(VectorOperators.LSHR, 8).add(vector).lanewise(VectorOperators.LSHR, 8);
  }

  public interface Blender {
    /**
     * Blend source and destination pixels for a row of pixels.
     *
     * <p>The pixels must be encoded as 32-bit ARGB, and the result will be likewise.
     *
     * @param dstPixels The bottom layer pixels.
     * @param srcPixels The top layer pixels.
     * @param outPixels A buffer that this method will write results into.
     * @param samples The number of pixels in the row.
     */
    void blendRow(int[] dstPixels, int[] srcPixels, int[] outPixels, int samples);
  }

  /**
   * Additive lights based on the screen blend mode.
   *
   * <p>The result of screen blending is always greater than the top and bottom inputs.
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>When the bottom component is 0, the result is the top component.
   *   <li>When the top component is 0, the result is the bottom component.
   *   <li>When either the top component or the bottom component is maxed, the result is maxed.
   * </ul>
   */
  private static final class ScreenBlender implements Blender {
    public void blendRow(int[] dstPixels, int[] srcPixels, int[] outPixels, int samples) {
      assert dstPixels.length >= samples
          && srcPixels.length >= samples
          && outPixels.length >= samples;
      assert samples % INT_SPECIES.length() == 0;

      final var upperBound = INT_SPECIES.loopBound(samples);
      for (int offset = 0; offset < upperBound; offset += INT_SPECIES.length()) {
        final var srcC = expand(IntVector.fromArray(INT_SPECIES, srcPixels, offset));
        final var dstC = expand(IntVector.fromArray(INT_SPECIES, dstPixels, offset));

        final var x =
            renormalize(srcC.neg().add((short) 255).mul(dstC).and(NO_ALPHA_MASK)).add(srcC);
        final var result = contract(x);

        result.intoArray(outPixels, offset);
      }
    }
  }

  /**
   * Inspired by overlay blending, this is an alternative that never darkens and which boosts dark
   * components by no more than some multiple of the component.
   *
   * <p>When the bottom component ({@code dstC}) is low, the result is between the bottom component
   * and a multiple of the bottom component controlled by {@link #MAX_DARKNESS_BOOST_PER_128}. The
   * exact result is determined by using the top component ({@code srcC}) to interpolate between the
   * two bounds.
   *
   * <p>When the bottom component is high, the result is between the bottom component and 255, again
   * using the top component to interpolate between the two.
   *
   * <p>The transition point from low to high depends on the value of {@link
   * #MAX_DARKNESS_BOOST_PER_128} - the higher that value, the lower the transition point.
   *
   * <p>When viewed as a function of the bottom component, this blend mode is built from two linear
   * pieces. The first piece has a slope no less than 1, and the second piece has a slope no greater
   * than one. So in addition to brightening, this function increases the contrast in dark regions,
   * while tapering off in bright regions.
   *
   * <p>The behaviour is actually is very similar to overlay, but where the value at the transition
   * point is always greater than the bottom component (in overlay it can be greater than or less
   * than the bottom component). The relation can be best seen when {@link
   * #MAX_DARKNESS_BOOST_PER_128} is set to 128. It also has a much looser relation to the soft
   * light blend mode, which inspired the idea of constraining the increase of dark components by
   * some multiple.
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>When the bottom component is 0, the result is 0.
   *   <li>When the bottom component is maxed, the result is maxed.
   *   <li>When the top component is 0, the result is the bottom component.
   *   <li>
   * </ul>
   */
  private static final class ConstrainedBrightenBlender implements Blender {
    public void blendRow(int[] dstPixels, int[] srcPixels, int[] outPixels, int samples) {
      assert dstPixels.length >= samples
          && srcPixels.length >= samples
          && outPixels.length >= samples;
      assert samples % INT_SPECIES.length() == 0;

      final var upperBound = INT_SPECIES.loopBound(samples);
      for (int offset = 0; offset < upperBound; offset += INT_SPECIES.length()) {
        final var srcC = expand(IntVector.fromArray(INT_SPECIES, srcPixels, offset));
        final var dstC = expand(IntVector.fromArray(INT_SPECIES, dstPixels, offset));

        // 0 if < 128, 1 if >= 128. Picks between the up and down cases.
        final var predicate = dstC.lanewise(VectorOperators.LSHR, 7);
        final var up = dstC;
        final var down = dstC.neg().add((short) 255);

        final var x =
            renormalize(down.sub(up).mul(predicate).add(up).mul(srcC).and(NO_ALPHA_MASK)).add(dstC);
        final var result = contract(x);

        result.intoArray(outPixels, offset);
      }
    }
  }
}
