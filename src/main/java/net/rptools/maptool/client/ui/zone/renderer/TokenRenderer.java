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
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.model.IsometricGrid;
import net.rptools.maptool.model.LookupTable;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.ImageManager;
import net.rptools.parser.ParserException;

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
      Graphics2D g,
      PlayerView view,
      Token token,
      int offsetX,
      int offsetY,
      Rectangle tokenFootprintBounds) {
    double scale = renderHelper.getScale();

    // get token image, using image table if present
    BufferedImage image = getTokenImage(token);

    // handle flipping
    BufferedImage workImage = image;
    if (token.isFlippedX() || token.isFlippedY()) {
      workImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getTransparency());

      int workW = image.getWidth() * (token.isFlippedX() ? -1 : 1);
      int workH = image.getHeight() * (token.isFlippedY() ? -1 : 1);
      int workX = token.isFlippedX() ? image.getWidth() : 0;
      int workY = token.isFlippedY() ? image.getHeight() : 0;

      Graphics2D wig = workImage.createGraphics();
      wig.drawImage(image, workX, workY, workW, workH, null);
      wig.dispose();
    }

    // OPTIMIZE: combine this with the code in renderTokens()
    Rectangle footprintBounds = token.getBounds(zone);

    // on the iso plane
    if (token.isFlippedIso()) {
      // TODO Image caching, or come up with an alternative.
      //  I am also confused, where does the flipping happen?
      //      if (flipIsoImageMap.get(token) == null) {
      workImage = IsometricGrid.isoImage(workImage);
      //      } else {
      //        workImage = flipIsoImageMap.get(token);
      //      }
      token.setHeight(workImage.getHeight());
      token.setWidth(workImage.getWidth());
      footprintBounds = token.getBounds(zone);
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
    // TODO Temp hack so the caller can act on whatever we decided the bounds to be.
    tokenFootprintBounds.setBounds(footprintBounds);

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
            footprintBounds.x + offsetX,
            footprintBounds.y + offsetY);
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

  /**
   * Checks to see if token has an image table and references that if the token has a facing
   * otherwise uses basic image
   *
   * @param token the token to get the image from.
   * @return BufferedImage
   */
  private BufferedImage getTokenImage(Token token) {
    BufferedImage image = null;
    // Get the basic image
    if (token.getHasImageTable() && token.hasFacing() && token.getImageTableName() != null) {
      LookupTable lookupTable =
          MapTool.getCampaign().getLookupTableMap().get(token.getImageTableName());
      if (lookupTable != null) {
        try {
          LookupTable.LookupEntry result = lookupTable.getLookup(token.getFacing().toString());
          if (result != null) {
            image = ImageManager.getImage(result.getImageId(), renderHelper.getImageObserver());
          }
        } catch (ParserException p) {
          // do nothing
        }
      }
    }

    if (image == null) {
      // Adds this as observer so we can repaint once the image is ready. Fixes #1700.
      image = ImageManager.getImage(token.getImageAssetId(), renderHelper.getImageObserver());
    }
    return image;
  }
}
