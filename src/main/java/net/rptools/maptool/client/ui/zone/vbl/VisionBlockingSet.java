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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.CodeTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineSegment;

public class VisionBlockingSet {
  private static final Logger log = LogManager.getLogger(VisionBlockingSet.class);

  private int mostOpenWalls = 0;
  private int wallChangeCount = 0;
  private int occludedWallCount = 0;
  private int endpointCount = 0;
  private int duplicateEndpointCount = 0;

  private final Coordinate origin;
  private final LineSegment initialRay;
  private final Envelope envelope;
  private final List<VisibilitySweepEndpoint> endpoints;

  // Note: we are essentially this collection as a priority queue, so we can always operate on the
  // closest wall. However, TreeSet is faster than PriorityQueue in this case, likely since the
  // size of the collection tends to remain quite small.
  // Note: it's not possible to totally order all walls by distance to `origin`. But it is
  // possible to do it for the set of open walls at any point in time, which is why we can use
  // this structure. So it's important to remove ended walls before adding opened walls, otherwise
  // we might momentarily violate that basic requirement of the comparison.
  // Note: as a rule, the number of open walls is small, usually well under 50.
  private final TreeSet<LineSegment> openWalls;

  public VisionBlockingSet() {
    this.origin = new Coordinate(0, 0);
    this.initialRay = new LineSegment(origin, new Coordinate(origin.x - 1, origin.y));
    this.envelope = new Envelope();
    this.endpoints = new ArrayList<>();
    this.openWalls = new TreeSet<>(this::compareOpenWalls);
  }

  public void init(Coordinate origin) {
    this.mostOpenWalls = 0;
    this.wallChangeCount = 0;
    this.occludedWallCount = 0;
    this.endpointCount = 0;
    this.duplicateEndpointCount = 0;

    this.origin.setCoordinate(origin);
    this.initialRay.p0 = this.origin;
    this.initialRay.p1 = new Coordinate(this.origin.x - 1, this.origin.y);
    this.envelope.init();
    this.endpoints.clear();
    this.openWalls.clear();
  }

  /**
   * Adds a string of vision blocking line segments into the problem space.
   *
   * <p>All line segments in the string must be oriented counterclockwise around the origin.
   *
   * <p>Internally, endpoints will be guaranteed to be unique and in a consistent orientation
   * (counterclockwise around the origin, starting with the negative x-axis). Each segment in the
   * string will be opened and closed by its respective endpoints.
   *
   * @param string The vision blocking segments to add
   */
  public void add(List<Coordinate> string) {
    if (string.size() < 2) {
      return;
    }

    // Always plainly add the first point.
    VisibilitySweepEndpoint previous = new VisibilitySweepEndpoint(string.get(0));
    this.envelope.expandToInclude(previous.getPoint());
    endpoints.add(previous);

    for (int i = 1; i < string.size(); ++i) {
      var endpoint = new VisibilitySweepEndpoint(string.get(i));
      this.envelope.expandToInclude(endpoint.getPoint());
      endpoints.add(endpoint);

      previous.startsWall(endpoint);
      endpoint.endsWall(previous);

      final var isOpen =
          Orientation.CLOCKWISE != initialRay.orientationIndex(endpoint.getPoint())
              && Orientation.CLOCKWISE == initialRay.orientationIndex(previous.getPoint());
      if (isOpen) {
        openWalls.add(new LineSegment(previous.getPoint(), endpoint.getPoint()));
      }

      previous = endpoint;
    }
  }

  // Don't actually call this, it's for testing purposes.
  private void verifyEndpoints() {
    for (var endpoint : endpoints) {
      for (var otherEndpoint : endpoint.getStartsWalls()) {
        assert otherEndpoint.getEndsWalls().contains(endpoint);
      }
      for (var otherEndpoint : endpoint.getEndsWalls()) {
        assert otherEndpoint.getStartsWalls().contains(endpoint);
      }
    }
  }

  private void deduplicateEndpoints() {
    // We might have duplicates we we don't want.
    VisibilitySweepEndpoint previous = null;
    for (var i = 0; i < endpoints.size(); ++i) {
      final var endpoint = endpoints.get(i);
      if (previous == null) {
        previous = endpoint;
        continue;
      }

      if (previous.getPoint().equals(endpoint.getPoint())) {
        duplicateEndpointCount += 1;

        // Merge with the existing one. Don't keep the duplicate.
        // Need to replace all references to endpoint with references to previous.
        for (final var otherEndpoint : endpoint.getStartsWalls()) {
          previous.getStartsWalls().add(otherEndpoint);
          otherEndpoint.getEndsWalls().remove(endpoint);
          otherEndpoint.getEndsWalls().add(previous);
        }
        for (final var otherEndpoint : endpoint.getEndsWalls()) {
          previous.getEndsWalls().add(otherEndpoint);
          otherEndpoint.getStartsWalls().remove(endpoint);
          otherEndpoint.getStartsWalls().add(previous);
        }

        // I could also .remove(), but that's expensive for long lists. We're just as well to skip
        // this while iterating later.
        endpoints.set(i, null);
        continue;
      }

      // Haven't seen this endpoint yet. Add it to the map.
      previous = endpoint;
      endpointCount += 1;
    }

    // Don't keep trailing nulls, those will mess things up.
    while (endpoints.getLast() == null) {
      endpoints.removeLast();
    }
  }

