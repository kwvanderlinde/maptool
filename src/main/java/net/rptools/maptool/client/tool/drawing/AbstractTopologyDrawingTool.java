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
package net.rptools.maptool.client.tool.drawing;

import java.awt.*;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.Pen;
import net.rptools.maptool.model.drawing.ShapeDrawable;

public abstract class AbstractTopologyDrawingTool extends AbstractDrawingTool {
  // TODO Just don't inherit from AbstractDrawingTool when we aren't drawing!!!
  @Override
  // Override abstracttool to prevent color palette from
  // showing up
  protected void attachTo(ZoneRenderer renderer) {
    super.attachTo(renderer);
    // Hide the drawable color palette
    MapTool.getFrame().removeControlPanel();
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  protected void paintTopologyOverlay(Graphics2D g, Shape shape, int penMode) {
    ShapeDrawable drawable = null;

    if (shape != null) {
      drawable = new ShapeDrawable(shape, false);
    }

    paintTopologyOverlay(g, drawable, penMode);
  }

  protected void paintTopologyOverlay(Graphics2D g, Drawable drawable, int penMode) {
    if (MapTool.getPlayer().isGM()) {
      Zone zone = renderer.getZone();

      Graphics2D g2 = (Graphics2D) g.create();
      g2.translate(renderer.getViewOffsetX(), renderer.getViewOffsetY());
      g2.scale(renderer.getScale(), renderer.getScale());

      g2.setColor(AppStyle.tokenMblColor);
      g2.fill(getTokenTopology(Zone.TopologyType.MBL));
      g2.setColor(AppStyle.tokenTopologyColor);
      g2.fill(getTokenTopology(Zone.TopologyType.WALL_VBL));
      g2.setColor(AppStyle.tokenHillVblColor);
      g2.fill(getTokenTopology(Zone.TopologyType.HILL_VBL));
      g2.setColor(AppStyle.tokenPitVblColor);
      g2.fill(getTokenTopology(Zone.TopologyType.PIT_VBL));
      g2.setColor(AppStyle.tokenCoverVblColor);
      g2.fill(getTokenTopology(Zone.TopologyType.COVER_VBL));

      g2.setColor(AppStyle.topologyTerrainColor);
      g2.fill(zone.getTopology(Zone.TopologyType.MBL));

      g2.setColor(AppStyle.topologyColor);
      g2.fill(zone.getTopology(Zone.TopologyType.WALL_VBL));

      g2.setColor(AppStyle.hillVblColor);
      g2.fill(zone.getTopology(Zone.TopologyType.HILL_VBL));

      g2.setColor(AppStyle.pitVblColor);
      g2.fill(zone.getTopology(Zone.TopologyType.PIT_VBL));

      g2.setColor(AppStyle.coverVblColor);
      g2.fill(zone.getTopology(Zone.TopologyType.COVER_VBL));

      g2.dispose();
    }

    if (drawable != null) {
      Pen pen = new Pen();
      pen.setEraser(getPen().isEraser());
      pen.setOpacity(AppStyle.topologyRemoveColor.getAlpha() / 255.0f);
      pen.setBackgroundMode(penMode);

      if (penMode == Pen.MODE_TRANSPARENT) {
        pen.setThickness(3.0f);
      }

      if (pen.isEraser()) {
        pen.setEraser(false);
      }
      if (isEraser()) {
        pen.setBackgroundPaint(new DrawableColorPaint(AppStyle.topologyRemoveColor));
      } else {
        pen.setBackgroundPaint(new DrawableColorPaint(AppStyle.topologyAddColor));
      }
      paintTransformed(g, renderer, drawable, pen);
    }
  }
}
