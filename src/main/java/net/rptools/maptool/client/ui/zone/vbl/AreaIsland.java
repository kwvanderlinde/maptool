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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

/**
 * Represents a piece of solid topology.
 *
 * <p>An island can contain holes, known as oceans, and will itself belong to an ocean.
 */
public final class AreaIsland implements AreaContainer<AreaIsland, AreaOcean> {

  private final AreaMeta meta;
  private final List<AreaOcean> oceans = new ArrayList<>();

  /**
   * Creates a new island with a given boundary.
   *
   * @param meta The boundary of the island. Must be a hole.
   */
  public AreaIsland(AreaMeta meta) {
    assert !meta.isHole();
    this.meta = meta;
  }

  @Override
  public boolean isOcean() {
    return false;
  }

  @Override
  public Iterable<AreaOcean> getChildren() {
    return Iterables.unmodifiableIterable(oceans);
  }

  @Override
  public boolean contains(Coordinate point) {
    return meta.contains(point);
  }

  @Override
  public boolean contains(AreaMeta other) {
    return meta.contains(other);
  }

  @Override
  public VisionBlockingSet getVisionBlockingBoundarySegments(
      Coordinate origin, Facing facing, Envelope visionBounds) {
    return meta.getFacingSegments(origin, facing, visionBounds);
  }

  public @Nonnull AreaContainer<?, ?> getDeepestContainingContainerIn(AreaOcean root) {
    AreaContainer<?, ?> child = root; // Yeah, it looks wierd, but it works.
    AreaContainer<?, ?> current;

    do {
      current = child;

      // Get the containing child.
      child = null;
      for (final var currentChild : current.getChildren()) {
        if (currentChild.contains(meta)) {
          child = currentChild;
        }
      }
    } while (child != null);

    return current;
  }

  public void addOcean(AreaOcean ocean) {
    oceans.add(ocean);
  }

  public double getBoundedBoxArea() {
    return meta.getBoundingBox().getArea();
  }
}
