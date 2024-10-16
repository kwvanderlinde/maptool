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

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import javax.swing.SwingUtilities;

import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.LineSegment;
import net.rptools.maptool.model.drawing.Pen;
import net.rptools.maptool.model.drawing.ShapeDrawable;

// TODO These aren't freehand lines. Also it's not worth inheriting from LineTool.
/** Tool for drawing freehand lines. */
public class PolygonTopologyTool extends AbstractTopologyDrawingTool implements MouseMotionListener {

  private static final long serialVersionUID = 3258132466219627316L;
  // TODO Surely a Path2D would suffice?
  private LineBuilder lineBuilder = new LineBuilder();

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

  @Override
  protected void completeDrawable(Pen pen, Drawable drawable) {
    // TODO Gross. We should avoid the nonsense of completeDrawable() for non-drawing tools.
    //  Btw, we do so for other topology tools.

    Area area = new Area();

    if (drawable instanceof LineSegment) {
      LineSegment line = (LineSegment) drawable;
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
    } else {
      area = new Area(((ShapeDrawable) drawable).getShape());
    }
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
    var shape = lineBuilder.asLineSegment(getPen().getThickness(), getPen().getSquareCap());
    paintTopologyOverlay(g, shape, Pen.MODE_TRANSPARENT);
  }

  private void stopLine() {
    if (lineBuilder.isEmpty()) {
      // Not started, or was already cancelled.
      return;
    }

    lineBuilder.trim();

    Drawable drawable;
    if (isBackgroundFill() && lineBuilder.size() > 2) {
      drawable = new ShapeDrawable(lineBuilder.asPolygon());
    }
    else {
      // TODO The topology tools should not need to have configurable pens.
      drawable = lineBuilder.asLineSegment(getPen().getThickness(), getPen().getSquareCap());
    }
    completeDrawable(getPen(), drawable);

    lineBuilder.clear();
    renderer.repaint();
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
        stopLine();
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
