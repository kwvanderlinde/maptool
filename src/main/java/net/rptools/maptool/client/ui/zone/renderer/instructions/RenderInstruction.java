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

import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.rptools.maptool.model.drawing.DrawableNoise;
import net.rptools.maptool.model.drawing.DrawablePaint;

public sealed interface RenderInstruction {
  // TODO Each batch comes with a clip. Should probably be an Area, but could be an enum given the
  //  common cases. E.g., Hard fog, clear area, soft fog, etc.
  //  ZoneCompositor should produce a stream of RenderInstruction.
  //  For fidelity with original, would need to include path with the TokenRenderInstruction
  //  and MovementRenderInstruction, but really we should render them on separate layers. Truly
  //  the compositor would output a list of rendering instructions, in the rendering order (back-
  //  to-front), with very direct meaning, e.g., render image x to rectangle y (in world space).
  //  Not everything comes down to images, but everything should be easily interpretable (minimal
  //  logic) and expressed in world space.

  record Board(DrawablePaint paint, @Nullable DrawableNoise noise) implements RenderInstruction {}

  record Map(BufferedImage mapImage, int offsetX, int offsetY, double scaleX, double scaleY) {}
}
