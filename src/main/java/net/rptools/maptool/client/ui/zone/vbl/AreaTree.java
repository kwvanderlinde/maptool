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

import com.google.common.collect.Iterables;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.model.Zone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

/** Class digesting a VBL area into an AreaOcean. */
public class AreaTree {
  private static final Logger log = LogManager.getLogger(AreaTree.class);

  /**
   * The results of locating a point in the {@code AreaTree}.
   *
   * @param nearestOcean The nearest ancestor ocean. If the point is in an ocean, {@code
   *     nearestOcean} will be that ocean. Otherwise, it will be the parent of the island.
   * @param island If the point is in an island, this will be that island. Otherwise {@code null}.
   */
  public record LocateResult(
      @Nullable Node parentIsland, @Nonnull Node nearestOcean, @Nullable Node island) {}

  /**
   * A node of an {@code AreaTree}.
   *
   * <p>A node in the tree references its children and has a boundary ({@code AreaMeta}) as a value.
   */
  public static final class Node {
    private final @Nonnull AreaMeta meta;
    private final List<Node> children = new ArrayList<>();

    private Node(@Nonnull AreaMeta meta) {
      this.meta = meta;
    }

    public @Nonnull AreaMeta getMeta() {
      return meta;
    }

    public Iterable<Node> getChildren() {
      return Iterables.unmodifiableIterable(this.children);
    }
  }

  private final Zone.TopologyType type;

  /** The original area digested. */
  private final @Nonnull Node theOcean;

  /** The original area, in case we want to return the original area undigested */
  private final @Nonnull Area theArea;

  /** Create an empty tree. */
  public AreaTree(Zone.TopologyType type) {
    this(type, new Area(), new Node(new AreaMeta()));
  }

  private AreaTree(@Nonnull Zone.TopologyType type, @Nonnull Area original, @Nonnull Node root) {
    this.type = type;
    this.theArea = original;
    this.theOcean = root;
  }

  public @Nonnull Zone.TopologyType getType() {
    return type;
  }

  public @Nonnull LocateResult locate(Coordinate point) {
    @Nullable Node parentIsland = null;
    @Nonnull Node nearestOcean = theOcean;
    @Nullable Node containingIsland = null;

    // Note: this loop termintes since we are descending the tree and will eventually hit a leaf
    // ocean or island.
    @Nonnull Node current = theOcean;
    while (true) {
      foundTheChild:
      {
        for (final var child : current.getChildren()) {
          if (child.getMeta().contains(point)) {
            if (!child.getMeta().isOcean()) {
              containingIsland = child;
            } else {
              parentIsland = containingIsland;
              nearestOcean = child;
              containingIsland = null;
            }
            current = child;
            break foundTheChild;
          }
        }
        // No containing child found.
        return new LocateResult(parentIsland, nearestOcean, containingIsland);
      }

      // Keep going. We have a new container to inspect.
    }
  }

  public @Nonnull Area getArea() {
    return theArea;
  }

  /**
   * Adds a container to the tree.
   *
   * <p>This is a utility for use during {@link #digest(net.rptools.maptool.model.Zone.TopologyType,
   * java.awt.geom.Area)}.
   *
   * @param toInsert The container that needs to be a added to the tree.
   * @return The parent into which {@code toInsert} was added as a child, or {@code null} if no
   *     suitable parent could be found.
   */
  private static @Nullable Node addToDeepestContainer(Node root, Node toInsert) {
    Node child = root; // Yeah, it looks weird, but it works.
    Node current;

    do {
      current = child;

      // Get the containing child.
      child = null;
      for (final var currentChild : current.getChildren()) {
        if (currentChild.getMeta().contains(toInsert.getMeta())) {
          child = currentChild;
        }
      }
    } while (child != null);

    if (current.getMeta().isOcean() == toInsert.getMeta().isOcean()) {
      // Can't add like to like.
      return null;
    }

    current.children.add(toInsert);
    return current;
  }

  /**
   * Digest a flat {@link java.awt.geom.Area} into a hierarchical {@link
   * net.rptools.maptool.client.ui.zone.vbl.AreaTree}.
   *
   * @param area The area to digest.
   * @return The {@code AreaTree} equivalent of {@code area}.
   */
  public static AreaTree digest(@Nonnull Zone.TopologyType type, @Nonnull Area area) {
    var jts = GeometryUtil.toJtsPolygons(area);

    // For each polygon, walk down its containment hierarchy to build AreaMeta, alternating between
    // ocean and island.
    final var islands = new ArrayList<Node>();
    for (final var polygon : jts) {
      // Each polygon defines an island with its child oceans. So we can skip the need to associate
      // oceans with islands by doing it here.

      final var island = new Node(new AreaMeta(polygon.getExteriorRing()));
      final var oceanCount = polygon.getNumInteriorRing();
      for (int i = 0; i < oceanCount; ++i) {
        final var ocean = new Node(new AreaMeta(polygon.getInteriorRingN(i)));
        island.children.add(ocean);
      }

      islands.add(island);
    }

    // Now we need to hook up islands to the hierarchy. By sorting them from large to small, then
    // consuming front-to-back, we know that parents will have been added to the hierarchy.
    final var root = new Node(new AreaMeta());
    islands.sort(
        (l, r) ->
            -Double.compare(l.getMeta().getBoundingBoxArea(), r.getMeta().getBoundingBoxArea()));
    for (var island : islands) {
      var newParent = addToDeepestContainer(root, island);
      if (newParent == null) {
        // Best container is an island, but we can't add an island to another island!
        // This shouldn't ever actually happen.
        log.warn("Unable to find a parent container for an island. Returning an empty tree");
        return new AreaTree(type);
      }
    }

    return new AreaTree(type, area, root);
  }
}
