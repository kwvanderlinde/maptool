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
package net.rptools.maptool.client.ui.zone.renderer.instructions;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.function.Consumer;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.tool.drawing.Measurement;
import net.rptools.maptool.client.ui.zone.renderer.LabelLocation;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Text;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InstructionSetBuilder {
  private static final Logger log = LogManager.getLogger(InstructionSetBuilder.class);

  private final Consumer<RenderInstruction> instructionSink;
  private final Zone zone;
  private final ZoneViewport viewport;

  public InstructionSetBuilder(
      Consumer<RenderInstruction> instructionSink, Zone zone, ZoneViewport viewport) {
    this.instructionSink = instructionSink;
    this.zone = zone;
    this.viewport = viewport;
  }

  public Zone getZone() {
    return zone;
  }

  public ZoneViewport getViewport() {
    return viewport;
  }

  public void unbufferedLayer(String layerName, ClipType clipType, Runnable action) {
    var timer = CodeTimer.get();
    timer.start("composite-%s", layerName);
    try {
      add(new RenderInstruction.Meta.StartUnbufferedLayer(layerName, clipType));
      try {
        action.run();
      } finally {
        add(new RenderInstruction.Meta.FinishLayer(layerName));
      }
    } finally {
      timer.stop("composite-%s", layerName);
    }
  }

  public void bufferedLayer(
      String layerName, ClipType clipType, BlendMode blendMode, double opacity, Runnable action) {
    var timer = CodeTimer.get();
    timer.start("composite-%s", layerName);
    try {
      add(new RenderInstruction.Meta.StartBufferedLayer(layerName, clipType, blendMode, opacity));
      try {
        action.run();
      } finally {
        add(new RenderInstruction.Meta.FinishLayer(layerName));
      }
    } finally {
      timer.stop("composite-%s", layerName);
    }
  }

  public void add(RenderInstruction instruction) {
    instructionSink.accept(instruction);
  }

  public void withCustomClip(Area clip, boolean invert, Runnable action) {
    if (clip == null) {
      action.run();
    } else {
      add(new RenderInstruction.Meta.SetCustomClip(clip, invert));
      try {
        action.run();
      } finally {
        add(new RenderInstruction.Meta.ClearCustomClip());
      }
    }
  }

  public void addMeasurement(double distance, ZonePoint p) {
    addMeasurement(distance, p, 0, 0);
  }

  public void addMeasurement(
      double distance, ZonePoint p, double screenOffsetX, double screenOffsetY) {
    ScreenPoint centerText = viewport.zoneScale().toScreenSpace(new Point2D.Double(p.x, p.y));
    centerText.x += screenOffsetX;
    centerText.y += screenOffsetY;
    String radius = NumberFormat.getInstance().format(distance);
    add(new RenderInstruction.BoxedString(centerText.x, centerText.y, radius));
  }

  public void addMeasurement(Measurement measurement) {
    switch (measurement) {
      case null -> {}
      case Measurement.Rectangular rectangular -> addBoxedMeasurement(rectangular.bounds());
      case Measurement.LineSegment lineSegment ->
          addLineMeasurement(lineSegment.p1(), lineSegment.p2());
      case Measurement.IsoRectangular isoRectangular ->
          addIsoBoxedMeasurement(
              isoRectangular.north(), isoRectangular.west(), isoRectangular.east());
    }
  }

  /**
   * Indicates the dimensions of a rectangle.
   *
   * @param worldBounds
   */
  private void addBoxedMeasurement(Rectangle2D worldBounds) {
    var offsetBase = 5 / viewport.zoneScale().getScale();
    var numberFormat = NumberFormat.getInstance();

    {
      var extentsPath = new Path2D.Double();
      // Lines spanning width and height.
      extentsPath.moveTo(worldBounds.getMinX(), worldBounds.getMinY() - 3 * offsetBase);
      extentsPath.lineTo(worldBounds.getMaxX(), worldBounds.getMinY() - 3 * offsetBase);
      extentsPath.moveTo(worldBounds.getMaxX() + 3 * offsetBase, worldBounds.getMinY());
      extentsPath.lineTo(worldBounds.getMaxX() + 3 * offsetBase, worldBounds.getMaxY());

      // Bars at end of each line.
      extentsPath.moveTo(worldBounds.getMinX(), worldBounds.getMinY() - 2 * offsetBase);
      extentsPath.lineTo(worldBounds.getMinX(), worldBounds.getMinY() - 4 * offsetBase);
      extentsPath.moveTo(worldBounds.getMaxX(), worldBounds.getMinY() - 2 * offsetBase);
      extentsPath.lineTo(worldBounds.getMaxX(), worldBounds.getMinY() - 4 * offsetBase);
      extentsPath.moveTo(worldBounds.getMaxX() + 2 * offsetBase, worldBounds.getMinY());
      extentsPath.lineTo(worldBounds.getMaxX() + 4 * offsetBase, worldBounds.getMinY());
      extentsPath.moveTo(worldBounds.getMaxX() + 2 * offsetBase, worldBounds.getMaxY());
      extentsPath.lineTo(worldBounds.getMaxX() + 4 * offsetBase, worldBounds.getMaxY());

      add(
          new RenderInstruction.Stroke(
              extentsPath,
              Paint.of(Color.white),
              new BasicStroke((float) (3. / viewport.zoneScale().getScale())),
              1.));
      add(
          new RenderInstruction.Stroke(
              extentsPath,
              Paint.of(Color.black),
              new BasicStroke((float) (1. / viewport.zoneScale().getScale())),
              1.));
    }

    var topCenter =
        new Point2D.Double(worldBounds.getCenterX(), worldBounds.getMinY() - 3 * offsetBase);
    var topDistance = worldBounds.getWidth() * zone.getUnitsPerCell() / zone.getGrid().getSize();
    add(
        new RenderInstruction.BoxedString(
            viewport.zoneScale().toScreenSpace(topCenter), numberFormat.format(topDistance)));

    var rightCenter =
        new Point2D.Double(worldBounds.getMaxX() + 3 * offsetBase, worldBounds.getCenterY());
    var rightDistance = worldBounds.getHeight() * zone.getUnitsPerCell() / zone.getGrid().getSize();
    add(
        new RenderInstruction.BoxedString(
            viewport.zoneScale().toScreenSpace(rightCenter), numberFormat.format(rightDistance)));
  }

  private void addLineMeasurement(Point2D start, Point2D end) {
    boolean dirLeft = start.getX() > end.getX();
    boolean dirUp = start.getY() < end.getY();

    String displayString =
        NumberFormat.getInstance()
            .format(start.distance(end) * zone.getUnitsPerCell() / zone.getGrid().getSize());

    var screenEnd = viewport.zoneScale().toScreenSpace(end);
    screenEnd.x += (dirLeft ? -15 : 10);
    screenEnd.y += (dirUp ? 15 : -15);

    add(
        new RenderInstruction.BoxedString(
            screenEnd, displayString
            // TODO left/right justification
            //  /dirLeft ? SwingUtilities.LEFT : SwingUtilities.RIGHT
            ));
  }

  /**
   * Indicates the dimensions of an isometric rectangle.
   *
   * @param north The top point of the rectangle
   * @param west The left point of the rectangle
   * @param east The right point of the rectangle
   */
  private void addIsoBoxedMeasurement(Point2D north, Point2D west, Point2D east) {
    var offsetBase = 5 / viewport.zoneScale().getScale();
    var numberFormat = NumberFormat.getInstance();

    {
      var extentsPath = new Path2D.Double();
      // Lines spanning width and height.
      extentsPath.moveTo(west.getX(), west.getY() - 3 * offsetBase);
      extentsPath.lineTo(north.getX(), north.getY() - 3 * offsetBase);
      extentsPath.lineTo(east.getX(), east.getY() - 3 * offsetBase);

      // Bars at end of each line.
      extentsPath.moveTo(west.getX(), west.getY() - 2 * offsetBase);
      extentsPath.lineTo(west.getX(), west.getY() - 4 * offsetBase);
      extentsPath.moveTo(north.getX(), north.getY() - 2 * offsetBase);
      extentsPath.lineTo(north.getX(), north.getY() - 4 * offsetBase);
      extentsPath.moveTo(east.getX(), east.getY() - 2 * offsetBase);
      extentsPath.lineTo(east.getX(), east.getY() - 4 * offsetBase);

      add(
          new RenderInstruction.Stroke(
              extentsPath,
              Paint.of(Color.white),
              new BasicStroke((float) (3. / viewport.zoneScale().getScale())),
              1.));
      add(
          new RenderInstruction.Stroke(
              extentsPath,
              Paint.of(Color.black),
              new BasicStroke((float) (1. / viewport.zoneScale().getScale())),
              1.));
    }

    var leftCenter =
        new Point2D.Double(north.getX() - 5 * offsetBase, north.getY() - 5 * offsetBase);
    var leftDistance =
        isometricDistance(west, north) * zone.getUnitsPerCell() / zone.getGrid().getSize();
    add(
        new RenderInstruction.BoxedString(
            viewport.zoneScale().toScreenSpace(leftCenter), numberFormat.format(leftDistance)));

    var rightCenter =
        new Point2D.Double(north.getX() + 5 * offsetBase, north.getY() - 5 * offsetBase);
    var rightDistance =
        isometricDistance(east, north) * zone.getUnitsPerCell() / zone.getGrid().getSize();
    add(
        new RenderInstruction.BoxedString(
            viewport.zoneScale().toScreenSpace(rightCenter), numberFormat.format(rightDistance)));
  }

  private double isometricDistance(Point2D p1, Point2D p2) {
    return 2 * Math.abs(p2.getY() - p1.getY());
  }

  public void addLabel(RenderInstruction.Label label) {
    var scale = viewport.zoneScale().getScale();
    var worldBounds = viewport.zoneScale().toWorldSpace(label.screenBounds());
    // TODO We really need to fix our GDX polygonizer. A single rounded rectangle cuts our
    //  frame rate in half.
    var box =
        new RoundRectangle2D.Double(
            worldBounds.getX(),
            worldBounds.getY(),
            worldBounds.getWidth(),
            worldBounds.getHeight(),
            label.cornerArc() / scale,
            label.cornerArc() / scale);
    if (label.background() != null) {
      add(new RenderInstruction.Fill(box, Paint.of(label.background()), 1.));
    }

    add(
        new Text(
            label.text(),
            label.font(),
            label.screenBounds(),
            label.foreground(),
            Text.Decoration.None));

    if (label.borderWidth() > 0 && label.borderColor() != null) {
      add(
          new RenderInstruction.Stroke(
              box,
              Paint.of(label.borderColor()),
              new BasicStroke(
                  (float) (label.borderWidth() / scale),
                  BasicStroke.CAP_ROUND,
                  BasicStroke.JOIN_ROUND),
              1.));
    }
  }

  public void addLabel(LabelLocation label) {
    addLabel(
        new RenderInstruction.Label(
            label.label().getLabel(),
            label.font(),
            label.bounds(),
            label.label().isShowBackground() ? label.label().getBackgroundColor() : null,
            label.label().getForegroundColor(),
            label.label().isShowBorder() ? label.label().getBorderColor() : null,
            label.label().getBorderWidth(),
            label.label().getBorderArc()));
  }
}
