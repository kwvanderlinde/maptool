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

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.model.LookupTable;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.ImageManager;
import net.rptools.parser.ParserException;

/**
 * Renders tokens along with selection boxes.
 *
 * <p>Does <emph>not</emph> render things such as paths, stacks, etc., nor does it calculate whether
 * tokens are on-screen. Also, if a token shouldn't be rendered (e.g., while dragging a stamp),
 * don't pass it to this renderer!
 */
public class TokenRenderer {
  private final RenderHelper renderHelper;
  private final Zone zone;
  private final ZoneView zoneView;

  public TokenRenderer(RenderHelper renderHelper, Zone zone, ZoneView zoneView) {
    this.renderHelper = renderHelper;
    this.zone = zone;
    this.zoneView = zoneView;
  }

  public void renderTokens(Graphics2D g, PlayerView view, Zone.Layer layer, List<Token> tokens) {
    var timer = CodeTimer.get();
    timer.start("renderTokens");
    try {
      renderHelper.render(g, worldG -> renderWorld(worldG, view, layer, tokens));
    } finally {
      timer.stop("renderAuras");
    }
  }

  private void renderWorld(
      Graphics2D worldG, PlayerView view, Zone.Layer layer, List<Token> tokens) {
    // So this is pretty basic rendering still, but I think it shows how simple token rendering can
    // actually be. An individual token just needs to be transformed into the right place.

    var timer = CodeTimer.get();
    worldG.setComposite(AlphaComposite.SrcOver);

    // Use the clipped version for rendering tokens that do not pass a "Visible of FOW" check.
    // Most renderers would just check view.isGMView(), but we actually want to render unclipped
    // for stamps.
    Graphics2D clippedWorldG = worldG;
    var visibleArea = zoneView.getVisibleArea(view);
    if (!view.isGMView()
        && zoneView.isUsingVision()
        // TODO Original was layer.supportsVision(), but I think this is more accurate.
        //  Other uses of supportsVision() might be similar.
        && !layer.isStampLayer()) {
      clippedWorldG = (Graphics2D) worldG.create();

      // TODO Original converted to a GeneralPath. Is the more efficient? If it is, surely the
      //  implementation would do exactly that?
      clippedWorldG.clip(visibleArea);
    }

    // TODO Snap-to-scale vs not.

    for (var token : tokens) {
      timer.start("tokenlist-1a");
      Rectangle footprintBounds = token.getBounds(zone);
      timer.stop("tokenlist-1a");

      timer.start("tokenlist-1b");
      // get token image, using image table if present
      BufferedImage image = getTokenImage(token);
      timer.stop("tokenlist-1b");

      // region Transform code. Need to read it backwards to understand what is being done.
      AffineTransform transform = new AffineTransform();

      // 7. Finally, now that all transforms have been done around (0, 0), move to the real anchor.
      transform.translate(
          token.getX() + footprintBounds.width / 2., token.getY() + footprintBounds.height / 2.);

      // 6. Rotate the token if needed.
      if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
        transform.rotate(Math.toRadians(token.getFacingInDegrees()));
      }

      // 5. Move so the anchor point is at (0, 0). Makes rotations around it easier.
      transform.translate(token.getAnchorX(), token.getAnchorY());

      // 4. "Flip iso". Has nothing to do with flipping, just transforming for iso renderering.
      //    Look, I really don't get this. We should do this automatically for non-figures on iso
      //    maps, and it makes no sense on other maps.
      if (token.isFlippedIso()) {
        // This isn't a proper isometric skew. In fact it's based on an assumption that only holds
        // for isometric grids (width == 2 * height, footprint height resulting image height).
        // This calculate is extremely non-obvious, but derives from these steps legacy steps:
        // 1. Rotate the image.
        // 2. Create a new buffer of dimensions [width + height, (width + height) / 2]
        // 3. Paint the rotated image onto the new buffer, filling it.
        // 4. (afterwards) scale the image down to the actual footprint, preserving aspect ratio.

        // For a normal transform we would scale by (√2, 1/√2). Comparing with the above, we get
        // some of that:
        // - The rotation of the image produces a new one that is √2 times larger (assuming a square
        //   image), which provides a natural isometric stretch in the horizontal.
        // - Mapping that result in onto (n, n/2) gives a true isometric look...
        // - ... however the resulting image is then scaled down to fit within the token footprint.
        //   On a non-isometric grid, this significantly shrinks it since the footprint is not twice
        //   as wide as it is tall. Thus, in effect we need to scale down by another half in order
        //   to match legacy behaviour.

        // Hence this seemingly bizarre additional scale factor based on aspect ratio.
        var additionalScale = footprintBounds.width / (double) footprintBounds.height / 2;
        transform.scale(additionalScale * Math.sqrt(2), additionalScale * Math.sqrt(2) / 2);
        transform.rotate(Math.toRadians(45));
      }

      // 3, Invert the image if needed. Since the image is not centered, requires an additional
      // translation.
      transform.scale(token.isFlippedX() ? -1 : 1, token.isFlippedY() ? -1 : 1);

      // 2. To make image manipulation easier, move so the center is at (0, 0).
      transform.translate(-footprintBounds.width / 2., -footprintBounds.height / 2.);

      // 1. Reduce the image to the actual token size.
      transform.scale(
          footprintBounds.width / (double) image.getWidth(),
          (double) footprintBounds.height / image.getHeight());
      // endregion

      // TODO Token opacity, and halve it if moving.

      clippedWorldG.drawImage(image, transform, null);

      // TODO Facing arrow

      // TODO Bars and states
    }
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
          LookupTable.LookupEntry result =
              lookupTable.getLookup(Integer.toString(token.getFacing()));
          if (result != null) {
            // TODO Original used ZoneRenderer as ImageObserver.
            image = ImageManager.getImage(result.getImageId());
          }
        } catch (ParserException p) {
          // do nothing
        }
      }
    }

    if (image == null) {
      // Adds this as observer so we can repaint once the image is ready. Fixes #1700.
      // TODO Original used ZoneRenderer as ImageObserver.
      image = ImageManager.getImage(token.getImageAssetId());
    }
    return image;
  }
}
