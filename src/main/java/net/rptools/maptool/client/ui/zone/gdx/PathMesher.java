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

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import java.awt.BasicStroke;

public class PathMesher {
  private static final float MAX_ANGLE_STEP_FOR_ROUND = MathUtils.PI / 20;

  private final FloatArray vertices = new FloatArray();
  private final ShortArray indices = new ShortArray();

  // A, B, and C are consecutive points along the path, with B being where joints need to be placed.
  // AB is the displacement vector from A to B, and BC the displacement vector from B to C.

  private final Vector2 A = new Vector2();
  private final Vector2 B = new Vector2();
  private final Vector2 C = new Vector2();
  private final Vector2 AB = new Vector2();
  private final Vector2 BC = new Vector2();

  // Each quad that we build has a trailing edge near A, and a leading edge near B. On the trailing
  // edge, E is to the left of A, D is to the right. On the leading edge, nextE is on the left of B,
  // nextD is on the right.

  private final Vector2 currentRight = new Vector2();
  private final Vector2 currentLeft = new Vector2();
  private final Vector2 nextRight = new Vector2();
  private final Vector2 nextLeft = new Vector2();

  // These are some auxiliary variables that have similar but not identical meanings in all cases.
  private final Vector2 pointToLeft = new Vector2();
  private final Vector2 futureOutside = new Vector2();

  // Generic vector that can be used in the join and cap methods for any purpose.
  private final Vector2 v1 = new Vector2();
  private final Vector2 v2 = new Vector2();
  private final Vector2 v3 = new Vector2();
  private final Vector2 v4 = new Vector2();

  public PathMesher() {}

  public void clear() {
    vertices.clear();
    indices.clear();
  }

