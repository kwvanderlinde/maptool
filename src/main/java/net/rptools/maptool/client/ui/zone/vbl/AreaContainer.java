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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

public sealed interface AreaContainer<
        SelfT extends AreaContainer<SelfT, ChildT>, ChildT extends AreaContainer<ChildT, SelfT>>
    permits AreaOcean, AreaIsland {
  public boolean isOcean();

  public Iterable<ChildT> getChildren();

  /**
   * Checks whether {@code point} is contained in the container.
   *
   * @param point The point to test.
   * @return {@code true} if {@code point} is within the boundary of this container.
   */
  public boolean contains(Coordinate point);

  public boolean contains(AreaMeta meta);

  /**
   * Find sections of the container's boundary that block vision.
   *
   * <p>Each returned segment is a sequence of connected line segments which constitute an unbroken
   * section of the container's boundary, where each segment faces the direction chosen by {@code
   * facing}.
   *
   * <p>The segments that are returned depend on `origin`, and `frontSegments`.
   *
   * @param origin The point from which visibility is calculated.
   * @param facing Whether the returned segments must have their island side or their ocean side
   *     facing the origin.
   * @return A list of segments, which together represent the complete set of boundary faces that
   *     block vision.
   */
  public VisionBlockingSet getVisionBlockingBoundarySegments(
      Coordinate origin, Facing facing, Envelope visionBounds);
}
