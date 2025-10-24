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

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.rptools.lib.GeometryUtil;
import net.rptools.lib.gdx.Earcut;
import org.locationtech.jts.geom.Polygon;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class AreaRenderer {
  public record TriangledPolygon(float[] vertices, short[] indices) {}

  private static final float POINTS_PER_BEZIER = 10f;

  private final ShapeDrawer drawer;
  private final PathMesher pathMesher = new PathMesher();
  private final Texture whitePixel;

  private final FloatArray tmpFloat = new FloatArray();

  private final IntArray segmentIndicies = new IntArray();

  private final Color color = Color.WHITE.cpy();

  public AreaRenderer(ShapeDrawer drawer, Texture whitePixel) {
    this.drawer = drawer;
    this.whitePixel = whitePixel;
  }

  public void setColor(Color value) {
    color.set(Objects.requireNonNullElse(value, Color.WHITE));
    texture = whitePixel;
  }

  private final float[] floatsFromArea = new float[6];
  private final Vector2 tmpVector = new Vector2();
  private final Vector2 tmpVector0 = new Vector2();
  private final Vector2 tmpVector1 = new Vector2();
  private final Vector2 tmpVector2 = new Vector2();
  private final Vector2 tmpVector3 = new Vector2();
  private final Vector2 tmpVectorOut = new Vector2();

  private Texture texture = null;

  public void setTexture(Texture texture) {
    this.texture = texture;
  }

  public List<TriangledPolygon> triangulate(Collection<Polygon> jts) {
    if (jts.isEmpty()) {
      return List.of();
    }

    var result = new ArrayList<TriangledPolygon>();
    for (var poly : jts) {
      short[] holeIndices = new short[poly.getNumInteriorRing()];
      tmpFloat.clear();

      for (var c : poly.getExteriorRing().getCoordinates()) {
        tmpFloat.add((float) c.x, -(float) c.y);
      }

      for (int holeI = 0, holeN = poly.getNumInteriorRing(); holeI < holeN; ++holeI) {
        holeIndices[holeI] = (short) (tmpFloat.size / 2);
        for (var c : poly.getInteriorRingN(holeI).getCoordinates()) {
          tmpFloat.add((float) c.x, -(float) c.y);
        }
      }

      var vertices = tmpFloat.toArray();
      var indices = Earcut.earcut(vertices, holeIndices, (short) 2);
      result.add(new TriangledPolygon(vertices, indices.toArray()));
    }
    return result;
  }

  public void fill(PolygonSpriteBatch batch, List<TriangledPolygon> polygons) {
    for (var poly : polygons) {
      var polyRegion = new PolygonRegion(new TextureRegion(texture), poly.vertices, poly.indices);
      paintRegion(batch, polyRegion);
    }
  }

  public void fillArea(PolygonSpriteBatch batch, Shape shape) {
    switch (shape) {
      case Line2D line2D -> {
        /*Not a filled shape, nothing to do*/
      }
      case Ellipse2D ellipse2D -> {
        drawer.setColor(color);
        drawer.filledEllipse(
            (float) ellipse2D.getCenterX(),
            -(float) ellipse2D.getCenterY(),
            (float) ellipse2D.getWidth() / 2,
            (float) ellipse2D.getHeight() / 2);
        drawer.setColor(Color.WHITE);
      }
      case Rectangle2D rectangle2D -> {
        drawer.setColor(color);
        drawer.filledRectangle(
            (float) rectangle2D.getMinX(), -(float) rectangle2D.getMaxY(),
            (float) rectangle2D.getWidth(), (float) rectangle2D.getHeight());
        drawer.setColor(Color.WHITE);
      }
      default -> {
        // Handles Path2D and Area in particular.
        if (!(shape instanceof Area)) {
          shape = new Area(shape);
        }

        for (var poly : triangulate(GeometryUtil.toJtsPolygons(shape))) {
          var polyRegion =
              new PolygonRegion(new TextureRegion(texture), poly.vertices, poly.indices);
          paintRegion(batch, polyRegion);
        }
      }
    }
  }

  public void drawArea(PolygonSpriteBatch batch, Shape shape, BasicStroke stroke) {
    if (shape == null) {
      return;
    }

    // TODO My wall implementation is exposing an issue with turnbacks.

    // TODO pathToFloatArray() should have some basic guarantees about the minimum distance between
    //  subsequent points, eliding any that fall afoul of this minimum. This will allow the jointer
    //  to proceed unabashedly.
    pathToFloatArray(shape.getPathIterator(null));

    if (segmentIndicies.size == 1) {
      var polygon = drawPathWithJoin(tmpFloat, stroke);
      paintPolygon(batch, polygon);
    } else {
      var floats = tmpFloat.toArray();
      var lastSegmentIndex = 0;
      for (int i = 1; i <= segmentIndicies.size; i++) {
        var idx = i == segmentIndicies.size ? floats.length / 2 : segmentIndicies.get(i);
        var vertexCount = (idx - lastSegmentIndex);

        tmpFloat.ensureCapacity(2 * vertexCount);
        System.arraycopy(floats, 2 * lastSegmentIndex, tmpFloat.items, 0, 2 * vertexCount);
        tmpFloat.setSize(2 * vertexCount);
        var polygon = drawPathWithJoin(tmpFloat, stroke);
        paintPolygon(batch, polygon);
        lastSegmentIndex = idx;
      }
    }
  }

  public void paintPolygon(PolygonSpriteBatch batch, TriangledPolygon polygon) {
    var polyReg = new PolygonRegion(new TextureRegion(texture), polygon.vertices, polygon.indices);
    paintRegion(batch, polyReg);
  }

  public void paintVertices(PolygonSpriteBatch batch, float[] vertices, short[] holeIndices) {
    var indices = Earcut.earcut(vertices, holeIndices, (short) 2).toArray();
    var polyReg = new PolygonRegion(new TextureRegion(texture), vertices, indices);
    paintRegion(batch, polyReg);
  }

  protected void paintRegion(PolygonSpriteBatch batch, PolygonRegion polygonRegion) {
    var oldColor = new Color(batch.getColor());
    batch.setColor(color);
    batch.draw(polygonRegion, 0, 0);
    batch.setColor(oldColor);
  }

  public FloatArray pathToFloatArray(PathIterator it) {
    tmpFloat.clear();
    segmentIndicies.clear();

    Point2D.Float lastMoveTo = null;

    var index = 0;
    for (; !it.isDone(); it.next()) {
      int type = it.currentSegment(floatsFromArea);

      switch (type) {
        case PathIterator.SEG_MOVETO:
          tmpFloat.add(floatsFromArea[0], -floatsFromArea[1]);
          lastMoveTo = new Point2D.Float(floatsFromArea[0], -floatsFromArea[1]);
          segmentIndicies.add(index);
          index += 1;
          break;
        case PathIterator.SEG_CLOSE:
          if (lastMoveTo != null) {
            tmpFloat.add(lastMoveTo.x, lastMoveTo.y);
            lastMoveTo = null;
          }
          break;
        case PathIterator.SEG_LINETO:
          if (tmpFloat.get(tmpFloat.size - 2) != floatsFromArea[0]
              || tmpFloat.get(tmpFloat.size - 1) != -floatsFromArea[1]) {
            tmpFloat.add(floatsFromArea[0], -floatsFromArea[1]);
            index += 1;
          }
          break;
        case PathIterator.SEG_QUADTO:
          tmpVector0.set(tmpFloat.get(tmpFloat.size - 2), tmpFloat.get(tmpFloat.size - 1));
          tmpVector1.set(floatsFromArea[0], -floatsFromArea[1]);
          tmpVector2.set(floatsFromArea[2], -floatsFromArea[3]);
          for (var i = 1; i <= POINTS_PER_BEZIER; i++) {
            Bezier.quadratic(
                tmpVectorOut, i / POINTS_PER_BEZIER, tmpVector0, tmpVector1, tmpVector2, tmpVector);
            tmpFloat.add(tmpVectorOut.x, tmpVectorOut.y);
            index += 1;
          }
          break;
        case PathIterator.SEG_CUBICTO:
          tmpVector0.set(tmpFloat.get(tmpFloat.size - 2), tmpFloat.get(tmpFloat.size - 1));
          tmpVector1.set(floatsFromArea[0], -floatsFromArea[1]);
          tmpVector2.set(floatsFromArea[2], -floatsFromArea[3]);
          tmpVector3.set(floatsFromArea[4], -floatsFromArea[5]);
          for (var i = 1; i <= POINTS_PER_BEZIER; i++) {
            Bezier.cubic(
                tmpVectorOut,
                i / POINTS_PER_BEZIER,
                tmpVector0,
                tmpVector1,
                tmpVector2,
                tmpVector3,
                tmpVector);
            tmpFloat.add(tmpVectorOut.x, tmpVectorOut.y);
            index += 1;
          }
          break;
        default:
          System.out.println("Type: " + type);
      }
    }

    return tmpFloat;
  }

  public TriangledPolygon drawPathWithJoin(FloatArray path, BasicStroke stroke) {
    pathMesher.clear();
    return pathMesher.stroke(stroke, path);
  }
}