  public AreaRenderer.TriangledPolygon stroke(BasicStroke stroke, FloatArray path) {
    var halfLineWidth = stroke.getLineWidth() / 2.f;

    if (path.size < 4) {
      // TODO Handle singular points specially.
      return new AreaRenderer.TriangledPolygon(vertices.toArray(), indices.toArray());
    }

    /*
     * When iterating over the path:
     * - A is the starting point of the current edge.
     * - B is the ending point of the current edge, and the starting point of the next edge.
     * - C is the ending point of the next edge.
     *
     * C is a future point, needed to calculate the upcoming corner geometry. But on any given loop
     * iteration, we won't directly include it in the mesh.
     *
     * D and E are associated in some way with A. At the start of the path, they form a butt cap,
     * straddling A on a perpendicular line segment of length `stroke.getLineWidth()`. After that,
     * the exact relationship with A varies by join type. Regardless, at the start of each loop
     * iteration, DE is the trailing edge of the new quad that will be created for the edge AB.
     */

    // Essentially A and B are the current points, C is a future point that we need to
    // figure out how to the upcoming angle.

    // Starting point: need to establish a butt cap to serve as the previous D and E for future
    // steps.
    A.set(path.get(0), path.get(1));
    B.set(path.get(2), path.get(3));

    {
      AB.set(B).sub(A);
      // Turn counterclockwise 90°.
      pointToLeft.set(AB).rotate90(1).setLength(halfLineWidth);
      // Set up the previous state for our first step.
      currentRight.set(A).sub(pointToLeft);
      currentLeft.set(A).add(pointToLeft);

      switch (stroke.getEndCap()) {
        case BasicStroke.CAP_BUTT -> {
          // A butt cap has no additional geometry at the end.
        }
        case BasicStroke.CAP_SQUARE -> {
          // A square cap can just extend the geometry backwards by halfLineWidth.
          v1.set(AB).setLength(halfLineWidth);
          currentRight.sub(v1);
          currentLeft.sub(v1);
        }
        case BasicStroke.CAP_ROUND -> {
          // A round cap needs to add a semicircle before the current quad.
          arcFan(A, A, currentLeft, currentRight, MAX_ANGLE_STEP_FOR_ROUND);
        }
      }
    }

    final var pointCount = path.size / 2;
    for (var indexOfC = 2; indexOfC < pointCount; ++indexOfC, A.set(B), B.set(C)) {
      C.set(path.get(2 * indexOfC), path.get(2 * indexOfC + 1));
      AB.set(B).sub(A);
      BC.set(C).sub(B);

      float deflection = AB.angleRad(BC);

      /*
       * It is up to the join methods to decide whether the push the quad that the main logic sets
       * up or whether to modify those vertices first (miters do this as they don't need
       * intermediate vertices before the join). They are also responsible for setting `D` and `E`
       * to make things ready for the next loop.
       */

      var turnsLeft = deflection < 0;

      float epsilon = 0.001f;
      float turnBackDeflection = MathUtils.PI - epsilon;
      boolean isTurnback = deflection < -turnBackDeflection || deflection > turnBackDeflection;

      boolean miterLimitExceeded;
      if (isTurnback) {
        miterLimitExceeded = true;
        // Arbitrarily decide that turnbacks count as turning left.
        turnsLeft = true;

        pointToLeft.set(AB).rotateRad(0.5f * (MathUtils.PI - deflection)).setLength(AB.len());
      } else {
        var mitreHalfLength = halfLineWidth / MathUtils.cos(deflection / 2);
        pointToLeft
            .set(AB)
            .rotateRad(0.5f * (MathUtils.PI - deflection))
            .setLength(mitreHalfLength);
        miterLimitExceeded = mitreHalfLength > stroke.getMiterLimit() * halfLineWidth;
      }

      if (turnsLeft) {
        var nextInside = nextLeft;
        var nextOutside = nextRight;
        nextInside.set(B).add(pointToLeft);
        // Turn clockwise 90°.
        nextOutside.set(AB).rotate90(-1).setLength(halfLineWidth).add(B);

        // Turn BC counterclockwise 90° at B
        futureOutside.set(BC).rotate90(-1).setLength(halfLineWidth).add(B);
      } else {
        var nextInside = nextRight;
        var nextOutside = nextLeft;
        nextInside.set(B).sub(pointToLeft);
        // Turn AB counterclockwise 90° at B.
        nextOutside.set(AB).rotate90(1).setLength(halfLineWidth).add(B);

        // Turn BC counterclockwise 90° at B
        futureOutside.set(BC).rotate90(1).setLength(halfLineWidth).add(B);
      }

      switch (stroke.getLineJoin()) {
        case BasicStroke.JOIN_ROUND -> joinRound(futureOutside, turnsLeft);
        case BasicStroke.JOIN_BEVEL -> joinBevel(futureOutside, turnsLeft);
        case BasicStroke.JOIN_MITER -> {
          if (miterLimitExceeded) {
            joinBevel(futureOutside, turnsLeft);
          } else {
            joinMiter(turnsLeft);
          }
        }
      }
    }

    // Now to handle the last segment. In this case there is nothing to act as C. Instead, just like
    // for the first segment, we set up a butt cap, but this time across B.
    {
      AB.set(B).sub(A);
      // Turn counterclockwise 90°.
      pointToLeft.set(AB).rotate90(1).setLength(halfLineWidth);

      nextRight.set(B).sub(pointToLeft);
      nextLeft.set(B).add(pointToLeft);

      switch (stroke.getEndCap()) {
        case BasicStroke.CAP_BUTT -> {
          // A butt cap has no additional geometry at the end.
          pushQuadAndAdvance();
        }
        case BasicStroke.CAP_SQUARE -> {
          // A square cap can just extend the geometry forward by halfLineWidth.
          v1.set(AB).setLength(halfLineWidth);
          nextRight.add(v1);
          nextLeft.add(v1);
          pushQuadAndAdvance();
        }
        case BasicStroke.CAP_ROUND -> {
          // A round cap needs to add a semicircle after the current quad.
          pushQuadAndAdvance();

          arcFan(B, B, currentRight, currentLeft, MAX_ANGLE_STEP_FOR_ROUND);
        }
      }
    }

    return new AreaRenderer.TriangledPolygon(vertices.toArray(), indices.toArray());
  }

  /**
   * Adds a quad to the mesh.
   *
   * <p>The vertices should be provided in counterclockwise order.
   *
   * @param vert1 The first vertex of the quad.
   * @param vert2 The second vertex of the quad.
   * @param vert3 The third vertex of the quad.
   * @param vert4 The fourth vertex of the quad.
   */
  private void pushQuad(Vector2 vert1, Vector2 vert2, Vector2 vert3, Vector2 vert4) {
    var index = vertices.size / 2;
    vertices.add(vert1.x);
    vertices.add(vert1.y);
    vertices.add(vert2.x);
    vertices.add(vert2.y);
    vertices.add(vert3.x);
    vertices.add(vert3.y);
    vertices.add(vert4.x);
    vertices.add(vert4.y);
    indices.add(index);
    indices.add(index + 1);
    indices.add(index + 2);
    indices.add(index);
    indices.add(index + 2);
    indices.add(index + 3);
  }

  /**
   * Pushes {@link #currentLeft}, {@link #currentRight}, {@link #nextRight} and {@link #nextLeft} as
   * a quad, then advances {@link #currentLeft} and {@link #currentRight} to {@link #nextLeft} and
   * {@link #nextRight}, respectively.
   */
  private void pushQuadAndAdvance() {
    pushQuad(currentLeft, currentRight, nextRight, nextLeft);
    currentRight.set(nextRight);
    currentLeft.set(nextLeft);
  }

