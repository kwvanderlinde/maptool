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
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.List;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;

public class MovementRenderer {
  private final RenderHelper renderHelper;
  private final ZoneRenderer renderer;
  private final Zone zone;
  private final ZoneView zoneView;

  public MovementRenderer(
      RenderHelper renderHelper, ZoneRenderer renderer, Zone zone, ZoneView zoneView) {
    this.renderHelper = renderHelper;
    this.renderer = renderer;
    this.zone = zone;
    this.zoneView = zoneView;
  }

  public void renderMovement(
      Graphics2D g, PlayerView view, List<MovementRenderInstruction> instructions) {
    var timer = CodeTimer.get();
    timer.start("renderTokens");
    try {
      renderHelper.render(g, worldG -> renderWorld(worldG, view, instructions));
      //renderScreen((Graphics2D) g.create(), view, instructions);
    } finally {
      timer.stop("renderAuras");
    }
  }

  // TODO Rework this into render world.
  private void renderScreen(
      Graphics2D screenG, PlayerView view, List<MovementRenderInstruction> instructions) {
    Scale zoneScale = renderer.getZoneScale();

    // Regardless of vision settings, no need to render beyond the fog.
    Area clearArea = null;
    if (!view.isGMView()) {
      if (zone.hasFog() && zoneView.isUsingVision()) {
        clearArea = new Area(zoneView.getExposedArea(view));
        clearArea.intersect(zoneView.getVisibleArea(view));
      } else if (zone.hasFog()) {
        clearArea = zoneView.getExposedArea(view);
      } else if (zoneView.isUsingVision()) {
        clearArea = zoneView.getVisibleArea(view);
      }

      if (clearArea != null) {

        AffineTransform af = new AffineTransform();
        af.translate(zoneScale.getOffsetX(), zoneScale.getOffsetY());
        af.scale(zoneScale.getScale(), zoneScale.getScale());
        var clip = clearArea.createTransformedArea(af);

        screenG.clip(clip);
      }
    }

    double scale = zoneScale.getScale();
    for (var instruction : instructions) {
      var token = instruction.token();
      var footprintBounds = instruction.bounds();

      // OPTIMIZE: combine this with the code in renderTokens()
      ScreenPoint newScreenPoint =
          ScreenPoint.fromZonePoint(renderer, footprintBounds.x, footprintBounds.y);

      int scaledWidth = (int) (footprintBounds.width * scale);
      int scaledHeight = (int) (footprintBounds.height * scale);

      // Tokens are centered on the image center point
      int x = (int) (newScreenPoint.x);
      int y = (int) (newScreenPoint.y);

      if (instruction.path() != null) {
        // TODO Like renderTokens(), move this instead a separate renderPaths() call? Or should I
        //  merge renderPaths() back into renderTokens()?
        renderer.renderPath(screenG, instruction.path(), instruction.footprint());
      }

      BufferedImage image = instruction.image();

      // Draw token
      Dimension imgSize = new Dimension(image.getWidth(), image.getHeight());
      SwingUtil.constrainTo(imgSize, footprintBounds.width, footprintBounds.height);

      int offsetx = 0;
      int offsety = 0;
      if (token.isSnapToScale()) {
        offsetx =
            (int)
                (imgSize.width < footprintBounds.width
                    ? (footprintBounds.width - imgSize.width) / 2 * scale
                    : 0);
        offsety =
            (int)
                (imgSize.height < footprintBounds.height
                    ? (footprintBounds.height - imgSize.height) / 2 * scale
                    : 0);
      }
      int tx = x + offsetx;
      int ty = y + offsety;

      AffineTransform at = new AffineTransform();
      at.translate(tx, ty);

      if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
        at.rotate(
            Math.toRadians(token.getFacingInDegrees()),
            scaledWidth / 2 - token.getAnchor().x * scale - offsetx,
            scaledHeight / 2 - token.getAnchor().y * scale - offsety);
      }
      if (token.isSnapToScale()) {
        at.scale(
            (double) imgSize.width / image.getWidth(), (double) imgSize.height / image.getHeight());
        at.scale(scale, scale);
      } else {
        if (token.getShape() == Token.TokenShape.FIGURE) {
          at.scale(
              (double) scaledWidth / image.getWidth(), (double) scaledWidth / image.getWidth());
        } else {
          at.scale(
              (double) scaledWidth / image.getWidth(), (double) scaledHeight / image.getHeight());
        }
      }

      screenG.drawImage(image, at, renderer);

      var labelY = y + 10 + scaledHeight;
      var labelX = x + scaledWidth / 2;
      if (instruction.distanceTravaledToShow() != null) {
        String distance = NumberFormat.getInstance().format(instruction.distanceTravaledToShow());
        renderer.delayRendering(new LabelRenderer(renderer, distance, labelX, labelY));
        labelY += 20;
      }
      if (instruction.playerName() != null) {
        renderer.delayRendering(
            new LabelRenderer(renderer, instruction.playerName(), labelX, labelY));
      }
    }
  }

    private void renderWorld(
            Graphics2D worldG, PlayerView view, List<MovementRenderInstruction> instructions) {
        // Regardless of vision settings, no need to render beyond the fog.
        Area clearArea = null;
        if (!view.isGMView()) {
            if (zone.hasFog() && zoneView.isUsingVision()) {
                clearArea = new Area(zoneView.getExposedArea(view));
                clearArea.intersect(zoneView.getVisibleArea(view));
            } else if (zone.hasFog()) {
                clearArea = zoneView.getExposedArea(view);
            } else if (zoneView.isUsingVision()) {
                clearArea = zoneView.getVisibleArea(view);
            }

            if (clearArea != null) {
                worldG.clip(clearArea);
            }
        }

        for (var instruction : instructions) {
            var token = instruction.token();
            var footprintBounds = instruction.bounds();

            // OPTIMIZE: combine this with the code in renderTokens()
            if (instruction.path() != null) {
                // TODO Like renderTokens(), move this instead a separate renderPaths() call? Or should I
                //  merge renderPaths() back into renderTokens()?
                // TODO Hmm... this probably won't fly. with a worldG.
                renderer.renderPath(worldG, instruction.path(), instruction.footprint());
            }

            BufferedImage image = instruction.image();

            // Draw token
            Dimension imgSize = new Dimension(image.getWidth(), image.getHeight());
            SwingUtil.constrainTo(imgSize, footprintBounds.width, footprintBounds.height);

            // TODO double instead of int here.
            int offsetx = 0;
            int offsety = 0;
            if (token.isSnapToScale()) {
                offsetx =
                                (imgSize.width < footprintBounds.width
                                        ? (footprintBounds.width - imgSize.width) / 2
                                        : 0);
                offsety =
                                (imgSize.height < footprintBounds.height
                                        ? (footprintBounds.height - imgSize.height) / 2
                                        : 0);
            }
            int tx = footprintBounds.x + offsetx;
            int ty = footprintBounds.y + offsety;

            AffineTransform at = new AffineTransform();
            at.translate(tx, ty);

            if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
                at.rotate(
                        Math.toRadians(token.getFacingInDegrees()),
                        footprintBounds.width / 2. - token.getAnchor().x - offsetx,
                        footprintBounds.height / 2. - token.getAnchor().y - offsety);
            }
            if (token.isSnapToScale()) {
                at.scale(
                        (double) imgSize.width / image.getWidth(), (double) imgSize.height / image.getHeight());
            } else {
                if (token.getShape() == Token.TokenShape.FIGURE) {
                    at.scale(
                            (double) footprintBounds.width / image.getWidth(), (double) footprintBounds.width / image.getWidth());
                } else {
                    at.scale(
                            (double) footprintBounds.width / image.getWidth(), (double) footprintBounds.height / image.getHeight());
                }
            }

            worldG.drawImage(image, at, renderer);

            // TODO Label rendering is inherently world-space yet.

            // int scaledWidth = (int) (footprintBounds.width * renderer.getScale());
            // int scaledHeight = (int) (footprintBounds.height * renderer.getScale());
//            var labelY = y + 10 + scaledHeight;
//            var labelX = x + scaledWidth / 2;
//            if (instruction.distanceTravaledToShow() != null) {
//                String distance = NumberFormat.getInstance().format(instruction.distanceTravaledToShow());
//                renderer.delayRendering(new LabelRenderer(renderer, distance, labelX, labelY));
//                labelY += 20;
//            }
//            if (instruction.playerName() != null) {
//                renderer.delayRendering(
//                        new LabelRenderer(renderer, instruction.playerName(), labelX, labelY));
//            }

        }
    }
}
