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
package net.rptools.maptool.client.ui.zone.gdx.drawing;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import java.awt.BasicStroke;
import java.awt.Shape;
import net.rptools.maptool.client.ui.zone.gdx.AreaRenderer;
import net.rptools.maptool.client.ui.zone.gdx.GdxPaint;

public class SimpleDrawingRenderer {
  private final AreaRenderer areaRenderer;
  private final Color tmpColor = new Color();

  public SimpleDrawingRenderer(AreaRenderer areaRenderer) {
    this.areaRenderer = areaRenderer;
  }

  public void fill(PolygonSpriteBatch batch, Shape shape, GdxPaint paint, float opacity) {
    applyPaint(paint, opacity);
    areaRenderer.fillArea(batch, shape);
  }

  public void stroke(
      PolygonSpriteBatch batch, Shape shape, GdxPaint paint, float opacity, BasicStroke stroke) {
    applyPaint(paint, opacity);
    areaRenderer.drawArea(
        batch, shape, stroke.getEndCap() != BasicStroke.CAP_SQUARE, stroke.getLineWidth());
  }

  private void applyPaint(GdxPaint paint, float opacity) {
    // Color is premultiplied, so mul() to apply more alpha.
    tmpColor.set(paint.color()).mul(opacity);

    areaRenderer.setColor(tmpColor);
    if (paint.texture() != null) {
      areaRenderer.setTexture(paint.texture());
    }
  }
}