  /**
   * Adds a triange to the mesh.
   *
   * <p>The vertices should be provided in counterclockwise order.
   *
   * @param vert1 The first vertex of the triangle.
   * @param vert2 The second vertex of the triangle.
   * @param vert3 The third vertex of the triangle.
   */
  private void pushTri(Vector2 vert1, Vector2 vert2, Vector2 vert3) {
    var index = vertices.size / 2;
    vertices.add(vert1.x);
    vertices.add(vert1.y);
    vertices.add(vert2.x);
    vertices.add(vert2.y);
    vertices.add(vert3.x);
    vertices.add(vert3.y);
    indices.add(index);
    indices.add(index + 1);
    indices.add(index + 2);
  }

  /**
   * Write a mitered join.
   *
   * <p>Shifts the outside point ({@link #nextLeft} or {@link #nextRight} depending on {@code
   * turnsLeft}) forward to sit at the apex of the rhombus that is the intersection of the AB stroke
   * and the BC stroke. This apex is also the mitre point.
   *
   * <p>When this method completes, {@link #currentLeft} and {@link #currentRight} will be advanced
   * to {@link #nextLeft} and {@link #nextRight}, respectively.
   *
   * @param turnsLeft {@code true} if the angle ∠ABC is a left turn.
   */
  private void joinMiter(boolean turnsLeft) {
    var nextInside = turnsLeft ? nextLeft : nextRight;
    var nextOutside = turnsLeft ? nextRight : nextLeft;
    nextOutside.set(B).sub(nextInside).add(B);

    pushQuadAndAdvance();
  }

  /**
   * Write a beveled join where E is the inside.
   *
   * <p>First, it finishes the current quad where it is. Then it connects the current inside and
   * outside ({@link #currentLeft} or {@link #currentRight} depending on {@code turnsLeft}) to the
   * point that will become the next outside point.
   *
   * <p>When this method completes, {@link #currentLeft} and {@link #currentRight} will be advanced
   * to the leading edge of the bevel.
   *
   * @param futureOutside The vertex that will be the trailing outside vertex in the next segment.
   * @param turnsLeft {@code true} if the angle ∠ABC is a left turn.
   */
  private void joinBevel(Vector2 futureOutside, boolean turnsLeft) {
    pushQuadAndAdvance();
    pushTri(currentLeft, currentRight, futureOutside);

    if (turnsLeft) {
      currentRight.set(futureOutside);
      currentLeft.set(nextLeft);
    } else {
      currentRight.set(nextRight);
      currentLeft.set(futureOutside);
    }
  }

  /**
   * Write a round join.
   *
   * <p>When this method completes, {@link #currentLeft} and {@link #currentRight} will be advanced
   * to the leading edge of the arc.
   *
   * @param futureOutside The vertex that will be the trailing outside vertex in the next segment.
   * @param turnsLeft {@code true} if the angle ∠ABC is a left turn.
   */
  private void joinRound(Vector2 futureOutside, boolean turnsLeft) {
    pushQuadAndAdvance();

    // Now we need to push a sequence of triangles to build a circular corner centered at B.
    var outside = turnsLeft ? currentRight : currentLeft;
    var inside = turnsLeft ? currentLeft : currentRight;

    var deltaAngle = MAX_ANGLE_STEP_FOR_ROUND;
    if (!turnsLeft) {
      deltaAngle = -deltaAngle;
    }
    arcFan(inside, B, outside, futureOutside, deltaAngle);

    outside.set(futureOutside);
  }

  /**
   * Fills a circular arc.
   *
   * <p>Note: does not update state, aside from pushing triangles to the mesh and using the
   * temporary vector fields.
   *
   * @param inside The inside point of the arc, to which each generated outside point on the arc is
   *     connected.
   * @param center The center of the arc. May be different {@code inside} in case filling a wedge
   *     with a circular outside edge.
   * @param outsideStart The first vertex along the arc.
   * @param outsideEnd The final vertex along the arc.
   * @param deltaAngle How large of an angular step to take, in radians. Needed to disambiguate
   *     clockwise and counterclockwise, particular when the angle made with the center is 180°
   */
  private void arcFan(
      Vector2 inside, Vector2 center, Vector2 outsideStart, Vector2 outsideEnd, float deltaAngle) {
    var outside = v1.set(outsideStart);

    var CenterToOutside = v2.set(outside).sub(center);
    var CenterToOutsideEnd = v3.set(outsideEnd).sub(center);

    var angle = CenterToOutsideEnd.angleRad(CenterToOutside);
    var steps = (int) Math.ceil(Math.abs(angle / deltaAngle));

    var nextOutside = v4;
    for (var i = 0; i < steps - 1; ++i) {
      CenterToOutside.rotateRad(deltaAngle);
      nextOutside.set(center).add(CenterToOutside);

      pushTri(inside, outside, nextOutside);

      outside.set(nextOutside);
    }

    // For the final step, we make sure to join up to outsideEnd exactly.
    pushTri(inside, outside, outsideEnd);
  }
}
