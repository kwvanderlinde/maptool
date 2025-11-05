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
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.DeveloperOptions;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.tool.drawing.Measurement;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.zone.renderer.LabelLocation;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Text;
import net.rptools.maptool.model.AbstractPoint;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GridlessGrid;
import net.rptools.maptool.model.HexGridHorizontal;
import net.rptools.maptool.model.HexGridVertical;
import net.rptools.maptool.model.IsometricGrid;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.SquareGrid;
import net.rptools.maptool.model.TokenFootprint;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.AbstractTemplate;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawablesGroup;
import net.rptools.maptool.model.drawing.DrawnElement;
import net.rptools.maptool.model.drawing.Pen;
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

  // TODO Though cool that this works, the new flexible layers makes it unnecessary.
  private final class DrawableCompositor {
    private ArrayList<Area> erasedAreas = new ArrayList<>();
    private ArrayList<Integer> eraseBarrierStack = new ArrayList<>();
    private ArrayList<DrawnElement> nonErasers = new ArrayList<>();
    // TODO We don't need indices since we can mutate the areas.
    private ArrayList<Integer> erasedAreasToUse = new ArrayList<>();

    {
      erasedAreas.add(new Area());
      eraseBarrierStack.add(0);
    }

    public void addElement(DrawnElement element) {
      var pen = element.getPen();
      var drawable = element.getDrawable();

      if (drawable instanceof DrawablesGroup group) {
        // The group does not affect erased areas below it.
        erasedAreas.add(new Area());
        eraseBarrierStack.add(erasedAreas.size() - 1);

        for (var element2 : group.getDrawableList()) {
          addElement(element2);
        }

        eraseBarrierStack.removeLast();
      } else {
        var stroke = pen.getStroke();
        var area = pen.getBackgroundPaint() == null ? null : drawable.getArea(zone);
        var border = pen.getPaint() == null ? null : drawable.getBorder(zone);

        if (pen.isEraser()) {
          // Add it to all previous masks, down to the latest barrier.
          var combinedArea = area == null ? new Area() : new Area(area);
          if (border != null) {
            combinedArea.add(new Area(stroke.createStrokedShape(border)));
          }

          for (var mask : erasedAreas.subList(eraseBarrierStack.getLast(), erasedAreas.size())) {
            mask.add(combinedArea);
          }

          erasedAreas.add(combinedArea);
        } else {
          nonErasers.add(element);
          erasedAreasToUse.add(erasedAreas.size() - 1);
        }
      }
    }

    public void flush() {
      for (int i = 0; i < nonErasers.size(); ++i) {
        var element = nonErasers.get(i);
        var maskIndex = erasedAreasToUse.get(i);
        var mask = erasedAreas.get(maskIndex);

        var pen = element.getPen();
        var drawable = element.getDrawable();

        var area = pen.getBackgroundPaint() == null ? null : drawable.getArea(zone);
        var border = pen.getPaint() == null ? null : drawable.getBorder(zone);
        var decorations =
            drawable instanceof AbstractTemplate template
                ? template.getDecorationsToStroke(zone)
                : null;

        withCustomClip(
            mask,
            true,
            () -> {
              if (area != null) {
                var fillOpacity =
                    pen.getOpacity()
                        * (drawable instanceof AbstractTemplate
                            ? AbstractTemplate.DEFAULT_BG_ALPHA
                            : 1);
                add(
                    new RenderInstruction.Fill(
                        area, Paint.of(pen.getBackgroundPaint()), fillOpacity));
              }
              if (border != null) {
                add(
                    new RenderInstruction.Stroke(
                        border, Paint.of(pen.getPaint()), pen.getStroke(), pen.getOpacity()));
              }
              if (decorations != null) {
                add(
                    new RenderInstruction.Stroke(
                        decorations, Paint.of(pen.getPaint()), pen.getStroke(), pen.getOpacity()));
              }
            });
      }
    }
  }

  public void addDrawnElements(List<DrawnElement> elements) {
    var compositor = new DrawableCompositor();
    for (var element : elements) {
      compositor.addElement(element);
    }
    compositor.flush();
  }

  public void addDrawable(Drawable drawable, Pen pen) {
    addDrawnElements(List.of(new DrawnElement(drawable, pen)));
  }

  public void addPath(Path<? extends AbstractPoint> path, TokenFootprint footprint) {
    if (path == null) {
      return;
    }
    if (path.getCellPath().isEmpty()) {
      return;
    }

    if (path.getCellPath().getFirst() instanceof CellPoint) {
      addCellPointPath((Path<CellPoint>) path, footprint);
    } else {
      addZonePointPath((Path<ZonePoint>) path, footprint);
    }
  }

  private void addCellPointPath(Path<CellPoint> path, TokenFootprint footprint) {
    var timer = CodeTimer.get();

    timer.start("renderPath-1");

    List<CellPoint> cellPath = path.getCellPath();

    var grid = zone.getGrid();

    Set<CellPoint> pathSet = new HashSet<>();
    List<Point2D> waypointList = new LinkedList<>();
    for (CellPoint p : cellPath) {
      pathSet.addAll(footprint.getOccupiedCells(p));

      if (path.isWaypoint(p)) {
        var bounds = footprint.getBounds(grid, p);
        waypointList.add(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()));
      }
    }
    // The first and last point are waypoints, but we don't want to draw those as waypoints.
    if (waypointList.size() < 2) {
      waypointList.clear();
    } else {
      waypointList.removeLast();
      waypointList.removeFirst();
    }
    timer.stop("renderPath-1");

    timer.start("renderPath-2");

    Images highlight =
        switch (zone.getGrid()) {
          case SquareGrid ignored -> Images.GRID_BORDER_SQUARE;
          case IsometricGrid ignored -> Images.GRID_BORDER_ISOMETRIC;
          case HexGridHorizontal ignored -> Images.GRID_BORDER_HEX_HORIZONTAL;
          case HexGridVertical ignored -> Images.GRID_BORDER_HEX;
          case GridlessGrid ignored -> null;
          default -> null;
        };
    // We don't really expect to end up here for gridless grids, but just in case, say, a token's
    // last path was for a different grid, let's avoid assuming we have a highlight.
    if (highlight != null) {
      // TODO I believe this would be simpler if we instead used `zone.getGrid.getCenterOffset()`.
      //  That would give a natural location to center the highlights upon.

      for (CellPoint p : pathSet) {
        var center = zone.getGrid().getCellCenter(p);

        var bounds = new Rectangle2D.Double();
        bounds.width = grid.getCellWidth();
        bounds.height = grid.getCellHeight();
        bounds.x = center.getX() - bounds.width / 2.;
        bounds.y = center.getY() - bounds.height / 2.;

        add(new RenderInstruction.Icon(highlight, bounds));
      }
    }

    // Line path
    if (grid.getCapabilities().isPathLineSupported()) {
      var curve2d = new Path2D.Double();

      CellPoint previousCellPoint = null;
      Point2D previousPoint = null;
      Point2D previousHalfPoint = null;
      for (CellPoint p : cellPath) {
        var bounds = footprint.getBounds(grid, p);
        var center = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());

        if (previousPoint == null) {
          previousCellPoint = p;
          previousPoint = center;
          continue;
        }

        var origin = previousPoint;
        var destination = center;

        var halfX = (origin.getX() + destination.getX()) / 2.;
        var halfY = (origin.getY() + destination.getY()) / 2.;
        var halfPoint = new Point2D.Double(halfX, halfY);

        if (previousHalfPoint == null) {
          curve2d.moveTo(origin.getX(), origin.getY());
          curve2d.lineTo(halfPoint.getX(), halfPoint.getY());
        } else if (path.isWaypoint(previousCellPoint)) {
          // For waypoints, run the line right up to the cell center.
          curve2d.lineTo(origin.getX(), origin.getY());
          curve2d.lineTo(halfPoint.getX(), halfPoint.getY());
        } else {
          // Tighten up the circular arc by extending the lines a bit.
          var p1 =
              new Point2D.Double(
                  (previousHalfPoint.getX() + origin.getX()) / 2.,
                  (previousHalfPoint.getY() + origin.getY()) / 2.);
          var p2 =
              new Point2D.Double(
                  (halfPoint.getX() + origin.getX()) / 2., (halfPoint.getY() + origin.getY()) / 2.);
          curve2d.lineTo(p1.getX(), p1.getY());
          curve2d.quadTo(origin.getX(), origin.getY(), p2.getX(), p2.getY());
          curve2d.lineTo(halfPoint.getX(), halfPoint.getY());
        }

        previousHalfPoint = halfPoint;
        previousCellPoint = p;
        previousPoint = center;
      }
      if (previousPoint != null && previousHalfPoint != null) {
        curve2d.lineTo(previousPoint.getX(), previousPoint.getY());
      }
      add(
          new RenderInstruction.Stroke(
              curve2d,
              // Paint.of(new Color(0xFF_C3_37_28, true)),
              // Paint.of(new Color(0xFF_E1_50_40, true)),
              // Paint.of(new Color(0xFF_E1_9A_92, true)),
              Paint.of(new Color(0x7F_A3_00_00, true)),
              new BasicStroke(
                  Math.max(1.f, grid.getSize() / 10.f),
                  BasicStroke.CAP_ROUND,
                  BasicStroke.JOIN_ROUND),
              1.));
    }

    for (Point2D center : waypointList) {
      var bounds = new Rectangle2D.Double();
      bounds.width = grid.getCellWidth() / 3.;
      bounds.height = grid.getCellHeight() / 3.;
      bounds.x = center.getX() - bounds.width / 2.;
      bounds.y = center.getY() - bounds.height / 2.;

      add(new RenderInstruction.Icon(Images.ZONE_RENDERER_CELL_WAYPOINT, bounds));
    }

    if (highlight != null && AppState.getShowMovementMeasurements()) {
      for (CellPoint p : cellPath) {
        var center = zone.getGrid().getCellCenter(p);
        var distance = p.getDistanceTraveled(zone);
        var distanceWithoutTerrain = p.getDistanceTraveledWithoutTerrain();

        if (distance <= 0) {
          continue;
        }

        // Font size of 12 at grid size 50 is default
        double fontScale = grid.getSize() / 50.;
        // 7 pixels at 100% zoom & grid size of 50
        double padding = 7 * fontScale;
        // For hexes, bump it a bit toward the center.
        var isHexGrid = grid.getType().isHex();
        double paddingX = padding + (isHexGrid ? grid.getCellWidth() / 10. : 0.);
        double paddingY = padding + (isHexGrid ? grid.getCellHeight() / 10. : 0.);

        var bounds = new Rectangle2D.Double();
        bounds.width = grid.getCellWidth() - 2 * paddingX;
        bounds.height = grid.getCellHeight() - 2 * paddingY;
        bounds.x = center.getX() - bounds.width / 2.;
        bounds.y = center.getY() - bounds.height / 2.;

        var screenBounds = viewport.zoneScale().toScreenSpace(bounds);

        int fontSize = (int) (viewport.zoneScale().getScale() * 12 * fontScale);
        String distanceText = NumberFormat.getInstance().format(distance);
        if (DeveloperOptions.Toggle.ShowAiDebugging.get()) {
          distanceText += " (" + NumberFormat.getInstance().format(distanceWithoutTerrain) + ")";
          fontSize = fontSize * 3 / 4;
        }

        Font font = new Font(Font.DIALOG, Font.BOLD, fontSize);

        var canvas = new Canvas();
        var fm = canvas.getFontMetrics(font);
        int textWidth = SwingUtilities.computeStringWidth(fm, distanceText);
        int textHeight = fm.getHeight();

        // Text is aligned to the right, with the baseline aligned with the bottom.
        // Add the descent is required to get the baseline rather than the lowest text point on
        // the bottom of the bounds.
        var textBounds =
            new Rectangle2D.Double(
                screenBounds.getMaxX() - textWidth,
                screenBounds.getMaxY() - textHeight + fm.getDescent(),
                textWidth,
                textHeight);

        add(
            new RenderInstruction.Text(
                distanceText,
                font,
                textBounds,
                Color.black,
                RenderInstruction.Text.Decoration.None));
      }
    }

    timer.stop("renderPath-2");
  }

  private void addZonePointPath(Path<ZonePoint> pathZP, TokenFootprint footprint) {
    var highlight = Color.white;
    var highlightStroke = new BasicStroke(9);
    var lineStroke = new BasicStroke(1);

    var grid = zone.getGrid();
    var footprintBounds = footprint.getBounds(grid);
    List<ZonePoint> pathList = pathZP.getCellPath();

    Point2D lastPoint = null;
    for (ZonePoint zp : pathList) {
      var nextPoint =
          new Point2D.Double(
              zp.x + footprintBounds.width * footprint.getScale() / 2d,
              zp.y + footprintBounds.height * footprint.getScale() / 2d);
      if (lastPoint == null) {
        lastPoint = nextPoint;
        continue;
      }

      var line = new Line2D.Double(lastPoint, nextPoint);
      add(new RenderInstruction.Stroke(line, Paint.of(highlight), highlightStroke, 80. / 255.));
      add(new RenderInstruction.Stroke(line, Paint.of(Color.blue), lineStroke, 1.));

      lastPoint = nextPoint;
    }

    if (pathList.size() > 2) {
      var waypoints = pathList.subList(1, pathList.size() - 1);
      for (var zp : waypoints) {
        var waypoint =
            new Point2D.Double(
                zp.x + footprintBounds.width * footprint.getScale() / 2d,
                zp.y + footprintBounds.height * footprint.getScale() / 2d);

        var bounds = new Rectangle2D.Double();
        bounds.width = grid.getCellWidth() / 3.;
        bounds.height = grid.getCellHeight() / 3.;
        bounds.x = waypoint.getX() - bounds.width / 2.;
        bounds.y = waypoint.getY() - bounds.height / 2.;

        add(new RenderInstruction.Icon(Images.ZONE_RENDERER_CELL_WAYPOINT, bounds));
      }
    }
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
