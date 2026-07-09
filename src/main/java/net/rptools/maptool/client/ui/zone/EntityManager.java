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
package net.rptools.maptool.client.ui.zone;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.rptools.maptool.client.entities.Entity;
import net.rptools.maptool.client.entities.ModelEntityId;

public class EntityManager {
  // TODO Deprecate and remove this.
  public enum RenderLayer {
    AboveBoard,
    AboveGrid,
    AboveLights,
    AboveFog,
  }

  private final Map<ModelEntityId, Entity> domainIdsToEcs = new HashMap<>();
  private final Map<Long, Entity> allKnownEntities = new HashMap<>();
  private final Entity board;
  private final Entity map;
  private final Entity grid;
  private final Entity backgroundDrawables;
  private final Entity objectDrawables;
  private final Entity gmDrawables;
  private final Entity tokenDrawables;

  public EntityManager() {
    board = new Entity(new Point2D.Double(0, 0));
    map = new Entity(new Point2D.Double(0, 0));
    grid = new Entity(new Point2D.Double(0, 0));
    backgroundDrawables = new Entity(new Point2D.Double(0, 0));
    objectDrawables = new Entity(new Point2D.Double(0, 0));
    gmDrawables = new Entity(new Point2D.Double(0, 0));
    tokenDrawables = new Entity(new Point2D.Double(0, 0));
  }

  private Entity createEntity() {
    var entity = new Entity(new Point2D.Double(0, 0));
    allKnownEntities.put(entity.getId(), entity);
    return entity;
  }

  public Entity ensureEntityFor(ModelEntityId domainId) {
    return domainIdsToEcs.computeIfAbsent(domainId, id -> createEntity());
  }

  public @Nullable Entity destroyEntity(ModelEntityId domainId) {
    var entity = domainIdsToEcs.remove(domainId);
    if (entity != null) {
      allKnownEntities.remove(entity.getId());
    }
    return entity;
  }

  public Entity getBoardEntity() {
    return board;
  }

  public Entity getMapEntity() {
    return map;
  }

  public Entity getGridEntity() {
    return grid;
  }

  public Entity getBackgroundDrawables() {
    return backgroundDrawables;
  }

  public Entity getObjectDrawables() {
    return objectDrawables;
  }

  public Entity getGmDrawables() {
    return gmDrawables;
  }

  public Entity getTokenDrawables() {
    return tokenDrawables;
  }
}
