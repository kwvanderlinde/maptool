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
package net.rptools.maptool.client.ui.zone.vbl;

import net.rptools.lib.GeometryUtil;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.PointLocation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/** Represents the boundary of a piece of topology. */
public class AreaMeta {
  private final Coordinate[] vertices;
  private final PreparedGeometry boundary;
  private final Envelope boundingBox;
  private final boolean isHole;

  public AreaMeta(LinearRing ring) {
    vertices = ring.getCoordinates();
    assert vertices.length >= 4; // Yes, 4, because a ring duplicates its first element as its last.

    boundingBox = CoordinateArrays.envelope(vertices);

    boundary =
        PreparedGeometryFactory.prepare(GeometryUtil.getGeometryFactory().createPolygon(vertices));

    isHole = Orientation.isCCW(vertices);
  }

  public Envelope getBoundingBox() {
    return boundingBox;
  }

  public boolean contains(AreaMeta other) {
    if (!boundingBox.contains(other.boundingBox)) {
      return false;
    }

    return boundary.contains(other.boundary.getGeometry());
  }

  public boolean contains(Coordinate point) {
    if (!boundingBox.contains(point)) {
      return false;
    }

    // Holes are open (do not include their boundary). This makes masks like Wall VBL function
    // correctly by ensuring any intersection with the mask counts as being inside.
    // On the other hand it makes Pit VBL still not behave correctly: vision can extend both into
    // and out of the region, though only through the one line.
    final var location = PointLocation.locateInRing(point, vertices);
    if (isHole) {
      return location == Location.INTERIOR;
    } else {
      return location != Location.EXTERIOR;
    }
  }

  /**
   * Returns all line segments in the boundary that face the requested direction.
   *
   * <p>For each line segment, the exterior region will be on one side of the segment while the
   * interior region will be on the other side. One of these regions will be an island and one will
   * be an ocean depending on {@link #isHole()}. The {@code facing} parameter uses this fact to
   * control whether a segment should be included in the result, based on whether the origin is on
   * the island-side of the line segment or on its ocean-side.
   *
   * <p>If {@code origin} is colinear with a line segment, that segment will never be returned.
   *
   * @param origin The vision origin, which is the point by which line segment orientation is
   *     measured.
   * @param facing Whether the island-side or the ocean-side of the returned segments must face
   *     {@code origin}.
   * @return All line segments with a facing that matches {@code facing} based on the position of
   *     {@code origin}. The line segments are joined into continguous line strings where possible.
   */
  public VisionBlockingSet getFacingSegments(
      Coordinate origin, Facing facing, Envelope visionBounds) {
    final var requiredOrientation =
        facing == Facing.ISLAND_SIDE_FACES_ORIGIN
            ? Orientation.CLOCKWISE
            : Orientation.COUNTERCLOCKWISE;
    final var result = new VisionBlockingSet();

    for (int i = 1; i < vertices.length; ++i) {
      final var faceLineSegment = new LineSegment(vertices[i - 1], vertices[i]);
      final var orientation = faceLineSegment.orientationIndex(origin);

      final var shouldIncludeFace =
          (orientation == requiredOrientation)
              // Don't need to be especially precise with the vision check.
              && visionBounds.intersects(faceLineSegment.p0, faceLineSegment.p1);
      if (!shouldIncludeFace) {
        continue;
      }

      // Regardless of the orientation required for inclusion, the vision algorithm wants the
      // segment oriented counterclockwise.
      if (orientation != Orientation.COUNTERCLOCKWISE) {
        faceLineSegment.reverse();
      }
      result.add(faceLineSegment);
    }

    return result;
  }

  public boolean isHole() {
    return isHole;
  }
}
