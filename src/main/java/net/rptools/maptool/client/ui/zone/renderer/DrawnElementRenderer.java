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
import java.util.List;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawablesGroup;
import net.rptools.maptool.model.drawing.DrawnElement;
import net.rptools.maptool.model.drawing.Pen;

// TODO Presumably the old DrawableRenderers served a performance need. Is it still the case?
//  Or is it just over-engineering. Need to check that before committing to this new approach.
//      Oh, actually, of course we do it this way. It is someone else's job to potentially do
//  spatial partitioning for performance reasons.
//      Oh, even more actually, the original looks like crap. It prerenders chunks into images, then
//  renders all those images. I do not accept that as being any better without proof.
//  TODO Don't foreground drawings (Token layer) need to clip?
public class DrawnElementRenderer {
  private final RenderHelper renderHelper;
  private final Zone zone;

  public DrawnElementRenderer(RenderHelper renderHelper, Zone zone) {
    this.renderHelper = renderHelper;
    this.zone = zone;
  }

  public void renderDrawnElement(Graphics2D g, List<DrawnElement> drawnElementList) {
    var timer = CodeTimer.get();
    timer.start("renderDrawnElement");
    try {
      renderHelper.render(g, worldG -> renderWorld(worldG, drawnElementList));
    } finally {
      timer.stop("renderDrawnElement");
    }
  }

  private void renderWorld(Graphics2D worldG, List<DrawnElement> drawableList) {
    if (drawableList.isEmpty()) {
      // Nothing to do.
      return;
    }

    final var timer = CodeTimer.get();
    final var originalClip = worldG.getClip();
    final var viewport = originalClip.getBounds();

    worldG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    for (DrawnElement element : drawableList) {
      // Handle pen size
      Pen pen = element.getPen();
      // Check zero to handle legacy pens. Besides, it doesn't make sense to have a non-visible pen.
      // TODO Surely the pen can just be updated to not have a zero opacity if that is what is
      // desired?
      if (pen.getOpacity() != 1 && pen.getOpacity() != 0) {
        worldG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pen.getOpacity()));
      }

      timer.start("drawDrawable");
      drawDrawable(worldG, pen, element.getDrawable());
      timer.stop("drawDrawable");
    }
  }

  private void drawDrawable(Graphics2D worldG, Pen pen, Drawable drawable) {
    if (drawable instanceof DrawablesGroup group) {
      for (var child : group.getDrawableList()) {
        drawDrawable(worldG, child.getPen(), child.getDrawable());
      }
      return;
    }

    // TODO New context here?

    if (pen == null) {
      pen = Pen.DEFAULT;
    }
    Stroke oldStroke = worldG.getStroke();
    worldG.setStroke(new BasicStroke(pen.getThickness(), pen.getStrokeCap(), pen.getStrokeJoin()));

    Composite oldComposite = worldG.getComposite();
    if (pen.isEraser()) {
      worldG.setComposite(AlphaComposite.Clear);
    } else if (pen.getOpacity() != 1) {
      worldG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pen.getOpacity()));
    }
    if (pen.getBackgroundMode() == Pen.MODE_SOLID) {
      if (pen.getBackgroundPaint() != null) {
        worldG.setPaint(pen.getBackgroundPaint().getPaint(renderHelper.getImageObserver()));
      } else {
        // **** Legacy support for 1.1
        worldG.setColor(new Color(pen.getBackgroundColor()));
      }
      drawBackground(worldG, drawable);
    }
    if (pen.getForegroundMode() == Pen.MODE_SOLID) {
      if (pen.getPaint() != null) {
        worldG.setPaint(pen.getPaint().getPaint(renderHelper.getImageObserver()));
      } else {
        // **** Legacy support for 1.1
        worldG.setColor(new Color(pen.getColor()));
      }
      drawForeground(worldG, drawable);
    }
    worldG.setComposite(oldComposite);
    worldG.setStroke(oldStroke);
  }

  private void drawBackground(Graphics2D worldG, Drawable drawable) {}

  private void drawForeground(Graphics2D worldG, Drawable drawable) {}
}
