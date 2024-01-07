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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.DeveloperOptions;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.model.AbstractPoint;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.Grid;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.TokenFootprint;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;

public class PathRenderer {
  private final RenderHelper renderHelper;
  private final Zone zone;

  public PathRenderer(RenderHelper renderHelper, Zone zone) {
    this.renderHelper = renderHelper;
    this.zone = zone;
  }

  /**
   * Render the path of a token. Highlight the cells and draw the waypoints, distance numbers, and
   * line path.
   *
   * @param g The Graphics2D renderer.
   * @param path The path of the token.
   * @param footprint The footprint of the token.
   */
  public <T extends AbstractPoint> void renderPath(
      Graphics2D g, Path<T> path, TokenFootprint footprint) {
    var timer = CodeTimer.get();
    timer.start("renderPath");
    try {
      if (path == null) {
        return;
      }
      if (path.getCellPath().isEmpty()) {
        return;
      }

      if (path.getCellPath().get(0) instanceof CellPoint) {
        renderHelper.render(
            g, worldG -> renderGriddedPath(worldG, (Path<CellPoint>) path, footprint));
      } else {
        renderHelper.render(
            g, worldG -> renderGridlessPath(worldG, (Path<ZonePoint>) path, footprint));
      }
    } finally {
      timer.stop("renderPath");
    }
  }

  private void renderGriddedPath(Graphics2D g, Path<CellPoint> path, TokenFootprint footprint) {
    final var timer = CodeTimer.get();
    timer.start("renderPath-1");

    Grid grid = zone.getGrid();
    Rectangle footprintBounds = footprint.getBounds(grid);

    List<CellPoint> cellPath = path.getCellPath();

    final var pathSet = new HashSet<CellPoint>();
    final var waypointList = new ArrayList<Point2D>();
    {
      CellPoint previousPoint = null;
      for (CellPoint p : cellPath) {
        pathSet.addAll(footprint.getOccupiedCells(p));

        if (path.isWaypoint(p) && previousPoint != null) {
          ZonePoint zp = grid.convert(p);
          var point =
              new Point2D.Double(
                  zp.x + footprintBounds.width / 2., zp.y + footprintBounds.height / 2.);
          waypointList.add(point);
        }
        previousPoint = p;
      }
    }

    // Don't show the final path point as a waypoint, it's redundant, and ugly
    if (!waypointList.isEmpty()) {
      waypointList.remove(waypointList.size() - 1);
    }
    timer.stop("renderPath-1");
    // log.info("pathSet size: " + pathSet.size());

    timer.start("renderPath-2");
    Dimension cellOffset = zone.getGrid().getCellOffset();
    {
      final var cellG = (Graphics2D) g.create();
      for (CellPoint p : pathSet) {
        ZonePoint zp = grid.convert(p);
        var point =
            new Point2D.Double(
                zp.x + grid.getCellWidth() / 2. + cellOffset.width,
                zp.y + grid.getCellHeight() / 2. + cellOffset.height);
        highlightCell(cellG, point, grid.getCellHighlight(), 1.0f);
      }
    }
    if (AppState.getShowMovementMeasurements()) {
      final var distanceG = (Graphics2D) g.create();
      double cellAdj = grid.isHex() ? 2.5 : 2;
      for (CellPoint p : cellPath) {
        ZonePoint zp = grid.convert(p);
        var point =
            new Point2D.Double(
                zp.x + grid.getCellWidth() / cellAdj + cellOffset.width,
                zp.y + grid.getCellHeight() / cellAdj + cellOffset.height);
        addDistanceText(
            distanceG, point, p.getDistanceTraveled(zone), p.getDistanceTraveledWithoutTerrain());
      }
    }
    {
      final var waypointG = (Graphics2D) g.create();
      for (Point2D point : waypointList) {
        point =
            new Point2D.Double(point.getX() + cellOffset.width, point.getY() + cellOffset.height);
        highlightCell(
            waypointG, point, RessourceManager.getImage(Images.ZONE_RENDERER_CELL_WAYPOINT), .333f);
      }
    }

    // Line path
    if (grid.getCapabilities().isPathLineSupported()) {
      final var lineG = (Graphics2D) g.create();
      ZonePoint lineOffset;
      if (grid.isHex()) {
        lineOffset = new ZonePoint(0, 0);
      } else {
        lineOffset =
            new ZonePoint(
                footprintBounds.x + footprintBounds.width / 2 - grid.getOffsetX(),
                footprintBounds.y + footprintBounds.height / 2 - grid.getOffsetY());
      }

      lineG.setColor(Color.blue);

      Point2D previousHalfPoint2D = null;
      // Really a ZonePoint, but that doesn't currently support floats.
      CellPoint previousPoint = null;
      for (CellPoint p : cellPath) {
        if (previousPoint != null) {
          ZonePoint originZp = grid.convert(previousPoint);
          ZonePoint destinationZp = grid.convert(p);

          var halfPoint2D =
              new Point2D.Double(
                  (originZp.x + destinationZp.x) / 2., (originZp.y + destinationZp.y) / 2.);

          if (previousHalfPoint2D != null) {
            var x1 = previousHalfPoint2D.getX() + lineOffset.x;
            var y1 = previousHalfPoint2D.getY() + lineOffset.y;

            var x2 = originZp.x + lineOffset.x;
            var y2 = originZp.y + lineOffset.y;

            var xh = halfPoint2D.x + lineOffset.x;
            var yh = halfPoint2D.y + lineOffset.y;

            QuadCurve2D curve = new QuadCurve2D.Double(x1, y1, x2, y2, xh, yh);
            lineG.draw(curve);
          }

          previousHalfPoint2D = halfPoint2D;
        }
        previousPoint = p;
      }
    }
    timer.stop("renderPath-2");
  }