  private List<Coordinate> sweep() {
    List<Coordinate> visionPoints = new ArrayList<>();

    var previousNearestWall = openWalls.getFirst();
    while (!endpoints.isEmpty()) {
      final var endpoint = endpoints.removeLast();
      if (endpoint == null) {
        // This was a deduplicated endpoint.
        continue;
      }

      var currentNearestWall = updateOpenWalls(endpoint, previousNearestWall);
      // If the current nearest wall hasn't changed, the endpoint is occluded and does not
      // contribute to the result.
      if (currentNearestWall != previousNearestWall) {
        consumeEndpoint(endpoint, previousNearestWall, currentNearestWall, visionPoints);
        wallChangeCount += 1;
        mostOpenWalls = Math.max(mostOpenWalls, openWalls.size());

        previousNearestWall = currentNearestWall;
      }
    }

    return visionPoints;
  }

  /**
   * Solve the visibility polygon problem.
   *
   * <p>This follows Asano's algorithm as described in section 3.2 of "Efficient Computation of
   * Visibility Polygons". The endpoints are already sorted as required, as is the initial set of
   * open walls. As the algorithm progresses, open walls are maintained as an ordered set to enable
   * efficient polling of the closest wall at any given point in time.
   *
   * @param visionBounds The bounds of the vision, in order to avoid the need for infinite polygonal
   *     areas.
   * @return A visibility polygon, represented as a ring of coordinates.
   * @see <a href="https://arxiv.org/abs/1403.3905">Efficient Computation of Visibility Polygons,
   *     arXiv:1403.3905</a>
   */
  public @Nullable Coordinate[] solve(Envelope visionBounds) {
    if (this.endpoints.isEmpty()) {
      // No topology, apparently.
      return null;
    }

    var timer = CodeTimer.get();

    timer.start("add bounds");
    envelope.expandToInclude(visionBounds);
    // Exact expansion distance doesn't matter, we just don't want the boundary walls to overlap
    // endpoints from real walls.
    envelope.expandBy(1.0);
    final var envelopeCoordinates =
        new Coordinate[] {
          new Coordinate(envelope.getMinX(), envelope.getMinY()),
          new Coordinate(envelope.getMaxX(), envelope.getMinY()),
          new Coordinate(envelope.getMaxX(), envelope.getMaxY()),
          new Coordinate(envelope.getMinX(), envelope.getMaxY()),
          new Coordinate(envelope.getMinX(), envelope.getMinY())
        };
    this.add(Arrays.asList(envelopeCoordinates));
    timer.stop("add bounds");

    timer.start("initialize");
    // verifyEndpoints();
    timer.start("sort");
    endpoints.sort((l, r) -> -comparePolar(l.getPoint(), r.getPoint()));
    timer.stop("sort");
    timer.start("deduplicate");
    deduplicateEndpoints();
    timer.stop("deduplicate");
    timer.stop("initialize");

    // Now for the real sweep. Make sure to process the first point once more at the end to ensure
    // the sweep covers the full 360 degrees.
    timer.start("sweep");
    final var visionPoints = sweep();
    timer.stop("sweep");

    timer.start("sanity check");
    try {
      if (visionPoints.size() < 3) {
        // This shouldn't happen, but just in case.
        log.warn("Sweep produced too few points: {}", visionPoints);
        return null;
      }
    } finally {
      timer.stop("sanity check");
    }
    timer.start("close polygon");
    visionPoints.add(visionPoints.get(0)); // Ensure a closed loop.
    timer.stop("close polygon");

    System.out.printf(
        "%d endpoints; %d duplicated; at most %d open walls; %d wall changes; %d occlusions%n",
        endpointCount, duplicateEndpointCount, mostOpenWalls, wallChangeCount, occludedWallCount);

    timer.start("build result");
    try {
      return visionPoints.toArray(Coordinate[]::new);
    } finally {
      timer.stop("build result");
    }
  }

