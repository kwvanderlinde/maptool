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
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.util.List;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.Pen;
import net.rptools.maptool.model.drawing.ShapeDrawable;

public abstract class AbstractTopologyDrawingTool extends AbstractDrawingLikeTool {
  /**
   * @return {@code true} if the shapes produced by the tool are filled-in areas; {@code false} if
   *     they are lines that need to be stroked.
   */
  protected abstract boolean isBackgroundFill();

  /**
   * @return {@code true} if the tool is in the middle of making a new shape; {@code false} if no
   *     drawing has started, or if one was cancelled.
   */
  protected abstract boolean isInProgress();

  /**
   * Start a new drawing using {@code point} as the first point.
   *
   * <p>Immediately after this call completes, {@link #isInProgress()} must return {@code true}.
   *
   * @param point
   */
  protected abstract void startNewAtPoint(ZonePoint point);

  /**
   * Update the last uncommitted point in the drawing.
   *
   * <p>This will only ever be called if {@link #isInProgress()} returns {@code true}, and it must
   * continue to return {@code true} after the call completes.
   *
   * @param point
   */
  protected abstract void updateLastPoint(ZonePoint point);

  /**
   * If the tool supports it, commit the last point and create a new one in its place.
   *
   * <p>This is only for tools such as line tools, where multiple points can be specified.
   */
  protected void pushPoint() {}

  /**
   * Commit the drawing as topology.
   *
   * <p>Immediately after this call completes, {@link #isInProgress()} must return {@code false}.
   *
   * @return The area to add to or erase from the zone's topology.
   */
  protected abstract Area finish();

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  private void submit(Area area) {
    if (isEraser()) {
      getZone().removeTopology(area);
      // TODO Surely the tool should be the place to keep track of the topology types.
      MapTool.serverCommand().removeTopology(getZone().getId(), area, getZone().getTopologyTypes());
    } else {
      getZone().addTopology(area);
      MapTool.serverCommand().addTopology(getZone().getId(), area, getZone().getTopologyTypes());
    }
  }

  protected Area getTokenTopology(Zone.TopologyType topologyType) {
    List<Token> topologyTokens = getZone().getTokensWithTopology(topologyType);

    Area tokenTopology = new Area();
    for (Token topologyToken : topologyTokens) {
      tokenTopology.add(topologyToken.getTransformedTopology(topologyType));
    }

    return tokenTopology;
  }

  protected void paintTopologyOverlay(Graphics2D g, Shape shape) {
    ShapeDrawable drawable = null;

    if (shape != null) {
      drawable = new ShapeDrawable(shape, false);
    }

    paintTopologyOverlay(g, drawable);
  }

  protected void paintTopologyOverlay(Graphics2D g, Drawable drawable) {
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
      pen.setEraser(isEraser());
      pen.setOpacity(AppStyle.topologyRemoveColor.getAlpha() / 255.0f);

      if (isBackgroundFill()) {
        pen.setBackgroundMode(Pen.MODE_SOLID);
      } else {
        pen.setThickness(3.0f);
        pen.setBackgroundMode(Pen.MODE_TRANSPARENT);
      }

      if (pen.isEraser()) {
        pen.setEraser(false);
      }
      if (isEraser()) {
        pen.setPaint(new DrawableColorPaint(AppStyle.topologyRemoveColor));
        pen.setBackgroundPaint(new DrawableColorPaint(AppStyle.topologyRemoveColor));
      } else {
        pen.setPaint(new DrawableColorPaint(AppStyle.topologyAddColor));
        pen.setBackgroundPaint(new DrawableColorPaint(AppStyle.topologyAddColor));
      }
      paintTransformed(g, renderer, drawable, pen);
    }
  }

  @Override
  public final void mouseDragged(MouseEvent e) {
    if (!isInProgress()) {
      super.mouseDragged(e);
    }
  }

  @Override
  public final void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    setIsEraser(isEraser(e));
    if (isInProgress()) {
      ZonePoint point = getPoint(e);
      updateLastPoint(point);
      renderer.repaint();
    }

    // TODO Via via via, the original Polygon and Polyline tools would have a guard of
    //  `SwingUtilities.isRightMouseButton(e)` in (from AbstractLineTool.addPoint()). As far as I
    //  can tell, this does absolutely nothing for the current implementation since we don't rely
    //  on the `tempPoint` hack.
  }

  @Override
  public final void mousePressed(MouseEvent e) {
    setIsEraser(isEraser(e));

    if (SwingUtilities.isLeftMouseButton(e)) {
      ZonePoint point = getPoint(e);

      if (!isInProgress()) {
        startNewAtPoint(point);
      } else {
        updateLastPoint(point);
        var area = finish();
        submit(area);
      }
      renderer.repaint();
    }
    // TODO Shouldn't we make sure it's a right-click?
    else if (isInProgress()) {
      pushPoint();
      renderer.repaint();
    }

    super.mousePressed(e);
  }
}
