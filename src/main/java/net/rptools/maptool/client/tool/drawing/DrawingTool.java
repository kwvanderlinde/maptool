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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolUtil;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.colorpicker.ColorPicker;
import net.rptools.maptool.client.tool.ToolHelper;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.Pen;
import net.rptools.maptool.model.drawing.ShapeDrawable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DrawingTool<StateT> extends AbstractDrawingLikeTool {
  private static final Logger log = LogManager.getLogger(DrawingTool.class);
  // TODO Need to draw measurements as well.

  private final String instructionKey;
  private final String tooltipKey;
  private final Strategy<StateT> strategy;

  /** The current state of the tool. If {@code null}, nothing is being drawn right now. */
  private @Nullable StateT state;

  private ZonePoint currentPoint = new ZonePoint(0, 0);
  private boolean centerOnOrigin = false;

  public DrawingTool(String instructionKey, String tooltipKey, Strategy<StateT> strategy) {
    this.instructionKey = instructionKey;
    this.tooltipKey = tooltipKey;
    this.strategy = strategy;
  }

  @Override
  public String getInstructions() {
    return instructionKey;
  }

  @Override
  public String getTooltip() {
    return tooltipKey;
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  @Override
  protected boolean isLinearTool() {
    return strategy.isLinear();
  }

  @Override
  protected void attachTo(ZoneRenderer renderer) {
    super.attachTo(renderer);
    if (MapTool.getPlayer().isGM()) {
      MapTool.getFrame()
          .showControlPanel(MapTool.getFrame().getColorPicker(), getLayerSelectionDialog());
    } else {
      MapTool.getFrame().showControlPanel(MapTool.getFrame().getColorPicker());
    }
    renderer.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
  }

  @Override
  protected void detachFrom(ZoneRenderer renderer) {
    MapTool.getFrame().removeControlPanel();
    renderer.setCursor(Cursor.getDefaultCursor());

    super.detachFrom(renderer);
  }

  /** If currently drawing, stop and clear it. */
  @Override
  protected void resetTool() {
    if (state != null) {
      state = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }

  @Override
  protected boolean isEraser(MouseEvent e) {
    // Use the color picker as the default, but invert based on key state.
    var inverted = super.isEraser(e);
    boolean defaultValue = MapTool.getFrame().getColorPicker().isEraseSelected();
    if (inverted) {
      defaultValue = !defaultValue;
    }
    return defaultValue;
  }

  @Override
  protected boolean isSnapToGrid(MouseEvent e) {
    // Use the color picker as the default, but invert based on key state.
    var inverted = super.isSnapToGrid(e);
    boolean defaultValue = MapTool.getFrame().getColorPicker().isSnapSelected();
    if (inverted) {
      // Invert from the color panel
      defaultValue = !defaultValue;
    }
    return defaultValue;
  }

  private boolean isBackgroundFill() {
    return MapTool.getFrame().getColorPicker().isFillBackgroundSelected();
  }

  private boolean hasPaint(Pen pen) {
    return pen.getForegroundMode() != Pen.MODE_TRANSPARENT
        || pen.getBackgroundMode() != Pen.MODE_TRANSPARENT;
  }

  private Pen getPen() {
    Pen pen = new Pen(MapTool.getFrame().getPen());
    pen.setEraser(isEraser());

    ColorPicker picker = MapTool.getFrame().getColorPicker();
    if (picker.isFillForegroundSelected()) {
      pen.setForegroundMode(Pen.MODE_SOLID);
    } else {
      pen.setForegroundMode(Pen.MODE_TRANSPARENT);
    }
    if (picker.isFillBackgroundSelected()) {
      pen.setBackgroundMode(Pen.MODE_SOLID);
    } else {
      pen.setBackgroundMode(Pen.MODE_TRANSPARENT);
    }
    pen.setSquareCap(picker.isSquareCapSelected());
    pen.setThickness(picker.getStrokeWidth());
    return pen;
  }

  private Drawable toDrawable(Shape shape) {
    return switch (shape) {
      case Path2D path -> new ShapeDrawable(path, true);
        // TODO Better would be Rectangle2D
      case Rectangle rectangle -> new ShapeDrawable(rectangle, false);
      case Ellipse2D ellipse -> new ShapeDrawable(ellipse, true);
      default -> {
        log.info("Unrecognized shape type: {}", shape.getClass());
        yield new ShapeDrawable(shape);
      }
    };
  }

  private void submit(Shape shape) {
    // TODO Even though I know we have to produce a drawable, I don't like that it feels like
    //  rendering is too abstracted.

    var pen = getPen();
    if (!hasPaint(pen)) {
      return;
    }

    Zone zone = getZone();
    var drawable = toDrawable(shape);
    if (drawable.getBounds(zone) == null) {
      return;
    }

    if (MapTool.getPlayer().isGM()) {
      drawable.setLayer(getSelectedLayer());
    } else {
      drawable.setLayer(Zone.Layer.getDefaultPlayerLayer());
    }

    // Send new textures
    MapToolUtil.uploadTexture(pen.getPaint());
    MapToolUtil.uploadTexture(pen.getBackgroundPaint());

    // Tell the local/server to render the drawable.
    MapTool.serverCommand().draw(zone.getId(), pen, drawable);

    // Allow it to be undone
    zone.addDrawable(pen, drawable);
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    Graphics2D g2 = (Graphics2D) g.create();
    // TODO Ensure SrcOver composite?
    g2.translate(renderer.getViewOffsetX(), renderer.getViewOffsetY());
    g2.scale(renderer.getScale(), renderer.getScale());

    if (state != null) {
      var result = strategy.getShape(state, currentPoint, centerOnOrigin, isBackgroundFill());
      // TODO Require shape to be non-null again.
      if (result != null) {
        var drawable = toDrawable(result.shape());

        Pen pen = getPen();
        if (isEraser()) {
          pen = new Pen(pen);
          pen.setEraser(false);
          pen.setPaint(new DrawableColorPaint(Color.white));
          pen.setBackgroundPaint(new DrawableColorPaint(Color.white));
        }

        drawable.draw(renderer.getZone(), g2, pen);

        // Measurements
        var measurement = result.measurement();
        switch (measurement) {
          case null -> {}
          case Measurement.Rectangular rectangular -> {
            var rectangle = rectangular.bounds();
            ToolHelper.drawBoxedMeasurement(
                renderer,
                g,
                ScreenPoint.fromZonePoint(renderer, rectangle.getX(), rectangle.getY()),
                ScreenPoint.fromZonePoint(renderer, rectangle.getMaxX(), rectangle.getMaxY()));
          }
          case Measurement.LineSegment lineSegment -> {
            var p1 =
                ScreenPoint.fromZonePoint(
                    renderer, lineSegment.p1().getX(), lineSegment.p1().getY());
            var p2 =
                ScreenPoint.fromZonePoint(
                    renderer, lineSegment.p2().getX(), lineSegment.p2().getY());
            ToolHelper.drawMeasurement(renderer, g, p1, p2);
          }
        }
      }
    }

    g2.dispose();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (state == null) {
      // We're not doing anything, so delegate to default behaviour.
      super.mouseDragged(e);
    } else if (strategy.isFreehand()) {
      // Extend the line.
      setIsEraser(isEraser(e));
      currentPoint = getPoint(e);
      centerOnOrigin = e.isAltDown(); // Pointless, but it doesn't hurt for consistency.
      strategy.pushPoint(state, currentPoint);
      renderer.repaint();
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    setIsEraser(isEraser(e));
    if (state != null) {
      currentPoint = getPoint(e);
      centerOnOrigin = e.isAltDown();
      renderer.repaint();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    setIsEraser(isEraser(e));

    if (SwingUtilities.isLeftMouseButton(e)) {
      currentPoint = getPoint(e);
      centerOnOrigin = e.isAltDown();

      if (state == null) {
        state = strategy.startNewAtPoint(currentPoint);
      } else if (!strategy.isFreehand()) {
        var result = strategy.getShape(state, currentPoint, centerOnOrigin, isBackgroundFill());
        state = null;
        if (result != null) {
          submit(result.shape());
        }
      }
      renderer.repaint();
    }
    // TODO Shouldn't we make sure it's a right-click?
    else if (state != null && !strategy.isFreehand()) {
      currentPoint = getPoint(e);
      centerOnOrigin = e.isAltDown();
      strategy.pushPoint(state, currentPoint);
      renderer.repaint();
    }

    super.mousePressed(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (strategy.isFreehand() && SwingUtilities.isLeftMouseButton(e)) {
      currentPoint = getPoint(e);
      centerOnOrigin = e.isAltDown();
      var result = strategy.getShape(state, currentPoint, centerOnOrigin, isBackgroundFill());
      state = null;
      if (result != null) {
        submit(result.shape());
      }
    }
  }
}
