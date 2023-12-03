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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.model.Zone;

public class FogRenderer {
  private final RenderHelper renderHelper;
  private final Zone zone;
  private final ZoneView zoneView;

  public FogRenderer(RenderHelper renderHelper, Zone zone, ZoneView zoneView) {
    this.renderHelper = renderHelper;
    this.zone = zone;
    this.zoneView = zoneView;
  }

  public void render(Graphics2D g, PlayerView view) {
    var timer = CodeTimer.get();
    timer.start("renderFog");
    try {
      if (!zone.hasFog()) {
        return;
      }

      this.renderHelper.bufferedRender(
          g, AlphaComposite.SrcOver, worldG -> renderWorld(worldG, view));
    } finally {
      timer.stop("renderFog");
    }
  }

  private void renderWorld(Graphics2D buffG, PlayerView view) {
    var timer = CodeTimer.get();

    /* The tricky thing in this method is that the areas we have (exposed, visible) are the areas
     * where we should _not_ render. So we have to do clipped fills and clears instead of directly
     * rendering the areas. */
    timer.start("renderFog-getVisibleArea");
    Area visibleArea = zoneView.getVisibleArea(view);
    timer.stop("renderFog-getVisibleArea");

    String msg = null;
    if (timer.isEnabled()) {
      msg =
          "renderFog-getExposedArea("
              + (view.isUsingTokenView() ? view.getTokens().size() : 0)
              + ")";
    }
    timer.start(msg);
    Area exposedArea = zoneView.getExposedArea(view);
    timer.stop(msg);

    // Hard FOW is cleared by exposed areas. The exposed area itself has two regions: the visible
    // area (rendered clear) and the soft FOW area (rendered translucent). But if vision is off,
    // treat the entire exposed area as visible.
    var softFogArea = zoneView.isUsingVision() ? exposedArea : new Area();
    var clearArea = zoneView.isUsingVision() ? visibleArea : exposedArea;

    var originalClip = buffG.getClip();

    timer.start("renderFog-hardFow");
    // Fill. This will be cleared out later to produce soft fog and clear visible area.
    buffG.setPaint(zone.getFogPaint().getPaint());
    // JFJ this fixes the GM exposed area view.
    buffG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, view.isGMView() ? .6f : 1f));
    var bounds = originalClip.getBounds();
    buffG.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    buffG.setClip(exposedArea);
    timer.start("renderFog-hardFow");

    timer.start("renderFog-softFow");
    if (!softFogArea.isEmpty()) {
      buffG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
      buffG.setColor(new Color(0, 0, 0, AppPreferences.getFogOverlayOpacity()));
      buffG.fill(softFogArea);
    }
    timer.stop("renderFog-softFow");

    timer.start("renderFog-exposedArea");
    if (!clearArea.isEmpty()) {
      // Now fill in the visible area.
      buffG.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
      buffG.fill(visibleArea);
    }
    timer.stop("renderFog-exposedArea");

    timer.start("renderFog-outline");
    // If there is no boundary between soft fog and visible area, there is no need for an outline.
    if (!softFogArea.isEmpty() && !clearArea.isEmpty()) {
      var scale = (float) buffG.getTransform().getScaleX();

      buffG.setComposite(AlphaComposite.Src);
      // Keep the line a consistent thickness
      buffG.setStroke(new BasicStroke(1 / (float) buffG.getTransform().getScaleX()));
      buffG.setColor(Color.BLACK);
      buffG.draw(visibleArea);
    }
    timer.stop("renderFog-outline");
  }
}
