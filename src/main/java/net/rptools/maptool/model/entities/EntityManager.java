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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.model.entities;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.entities.components.CameraComponent;
import net.rptools.maptool.model.entities.components.PlacementComponent;

public class EntityManager {
  // TODO Z-order management.
  private final List<Entity> entities = new ArrayList<>();
  private final EntityResolver entityResolver = new EntityResolver();

  public EntityResolver getEntityResolver() {
    return entityResolver;
  }

  public List<Entity> getAllEntities() {
    return Collections.unmodifiableList(entities);
  }

  public void adopt(Entity entity) {
    entity.entityResolver.set(entityResolver);
    entityResolver.add(entity);

    this.entities.add(entity);
  }

  public void disavow(GUID id) {
    var entity = entityResolver.remove(id);
    if (entity != null) {
      entity.entityResolver.set(new EntityResolver());
      entities.remove(entity);
    }
  }

  public Entity spawn() {
    var entity = new Entity();
    adopt(entity);
    return entity;
  }

  public Entity spawnCamera() {
    var cameraEntity = spawn();
    cameraEntity.placement.set(new PlacementComponent(new Point2D.Double(0, 0), 0., 1.));
    cameraEntity.derive(
        CameraComponent.class,
        cameraEntity.placement,
        placement -> {
          var transform = new AffineTransform();
          transform.rotate(-placement.rotation());
          transform.scale(1. / placement.scale(), 1. / placement.scale());
          transform.translate(-placement.position().getX(), -placement.position().getY());
          return new CameraComponent(transform);
        });
    return cameraEntity;
  }
}
