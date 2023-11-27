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
package net.rptools.maptool.client.ui.zone;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.client.ui.zone.vbl.AreaTree;
import net.rptools.maptool.client.ui.zone.vbl.VisibilitySweepEndpoint;
import net.rptools.maptool.client.ui.zone.vbl.VisionBlockingAccumulator;
import net.rptools.maptool.client.ui.zone.vbl.VisionBlockingSet;
import net.rptools.maptool.model.AbstractPoint;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.ExposedAreaMetaData;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Grid;
import net.rptools.maptool.model.GridCapabilities;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.player.Player.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;

public class FogUtil {
  private static final Logger log = LogManager.getLogger(FogUtil.class);
  private static final GeometryFactory geometryFactory = GeometryUtil.getGeometryFactory();

  /**
   * Return the visible area for an origin, a lightSourceArea and a VBL.
   *
   * @param origin the vision origin.
   * @param vision the lightSourceArea.
   * @param topology the VBL topology.
   * @return the visible area.
   */
  // TODO Accept a LinearRing to represent the vision bounds to use. That will allow us to construct
  //  a precise visibility polygon for the case of calculating vision specifically. In that case, do
  //  not intersect with any provided vision Area (actually that should just not be provided), but
  //  still return the created Area.
  //  Note that this does not work for light sources (normal, personal, or aura) as they can have
  //  gaps (e.g., light exterior with a gooey darkness interior). Although, in that case, a
  //  LinearRing may still provide certain efficiencies and may also enable intersecting all vision
  //  sweeps in JTS-land. If we do provide a ring for those cases as well, then we should push the
  //  final Area intersection to the caller, keeping this algorithm strictly about visibility
  //  _polygons_.
  public static @Nonnull Area calculateVisibility(
      Point origin,
      Area vision,
      AreaTree topology,
      AreaTree hillVbl,
      AreaTree pitVbl,
      AreaTree coverVbl) {
    var timer = CodeTimer.get();
    timer.start("FogUtil::calculateVisibility");
    try {
    timer.start("get vision bounds");
    Envelope visionBounds;
    {
      var awtBounds = vision.getBounds2D();
      visionBounds =
          new Envelope(
              new Coordinate(awtBounds.getMinX(), awtBounds.getMinY()),
              new Coordinate(awtBounds.getMaxX(), awtBounds.getMaxY()));
    }
    timer.stop("get vision bounds");

    /*
     * Find the visible area for each topology type independently.
     *
     * In principle, we could also combine all the vision blocking segments for all topology types
     * and run the sweep algorithm once. But this is subject to some pathological cases that JTS
     * cannot handle. These cases do not exist within a single type of topology, but can arise when
     * we combine them.
     */
    List<Coordinate[]> visibilityPolygons = new ArrayList<>();
    final List<Function<VisionBlockingAccumulator, Boolean>> topologyConsumers = new ArrayList<>();
    topologyConsumers.add(acc -> acc.addWallBlocking(topology));
    topologyConsumers.add(acc -> acc.addHillBlocking(hillVbl));
    topologyConsumers.add(acc -> acc.addPitBlocking(pitVbl));
    topologyConsumers.add(acc -> acc.addCoverBlocking(coverVbl));
    for (final var consumer : topologyConsumers) {
      timer.start("accumulate blocking walls");
      final var accumulator = new VisionBlockingAccumulator(origin, visionBounds);
      final var isVisionCompletelyBlocked = consumer.apply(accumulator);
      timer.stop("accumulate blocking walls");
      if (!isVisionCompletelyBlocked) {
        // Vision has been completely blocked by this topology. Short circuit.
        return new Area();
      }

      timer.start("calculate visible area");
      final var visibleArea =
          calculateVisibleArea(
              new Coordinate(origin.getX(), origin.getY()),
              accumulator.getVisionBlockingSegments(),
              visionBounds);
      timer.stop("calculate visible area");
      timer.start("add visibility polygon");
      if (visibleArea != null) {
        visibilityPolygons.add(visibleArea);
      }
      timer.stop("add visibility polygon");
    }

    if (visibilityPolygons.isEmpty()) {
      return vision;
    }

    // We have to intersect all the results in order to find the true remaining visible area.
    timer.start("clone existing vision");
    vision = new Area(vision);
    timer.stop("clone existing vision");
    timer.start("combine visibility polygons with vision");
    // We intersect in AWT space because JTS can be really finicky about intersection precision.
    var shapeWriter = new ShapeWriter();
    for (var visibilityPolygon : visibilityPolygons) {
      // Even though linear ring is just the boundary, the Area constructor uses the entire
      // enclosed region.
      var area = new Area(shapeWriter.toShape(geometryFactory.createLinearRing(visibilityPolygon)));
      vision.intersect(area);
    }
    timer.stop("combine visibility polygons with vision");


    // For simplicity, this catches some of the edge cases
    return vision;
    }
    finally {
      timer.stop("FogUtil::calculateVisibility");
    }
  }

