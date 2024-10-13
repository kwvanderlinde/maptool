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
package net.rptools.maptool.client.ui.zone.renderer.instructions;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;

public sealed interface RenderablePath {
  record CellPath(
      BufferedImage cellHighlight,
      BufferedImage waypointDecoration,
      double waypointDecorationScale,
      Font distanceTextFont,
      List<PathCell> occupiedCells,
      List<Point2D> pathCentres)
      implements RenderablePath {}

  record PointPath(
      BufferedImage waypointDecoration,
      double waypointSizeX,
      double waypointSizeY,
      List<Point2D> points,
      List<Point2D> waypoints)
      implements RenderablePath {}

  final class PathCell {
    public Rectangle2D bounds;
    public @Nullable DistanceText distanceText;
    public boolean isWaypoint;

    public PathCell(Rectangle2D bounds, @Nullable DistanceText distanceText, boolean isWaypoint) {
      this.bounds = bounds;
      this.distanceText = distanceText;
      this.isWaypoint = isWaypoint;
    }
  }

  record DistanceText(String text, Point2D bottomRightPosition) {}
}