  private LineSegment updateOpenWalls(
      VisibilitySweepEndpoint endpoint, LineSegment previousNearestWall) {
    assert !openWalls.isEmpty();

    for (var otherEndpoint : endpoint.getEndsWalls()) {
      var removed =
          openWalls.remove(new LineSegment(otherEndpoint.getPoint(), endpoint.getPoint()));
      assert removed : "The endpoint's ended walls should be open just prior to this point";
    }

    if (endpoint.getStartsWalls().isEmpty()) {
      // There are no about-to-be-opened walls that need culling, so just avoid the whole deal.
      assert !openWalls.isEmpty();
      return openWalls.getFirst();
    }

    // The equals check here is redundant, but quick.
    if (!endpoint.getPoint().equals(previousNearestWall.p1)
        && previousNearestWall.orientationIndex(endpoint.getPoint()) == Orientation.CLOCKWISE) {
      // Since previousNearestWall is still open, this endpoint is behind it. Only add segments for
      // those ending points that are not also behind the wall (otherwise the new wall would be
      // completely occluded anyways).
      // Also, don't bother querying the collection again for a new nearest wall since it won't have
      // changed.
      for (var otherEndpoint : endpoint.getStartsWalls()) {
        // This orientation check relies on the fact that walls do not intersect. So all we have to
        // do is determine that the endpoint does not come after p1 to decide that it is also
        // occluded.
        var occluded =
            Orientation.index(origin, otherEndpoint.getPoint(), previousNearestWall.p1)
                == Orientation.COUNTERCLOCKWISE;
        if (occluded) {
          // Occluded, we can remove the edge entirely. No need to remove it from this endpoint, but
          // remove it from the upcoming one.
          var removed = otherEndpoint.getEndsWalls().remove(endpoint);
          assert removed;
          occludedWallCount += 1;
        } else {
          openWalls.add(new LineSegment(endpoint.getPoint(), otherEndpoint.getPoint()));
        }
      }
    } else {
      for (var otherEndpoint : endpoint.getStartsWalls()) {
        openWalls.add(new LineSegment(endpoint.getPoint(), otherEndpoint.getPoint()));
      }
    }

    // Find a new nearest wall.
    assert !openWalls.isEmpty();
    return openWalls.getFirst();
  }

  private void consumeEndpoint(
      VisibilitySweepEndpoint endpoint,
      LineSegment previousNearestWall,
      LineSegment currentNearestWall,
      List<Coordinate> visionPoints) {
    // Implies we have changed which wall we are at. Need to figure out projections.
    final var ray = new LineSegment(origin, endpoint.getPoint());
    if (!ray.p1.equals(previousNearestWall.p1)) {
      // The previous nearest wall is still open. I.e., we didn't fall of its end but
      // encountered a new closer wall. So we project the current point to the previous
      // nearest wall, then step to the current point.
      assert ray.p1.equals(currentNearestWall.p0)
          : "Uh-oh, this case should only happen if we encountered a newly opened closer wall";
      visionPoints.add(projectOntoOpenWall(ray, previousNearestWall));
      visionPoints.add(currentNearestWall.p0);
    } else {
      // The previous nearest wall is now closed. I.e., we "fell off" it and therefore have
      // encountered a different wall. So we step from the current point (which is on the
      // previous wall) to the projection on the new wall.
      assert ray.p1.equals(previousNearestWall.p1)
          : "Uh-oh, this case should only happen if we left a closed wall for something farther away";

      visionPoints.add(previousNearestWall.p1);
      // Special case: if the two walls are adjacent, they share the current point. We don't
      // need to add the point twice, so just skip in that case.
      if (!previousNearestWall.p1.equals(currentNearestWall.p0)) {
        visionPoints.add(projectOntoOpenWall(ray, currentNearestWall));
      }
    }
  }

  /**
   * Projects an event line ray onto an open wall.
   *
   * <p>Since the wall is open for the event line, the intersection will succeed.
   *
   * @param ray A ray representing the event line.
   * @param wall A wall that is open according to {@code ray}.
   * @return The point at which {@code ray} would intersect with {@code wall} if extended
   *     indefinitely.
   */
  private static @Nonnull Coordinate projectOntoOpenWall(LineSegment ray, LineSegment wall) {
    // TODO This assertion is not quite right: it's okay to project onto an about-to-be-closed wall.
    //  assert isWallOpen(ray, wall) : String.format("Wall %s is not open for ray %s", wall, ray);
    var intersection = ray.lineIntersection(wall);
    assert intersection != null
        : String.format(
            "Unable to project ray %s onto wall %s despite the wall being open", ray, wall);
    return intersection;
  }

