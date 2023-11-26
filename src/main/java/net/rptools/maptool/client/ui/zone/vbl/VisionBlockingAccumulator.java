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

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.prep.PreparedGeometry;

public final class VisionBlockingAccumulator {
  private final GeometryFactory geometryFactory;
  private final Coordinate origin;
  private final PreparedGeometry vision;
  private final List<LineString> visionBlockingSegments;

  public VisionBlockingAccumulator(
      GeometryFactory geometryFactory, Point origin, PreparedGeometry vision) {
    this.geometryFactory = geometryFactory;
    this.origin = new Coordinate(origin.getX(), origin.getY());

    this.vision = vision;

    this.visionBlockingSegments = new ArrayList<>();
  }

  public List<LineString> getVisionBlockingSegments() {
    return visionBlockingSegments;
  }

  private @Nonnull AreaTree.LocateResult locateOriginIn(AreaTree topology) {
    return topology.locate(origin);
  }

  private void blockVisionBeyondContainer(AreaContainer<?, ?> container) {
    final var facing =
        container.isOcean() ? Facing.OCEAN_SIDE_FACES_ORIGIN : Facing.ISLAND_SIDE_FACES_ORIGIN;

    visionBlockingSegments.addAll(
        container.getVisionBlockingBoundarySegments(geometryFactory, origin, facing, vision));

    for (var child : container.getChildren()) {
      visionBlockingSegments.addAll(
          child.getVisionBlockingBoundarySegments(geometryFactory, origin, facing, vision));
    }
  }

  /**
   * Finds all wall topology segments that can take part in blocking vision.
   *
   * @param topology The topology to treat as Wall VBL.
   * @return false if the vision has been completely blocked by topology, or true if vision may be
   *     blocked by particular segments.
   */
  public boolean addWallBlocking(AreaTree topology) {
    final var result = locateOriginIn(topology);

    if (result.island() != null) {
      // Since we're contained in a wall island, there can be no vision through it.
      return false;
    }

    blockVisionBeyondContainer(result.nearestOcean());
    return true;
  }

  /**
   * Finds all hill topology segments that can take part in blocking vision.
   *
   * @param topology The topology to treat as Hill VBL.
   * @return false if the vision has been completely blocked by topology, or true if vision can be
   *     blocked by particular segments.
   */
  public boolean addHillBlocking(AreaTree topology) {
    final var result = locateOriginIn(topology);

    /*
     * There are two cases for Hill VBL:
     * 1. A token inside hill VBL can see into adjacent oceans, and therefore into other areas of
     *    Hill VBL in those oceans.
     * 2. A token outside hill VBL can see into hill VBL, but not into any oceans adjacent to it.
     */

    if (result.parentIsland() != null) {
      blockVisionBeyondContainer(result.parentIsland());
    }

    // Check each contained island.
    for (var containedIsland : result.nearestOcean().getChildren()) {
      if (containedIsland == result.island()) {
        // We don't want to block vision for the hill we're currently in.
        // TODO Ideally we could block the second occurence of the current island, but we need
        //  a way to do that reliably.
        continue;
      }

      blockVisionBeyondContainer(containedIsland);
    }

    if (result.island() != null) {
      // Same basics as the nearestOcean logic above, but applied to children of this island
      // (grandchildren of nearestOcean).
      for (final var childOcean : result.island().getChildren()) {
        for (final var containedIsland : childOcean.getChildren()) {
          blockVisionBeyondContainer(containedIsland);
        }
      }
    }

    return true;
  }

  /**
   * Finds all pit topology segments that can take part in blocking vision.
   *
   * @param topology The topology to treat as Pit VBL.
   * @return false if the vision has been completely blocked by topology, or true if vision can be
   *     blocked by particular segments.
   */
  public boolean addPitBlocking(AreaTree topology) {
    final var result = locateOriginIn(topology);

    /*
     * There are two cases for Pit VBL:
     * 1. A token inside Pit VBL can see only see within the current island, not into any adjacent
     *    oceans.
     * 2. A token outside Pit VBL is unobstructed by the Pit VBL (nothing special to do).
     */
    if (result.island() != null) {
      blockVisionBeyondContainer(result.island());
    }

    return true;
  }

  /**
   * Finds all cover topology segments that can take part in blocking vision.
   *
   * @param topology The topology to treat as Cover VBL.
   * @return false if the vision has been completely blocked by topology, or true if vision can be
   *     blocked by particular segments.
   */
  public boolean addCoverBlocking(AreaTree topology) {
    final var result = locateOriginIn(topology);

    /*
     * There are two cases for Cover VBL:
     * 1. A token inside Cover VBL can see everything, unobstructed by the cover.
     * 2. A token outside Cover VBL can see nothing, as if it were wall.
     */

    blockVisionBeyondContainer(result.nearestOcean());

    if (result.island() != null) {
      for (var ocean : result.island().getChildren()) {
        blockVisionBeyondContainer(ocean);
      }
    }

    return true;
  }
}
