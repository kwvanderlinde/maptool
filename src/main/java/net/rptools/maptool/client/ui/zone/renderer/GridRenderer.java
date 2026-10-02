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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.ui.zone.renderer;

import com.github.weisj.jsvg.util.ColorUtil;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.swing.SwingUtilities;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.model.*;
import net.rptools.maptool.model.entities.components.GridComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GridRenderer {
  private static final Logger log = LogManager.getLogger(GridRenderer.class);

  private final ZoneRenderer renderer;
  private final Zone zone;

  GridRenderer(ZoneRenderer renderer) {
    this.renderer = renderer;
    this.zone = renderer.getZone();
  }

  private static Color[] getGridColours(int gridColourInt) {
    Color gc = new Color(gridColourInt);
    Color contrast = new Color(ImageUtil.negativeColourInt(gridColourInt));
    return new Color[] {
      gc,
      ColorUtil.withAlpha(gc, 0.14f),
      ColorUtil.withAlpha(contrast, 0.04f),
      ColorUtil.withAlpha(contrast, 0.05f)
    };
  }

  private void drawGridShape(
      Scale zoneScale, double gridSize, Color[] gridColours, Graphics2D g, Shape shape) {
    final var gridLineWeight = AppState.getGridLineWeight();
    final var scale = (float) zoneScale.getScale();
    final var baseWidth = (float) (gridSize / 50);

    if (scale > 0.49f && gridColours.length > 1) {
      for (int i = gridColours.length - 1; i > -1; i--) {
        g.setColor(gridColours[i]);
        g.setStroke(
            new BasicStroke(
                baseWidth * (i + 1) * 0.5f * gridLineWeight * scale,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_MITER));
        g.draw(shape);
      }
    } else {
      g.setColor(gridColours[0]);
      g.setStroke(
          new BasicStroke(
              Math.clamp(
                  baseWidth * gridLineWeight * scale,
                  baseWidth * gridLineWeight * 0.15f,
                  baseWidth * gridLineWeight * 0.25f),
              BasicStroke.CAP_ROUND,
              BasicStroke.JOIN_MITER));
      g.draw(shape);
    }
  }

  public void renderGrid(Graphics2D g, PlayerView view) {
    if (true || !AppState.isShowGrid()) {
      return;
    }

    var grid = zone.getGrid();
    var zoneScale = renderer.getViewModel().getZoneScale();

    if (grid.getSize() * zoneScale.getScale() < ZoneRendererConstants.MIN_GRID_SIZE) {
      return;
    }

    var gridColours = getGridColours(zone.getGridColor());

    var bounds = new Rectangle(0, 0, renderer.getWidth(), renderer.getHeight());
    switch (grid) {
      case SquareGrid squareGrid -> draw(g, squareGrid, gridColours, bounds);
      case HexGrid hexGrid -> draw(g, hexGrid, gridColours, bounds);
      case IsometricGrid isometricGrid -> draw(g, isometricGrid, gridColours, bounds);
      case GridlessGrid gridlessGrid -> {
        /* Nothing to do */
      }
      default -> {
        log.error("Unknown grid type: {}", grid.getClass());
      }
    }
  }

  public void renderCoordinates(Graphics2D g, PlayerView view) {
    if (AppState.isShowCoordinates()) {
      var grid = zone.getGrid();
      var bounds = new Rectangle(0, 0, renderer.getWidth(), renderer.getHeight());
      switch (grid) {
        case SquareGrid squareGrid -> drawCoordinates(g, squareGrid, bounds);
        // We only support coordinates for square grids at this time.
        case HexGrid hexGrid -> {
          /* Nothing to do */
        }
        case IsometricGrid isometricGrid -> {
          /* Nothing to do */
        }
        case GridlessGrid gridlessGrid -> {
          /* Nothing to do */
        }
        default -> {
          log.error("Unknown grid type: {}", grid.getClass());
        }
      }
    }
  }

  private void draw(Graphics2D g, SquareGrid grid, Color[] gridColours, Rectangle bounds) {
    var size = grid.getSize();
    var zoneScale = renderer.getViewModel().getZoneScale();
    double scale = zoneScale.getScale();
    double scaledSize = size * scale;

    g.setColor(new Color(zone.getGridColor()));

    int offX = (int) (zoneScale.getOffsetX() % scaledSize + grid.getOffsetX() * scale);
    int offY = (int) (zoneScale.getOffsetY() % scaledSize + grid.getOffsetY() * scale);

    int startCol = (int) ((int) (bounds.x / scaledSize) * scaledSize);
    int startRow = (int) ((int) (bounds.y / scaledSize) * scaledSize);
    Path2D path = new Path2D.Double();
    for (double row = startRow; row < bounds.y + bounds.height + scaledSize; row += scaledSize) {
      path.append(
          new Line2D.Double(
              bounds.x, (int) (row + offY), bounds.x + bounds.width, (int) (row + offY)),
          false);
    }
    for (double col = startCol; col < bounds.x + bounds.width + scaledSize; col += scaledSize) {
      path.append(
          new Line2D.Double(
              (int) (col + offX), bounds.y, (int) (col + offX), bounds.y + bounds.height),
          false);
    }
    drawGridShape(zoneScale, size, gridColours, g, path);
  }

  private void draw(Graphics2D g, HexGrid grid, Color[] gridColours, Rectangle bounds) {
    var isHorizontal = grid.getType() == Grid.GridType.HexHorizontal;
    var zoneScale = renderer.getViewModel().getZoneScale();
    var scale = zoneScale.getScale();
    var scaledMinorRadius = grid.getMinorRadius() * scale;
    var scaledEdgeLength = grid.getEdgeLength() * scale;
    var scaledEdgeProjection = grid.getEdgeProjection() * scale;
    var scaledHex =
        createHexHalfShape(isHorizontal, scaledMinorRadius, scaledEdgeProjection, scaledEdgeLength);

    int offU = grid.getOffU(zoneScale);
    int offV = grid.getOffV(zoneScale);
    int count = 0;

    Object oldAntiAlias = SwingUtil.useAntiAliasing(g);
    g.setColor(new Color(zone.getGridColor()));
    g.setStroke(new BasicStroke(AppState.getGridLineWeight()));

    for (double v = offV % (scaledMinorRadius * 2) - (scaledMinorRadius * 2);
        v < grid.getSizeV(bounds.getSize());
        v += scaledMinorRadius) {
      double offsetU = (int) ((count & 1) == 0 ? 0 : -(scaledEdgeProjection + scaledEdgeLength));
      count++;

      double start =
          offU % (2 * scaledEdgeLength + 2 * scaledEdgeProjection)
              - (2 * scaledEdgeLength + 2 * scaledEdgeProjection);
      double end =
          grid.getSizeU(bounds.getSize()) + 2 * scaledEdgeLength + 2 * scaledEdgeProjection;
      double incr = 2 * scaledEdgeLength + 2 * scaledEdgeProjection;
      for (double u = start; u < end; u += incr) {
        var translateX = isHorizontal ? v : u + offsetU;
        var translateY = isHorizontal ? u + offsetU : v;

        g.translate(translateX, translateY);

        drawGridShape(zoneScale, grid.getSize(), gridColours, g, scaledHex);

        // Undo the translation.
        g.translate(-translateX, -translateY);
      }
    }
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntiAlias);
  }

  private static Path2D createHexHalfShape(
      boolean isHorizontal, double minorRadius, double edgeProjection, double edgeLength) {
    var hex = new Path2D.Double();
    hex.moveTo(0, minorRadius);
    hex.lineTo(edgeProjection, 0);
    hex.lineTo(edgeProjection + edgeLength, 0);
    hex.lineTo(edgeProjection + edgeLength + edgeProjection, minorRadius);

    if (isHorizontal) {
      // flip the half-hex over y = x
      AffineTransform at = new AffineTransform();
      at.rotate(Math.toRadians(90.0));
      at.scale(1, -1);
      hex.transform(at);
    }

    return hex;
  }

  private void draw(Graphics2D g, IsometricGrid grid, Color[] gridColours, Rectangle bounds) {
    var size = grid.getSize();
    var zoneScale = renderer.getViewModel().getZoneScale();
    double scale = zoneScale.getScale();
    double gridSize = size * scale;
    double isoHeight = size * scale;
    double isoWidth = size * 2 * scale;
    Path2D path = new Path2D.Double();

    int offX = (int) (zoneScale.getOffsetX() % isoWidth + grid.getOffsetX() * scale);
    int offY = (int) (zoneScale.getOffsetY() % gridSize + grid.getOffsetY() * scale);

    int startCol = (int) ((int) (bounds.x / isoWidth) * isoWidth);
    int startRow = (int) ((int) (bounds.y / gridSize) * gridSize);

    for (double row = startRow; row < bounds.y + bounds.height + gridSize; row += gridSize) {
      for (double col = startCol; col < bounds.x + bounds.width + isoWidth; col += isoWidth) {
        path.append(drawIsoHatch(zoneScale, size, (int) (col + offX), (int) (row + offY)), false);
      }
    }

    for (double row = startRow - (isoHeight / 2);
        row < bounds.y + bounds.height + gridSize;
        row += gridSize) {
      for (double col = startCol - (isoWidth / 2);
          col < bounds.x + bounds.width + isoWidth;
          col += isoWidth) {
        path.append(drawIsoHatch(zoneScale, size, (int) (col + offX), (int) (row + offY)), false);
      }
    }
    drawGridShape(zoneScale, size, gridColours, g, path);
  }

  private static Shape drawIsoHatch(Scale zoneScale, double gridSize, int x, int y) {
    return drawIsoHatch(gridSize * zoneScale.getScale(), x, y);
  }

  private static Shape drawIsoHatch(double isoHeight, double x, double y) {
    double hatchSize = isoHeight / 8;
    Path2D path = new Path2D.Double();
    path.append(
        new Line2D.Double(x - (hatchSize * 2), y - hatchSize, x + (hatchSize * 2), y + hatchSize),
        false);
    path.append(
        new Line2D.Double(x - (hatchSize * 2), y + hatchSize, x + (hatchSize * 2), y - hatchSize),
        false);
    return path;
  }

  private void drawCoordinates(Graphics2D g, SquareGrid grid, Rectangle bounds) {
    Object oldAA = SwingUtil.useAntiAliasing(g);

    Font oldFont = g.getFont();
    g.setFont(g.getFont().deriveFont(20f).deriveFont(Font.BOLD));
    FontMetrics fm = g.getFontMetrics();

    var zoneScale = renderer.getViewModel().getZoneScale();
    double cellSize = grid.getSize() * zoneScale.getScale();
    CellPoint topLeft = grid.convert(new ScreenPoint(0, 0).convertToZone(zoneScale));
    var topLeftZone = grid.convert(topLeft);
    ScreenPoint sp = zoneScale.toScreenSpace(topLeftZone.x, topLeftZone.y);

    Dimension size = bounds.getSize();

    int startX = SwingUtilities.computeStringWidth(fm, "MMM") + 10;

    double x = sp.x + cellSize / 2; // Start at middle of the cell that's on screen
    int nextAvailableSpace = -1;
    while (x < size.width) {
      String coord = Integer.toString(topLeft.x);

      int strWidth = SwingUtilities.computeStringWidth(fm, coord);
      int strX = (int) x - strWidth / 2;

      if (x > startX && strX > nextAvailableSpace) {
        g.setColor(Color.black);
        g.drawString(coord, strX - 1, fm.getHeight() - 1);
        g.drawString(coord, strX + 1, fm.getHeight() - 1);
        g.drawString(coord, strX - 1, fm.getHeight() + 1);
        g.drawString(coord, strX + 1, fm.getHeight() + 1);
        g.setColor(Color.orange);
        g.drawString(coord, strX, fm.getHeight());

        nextAvailableSpace = strX + strWidth + 10;
      }
      x += cellSize;
      topLeft.x++;
    }
    double y = sp.y + cellSize / 2; // Start at middle of the cell that's on screen
    nextAvailableSpace = -1;
    while (y < size.height) {
      String coord = SquareGrid.decimalToAlphaCoord(topLeft.y);

      int strY = (int) y + fm.getAscent() / 2;

      if (y > fm.getHeight() && strY > nextAvailableSpace) {
        g.setColor(Color.black);
        g.drawString(coord, 10 - 1, strY - 1);
        g.drawString(coord, 10 + 1, strY - 1);
        g.drawString(coord, 10 - 1, strY + 1);
        g.drawString(coord, 10 + 1, strY + 1);
        g.setColor(Color.yellow);
        g.drawString(coord, 10, strY);

        nextAvailableSpace = strY + fm.getAscent() / 2 + 10;
      }
      y += cellSize;
      topLeft.y++;
    }
    g.setFont(oldFont);
    SwingUtil.restoreAntiAliasing(g, oldAA);
  }

  public void renderGrid(Graphics2D g, GridComponent grid) {
    if (!AppState.isShowGrid()) {
      return;
    }

    var zoneScale = renderer.getViewModel().getZoneScale();

    if (grid.size() * zoneScale.getScale() < ZoneRendererConstants.MIN_GRID_SIZE) {
      return;
    }

    var gridColours = getGridColours(zone.getGridColor());
    var bounds = g.getClip().getBounds2D();

    switch (grid.type()) {
      case Square -> renderSquareGrid(g, grid, gridColours, bounds);
      case HexPointyTop -> renderHexGrid(g, true, grid, gridColours, bounds);
      case HexFlatTop -> renderHexGrid(g, false, grid, gridColours, bounds);
      case Isometric -> renderIsomatricGrid(g, grid, gridColours, bounds);
      case Gridless -> {
        /* Nothing to do. */
      }
    }
  }

  private static void renderSquareGrid(
      Graphics2D g, GridComponent grid, Color[] gridColours, Rectangle2D bounds) {
    Path2D path = new Path2D.Double();

    {
      // Horizontal lines
      var sizeY = grid.size();
      var minX = bounds.getMinX();
      var maxX = bounds.getMaxX();
      // Lowest multiple greater-than-or-equal to lower bound.
      var minY = Math.ceil(bounds.getMinY() / sizeY) * sizeY;
      var maxY = bounds.getMaxY();
      for (double y = minY; y <= maxY; y += sizeY) {
        path.append(new Line2D.Double(minX, y, maxX, y), false);
      }
    }
    {
      // Vertical lines
      // Lowest multiple greater-than-or-equal to lower bound.
      var sizeX = grid.size();
      var minX = Math.ceil(bounds.getMinX() / sizeX) * sizeX;
      var maxX = bounds.getMaxX();
      var minY = bounds.getMinY();
      var maxY = bounds.getMaxY();
      for (double x = minX; x <= maxX; x += sizeX) {
        path.append(new Line2D.Double(x, minY, x, maxY), false);
      }
    }

    // TODO Fade-out grid lines with gridColours. I don't like their performance, though.
    g.setColor(grid.color());
    g.setStroke(
        new BasicStroke((float) grid.thickness(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
    g.draw(path);
  }

  private static void renderHexGrid(
      Graphics2D g,
      boolean isPointyTop,
      GridComponent grid,
      Color[] gridColours,
      Rectangle2D bounds) {
    var minorRadius = grid.size() / 2.;
    var edgeLength = grid.secondSize() / 2.;
    var edgeProjection = edgeLength / 2.;

    var hexTop = createHexHalfShape(isPointyTop, minorRadius, edgeProjection, edgeLength);

    var boundsMinV = isPointyTop ? bounds.getMinX() : bounds.getMinY();
    var boundsMaxV = isPointyTop ? bounds.getMaxX() : bounds.getMaxY();
    var boundsMinU = isPointyTop ? bounds.getMinY() : bounds.getMinX();
    var boundsMaxU = isPointyTop ? bounds.getMaxY() : bounds.getMaxX();

    Object oldAntiAlias = SwingUtil.useAntiAliasing(g);
    g.setColor(grid.color());
    g.setStroke(
        new BasicStroke((float) grid.thickness(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));

    var strideU = 3 * edgeLength / 2;
    var strideV = grid.size();
    // Cell (0, 0) has bounds [0, 0]x[secondSize, size]. That means a bit of cell (-1, -1) is
    // peeking at the [0, 0] corner. So the starting cell is a little less than we might expect.
    var startCellU = (int) Math.floor((boundsMinU - edgeProjection) / strideU);
    var startCellV =
        (int)
            Math.floor(
                (boundsMinV - minorRadius - (isEven(startCellU) ? 0 : strideV / 2.)) / strideV);
    var startU = startCellU * strideU;
    var startV = startCellV * strideV;

    // Render one column at a time.
    var column = startCellU;
    for (double u = startU; u < boundsMaxU; u += strideU) {
      var isEvenColumn = isEven(column++);

      for (double v = startV - (isEvenColumn ? 0 : strideV / 2); v < boundsMaxV; v += strideV) {
        var translateX = isPointyTop ? v : u;
        var translateY = isPointyTop ? u : v;

        var oldTransform = g.getTransform();
        try {
          g.translate(translateX, translateY);
          g.draw(hexTop);
        } finally {
          // Undo the translation.
          g.setTransform(oldTransform);
        }
      }
    }
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntiAlias);
  }

  private static void renderIsomatricGrid(
      Graphics2D g, GridComponent grid, Color[] gridColours, Rectangle2D bounds) {

    double isoHeight = grid.size();
    double isoWidth = grid.size() * 2;
    Path2D path = new Path2D.Double();

    double startCol = Math.floor(bounds.getMinX() / isoWidth) * isoWidth;
    double startRow = Math.floor(bounds.getMinY() / isoHeight) * isoHeight;
    for (var row = startRow; row < bounds.getMaxY() + isoHeight; row += isoHeight) {
      for (var col = startCol; col < bounds.getMaxX() + isoWidth; col += isoWidth) {
        path.append(drawIsoHatch(isoHeight, col, row), false);
      }
    }

    for (var row = startRow - isoHeight / 2; row < bounds.getMaxY() + isoHeight; row += isoHeight) {
      for (var col = startCol - isoWidth / 2; col < bounds.getMaxX() + isoWidth; col += isoWidth) {
        path.append(drawIsoHatch(isoHeight, col, row), false);
      }
    }

    g.setColor(grid.color());
    g.setStroke(
        new BasicStroke((float) grid.thickness(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
    g.draw(path);
  }

  private static boolean isEven(int n) {
    return (n & 1) == 0;
  }
}
