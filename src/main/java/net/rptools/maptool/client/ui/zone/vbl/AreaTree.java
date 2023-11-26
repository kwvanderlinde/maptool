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

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Comparator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.GeometryUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

/** Class digesting a VBL area into an AreaOcean. */
public class AreaTree {
  private static final Logger log = LogManager.getLogger(AreaTree.class);

  // TODO I want to get rid of this phony AreaOcean as it pollutes the AreaOcean interface.
  //  Instead I would have a list of root AreaIsland. That means that `AreaIsland.getParent()` may
  //  return null, and that the generalized lookup result is no longer guaranteed to have an ocean
  //  or an island. But that might be fine if we change the representation a bit. On the other hand,
  //  we have a very nice degree of expressiveness right now.
  //      Another angle here is of representation. A JTS Polygon so nicely captures the idea of an
  //  AreaIsland plus its child AreaOcean. If that become part of our basic representation, then a
  //  parent relationship in AreaIsland would actually reference another AreaIsland plus an index to
  //  identify the AreaOcean. Meanwhile, AreaOcean would all but disappear. Then, instead of asking
  //  for the parent AreaOcean when needed, we actually have higher-level operations that can
  //  navigate the tree, e.g.: get nearest ocean ring for a given AreaIsland; get all sibling
  //  AreaIslands for a given AreaIsland; get parent AreaIsland for a given AreaIsland.
  //      On the other hand, the JTS polygon representation would exclude the oceans during
  //  containment checks, so on that front the representation is not as directly as we might like.
  //      And yet, it would seem that some of these ideas have merit regardless of representation.
  //  Having a more abstract means of navigating the tree could be beneficial and free up our
  //  ability to represent things.

  /** The original area digested. */
  private final @Nonnull AreaOcean theOcean;

  /** The original area, in case we want to return the original area undigested */
  private final @Nonnull Area theArea;

  /** Create an empty tree. */
  public AreaTree() {
    this(new Area(), new AreaOcean());
  }

  private AreaTree(@Nonnull Area original, @Nonnull AreaOcean root) {
    this.theArea = original;
    this.theOcean = root;
  }

  /**
   * The results of locating a point in the {@code AreaTree}.
   *
   * @param nearestOcean The nearest ancestor ocean. If the point is in an ocean, {@code
   *     nearestOcean} will be that ocean. Otherwise, it will be the parent of the island.
   * @param island If the point is in an island, this will be that island. Otherwise {@code null}.
   */
  public record LocateResult(
      @Nullable AreaIsland parentIsland,
      @Nonnull AreaOcean nearestOcean,
      @Nullable AreaIsland island) {}

  public @Nonnull LocateResult locate(Coordinate point) {
    @Nonnull AreaOcean nearestOcean = theOcean;
    @Nullable AreaIsland oceanParent = null; // The parent of nearestOcean, if any.

    // Note: this loop termintes since we are descending the tree and will eventually hit a leaf
    // ocean or island.
    while (true) {
      // First try to find a better island.
      @Nonnull AreaIsland foundIsland;
      foundTheIsland:
      {
        for (final var childIsland : nearestOcean.getChildren()) {
          if (childIsland.contains(point)) {
            foundIsland = childIsland;
            break foundTheIsland;
          }
        }
        // The ocean is the deepest possible, and point is not in an island.
        return new LocateResult(oceanParent, nearestOcean, null);
      }

      // Now try to find an even better ocean.
      foundTheOcean:
      {
        for (final var childOcean : foundIsland.getChildren()) {
          if (childOcean.contains(point)) {
            nearestOcean = childOcean;
            oceanParent = foundIsland;
            break foundTheOcean;
          }
        }
        // The island is the deepest possible, and point is in that island.
        return new LocateResult(oceanParent, nearestOcean, foundIsland);
      }

      // Keep going. We have a new nearest ocean to work with.
    }
  }

  public @Nonnull Area getArea() {
    return theArea;
  }

  /**
   * Digest a flat {@link java.awt.geom.Area} into a hierarchical {@link
   * net.rptools.maptool.client.ui.zone.vbl.AreaTree}.
   *
   * @param area The area to digest.
   * @return The {@code AreaTree} equivalent of {@code area}.
   */
  public static AreaTree digest(@Nonnull Area area) {
    var jts = GeometryUtil.toJtsPolygons(area);

    // For each polygon, walk down its containment hierarchy to build AreaMeta, alternating between
    // ocean and island.
    final var islands = new ArrayList<AreaIsland>();
    for (final var polygon : jts) {
      // Each polygon defines an island with its child oceans. So we can skip the need to associate
      // oceans with islands by doing it here.

      final var island = new AreaIsland(new AreaMeta(polygon.getExteriorRing()));
      final var oceanCount = polygon.getNumInteriorRing();
      for (int i = 0; i < oceanCount; ++i) {
        final var ocean = new AreaOcean(new AreaMeta(polygon.getInteriorRingN(i)));
        island.addOcean(ocean);
      }

      islands.add(island);
    }

    // Now we need to hook up islands to the hierarchy. By sorting them from large to small, then
    // consuming front-to-back, we know that parents will have been added to the hierarchy.
    final var root = new AreaOcean();
    islands.sort(Comparator.comparingDouble(AreaIsland::getBoundedBoxArea).reversed());
    for (var island : islands) {
      var possibleParent = island.getDeepestContainingContainerIn(root);
      if (possibleParent instanceof AreaOcean ocean) {
        // Awesome, found a viable parent!
        ocean.addIsland(island);
      } else {
        // Best container is an island, but we can't add an island to another island!
        // This shouldn't ever actually happen.
        log.warn("Unable to find a parent container for an island. Returning an empty tree");
        return new AreaTree();
      }
    }

    return new AreaTree(area, root);
  }
}
