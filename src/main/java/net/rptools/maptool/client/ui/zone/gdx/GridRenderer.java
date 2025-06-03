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
package net.rptools.maptool.client.ui.zone.gdx;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Pools;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.model.*;
import space.earlygrey.shapedrawer.JoinType;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class GridRenderer {
  private ZoneCache zoneCache;
  private final AreaRenderer areaRenderer;
  private final ShapeDrawer drawer;
  private final Batch batch;
  private final Camera hudCam;
  private final OrthographicCamera cam;

  public GridRenderer(AreaRenderer areaRenderer, Camera hudCam, OrthographicCamera cam) {
    this.areaRenderer = areaRenderer;
    this.drawer = areaRenderer.getShapeDrawer();
    batch = drawer.getBatch();
    this.hudCam = hudCam;
    this.cam = cam;
  }

  public void setZoneCache(ZoneCache zoneCache) {
    this.zoneCache = zoneCache;
  }

  public void render() {
    var grid = zoneCache.getZone().getGrid();

    // Do nothing for GridlessGrid
    if (grid instanceof HexGrid hexGrid) {
      renderGrid(hexGrid);
    } else if (grid instanceof SquareGrid squareGrid) {
      renderGrid(squareGrid);
    } else if (grid instanceof IsometricGrid isometricGrid) {
      renderGrid(isometricGrid);
    }
  }

  private void renderGrid(HexGrid grid) {
    var zoneScale = zoneCache.getZoneViewModel().getZoneScale();
    var size = zoneCache.getZoneRenderer().getSize();
    var scale = zoneScale.getScale();
    var scaledMinorRadius = grid.getMinorRadius() * scale;
    var scaledEdgeLength = grid.getEdgeLength() * scale;
    var scaledEdgeProjection = grid.getEdgeProjection() * scale;
    var scaledHex = grid.createHalfShape(scaledMinorRadius, scaledEdgeProjection, scaledEdgeLength);

    int offU = grid.getOffU(zoneScale);
    int offV = grid.getOffV(zoneScale);
    int count = 0;

    var tmpColor = Pools.obtain(Color.class);
    Color.argb8888ToColor(tmpColor, zoneCache.getZone().getGridColor());
    tmpColor.premultiplyAlpha();
    drawer.setColor(tmpColor);
    var floats = areaRenderer.pathToFloatArray(scaledHex.getPathIterator(null));
    var lineWidth = AppState.getGridLineWeight();

    for (double v = offV % (scaledMinorRadius * 2) - (scaledMinorRadius * 2);
        v < grid.getSizeV(size);
        v += scaledMinorRadius) {
      double offsetU = (int) ((count & 1) == 0 ? 0 : -(scaledEdgeProjection + scaledEdgeLength));
      count++;
      double start =
          offU % (2 * scaledEdgeLength + 2 * scaledEdgeProjection)
              - (2 * scaledEdgeLength + 2 * scaledEdgeProjection);
      double end = grid.getSizeU(size) + 2 * scaledEdgeLength + 2 * scaledEdgeProjection;
      double incr = 2 * scaledEdgeLength + 2 * scaledEdgeProjection;
      for (double u = start; u < end; u += incr) {
        float transX;
        float transY;
        if (grid instanceof HexGridVertical) {
          transX = (float) (u + offsetU);
          transY = hudCam.viewportHeight - (float) v;
        } else {
          transX = (float) v;
          transY = (float) (-u - offsetU) + hudCam.viewportHeight;
        }

        var tmpMatrix = Pools.obtain(Matrix4.class);
        tmpMatrix.translate(transX, transY, 0);
        batch.setTransformMatrix(tmpMatrix);
        drawer.update();

        drawer.path(floats, lineWidth, JoinType.SMOOTH, true);
        tmpMatrix.idt();
        batch.setTransformMatrix(tmpMatrix);
        Pools.free(tmpMatrix);
        drawer.update();
      }
    }
    Pools.free(tmpColor);
  }

  private void renderGrid(IsometricGrid grid) {
    var zoneScale = zoneCache.getZoneViewModel().getZoneScale();

    var scale = (float) zoneScale.getScale();
    int gridSize = (int) (grid.getSize() * scale);

    var tmpColor = Pools.obtain(Color.class);
    Color.argb8888ToColor(tmpColor, zoneCache.getZone().getGridColor());
    tmpColor.premultiplyAlpha();

    drawer.setColor(tmpColor);

    var x = hudCam.position.x - hudCam.viewportWidth / 2;
    var y = hudCam.position.y - hudCam.viewportHeight / 2;
    var w = hudCam.viewportWidth;
    var h = hudCam.viewportHeight;

    double isoHeight = grid.getSize() * scale;
    double isoWidth = grid.getSize() * 2 * scale;

    int offX = (int) (zoneScale.getOffsetX() % isoWidth + grid.getOffsetX() * scale) + 1;
    int offY = (int) (zoneScale.getOffsetY() % gridSize + grid.getOffsetY() * scale) + 1;

    int startCol = (int) ((int) (x / isoWidth) * isoWidth);
    int startRow = (int) (y / gridSize) * gridSize;

    for (double row = startRow; row < y + h + gridSize; row += gridSize) {
      for (double col = startCol; col < x + w + isoWidth; col += isoWidth) {
        drawHatch(grid, (int) (col + offX), h - (int) (row + offY));
      }
    }

    for (double row = startRow - (isoHeight / 2); row < y + h + gridSize; row += gridSize) {
      for (double col = startCol - (isoWidth / 2); col < x + w + isoWidth; col += isoWidth) {
        drawHatch(grid, (int) (col + offX), h - (int) (row + offY));
      }
    }
    Pools.free(tmpColor);
  }

  private void drawHatch(IsometricGrid grid, float x, float y) {
    var zoneScale = zoneCache.getZoneViewModel().getZoneScale();

    double isoWidth = grid.getSize() * zoneScale.getScale();
    int hatchSize = isoWidth > 10 ? (int) isoWidth / 8 : 2;

    var lineWidth = AppState.getGridLineWeight();

    drawer.line(x - (hatchSize * 2), y - hatchSize, x + (hatchSize * 2), y + hatchSize, lineWidth);
    drawer.line(x - (hatchSize * 2), y + hatchSize, x + (hatchSize * 2), y - hatchSize, lineWidth);
  }

  private void renderGrid(SquareGrid grid) {
    var zoneScale = zoneCache.getZoneViewModel().getZoneScale();

    var lineWidth = AppState.getGridLineWeight();
    float scale = (float) zoneScale.getScale();
    float gridSize = (grid.getSize() / cam.zoom);
    var tmpColor = Pools.obtain(Color.class);
    Color.argb8888ToColor(tmpColor, zoneCache.getZone().getGridColor());
    tmpColor.premultiplyAlpha();

    drawer.setColor(tmpColor);

    var x = 0;
    var y = 0;
    var w = hudCam.viewportWidth;
    var h = hudCam.viewportHeight;

    var offsetX = zoneScale.getOffsetX() / scale + grid.getOffsetX();
    offsetX %= grid.getSize();
    offsetX /= cam.zoom;

    var offsetY = zoneScale.getOffsetY() / scale + grid.getOffsetY();
    offsetY %= grid.getSize();
    offsetY /= cam.zoom;

    for (float x_ = x; x_ < x + w; x_ += gridSize) {
      // var rounded = Math.round(offsetX + x_);
      var rounded = offsetX + x_;
      drawer.line(rounded, y, rounded, y + h, lineWidth);
    }
    for (float y_ = y; y_ < y + h; y_ += gridSize) {
      // var rounded = Math.round(cam.viewportHeight - y_ - offsetY);
      var rounded = cam.viewportHeight - y_ - offsetY;
      drawer.line(x, rounded, x + w, rounded, lineWidth);
    }

    Pools.free(tmpColor);
  }
}
