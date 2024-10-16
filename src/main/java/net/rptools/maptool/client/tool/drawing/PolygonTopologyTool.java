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
import java.awt.event.MouseMotionListener;
import java.awt.geom.Area;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.Pen;

// TODO These aren't freehand lines. Also it's not worth inheriting from LineTool.
/** Tool for drawing freehand lines. */
public class PolygonTopologyTool extends AbstractTopologyDrawingTool
    implements MouseMotionListener {

  private static final long serialVersionUID = 3258132466219627316L;
  protected final LineBuilder lineBuilder = new LineBuilder();

  public PolygonTopologyTool() {}

  @Override
  public String getTooltip() {
    return "tool.polytopo.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.poly.instructions";
  }

  @Override
  protected boolean isBackgroundFill() {
    return true;
  }

  protected void complete(Pen pen, Polygon drawable) {
    Area area = new Area(drawable);

    if (pen.isEraser()) {
      getZone().removeTopology(area);
      MapTool.serverCommand().removeTopology(getZone().getId(), area, getZone().getTopologyTypes());
    } else {
      getZone().addTopology(area);
      MapTool.serverCommand().addTopology(getZone().getId(), area, getZone().getTopologyTypes());
    }
    renderer.repaint();
  }

  @Override
  protected Pen getPen() {
    Pen pen = new Pen(MapTool.getFrame().getPen());
    pen.setEraser(isEraser());
    pen.setForegroundMode(Pen.MODE_TRANSPARENT);
    pen.setBackgroundMode(Pen.MODE_SOLID);
    pen.setThickness(1.0f);
    pen.setOpacity(AppStyle.topologyRemoveColor.getAlpha() / 255.0f);
    pen.setPaint(
        new DrawableColorPaint(
            isEraser() ? AppStyle.topologyRemoveColor : AppStyle.topologyAddColor));
    return pen;
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    // TODO Why do we only render the lines and not the fill? I have arbitrarily decided otherwise
    // now.
    var shape = lineBuilder.asPolygon();
    paintTopologyOverlay(g, shape);
  }

  ////
  // MOUSE LISTENER
  @Override
  public void mousePressed(MouseEvent e) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      var isNewLine = lineBuilder.isEmpty();
      lineBuilder.addPoint(getPoint(e));

      if (isNewLine) {
        // Yes, add twice. The first is to commit the first point. The second is as the temporary
        // last point that we will replace as we go.
        lineBuilder.addPoint(getPoint(e));
        setIsEraser(isEraser(e));
      } else {
        lineBuilder.trim();

        var polygon = lineBuilder.asPolygon();
        complete(getPen(), polygon);

        lineBuilder.clear();
        renderer.repaint();
      }
      renderer.repaint();
    } else if (!lineBuilder.isEmpty()) {
      // Create a joint
      lineBuilder.addPoint(lineBuilder.getLastPoint());
      renderer.repaint();
      return;
    }
    super.mousePressed(e);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (lineBuilder.isEmpty()) {
      // We're not drawing, so use default behaviour.
      super.mouseDragged(e);
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    if (SwingUtilities.isRightMouseButton(e)) {
      // A drag?
      return;
    }

    if (!lineBuilder.isEmpty()) {
      lineBuilder.replaceLastPoint(getPoint(e));
      renderer.repaint();
    }
  }
}
