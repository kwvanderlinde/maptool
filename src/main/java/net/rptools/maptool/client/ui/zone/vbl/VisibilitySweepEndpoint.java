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
import java.util.List;
import org.locationtech.jts.geom.Coordinate;

/**
 * Represents a vertex used in the visibility sweep.
 *
 * <p>This class conveniently associates a point in space with the wall that are incident to it. The
 * walls are distinguished as starting or ending at this point, in agreement with the direction of
 * the sweep.
 */
public final class VisibilitySweepEndpoint {
  private final Coordinate point;
  private final List<VisibilitySweepEndpoint> startsWalls = new ArrayList<>();
  private final List<VisibilitySweepEndpoint> endsWalls = new ArrayList<>();

  public VisibilitySweepEndpoint(Coordinate point) {
    this.point = point;
  }

  public Coordinate getPoint() {
    return point;
  }

  public List<VisibilitySweepEndpoint> getStartsWalls() {
    return startsWalls;
  }

  public List<VisibilitySweepEndpoint> getEndsWalls() {
    return endsWalls;
  }

  public void startsWall(VisibilitySweepEndpoint ending) {
    startsWalls.add(ending);
  }

  public void endsWall(VisibilitySweepEndpoint starting) {
    endsWalls.add(starting);
  }

  @Override
  public String toString() {
    return String.format("VisibilitySweepEndpoint(%s)", point.toString());
  }
}
