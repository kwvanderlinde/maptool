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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;

public class TokenRenderer {
  private final RenderHelper renderHelper;
  private final Zone zone;

  public TokenRenderer(RenderHelper renderHelper, Zone zone) {
    this.renderHelper = renderHelper;
    this.zone = zone;
  }

  // TODO Methinks offsetX, offsetY is the position of the SelectionSet relative to the actual
  //  token. Which means I should be able to ask the caller to translate the bounds first. Though
  //  our figure logic would suffer.
  public void renderTokens(
      Graphics2D g, PlayerView view, Token token, BufferedImage image, Rectangle footprintBounds) {
    double scale = renderHelper.getScale();

    // handle flipping
    BufferedImage workImage = image;
    if (token.isFlippedX() || token.isFlippedY()) {
      // TODO Surely this can be done via AffineTransform, no need to create new images?
      workImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getTransparency());

      int workW = image.getWidth() * (token.isFlippedX() ? -1 : 1);
      int workH = image.getHeight() * (token.isFlippedY() ? -1 : 1);
      int workX = token.isFlippedX() ? image.getWidth() : 0;
      int workY = token.isFlippedY() ? image.getHeight() : 0;

      Graphics2D wig = workImage.createGraphics();
      wig.drawImage(image, workX, workY, workW, workH, null);
      wig.dispose();
    }

    // Draw token
    double iso_ho = 0;
    Dimension imgSize = new Dimension(workImage.getWidth(), workImage.getHeight());
    if (token.getShape() == Token.TokenShape.FIGURE) {
      double th = token.getHeight() * (double) footprintBounds.width / token.getWidth();
      iso_ho = footprintBounds.height - th;
      footprintBounds =
          new Rectangle(
              footprintBounds.x, footprintBounds.y - (int) iso_ho, footprintBounds.width, (int) th);
      iso_ho = iso_ho * scale;
    }
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

    // TODO This dependence on scaled with etc would probably not be needed if we operated in
    //  world space.

    ScreenPoint newScreenPoint =
        ScreenPoint.fromZonePoint(
            renderHelper.getScale(),
            renderHelper.getViewOffsetX(),
            renderHelper.getViewOffsetY(),
            footprintBounds.x,
            footprintBounds.y);
    // Tokens are centered on the image center point
    int x = (int) (newScreenPoint.x);
    int y = (int) (newScreenPoint.y);

    int tx = x + offsetx;
    int ty = y + offsety + (int) iso_ho;

    int scaledWidth = (int) (footprintBounds.width * scale);
    int scaledHeight = (int) (footprintBounds.height * scale);

    AffineTransform at = new AffineTransform();
    at.translate(tx, ty);

    if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
      at.rotate(
          Math.toRadians(-token.getFacing() - 90),
          scaledWidth / 2 - token.getAnchor().x * scale - offsetx,
          scaledHeight / 2
              - token.getAnchor().y * scale
              - offsety); // facing defaults to down, or -90 degrees
    }
    if (token.isSnapToScale()) {
      at.scale(
          (double) imgSize.width / workImage.getWidth(),
          (double) imgSize.height / workImage.getHeight());
      at.scale(scale, scale);
    } else {
      if (token.getShape() == Token.TokenShape.FIGURE) {
        at.scale(
            (double) scaledWidth / workImage.getWidth(),
            (double) scaledWidth / workImage.getWidth());
      } else {
        at.scale(
            (double) scaledWidth / workImage.getWidth(),
            (double) scaledHeight / workImage.getHeight());
      }
    }

    g.drawImage(workImage, at, renderHelper.getImageObserver());
  }
}
