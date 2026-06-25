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
package net.rptools.maptool.client.entities;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public final class Entity {
  private final Point2D position;
  private final Rectangle2D bounds;

  public Entity(Point2D position) {
    this.position = new Point2D.Double(position.getX(), position.getY());
    this.bounds = new Rectangle2D.Double(this.position.getX(), this.position.getY(), 0, 0);
  }

  public Point2D getPosition() {
    return new Point2D.Double(position.getX(), position.getY());
  }

  public Rectangle2D getBounds() {
    return new Rectangle2D.Double(
        bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
  }
}
