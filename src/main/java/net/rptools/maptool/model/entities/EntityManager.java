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
package net.rptools.maptool.model.entities;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import java.util.HashMap;
import java.util.Map;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.entities.components.LocalId;

public class EntityManager {
  private final Engine world;

  /** Invariant: if an entity exists in `world`, it must also exist in this map. */
  private final Map<GUID, Entity> entitiesByLocalId;

  /** The parent of all entities. */
  private final Entity mapEntity;

  public EntityManager() {
    this.world = new Engine();
    this.entitiesByLocalId = new HashMap<>();

    // The map entity gets a special ID for easy recognition.
    this.mapEntity = new Entity();
    var mapEntityId = GUID.zero();
    this.entitiesByLocalId.put(mapEntityId, mapEntity);
    this.world.addEntity(mapEntity);
  }

  private GUID claimLocalId() {
    GUID entityId = GUID.random();

    /*
     * In most cases we will have no issue with duplicate IDs. But there is a small chance of
     * collision. So if we do collide, try again (up to some reasonable limit).
     */
    int iterationCount = 0;
    while (entitiesByLocalId.containsKey(entityId)) {
      if (++iterationCount >= 10_000) {
        throw new RuntimeException("Unable to generate a unique entity ID");
      }

      entityId = GUID.random();
    }

    return entityId;
  }

  public Entity getMapEntity() {
    return mapEntity;
  }

  public Entity getByLocalId(GUID id) {
    return entitiesByLocalId.get(id);
  }

  public Entity spawn() {
    var entityId = claimLocalId();

    var entity = new Entity();
    entity.add(new LocalId(entityId));
    entitiesByLocalId.put(entityId, entity);
    world.addEntity(entity);

    return entity;
  }

  public void addEntity(Entity entity) {
    var idComp = entity.getComponent(LocalId.class);
    if (idComp == null) {
      throw new IllegalArgumentException("Entity has no LocalId");
    }

    if (entitiesByLocalId.putIfAbsent(idComp.id(), entity) != null) {
      throw new IllegalArgumentException("Duplicate LocalID: " + idComp.id());
    }

    world.addEntity(entity);
  }

  public void destroy(Entity entity) {
    var localId = entity.getComponent(LocalId.class);
    if (localId == null) {
      // We don't recognize this entity.
      return;
    }

    world.removeEntity(entity);
    entitiesByLocalId.remove(localId.id());
  }
}
