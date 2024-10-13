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
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction;
import net.rptools.maptool.model.drawing.DrawableNoise;
import net.rptools.maptool.model.drawing.DrawablePaint;

// TODO Just need to test the render quality is correct.
public class BoardRenderer {
  private final RenderHelper renderHelper;

  public BoardRenderer(RenderHelper renderHelper) {
    this.renderHelper = renderHelper;
  }

  public void renderBoard(Graphics2D g, RenderInstruction.Board instruction) {
    renderHelper.render(
        g, worldG -> renderBoardWorld(worldG, instruction.paint(), instruction.noise()));
  }

  public void renderMap(Graphics2D g, RenderInstruction.Map instruction) {
    renderHelper.render(
        g,
        worldG ->
            renderMapWorld(
                worldG,
                instruction.mapImage(),
                instruction.offsetX(),
                instruction.offsetY(),
                instruction.scaleX(),
                instruction.scaleY()));
  }

  private void renderBoardWorld(
      Graphics2D worldG, DrawablePaint drawablePaint, @Nullable DrawableNoise noise) {
    AppPreferences.renderQuality.get().setRenderingHints(worldG);

    var originalClip = worldG.getClip();
    var bounds = originalClip.getBounds();

    // Background texture
    Paint paint = drawablePaint.getPaint(0, 0, 1, renderHelper.getImageObserver());
    worldG.setPaint(paint);
    worldG.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

    // Only apply the noise if the feature is on and the background a textured paint
    if (noise != null && paint instanceof TexturePaint) {
      worldG.setPaint(noise.getPaint(0, 0, 1));
      worldG.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
  }

  private void renderMapWorld(
      Graphics2D worldG,
      BufferedImage mapImage,
      int offsetX,
      int offsetY,
      double scaleX,
      double scaleY) {
    AppPreferences.renderQuality.get().setRenderingHints(worldG);

    worldG.drawImage(
        mapImage,
        offsetX,
        offsetY,
        (int) (mapImage.getWidth() * scaleX),
        (int) (mapImage.getHeight() * scaleY),
        renderHelper.getImageObserver());
  }
}
