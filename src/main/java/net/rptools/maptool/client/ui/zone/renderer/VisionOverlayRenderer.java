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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.util.Collections;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;

/**
 * This outlines the area visible to the token under the cursor. For player views, this is clipped
 * to the current fog-of-war, while for GMs they see everything.
 */
public class VisionOverlayRenderer {
  private final RenderHelper renderHelper;
  private final Zone zone;
  private final ZoneView zoneView;

  public VisionOverlayRenderer(RenderHelper renderHelper, Zone zone, ZoneView zoneView) {
    this.renderHelper = renderHelper;
    this.zone = zone;
    this.zoneView = zoneView;
  }

  public void render(Graphics2D g, PlayerView view, Token tokenUnderMouse) {
    var timer = CodeTimer.get();
    timer.start("renderVisionOverlay");
    try {
      if (tokenUnderMouse == null) {
        return;
      }

      boolean isOwner = AppUtil.playerOwns(tokenUnderMouse);
      boolean tokenIsPC = tokenUnderMouse.getType() == Token.Type.PC;
      boolean strictOwnership =
          MapTool.getServerPolicy() != null && MapTool.getServerPolicy().useStrictTokenManagement();
      boolean showVisionAndHalo = isOwner || view.isGMView() || (tokenIsPC && !strictOwnership);
      if (!showVisionAndHalo) {
        return;
      }

      this.renderHelper.render(g, worldG -> renderWorld(worldG, view, tokenUnderMouse));
    } finally {
      timer.stop("renderVisionOverlay");
    }
  }

  private void renderWorld(Graphics2D g2, PlayerView view, Token token) {
    Area currentTokenVisionArea = zoneView.getVisibleArea(token, view);
    // Nothing to show.
    if (currentTokenVisionArea.isEmpty()) {
      return;
    }
    if (zone.hasFog()) {
      currentTokenVisionArea = new Area(currentTokenVisionArea);
      currentTokenVisionArea.intersect(
          zoneView.getExposedArea(view.derive(Collections.singleton(token))));
    }

    // Keep the line a consistent thickness
    g2.setStroke(new BasicStroke(1 / (float) g2.getTransform().getScaleX()));
    g2.setColor(new Color(255, 255, 255)); // outline around visible area
    g2.draw(currentTokenVisionArea);

    Color visionColor = token.getVisionOverlayColor();
    if (visionColor == null && AppPreferences.getUseHaloColorOnVisionOverlay()) {
      visionColor = token.getHaloColor();
    }
    if (visionColor != null) {
      g2.setColor(
          new Color(
              visionColor.getRed(),
              visionColor.getGreen(),
              visionColor.getBlue(),
              AppPreferences.getHaloOverlayOpacity()));
      g2.fill(currentTokenVisionArea);
    }
  }
}
