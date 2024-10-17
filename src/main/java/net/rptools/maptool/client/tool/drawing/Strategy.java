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
package net.rptools.maptool.client.tool.drawing;

import java.awt.Rectangle;
import java.awt.Shape;
import javax.annotation.Nullable;
import net.rptools.maptool.model.ZonePoint;

public interface Strategy<StateT> {
  /**
   * Check if the tool is a freehand tool.
   *
   * <p>Freehand tools have a different flow to other tools. Other tools are click-based, freehand
   * tools are dragged-based.
   *
   * @return {@code true} if the strategy is for a freehand tool.
   */
  default boolean isFreehand() {
    return false;
  }

  /**
   * Check if the tool is a linear tool.
   *
   * <p>Linear tools support snap-to-center, but not origin-as-center since they don't have a real
   * center.
   *
   * @return {@code true} if the strategy is for a linear tool.
   */
  default boolean isLinear() {
    return false;
  }

  /**
   * Start a new drawing using {@code point} as the first point.
   *
   * @param point
   */
  StateT startNewAtPoint(ZonePoint point);

  /**
   * If the tool supports it, commit the last point and create a new one in its place.
   *
   * <p>This is only for tools such as line tools, where multiple points can be specified.
   */
  default void pushPoint(StateT state, ZonePoint point) {}

  /**
   * Get the current shape of the tool.
   *
   * @return The shape of the new topology, or {@code null} to indicate there is nothing to add or
   *     remove.
   */
  @Nullable
  Shape getShape(StateT state, ZonePoint currentPoint, boolean centerOnOrigin);

  static Rectangle normalizedRectangle(ZonePoint p1, ZonePoint p2, boolean p1IsCenter) {
    if (p1IsCenter) {
      var halfWidth = Math.abs(p2.x - p1.x);
      var halfHeight = Math.abs(p2.y - p1.y);
      return new Rectangle(p1.x - halfWidth, p1.y - halfHeight, 2 * halfWidth, 2 * halfHeight);
    }

    // AWT doesn't like drawing rectangles with negative width or height. So normalize it first.
    int minX = Math.min(p1.x, p2.x);
    int maxX = Math.max(p1.x, p2.x);
    int minY = Math.min(p1.y, p2.y);
    int maxY = Math.max(p1.y, p2.y);
    return new Rectangle(minX, minY, maxX - minX, maxY - minY);
  }
}
