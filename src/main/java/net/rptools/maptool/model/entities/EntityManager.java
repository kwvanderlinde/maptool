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
import net.rptools.maptool.model.entities.components.LayoutComponent;
import net.rptools.maptool.model.entities.components.LocalTransformComponent;
import net.rptools.maptool.model.entities.components.PlacementComponent;
import net.rptools.maptool.model.entities.components.PogComponent;
import net.rptools.maptool.model.entities.components.WorldTransformComponent;

public class EntityManager {
  // TODO Z-order management.
  private List<Entity> entities = new ArrayList<>();

  public List<Entity> getAllEntities() {
    return Collections.unmodifiableList(entities);
  }

  private void adopt(Entity entity) {
    // TODO Ensure the entity has a local id, and store it in a lookup map.
    this.entities.add(entity);
  }

  public Entity spawn() {
    var entity = new Entity();
    adopt(entity);
    return entity;
  }

  public Entity spawnToken(LayoutComponent layout, PogComponent pog) {
    var entity = spawn();
    var placementNode =
        entity.defineSource(
            PlacementComponent.class,
            new PlacementComponent(
                // Default to top-left being at (0, 0)
                new Point2D.Double(
                    layout.bounds().getWidth() / 2., layout.bounds().getHeight() / 2.),
                0.,
                1.));
    entity.defineSource(LayoutComponent.class, layout);
    // TODO Pog should be downstream of image asset selection.
    entity.defineSource(PogComponent.class, pog);
    var localTransformNode =
        entity.derive(
            LocalTransformComponent.class,
            placementNode,
            placement -> {
              var localTransform = new AffineTransform();
              localTransform.translate(placement.position().getX(), placement.position().getY());
              localTransform.rotate(placement.rotation());
              localTransform.scale(placement.scale(), placement.scale());
              return new LocalTransformComponent(localTransform);
            });
    entity.derive(
        WorldTransformComponent.class,
        localTransformNode,
        localTransform -> {
          var worldTransform = new AffineTransform(localTransform.transform());

          var parent = entity.parent.get();
          if (parent != null) {
            var parentTransform = parent.parent().getValue(WorldTransformComponent.class);
            if (parentTransform != null) {
              worldTransform.preConcatenate(parentTransform.transform());
            }
          }

          return new WorldTransformComponent(worldTransform);
        });

    // exampleEntity.add(new Trajectory(0.75));
    // entityManager.getParentageApi().setParentTo(exampleEntity, rootEntity);

    return entity;
  }
}
