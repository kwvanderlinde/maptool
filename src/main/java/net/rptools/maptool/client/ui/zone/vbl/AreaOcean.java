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
 * Represents a hole in the topology.
 *
 * <p>An ocean may contain islands of topology within it, and may or may not be contained within an
 * island.
 */
public final class AreaOcean implements AreaContainer<AreaOcean, AreaIsland> {

  private final AreaMeta meta;
  private final List<AreaIsland> islands = new ArrayList<>();

  public AreaOcean() {
    this.meta = null;
  }

  /**
   * Creates a new ocean with a given boundary.
   *
   * @param meta The boundary of a hole.
   */
  public AreaOcean(@Nonnull AreaMeta meta) {
    assert meta.isHole();
    this.meta = meta;
  }

  @Override
  public boolean isOcean() {
    return true;
  }

  @Override
  public Iterable<AreaIsland> getChildren() {
    return Iterables.unmodifiableIterable(this.islands);
  }

  @Override
  public boolean contains(Coordinate point) {
    return meta == null || meta.contains(point);
  }

  @Override
  public boolean contains(AreaMeta other) {
    return meta == null || meta.contains(other);
  }

  @Override
  public VisionBlockingSet getVisionBlockingBoundarySegments(
      Coordinate origin, Facing facing, Envelope visionBounds) {
    if (meta == null) {
      return new VisionBlockingSet();
    }

    return meta.getFacingSegments(origin, facing, visionBounds);
  }

  public void addIsland(AreaIsland island) {
    islands.add(island);
  }
}
