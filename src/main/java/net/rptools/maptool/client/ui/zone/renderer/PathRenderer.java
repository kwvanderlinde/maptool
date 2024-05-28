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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderablePath;
import net.rptools.maptool.model.AbstractPoint;

/** A new take on a PathRenderer that hates logic. */
public class PathRenderer {
  private final RenderHelper renderHelper;

  public PathRenderer(RenderHelper renderHelper) {
    this.renderHelper = renderHelper;
  }

  /**
   * Render the path of a token. Highlight the cells and draw the waypoints, distance numbers, and
   * line path.
   *
   * @param g The Graphics2D renderer.
   * @param path The path of the token.
   */
  public <T extends AbstractPoint> void renderPath(Graphics2D g, RenderablePath path) {
    var timer = CodeTimer.get();
    timer.start("renderPath");
    try {
      switch (path) {
        case RenderablePath.CellPath cellPath -> renderHelper.render(
            g, worldG -> renderGriddedPathWorld(worldG, cellPath));
        case RenderablePath.PointPath pointPath -> renderHelper.render(
            g, worldG -> renderGridlessPathWorld(worldG, pointPath));
      }
    } finally {
      timer.stop("renderPath");
    }
  }

  private void renderGridlessPathWorld(Graphics2D g, RenderablePath.PointPath path) {
    {
      final var lineG = (Graphics2D) g.create();

      // Line
      Color highlight = new Color(255, 255, 255, 80);
      Stroke highlightStroke = new BasicStroke(9 / (float) lineG.getTransform().getScaleX());
      Stroke centerStroke = new BasicStroke(1 / (float) lineG.getTransform().getScaleX());

      // Really a ZonePoint, but that doesn't currently handle floats.
      Point2D lastPoint = null;
      for (var point : path.points()) {
        if (lastPoint == null) {
          lastPoint = point;
          continue;
        }

        final var line = new Line2D.Double(lastPoint, point);

        lineG.setColor(highlight);
        lineG.setStroke(highlightStroke);
        lineG.draw(line);

        lineG.setStroke(centerStroke);
        lineG.setColor(Color.blue);
        lineG.draw(line);

        lastPoint = point;
      }
      lineG.dispose();
    }

    {
      // Waypoints
      final var waypointG = (Graphics2D) g.create();
      for (var point : path.waypoints()) {
        drawCellImage(
            waypointG,
            new Rectangle2D.Double(
                point.getX() - path.waypointSizeX() / 2.,
                point.getY() - path.waypointSizeY() / 2.,
                path.waypointSizeX(),
                path.waypointSizeY()),
            path.waypointDecoration(),
            1.0);
      }
    }
  }

  private void renderGriddedPathWorld(Graphics2D g, RenderablePath.CellPath path) {
    for (RenderablePath.PathCell p : path.occupiedCells()) {
      drawCellImage(g, p.bounds, path.cellHighlight(), 1.0);
      if (p.isWaypoint) {
        drawCellImage(g, p.bounds, path.waypointDecoration(), path.waypointDecorationScale());
      }
      if (p.distanceText != null) {
        drawCellText(
            g,
            p.bounds,
            p.distanceText.text(),
            path.distanceTextFont(),
            p.distanceText.bottomRightPosition());
      }
    }
    {
      var lineG = (Graphics2D) g.create();
      try {
        lineG.setColor(Color.blue);

        // Draw a curve through all the grid crossings, using the cell centers as control points.
        var path2D = new Path2D.Double();
        Point2D previousHalfPoint = null;
        Point2D previousPoint = null;
        for (var currentPoint : path.pathCentres()) {
          if (previousPoint != null) {
            var halfPoint =
                new Point2D.Double(
                    (previousPoint.getX() + currentPoint.getX()) / 2.,
                    (previousPoint.getY() + currentPoint.getY()) / 2.);

            if (previousHalfPoint == null) {
              path2D.moveTo(halfPoint.getX(), halfPoint.getY());
            } else {
              // Previous point is used as a control point here.
              path2D.quadTo(
                  previousPoint.getX(), previousPoint.getY(), halfPoint.getX(), halfPoint.getY());
            }

            previousHalfPoint = halfPoint;
          }
          previousPoint = currentPoint;
        }

        lineG.draw(path2D);
      } finally {
        lineG.dispose();
      }
    }
  }

  private void drawCellImage(Graphics2D g, Rectangle2D bounds, BufferedImage image, double scale) {
    double iwidth = bounds.getWidth() * scale;
    double iheight = bounds.getHeight() * scale;
    var center = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());

    AffineTransform af = new AffineTransform();
    af.translate(center.getX() - iwidth / 2., center.getY() - iheight / 2.);
    af.scale(iwidth / image.getWidth(), iheight / image.getHeight());

    g.drawImage(image, af, renderHelper.getImageObserver());
  }

  private void drawCellText(
      Graphics2D g, Rectangle2D bounds, String text, Font font, Point2D bottomRightPosition) {
    // Draw distance for each cell
    g = (Graphics2D) g.create();
    try {
      g.setFont(font);
      g.setColor(Color.BLACK);

      FontMetrics fm = g.getFontMetrics(font);
      int textWidth = fm.stringWidth(text);

      // Note: vertically, the point at the baseline
      g.drawString(
          text,
          (float) (bounds.getMaxX() - bottomRightPosition.getX() - textWidth),
          (float) (bounds.getMaxY() - bottomRightPosition.getY()));
    } finally {
      g.dispose();
    }
  }
}
