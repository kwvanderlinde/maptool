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
package net.rptools.maptool.client.ui.zone.renderer.instructions;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.ui.theme.LabelBackgrounds;
import net.rptools.maptool.model.drawing.DrawableNoise;

public sealed interface RenderInstruction {
  interface Meta {
    record StartUnbufferedLayer(String layerName, ClipType clipType) implements RenderInstruction {}

    record StartBufferedLayer(
        String layerName, ClipType clipType, BlendMode blendMode, double opacity)
        implements RenderInstruction {}

    record FinishLayer(String layerName) implements RenderInstruction {}

    record SwitchAlphaMode(AlphaMode mode) implements RenderInstruction {}

    record SetClipType(ClipType clipType) implements RenderInstruction {}
  }

  record ClearScreen(Color color) implements RenderInstruction {}

  record FillFrameBuffer(Paint paint, double opacity) implements RenderInstruction {
    public FillFrameBuffer(Paint paint) {
      this(paint, 1.);
    }
  }

  record BoxedString(Point2D center, String text, LabelBackgrounds background, Color foreground)
      implements RenderInstruction {
    public BoxedString(ScreenPoint center, String text) {
      this(center, text, LabelBackgrounds.BOX_GRAY, Color.black);
    }

    public BoxedString(double centerX, double centerY, String text) {
      this(new ScreenPoint(centerX, centerY), text);
    }

    public BoxedString(
        double centerX,
        double centerY,
        String text,
        LabelBackgrounds background,
        Color foreground) {
      this(new ScreenPoint(centerX, centerY), text, background, foreground);
    }
  }

  record Noise(DrawableNoise noise) implements RenderInstruction {}

  record ImageAsset(
      MD5Key id, Rectangle2D preTransformBounds, AffineTransform transform, double opacity)
      implements RenderInstruction {
    public ImageAsset(MD5Key id, AffineTransform transform) {
      this(id, transform, 1.);
    }

    public ImageAsset(MD5Key id, AffineTransform transform, double opacity) {
      this(id, null, transform, opacity);
    }
  }

  record Fill(Shape shape, Paint paint, double opacity) implements RenderInstruction {}

  record Stroke(Shape shape, Paint paint, BasicStroke stroke, double opacity)
      implements RenderInstruction {}
}