  private static int compareWalls(Coordinate origin, LineSegment s0, LineSegment s1) {
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

    // Negative result => s0 is closer than s1.

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

  /**
   * Builds a list of endpoints for the sweep algorithm to consume.
   *
   * <p>The endpoints will be unique (i.e., no coordinate is represented more than once) and in a
   * consistent orientation (i.e., counterclockwise around the origin). In addition, all endpoints
   * will have their starting and ending walls filled according to which walls are incident to the
   * corresponding point.
   *
   * @param origin The center of vision, by which orientation can be determined.
   * @param visionBlockingSegments The "walls" that are able to block vision. All points in these
   *     walls will be present in the returned list.
   * @return A list of all endpoints in counterclockwise order.
   */
  private static List<VisibilitySweepEndpoint> buildWallNetwork(
      Coordinate origin, Collection<LineSegment> visionBlockingSegments) {
    final Map<Coordinate, VisibilitySweepEndpoint> endpointsByPosition = new TreeMap<>((l, r) -> comparePolar(origin, l, r));

    for (final var wall : visionBlockingSegments) {
      assert wall.orientationIndex(origin) == Orientation.COUNTERCLOCKWISE;

      var start = endpointsByPosition.computeIfAbsent(wall.p0, VisibilitySweepEndpoint::new);
      var end = endpointsByPosition.computeIfAbsent(wall.p1, VisibilitySweepEndpoint::new);

      start.startsWall(wall);
      end.endsWall(wall);
    }
    return new ArrayList<>(endpointsByPosition.values());
  }

  private static Coordinate[] envelopeToRing(Envelope envelope) {
    if (envelope.isNull()) {
      return new Coordinate[0];
    }

    return new Coordinate[] {
      new Coordinate(envelope.getMinX(), envelope.getMinY()),
      new Coordinate(envelope.getMaxX(), envelope.getMinY()),
      new Coordinate(envelope.getMaxX(), envelope.getMaxY()),
      new Coordinate(envelope.getMinX(), envelope.getMaxY()),
      new Coordinate(envelope.getMinX(), envelope.getMinY())
    };
  }

  private static @Nonnull Coordinate projectOntoOpenWall(LineSegment ray, LineSegment wall) {
    // TODO This assertion is not quite right: it's okay to project onto an about-to-be-closed wall.
    //  assert isWallOpen(ray, wall) : String.format("Wall %s is not open for ray %s", wall, ray);
    var intersection = ray.lineIntersection(wall);
    assert intersection != null
        : String.format(
            "Unable to project ray %s onto wall %s despite the wall being open", ray, wall);
    return intersection;
  }

  private static int comparePolar(Coordinate origin, Coordinate a, Coordinate b) {
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
    return Double.compare(a.distance(origin), b.distance(origin));
  }

  private static @Nullable Coordinate[] calculateVisibleArea(
      Coordinate origin, VisionBlockingSet visionBlockingSet, Envelope visionBounds) {
    final var timer = CodeTimer.get();

    if (visionBlockingSet.isEmpty()) {
      // No topology, apparently.
      return null;
    }

    timer.start("add bounds");
    /*
     * The algorithm requires walls in every direction. The easiest way to accomplish this is to add
     * the boundary of the bounding box.
     */
    final var envelope = visionBlockingSet.getEnvelope();
    envelope.expandToInclude(visionBounds);
    // Exact expansion distance doesn't matter, we just don't want the boundary walls to overlap
    // endpoints from real walls.
    envelope.expandBy(1.0);
    var coordinates = envelopeToRing(envelope);
    for (int i = 1, n = coordinates.length; i < n; ++i) {
      visionBlockingSet.add(new LineSegment(coordinates[i - 1], coordinates[i]));
    }
    timer.stop("add bounds");

    timer.start("get segments");
    var visionBlockingSegments = visionBlockingSet.getSegments();
    timer.stop("get segments");

    // TODO It would be interesting to try an Acton-style set of arrays:
    //  1. Coordinate[n] - the actual points
    //  2. LineSegment[n] - the started walls
    //  3. LineSegment[n] - the ended walls

    timer.start("build network");
    final var endpoints = buildWallNetwork(origin, visionBlockingSegments);
    timer.stop("build network");

    timer.start("initialize");
    // Make sure to process the first point once more at the end to ensure the sweep covers the full
    // 360 degrees.
    endpoints.add(endpoints.get(0));

    // Note: we are essentially this collection as a priority queue, so we can always operate on the
    // closest wall. However, TreeSet is faster than PriorityQueue in this case, likely since the
    // size of the collection tends to remain quite small.
    // Note: it's not possible to totally order all walls by distance to `origin`. But it is
    // possible to do it for the set of open walls at any point in time, which is why we can use
    // this structure. So it's important to remove ended walls before adding opened walls, otherwise
    // we might momentarily violate that basic requirement of the comparison.
    final var openWalls = new TreeSet<LineSegment>((l, r) -> FogUtil.compareWalls(origin, l, r));

    // The starting condition is a little bit subtle. We need to initialize the open set to have all
    // walls that would have been open _just prior to_ processing the first point, assuming we had
    // done the sweep all the way around. I.e., such a wall would have just ended, or straddles the
    // starting ray. IOW, the wall's first point must be strictly CLOCKWISE, while the second point
    // must be COLINEAR or COUNTERCLOCKWISE.
    {
      final var initialRay = new LineSegment(origin, endpoints.getFirst().getPoint());
      for (final var wall : visionBlockingSegments) {
        final var isOpen =
            Orientation.COUNTERCLOCKWISE == initialRay.orientationIndex(wall.p1)
                && Orientation.COUNTERCLOCKWISE != initialRay.orientationIndex(wall.p0);
        if (isOpen) {
          openWalls.add(wall);
        }
      }
    }
    timer.stop("initialize");
    var previousNearestWall = openWalls.getFirst();

    timer.start("sweep");
    // Now for the real sweep. Make sure to process the first point once more at the end to ensure
    // the sweep covers the full 360 degrees.
    List<Coordinate> visionPoints = new ArrayList<>();
    for (final var endpoint : endpoints) {
      assert !openWalls.isEmpty();

      // Note: removeAll can be slow, but not in our case with few removed elements.
      openWalls.removeAll(endpoint.getEndsWalls());
      openWalls.addAll(endpoint.getStartsWalls());

      // Find a new nearest wall.
      assert !openWalls.isEmpty();
      final var currentNearestWall = openWalls.getFirst();
      if (currentNearestWall == previousNearestWall) {
        continue;
      }

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

      previousNearestWall = currentNearestWall;
    }
    timer.stop("sweep");
    if (visionPoints.size() < 3) {
      // This shouldn't happen, but just in case.
      log.warn("Sweep produced too few points: {}", visionPoints);
      return null;
    }
    timer.start("close polygon");
    // Ensure a closed loop.
    visionPoints.add(visionPoints.get(0));
    timer.stop("close polygon");

    timer.start("build result");
    try {
      return visionPoints.toArray(Coordinate[]::new);
    }
    finally {
      timer.stop("build result");
    }
  }

  /**
   * Expose visible area and previous path of all tokens in the token set. Server and clients are
   * updated.
   *
   * @param renderer the ZoneRenderer of the map
   * @param tokenSet the set of GUID of the tokens
   */
  public static void exposeVisibleArea(final ZoneRenderer renderer, Set<GUID> tokenSet) {
    exposeVisibleArea(renderer, tokenSet, false);
  }

  /**
   * Expose the visible area of all tokens in the token set. Server and clients are updated.
   *
   * @param renderer the ZoneRenderer of the map
   * @param tokenSet the set of GUID of the tokens
   * @param exposeCurrentOnly show only the current vision be exposed, or the last path too?
   */
  @SuppressWarnings("unchecked")
  public static void exposeVisibleArea(
      final ZoneRenderer renderer, Set<GUID> tokenSet, boolean exposeCurrentOnly) {
    final Zone zone = renderer.getZone();

    for (GUID tokenGUID : tokenSet) {
      Token token = zone.getToken(tokenGUID);
      if (token == null) {
        continue;
      }
      if (!token.getHasSight()) {
        continue;
      }
      if (token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token)) {
        continue;
      }

      if (zone.getWaypointExposureToggle() && !exposeCurrentOnly) {
        if (token.getLastPath() == null) return;

        List<CellPoint> wayPointList = (List<CellPoint>) token.getLastPath().getWayPointList();

        final Token tokenClone = token.clone();

        for (final Object cell : wayPointList) {
          ZonePoint zp = null;
          if (cell instanceof CellPoint) {
            zp = zone.getGrid().convert((CellPoint) cell);
          } else {
            zp = (ZonePoint) cell;
          }

          tokenClone.setX(zp.x);
          tokenClone.setY(zp.y);

          renderer.flush(tokenClone);
          Area tokenVision =
              renderer.getZoneView().getVisibleArea(tokenClone, renderer.getPlayerView());
          if (tokenVision != null) {
            Set<GUID> filteredToks = new HashSet<GUID>();
            filteredToks.add(tokenClone.getId());
            MapTool.serverCommand().exposeFoW(zone.getId(), tokenVision, filteredToks);
          }
        }
        // System.out.println("2. Token: " + token.getGMName() + " - ID: " + token.getId());
        renderer.flush(token);
      } else {
        renderer.flush(token);
        Area tokenVision = renderer.getZoneView().getVisibleArea(token, renderer.getPlayerView());
        if (tokenVision != null) {
          Set<GUID> filteredToks = new HashSet<GUID>();
          filteredToks.add(token.getId());
          MapTool.serverCommand().exposeFoW(zone.getId(), tokenVision, filteredToks);
        }
      }
    }
  }