  /**
   * Compares to points for there polar ordering around an origin.
   *
   * <p>The coordinates are ordered by polar angle ranging in the interval [-π, π]. If the angle is
   * equal, they will be ordered so that the point closer to the origin comes first.
   *
   * <p>The implementation does not actually compute angles, but is instead based on the clockwise /
   * counterclockwise orientation of the points around the origin.
   *
   * @param a The first coordinate to compare.
   * @param b The second coordinate to compare.
   * @return The comparison result of {@code a} and {@code b}.
   */
  private int comparePolar(Coordinate a, Coordinate b) {
    // If they are in different half planes, it's easy. This defines the cut point as on the x-axis.
    if (a.y <= origin.y && b.y > origin.y) {
      return -1;
    }
    if (a.y > origin.y && b.y <= origin.y) {
      return 1;
    }

    // a and b are in the same half-plane, i.e., they definitely don't straddle the x-axis. Now
    // orientation is sufficient to compare, i.e., "increase" means move counterclockwise.
    final var orientation = Orientation.index(origin, a, b);
    if (orientation == Orientation.COUNTERCLOCKWISE) {
      return -1;
    }
    if (orientation == Orientation.CLOCKWISE) {
      return 1;
    }

    // Points are collinear with the origin. As a fallback, sort by distance. It's not important
    // which way, we just need to be consistent.
    // Surprise! JTS doesn't have a squared distance method, and I don't want to spend the sqrt.
    final var dxa = a.x - origin.x;
    final var dya = a.y - origin.y;
    final var dxb = b.x - origin.x;
    final var dyb = b.y - origin.y;
    return Double.compare(dxa * dxa + dya * dya, dxb * dxb + dyb * dyb);
  }

  /**
   * Compares two open walls to determine which one is closer to the origin.
   *
   * <p>By construction, we do not have any non-noded intersections, and as a result open walls can
   * be totally ordered by closeness to the origin. Note though that walls cannot in general be
   * totally ordered this way, the property only holds for open walls, with the ordering
   * corresponding to the ordering of intersections with the event line.
   *
   * @param s0 The first wall to compare.
   * @param s1 The second wall to compare.
   * @return {@code -1} if {@code s0} is closer to {@code origin} than {@code s1}; {@code 1} if
   *     {@code s0} is further from {@code origin} than {@code s1}; {@code 0} if {@code s0} and
   *     {@code s1} are the same wall.
   */
  private int compareOpenWalls(LineSegment s0, LineSegment s1) {
    assert s0.orientationIndex(origin) == Orientation.COUNTERCLOCKWISE
        : String.format("Wall %s is not oriented correctly", s0);
    assert s1.orientationIndex(origin) == Orientation.COUNTERCLOCKWISE
        : String.format("Wall %s is not oriented correctly", s1);

    if (s0 == s1) {
      return 0;
    }

    final var pointwise = s0.compareTo(s1);
    if (pointwise == 0) {
      return 0;
    }

    // For orientation checks: counterclockwise indicates the tested point is nearer than the line
    // segment, since by construction all segments are oriented counterclockwise relative to the
    // vision origin.

    // Start by trying to prove that s0 is definitely closer or further than s1.
    var p0Orientation = Orientation.index(s0.p0, s1.p0, s1.p1);
    var p1Orientation = Orientation.index(s0.p1, s1.p0, s1.p1);

    assert p0Orientation != Orientation.COLLINEAR || p1Orientation != Orientation.COLLINEAR
        : String.format(
            "It should not be possible for two open walls %s and %s to be collinear with one another",
            s0, s1);

    if (p0Orientation == Orientation.COLLINEAR) {
      // p1 is authoritative.
      return p1Orientation == Orientation.COUNTERCLOCKWISE ? -1 : 1;
    }
    if (p1Orientation == Orientation.COLLINEAR) {
      // p0 is authoritative.
      return p0Orientation == Orientation.COUNTERCLOCKWISE ? -1 : 1;
    }
    if (p0Orientation == p1Orientation) {
      // There is agreement, so we know our answer.
      return p0Orientation == Orientation.COUNTERCLOCKWISE ? -1 : 1;
    }

    // Indefinite result (one point looks closer, one look further). So test the other segment's
    // points. Actually only need to test one point to be sure.
    p0Orientation = Orientation.index(s1.p0, s0.p0, s0.p1);
    // Colinearity of one point implies colinearity of the other, otherwise we would have a definite
    // result above. And this case can't happen in the context of the sweep algorithm.
    assert p0Orientation != Orientation.COLLINEAR
        : String.format(
            "It should not be possible to get a collinear result in the fallback check for %s and %s",
            s0, s1);

    // If clockwise, this point on s1 is nearer than s0, so s1 as a whole is in front of s0.
    return p0Orientation == Orientation.COUNTERCLOCKWISE ? 1 : -1;
  }
}
