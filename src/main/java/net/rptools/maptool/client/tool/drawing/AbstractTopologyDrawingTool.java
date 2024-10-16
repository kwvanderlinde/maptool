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
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;

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
   * @return The topology to add to or erase from the zone's topology, or {@code null} if there is
   *     nothing to add or remove.
   */
  protected abstract @Nullable Shape finish();

  private BasicStroke getLineStroke() {
    return new BasicStroke(2.f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  private void submit(Shape shape) {
    Area area;
    if (shape instanceof Area tmpArea) {
      area = tmpArea;
    } else if (isBackgroundFill()) {
      // Fill the shape without stroking.
      area = new Area(shape);
    } else {
      // Stroke the shape into an area.
      var stroke = getLineStroke();
      area = new Area(stroke.createStrokedShape(shape));
    }

    if (isEraser()) {
      getZone().removeTopology(area);
      // TODO Surely the tool should be the place to keep track of the topology types.
      MapTool.serverCommand().removeTopology(getZone().getId(), area, getZone().getTopologyTypes());
    } else {
      getZone().addTopology(area);
      MapTool.serverCommand().addTopology(getZone().getId(), area, getZone().getTopologyTypes());
    }
  }

  protected final Rectangle normalizedRectangle(Rectangle rectangle) {
    // AWT doesn't like drawing rectangles with negative width or height. So normalize it first.
    int minX = Math.min(rectangle.x, rectangle.x + rectangle.width);
    int minY = Math.min(rectangle.y, rectangle.y + rectangle.height);
    int width = Math.abs(rectangle.width);
    int height = Math.abs(rectangle.height);
    return new Rectangle(minX, minY, width, height);
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
    if (MapTool.getPlayer().isGM()) {
      Zone zone = renderer.getZone();

      Graphics2D g2 = (Graphics2D) g.create();
      // TODO Ensure SrcOver composite?
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

      if (shape != null) {
        var stroke = getLineStroke();
        var color = isEraser() ? AppStyle.topologyRemoveColor : AppStyle.topologyAddColor;
        g2.setColor(color);

        if (isBackgroundFill()) {
          g2.fill(shape);

          // Render the outline just to make it stand out more.
          g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 255));
          // This line is purely visual, so keep it a consistent thickness
          g2.setStroke(
              new BasicStroke(
                  1 / (float) renderer.getScale(), stroke.getEndCap(), stroke.getLineJoin()));
          g2.draw(shape);
        } else {
          g2.setStroke(stroke);
          g2.draw(shape);
        }
      }

      g2.dispose();
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
        var shape = finish();
        if (shape != null) {
          submit(shape);
        }
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
