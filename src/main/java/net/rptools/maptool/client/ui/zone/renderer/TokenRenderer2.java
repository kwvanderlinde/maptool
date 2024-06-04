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

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderableImage;

public class TokenRenderer2 {
  private final RenderHelper renderHelper;

  public TokenRenderer2(RenderHelper renderHelper) {
    this.renderHelper = renderHelper;
  }

  public void renderToken(Graphics2D g, RenderableImage renderable) {
    renderToken2(g, renderable);
  }

  private void renderToken2(Graphics2D g, RenderableImage renderable) {
    AffineTransform at = new AffineTransform();
    at.translate(renderable.bounds().getX(), renderable.bounds().getY());
    at.scale(
        (renderable.flipX() ? -1 : 1)
            * renderable.bounds().getWidth()
            / renderable.image().getWidth(),
        (renderable.flipY() ? -1 : 1)
            * renderable.bounds().getHeight()
            / renderable.image().getHeight());
    at.rotate(
        renderable.rotation(),
        renderable.rotationAnchor().getX(),
        renderable.rotationAnchor().getY());

    g.drawImage(renderable.image(), at, renderHelper.getImageObserver());
  }
}
