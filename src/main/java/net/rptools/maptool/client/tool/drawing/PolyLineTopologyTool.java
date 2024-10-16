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
import java.awt.geom.Path2D;
import javax.swing.*;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.drawing.*;

/** Tool for drawing freehand lines. */
public class PolyLineTopologyTool extends AbstractTopologyDrawingTool
    implements MouseMotionListener {
  private static final long serialVersionUID = 3258132466219627316L;
  private final LineBuilder lineBuilder = new LineBuilder();

  public PolyLineTopologyTool() {}

  @Override
  public String getTooltip() {
    return "tool.polylinetopo.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.poly.instructions";
  }

  protected boolean isBackgroundFill() {
    return false;
  }

  protected void complete(Pen pen, LineSegment line) {
    // TODO Bleh. Just pass the path2d from the get-go.
    Area area = new Area();

    BasicStroke stroke =
        new BasicStroke(pen.getThickness(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

    Path2D path = new Path2D.Double();
    Point lastPoint = null;

    for (Point point : line.getPoints()) {
      if (path.getCurrentPoint() == null) {
        path.moveTo(point.x, point.y);
      } else if (!point.equals(lastPoint)) {
        path.lineTo(point.x, point.y);
        lastPoint = point;
      }
    }

    area.add(new Area(stroke.createStrokedShape(path)));
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
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    var shape = lineBuilder.asLineSegment(getPen().getThickness(), getPen().getSquareCap());
    paintTopologyOverlay(g, shape);
  }

  protected Pen getPen() {

    Pen pen = new Pen(MapTool.getFrame().getPen());
    pen.setEraser(isEraser());
    pen.setForegroundMode(Pen.MODE_SOLID);
    pen.setBackgroundMode(Pen.MODE_TRANSPARENT);
    pen.setThickness(2.0f);
    pen.setOpacity(AppStyle.topologyRemoveColor.getAlpha() / 255.0f);
    pen.setPaint(
        new DrawableColorPaint(
            isEraser() ? AppStyle.topologyRemoveColor : AppStyle.topologyAddColor));

    return pen;
  }

  ////
  // MOUSE LISTENER
  // TODO Copied from PolygonTopologyTool. Really what we need is to compositionally define line
  //  tools, etc., so that we can adapt them to topological, drawing, and exposure cases.
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

        // TODO The topology tools should not need to have configurable pens.
        var drawable = lineBuilder.asLineSegment(getPen().getThickness(), getPen().getSquareCap());
        complete(getPen(), drawable);

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