  public static void exposeVisibleAreaAtWaypoint(
      final ZoneRenderer renderer, Set<GUID> tokenSet, ZonePoint zp) {
    final Zone zone = renderer.getZone();

    for (GUID tokenGUID : tokenSet) {
      Token token = zone.getToken(tokenGUID);
      if (token == null) {
        continue;
      }
      if (!token.getHasSight()) {
        continue;
      }
      if (token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token)) {
        continue;
      }

      ZonePoint zpStart = new ZonePoint(token.getX(), token.getY());
      token.setX(zp.x);
      token.setY(zp.y);
      renderer.flush(token);

      Area tokenVision = renderer.getZoneView().getVisibleArea(token, renderer.getPlayerView());
      if (tokenVision != null) {
        Set<GUID> filteredToks = new HashSet<GUID>();
        filteredToks.add(token.getId());
        MapTool.serverCommand().exposeFoW(zone.getId(), tokenVision, filteredToks);
      }

      token.setX(zpStart.x);
      token.setY(zpStart.y);
      renderer.flush(token);
    }
  }

  /**
   * This function is called by Meta-Shift-O, the token right-click, Expose {@code ->} only
   * Currently visible menu, from the Client/Server methods calls from
   * net.rptools.maptool.server.ServerMethodHandler.exposePCArea(GUID), and the macro
   * exposePCOnlyArea(). It takes the list of all PC tokens with sight and clear their exposed area,
   * clear the general exposed area, and expose the currently visible area. The server and other
   * clients are also updated.
   *
   * @author updated Jamz, Merudo
   * @since updated 1.5.8
   * @param renderer the ZoneRenderer
   */
  public static void exposePCArea(ZoneRenderer renderer) {
    Set<GUID> tokenSet = new HashSet<GUID>();
    List<Token> tokList = renderer.getZone().getPlayerTokensWithSight();

    String playerName = MapTool.getPlayer().getName();
    boolean isGM = MapTool.getPlayer().getRole() == Role.GM;

    for (Token token : tokList) {
      // why check ownership? Only GM can run this.
      boolean owner = token.isOwner(playerName) || isGM;

      if ((!MapTool.isPersonalServer() || MapTool.getServerPolicy().isUseIndividualViews())
          && !owner) {
        continue;
      }

      tokenSet.add(token.getId());
    }

    clearExposedArea(renderer.getZone(), true);
    renderer.getZone().clearExposedArea(tokenSet);
    exposeVisibleArea(renderer, tokenSet, true);
  }

  /**
   * Clear the FoW on one map. Updates server and clients.
   *
   * @param zone the Zone of the map.
   * @param globalOnly should only common area be cleared, or all token exposed areas?
   */
  private static void clearExposedArea(Zone zone, boolean globalOnly) {
    zone.clearExposedArea(globalOnly);
    MapTool.serverCommand().clearExposedArea(zone.getId(), globalOnly);
  }

  // Jamz: Expose not just PC tokens but also any NPC tokens the player owns
  /**
   * This function is called by Meta-Shift-F and the macro exposeAllOwnedArea()
   *
   * <p>Changed base function to select tokens now on ownership and based on TokenSelection menu
   * buttons.
   *
   * @author Jamz
   * @since 1.4.0.1
   * @param renderer the ZoneRenderer
   */
  public static void exposeAllOwnedArea(ZoneRenderer renderer) {
    Set<GUID> tokenSet = new HashSet<GUID>();

    // Jamz: Possibly pass a variable to override buttons? Also, maybe add a return a list of ID's
    List<Token> tokList = renderer.getZone().getOwnedTokensWithSight(MapTool.getPlayer());

    for (Token token : tokList) tokenSet.add(token.getId());

    // System.out.println("tokList: " + tokList.toString());

    /*
     * TODO: Jamz: May need to add back the isUseIndividualViews() logic later after testing... String playerName = MapTool.getPlayer().getName(); boolean isGM = MapTool.getPlayer().getRole() ==
     * Role.GM;
     *
     * for (Token token : tokList) { boolean owner = token.isOwner(playerName) || isGM;
     *
     * //System.out.println("token: " + token.getName() + ", owner: " + owner);
     *
     * if ((!MapTool.isPersonalServer() || MapTool.getServerPolicy().isUseIndividualViews()) && !owner) { continue; } tokenSet.add(token.getId()); }
     */

    renderer.getZone().clearExposedArea(tokenSet);
    exposeVisibleArea(renderer, tokenSet, true);
  }

  /**
   * Restore the FoW on one map. Updates server and clients.
   *
   * @param renderer the ZoneRenderer of the map.
   */
  public static void restoreFoW(final ZoneRenderer renderer) {
    // System.out.println("Zone ID: " + renderer.getZone().getId());
    clearExposedArea(renderer.getZone(), false);
  }

  public static void exposeLastPath(final ZoneRenderer renderer, final Set<GUID> tokenSet) {
    CodeTimer.using("exposeLastPath", timer -> {
      renderer.getZoneView().flushTopology();

    final Zone zone = renderer.getZone();
    final Grid grid = zone.getGrid();
    GridCapabilities caps = grid.getCapabilities();

    if (!caps.isPathingSupported() || !caps.isSnapToGridSupported()) {
      return;
    }

    final Set<GUID> filteredToks = new HashSet<GUID>(2);

    for (final GUID tokenGUID : tokenSet) {
      final Token token = zone.getToken(tokenGUID);
      timer.start("exposeLastPath-" + token.getName());

      Path<? extends AbstractPoint> lastPath = token.getLastPath();

      if (lastPath == null) return;

      Map<GUID, ExposedAreaMetaData> fullMeta = zone.getExposedAreaMetaData();
      GUID exposedGUID = token.getExposedAreaGUID();
      final ExposedAreaMetaData meta =
          fullMeta.computeIfAbsent(exposedGUID, guid -> new ExposedAreaMetaData());

      final Token tokenClone = new Token(token);
      final ZoneView zoneView = renderer.getZoneView();
      Area visionArea = new Area();

      // Lee: get path according to zone's way point exposure toggle...
      List<? extends AbstractPoint> processPath =
          zone.getWaypointExposureToggle() ? lastPath.getWayPointList() : lastPath.getCellPath();

      int stepCount = processPath.size();
      log.debug("Path size = " + stepCount);

      Consumer<ZonePoint> revealAt =
          zp -> {
            tokenClone.setX(zp.x);
            tokenClone.setY(zp.y);

            Area currVisionArea = zoneView.getVisibleArea(tokenClone, renderer.getPlayerView());
            if (currVisionArea != null) {
              visionArea.add(currVisionArea);
              meta.addToExposedAreaHistory(currVisionArea);
            }

            zoneView.flush(tokenClone);
          };
      if (token.isSnapToGrid()) {
        // For each cell point along the path, reveal FoW.
        for (final AbstractPoint cell : processPath) {
          assert cell instanceof CellPoint;
          revealAt.accept(grid.convert((CellPoint) cell));
        }
      } else {
        // Only reveal the final position.
        final AbstractPoint finalCell = processPath.get(processPath.size() - 1);
        assert finalCell instanceof ZonePoint;
        revealAt.accept((ZonePoint) finalCell);
      }

      timer.stop("exposeLastPath-" + token.getName());
      renderer.flush(tokenClone);

      filteredToks.clear();
      filteredToks.add(token.getId());
      zone.putToken(token);
      MapTool.serverCommand().exposeFoW(zone.getId(), visionArea, filteredToks);
      MapTool.serverCommand().updateExposedAreaMeta(zone.getId(), exposedGUID, meta);
    }
    });
  }

  /**
   * Find the center point of a vision TODO: This is a horrible horrible method. the API is just
   * plain disgusting. But it'll work to consolidate all the places this has to be done until we can
   * encapsulate it into the vision itself.
   *
   * @param token the token to get the vision center of.
   * @param zone the Zone where the token is.
   * @return the center point
   */
  public static Point calculateVisionCenter(Token token, Zone zone) {
    Grid grid = zone.getGrid();
    int x = 0, y = 0;

    Rectangle bounds = null;
    if (token.isSnapToGrid()) {
      bounds =
          token
              .getFootprint(grid)
              .getBounds(grid, grid.convert(new ZonePoint(token.getX(), token.getY())));
    } else {
      bounds = token.getBounds(zone);
    }

    x = bounds.x + bounds.width / 2;
    y = bounds.y + bounds.height / 2;

    return new Point(x, y);
  }
}