  private void renderGridlessPath(Graphics2D g, Path<ZonePoint> pathZP, TokenFootprint footprint) {
    final var timer = CodeTimer.get();
    timer.start("renderPath-3");

    Rectangle footprintBounds = footprint.getBounds(zone.getGrid());

    List<ZonePoint> pathList = pathZP.getCellPath();

    {
      final var lineG = (Graphics2D) g.create();

      // Line
      Color highlight = new Color(255, 255, 255, 80);
      Stroke highlightStroke = new BasicStroke(9 / (float) lineG.getTransform().getScaleX());
      Stroke centerStroke = new BasicStroke(1 / (float) lineG.getTransform().getScaleX());

      // Really a ZonePoint, but that doesn't currently handle floats.
      Point2D lastPoint = null;
      for (ZonePoint zp : pathList) {
        if (lastPoint == null) {
          lastPoint =
              new Point2D.Double(
                  zp.x + (footprintBounds.width / 2.) * footprint.getScale(),
                  zp.y + (footprintBounds.height / 2.) * footprint.getScale());
          continue;
        }
        final var nextPoint =
            new Point2D.Double(
                zp.x + (footprintBounds.width / 2.) * footprint.getScale(),
                zp.y + (footprintBounds.height / 2.) * footprint.getScale());
        final var line = new Line2D.Double(lastPoint, nextPoint);

        lineG.setColor(highlight);
        lineG.setStroke(highlightStroke);
        lineG.draw(line);

        lineG.setStroke(centerStroke);
        lineG.setColor(Color.blue);
        lineG.draw(line);

        lastPoint = nextPoint;
      }
      lineG.dispose();
    }

    {
      final var waypointG = (Graphics2D) g.create();

      // Waypoints
      boolean originPoint = true;
      for (ZonePoint p : pathList) {
        // Skip the first point (it's the path origin)
        if (originPoint) {
          originPoint = false;
          continue;
        }

        // Skip the final point
        if (p == pathList.get(pathList.size() - 1)) {
          continue;
        }
        var point =
            new Point2D.Double(
                p.x + footprintBounds.width / 2. * footprint.getScale(),
                p.y + footprintBounds.height / 2. * footprint.getScale());
        highlightCell(
            waypointG, point, RessourceManager.getImage(Images.ZONE_RENDERER_CELL_WAYPOINT), .333f);
      }
    }

    timer.stop("renderPath-3");
  }

  /**
   * Draw an image at given zone point
   *
   * @param g
   * @param point
   * @param image
   * @param size The size of the image, as a proportion of the grid size.
   */
  private void highlightCell(Graphics2D g, Point2D point, BufferedImage image, float size) {
    Grid grid = zone.getGrid();

    double iwidth = grid.getCellWidth() * size;
    double iheight = grid.getCellHeight() * size;

    AffineTransform af = new AffineTransform();
    af.translate(point.getX() - iwidth / 2., point.getY() - iheight / 2.);
    af.scale(iwidth / image.getWidth(), iheight / image.getHeight());

    g.drawImage(image, af, renderHelper.getImageObserver());
  }

  public void addDistanceText(
      Graphics2D g, Point2D point, double distance, double distanceWithoutTerrain) {
    if (distance == 0) {
      return;
    }

    Grid grid = zone.getGrid();

    // Draw distance for each cell
    double fontScale = (double) grid.getSize() / 50; // Font size of 12 at grid size 50 is default
    int fontSize = (int) (12 * fontScale);
    var textOffset = 7 * fontScale; // 7 pixels at 100% zoom & grid size of 50

    String distanceText = NumberFormat.getInstance().format(distance);
    if (DeveloperOptions.Toggle.ShowAiDebugging.isEnabled()) {
      distanceText += " (" + NumberFormat.getInstance().format(distanceWithoutTerrain) + ")";
      fontSize = (int) (fontSize * 0.75);
    }

    Font font = new Font(Font.DIALOG, Font.BOLD, fontSize);
    FontMetrics fm = g.getFontMetrics(font);
    int textWidth = fm.stringWidth(distanceText);

    g.setFont(font);
    g.setColor(Color.BLACK);

    g.drawString(
        distanceText,
        (float) (point.getX() + grid.getCellWidth() / 2 - textWidth - textOffset),
        (float) (point.getY() + grid.getCellHeight() / 2 - textOffset));
  }
}
