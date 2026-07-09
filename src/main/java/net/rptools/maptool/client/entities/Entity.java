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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

public final class Entity {
  private static long previousId = 0;

  private static long createId() {
    return ++previousId;
  }

  private final long id;
  private final Point2D position;
  private final Rectangle2D bounds;
  private final List<Component> components = new ArrayList<>();

  // TODO Not all entities need bounds (e.g., grid, board, etc). So make that a component.
  public Entity(Point2D position) {
    this(position, new Rectangle2D.Double(position.getX(), position.getY(), 0, 0));
  }

  public Entity(Point2D position, Rectangle2D bounds) {
    this.id = createId();
    this.position = new Point2D.Double(position.getX(), position.getY());
    this.bounds =
        new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
  }

  public long getId() {
    return id;
  }

  public Point2D getPosition() {
    return position;
  }

  public Rectangle2D getBounds() {
    return bounds;
  }

  private int findComponent(Class<?> type) {
    int i = 0;
    for (var component : components) {
      if (type.isInstance(component)) {
        return i;
      }
      ++i;
    }
    return -1;
  }

  public <T extends Record & Component> Optional<@NonNull T> getComponent(Class<T> type) {
    var index = findComponent(type);
    if (index < 0) {
      return Optional.empty();
    }
    return Optional.of(type.cast(components.get(index)));
  }

  public <T extends Record & Component> void setComponent(T component) {
    var index = findComponent(component.getClass());
    if (index < 0) {
      components.add(component);
    } else {
      components.set(index, component);
    }
  }

  public <T extends Record & Component> void removeComponent(Class<T> type) {
    var index = findComponent(type);
    if (index >= 0) {
      components.remove(index);
    }
  }
}
