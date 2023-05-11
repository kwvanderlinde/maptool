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
import jdk.incubator.vector.VectorMask;
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
  private static final int PART_COUNT;
  private static final VectorSpecies<Integer> INT_SPECIES;
  private static final VectorSpecies<Byte> BYTE_SPECIES;
  private static final ShortVector TWO_FIVE_FIVE;
  private static final VectorMask<Short> COLOR_ONLY_MASK;

  static {
    // We need to convert our ints to the constituent four bytes, then expand those to four shorts.
    // So we need the bit size of the int and byte species to be half that of the short species. If
    // there's no room to make a smaller one, we must act in parts instead, at a penalty.
    PART_COUNT = (SHORT_SPECIES.vectorBitSize() < 128) ? 2 : 1;
    INT_SPECIES =
        VectorSpecies.of(
            int.class, VectorShape.forBitSize(SHORT_SPECIES.vectorBitSize() * PART_COUNT / 2));

    BYTE_SPECIES = INT_SPECIES.withLanes(byte.class);
    TWO_FIVE_FIVE = ShortVector.broadcast(SHORT_SPECIES, (short) 0xFF);
    COLOR_ONLY_MASK =
        unpackFromArgb(IntVector.broadcast(INT_SPECIES, 0x00_FF_FF_FF), 0)
            .compare(VectorOperators.NE, 0);
  }

  private static final Composite BlendedLightsVectorized =
      new LightingComposite(new ScreenBlenderVectorized());
  private static final Composite BlendedLights = new LightingComposite(new ScreenBlender());
  public static final Composite OverlaidLightsVectorized =
      new LightingComposite(new ConstrainedBrightenBlenderVectorized());
  public static final Composite OverlaidLights =
      new LightingComposite(new ConstrainedBrightenBlender());

  /**
   * Used to blend lights together to give an additive effect.
   *
   * <p>To use to good effect, the initial image should be black (when used together with {@link
   * #OverlaidLightsVectorized}) or clear (when used with {@link java.awt.AlphaComposite}) and then
   * lights should be added to it one-by-one.
   *
   * @param allowVectorized If true, an explicitly vectorized implementation may be returned. This
   *     flag can be ignored if suitable conditions for vectorization are not present.
   */
  public static Composite getBlendedLights(boolean allowVectorized) {
    return allowVectorized ? BlendedLightsVectorized : BlendedLights;
  }

  /**
   * Used to blend lighting results with an underlying image.
   *
   * @param allowVectorized If true, an explicitly vectorized implementation may be returned. This
   *     flag can be ignored if suitable conditions for vectorization are not present.
   */
  public static Composite getOverlaidLights(boolean allowVectorized) {
    return allowVectorized ? OverlaidLightsVectorized : OverlaidLights;
  }

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
   * Converts a vector of ARGB pixels into a vector of individual components.
   *
   * @param vector The ARGB vector to convert.
   * @param part The part to pick if the expansion won't fit into SHORT_SPECIES.
   * @return The color component vector for the chosen part of {@code vector}.
   */
  private static ShortVector unpackFromArgb(IntVector vector, int part) {
    return (ShortVector)
        vector
            .reinterpretAsBytes()
            .convertShape(VectorOperators.ZERO_EXTEND_B2S, SHORT_SPECIES, part);
  }

  /**
   * Converts a vector of color components into a vector of ARGB pixels.
   *
   * @param vector The color components vector to pack.
   * @param part If there is extra space in the result, decides where to put the converted values
   *     within the result.
   * @return The packed ARGB equivalent of {@code vector}.
   */
  private static IntVector packToArgb(ShortVector vector, int part) {
    return vector.convertShape(VectorOperators.S2B, BYTE_SPECIES, -part).reinterpretAsInts();
  }

  /**
   * Renormalizes a product of bytes by magically dividing by 255.
   *
   * <p>This is very non-obvious, but is described well in this course sheet: <a
   * href="https://docs.google.com/document/d/1tNrMWShq55rfltcZxAx1N-6f82Dt7MWLDHm-5GQVEnE/edit">Dividing
   * by 255</a>.
   *
   * <p>The SWAR technique of multiplying in parallel is inapplicable to our operations, so that is
   * not represented anywhere.
   *
   * @param vector A vector contains results of byte products, where each value must be in the range
   *     0 .. 0xFFFF.
   * @return A vector containing the normalized values in the range 0 .. 0xFF.
   */
  private static ShortVector renormalize(ShortVector vector) {
    return vector.lanewise(VectorOperators.LSHR, 8).add(vector).lanewise(VectorOperators.LSHR, 8);
  }

  /**
   * Equivalent of {@link ##renormalize(jdk.incubator.vector.ShortVector)}, but converting an int to
   * a byte.
   *
   * @param x The result of a byte product, in the range 0 .. 0xFFFF.
   * @return The normalized value of x, in the range 0 .. 0xFF.
   */
  private static int renormalize(int x) {
    return (x + (x >>> 8)) >>> 8;
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

      // Motivation: can't use SWAR multiplication on three color components at once.
      // Idea: mixed outer and inner bit hacks so that we can multiply three bytes by a single other
      // byte.

      for (int x = 0; x < samples; ++x) {
        final int srcPixel = srcPixels[x];
        final int dstPixel = dstPixels[x];

        final int resultR, resultG, resultB;

        {
          final var dstC = (dstPixel >>> 16) & 0xFF;
          final var srcC = (srcPixel >>> 16) & 0xFF;
          resultR = (renormalize((255 - srcC) * dstC) << 16);
        }
        {
          final var dstC = (dstPixel >>> 8) & 0xFF;
          final var srcC = (srcPixel >>> 8) & 0xFF;
          resultG = (renormalize((255 - srcC) * dstC) << 8);
        }
        {
          final var dstC = dstPixel & 0xFF;
          final var srcC = srcPixel & 0xFF;
          resultB = renormalize((255 - srcC) * dstC);
        }

        outPixels[x] = srcPixel + (resultR | resultG | resultB);
      }
    }
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
  private static final class ScreenBlenderVectorized implements Blender {
    public void blendRow(int[] dstPixels, int[] srcPixels, int[] outPixels, int samples) {
      assert dstPixels.length >= samples
          && srcPixels.length >= samples
          && outPixels.length >= samples;
      assert samples % INT_SPECIES.length() == 0;

      final var upperBound = INT_SPECIES.loopBound(samples);
      for (int offset = 0; offset < upperBound; offset += INT_SPECIES.length()) {
        var result = IntVector.zero(INT_SPECIES);
        for (int part = 0; part < PART_COUNT; ++part) {
          final var srcC =
              unpackFromArgb(IntVector.fromArray(INT_SPECIES, srcPixels, offset), part);
          final var dstC =
              unpackFromArgb(IntVector.fromArray(INT_SPECIES, dstPixels, offset), part);

          final var x = srcC.add(renormalize(TWO_FIVE_FIVE.sub(srcC).mul(dstC)), COLOR_ONLY_MASK);
          result = result.or(packToArgb(x, part));
        }

        result.intoArray(outPixels, offset);
      }
    }
  }

  /**
   * Inspired by overlay blending, this is an alternative that never darkens and which boosts dark
   * components by no more than some multiple of the component.
   *
   * <p>When the bottom component ({@code dstC}) is low, the result is between the bottom component
   * and twice the bottom component. The exact result is determined by using the top component
   * ({@code srcC}) to interpolate between the two bounds.
   *
   * <p>When the bottom component is high, the result is between the bottom component and 255, again
   * using the top component to interpolate between the two.
   *
   * <p>The transition point from low to high is at 0.5 (or 128 as an int).
   *
   * <p>When viewed as a function of the bottom component, this blend mode is built from two linear
   * pieces. The first piece has a slope no less than 1, and the second piece has a slope no greater
   * than one. So in addition to brightening, this function increases the contrast in dark regions,
   * while tapering off in bright regions.
   *
   * <p>The behaviour is actually is very similar to overlay, but where the value at the transition
   * point is always greater than the bottom component (in overlay it can be greater than or less
   * than the bottom component). It also has a much looser relation to the soft light blend mode,
   * which inspired the idea of constraining the increase of dark components by some multiple.
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
  private static final class ConstrainedBrightenBlenderVectorized implements Blender {
    public void blendRow(int[] dstPixels, int[] srcPixels, int[] outPixels, int samples) {
      assert dstPixels.length >= samples
          && srcPixels.length >= samples
          && outPixels.length >= samples;
      assert samples % INT_SPECIES.length() == 0;

      final var upperBound = INT_SPECIES.loopBound(samples);
      for (int offset = 0; offset < upperBound; offset += INT_SPECIES.length()) {
        var result = IntVector.zero(INT_SPECIES);
        for (int part = 0; part < PART_COUNT; ++part) {
          final var srcC =
              unpackFromArgb(IntVector.fromArray(INT_SPECIES, srcPixels, offset), part);
          final var dstC =
              unpackFromArgb(IntVector.fromArray(INT_SPECIES, dstPixels, offset), part);

          // Vectorized calculation of min(dstC, 255 - dstC)
          final var predicate = dstC.compare(VectorOperators.LT, 128);
          final var dstContribution = TWO_FIVE_FIVE.sub(dstC).blend(dstC, predicate);

          final var x = dstC.add(renormalize(dstContribution.mul(srcC)), COLOR_ONLY_MASK);
          result = result.or(packToArgb(x, part));
        }

        result.intoArray(outPixels, offset);
      }
    }
  }

  private static final class ConstrainedBrightenBlender implements Blender {
    public void blendRow(int[] dstPixels, int[] srcPixels, int[] outPixels, int samples) {
      assert dstPixels.length >= samples
          && srcPixels.length >= samples
          && outPixels.length >= samples;
      assert samples % INT_SPECIES.length() == 0;

      for (int x = 0; x < samples; ++x) {
        final int srcPixel = srcPixels[x];
        final int dstPixel = dstPixels[x];

        int resultPixel = 0;
        for (int shift = 0; shift < 24; shift += 8) {
          final var dstC = (dstPixel >>> shift) & 0xFF;
          final var srcC = (srcPixel >>> shift) & 0xFF;

          // dstPart is the minimum of dstC and 255 - dstC.
          final var dstPart = dstC + (dstC >>> 7) * (255 - 2 * dstC);

          resultPixel |= (renormalize(srcC * dstPart) << shift);
        }
        // This deliberately keeps the bottom alpha around.
        resultPixel += dstPixel;

        outPixels[x] = resultPixel;
      }
    }
  }
}
