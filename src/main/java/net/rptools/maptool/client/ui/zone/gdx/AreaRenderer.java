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
import com.badlogic.gdx.utils.FloatArray;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ShortArray;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.GeometryUtil;
import net.rptools.lib.gdx.Earcut;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulationBuilder;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class AreaRenderer {
  public record TriangledPolygon(float[] vertices, short[] indices) {}

  private final ShapeDrawer drawer;
  private final Texture whitePixel;

  private final FloatArray tmpFloat = new FloatArray();

  private final Color color = Color.WHITE.cpy();

  public AreaRenderer(ShapeDrawer drawer, Texture whitePixel) {
    this.drawer = drawer;
    this.whitePixel = whitePixel;
  }

  public void setColor(Color value) {
    color.set(Objects.requireNonNullElse(value, Color.WHITE));
    texture = whitePixel;
  }

  private Texture texture = null;

  public void setTexture(Texture texture) {
    this.texture = texture;
  }

  public List<TriangledPolygon> triangulate(Collection<Polygon> jts) {
    if (jts.isEmpty()) {
      return List.of();
    }

    if (true) {
      ConformingDelaunayTriangulationBuilder b = new ConformingDelaunayTriangulationBuilder();
      var geometryFactory = new GeometryFactory();
      var geometry = geometryFactory.createMultiPolygon(jts.toArray(Polygon[]::new));
      b.setConstraints(geometry);
      b.setSites(geometry);

      var triangles = b.getTriangles(geometryFactory);

      // TODO The robust option would be to dedupe vertices.
      // TODO Use temporary buffers.
      var vertices = new FloatArray();
      var indicies = new ShortArray();
      for (var n = 0; n < triangles.getNumGeometries(); ++n) {
        Polygon tri = (Polygon) triangles.getGeometryN(n);
        Coordinate[] c = tri.getCoordinates(); // 0,1,2,0 (closed)

        // We want the first 3 only.
        for (int i = 0; i < 3; i++) {
          Coordinate coord = c[i];
          var idx = vertices.size / 2;
          vertices.add((float) coord.x);
          vertices.add((float) coord.y);

          indicies.add(idx);
        }
      }

      return List.of(new TriangledPolygon(vertices.toArray(), indicies.toArray()));
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
    var timer = CodeTimer.get();

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
        timer.start("AreaRenderer#fillArea()-convertToArea");
        // Areas have consistent orientations for its segments, while generate shapes do not.
        if (!(shape instanceof Area)) {
          shape = new Area(shape);
        }
        timer.stop("AreaRenderer#fillArea()-convertToArea");

        timer.start("AreaRenderer#fillArea()-convertToPolygons");
        // TODO Precision should depend on the current zoneScale' scale.
        var polygons = GeometryUtil.toJtsPolygons(shape, new PrecisionModel(1e1));
        timer.stop("AreaRenderer#fillArea()-convertToPolygons");

        timer.start("AreaRenderer#fillArea()-triangulate");
        var triangulatedPolygons = triangulate(polygons);
        timer.stop("AreaRenderer#fillArea()-triangulate");

        timer.start("AreaRenderer#fillArea()-paint");
        for (var poly : triangulatedPolygons) {
          var polyRegion =
              new PolygonRegion(new TextureRegion(texture), poly.vertices, poly.indices);
          paintRegion(batch, polyRegion);
        }
        timer.stop("AreaRenderer#fillArea()-paint");
      }
    }
  }

  public void drawArea(PolygonSpriteBatch batch, Shape shape, BasicStroke stroke) {
    if (shape == null) {
      return;
    }

    var timer = CodeTimer.get();
    timer.start("AreaRenderer-drawArea:stroke");
    var stroked = stroke.createStrokedShape(shape);
    timer.stop("AreaRenderer-drawArea:stroke");

    /*
     * The conversion to Area is not ideal, but the stroked shape may not represent shells and holes
     * in the expected orientation. Area, on the other hand, will make them consistently clockwise
     * or counterclockwise.
     */
    timer.start("AreaRenderer-drawArea:convertToArea");
    stroked = new Area(stroked);
    timer.stop("AreaRenderer-drawArea:convertToArea");

    timer.start("AreaRenderer-drawArea:convertToPolygons");
    var polygons = GeometryUtil.toJtsSimple(stroked);
    timer.stop("AreaRenderer-drawArea:convertToPolygons");

    timer.start("AreaRenderer-drawArea:triangulate");
    var triangulated = triangulate(polygons);
    timer.stop("AreaRenderer-drawArea:triangulate");

    timer.start("AreaRenderer-drawArea:fill");
    fill(batch, triangulated);
    timer.stop("AreaRenderer-drawArea:fill");
  }

  protected void paintRegion(PolygonSpriteBatch batch, PolygonRegion polygonRegion) {
    var oldColor = new Color(batch.getColor());
    batch.setColor(color);
    batch.draw(polygonRegion, 0, 0);
    batch.setColor(oldColor);
  }
}
