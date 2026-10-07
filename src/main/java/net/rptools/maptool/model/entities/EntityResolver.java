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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.entities.reactive.ReactiveDag;
import org.jspecify.annotations.Nullable;

/** A reactive mapping from entity local IDs to entities. */
public class EntityResolver {
  private final ReactiveDag.SourceNode<Long> versionPumper;
  private final Map<GUID, Entity> knownEntities = new HashMap<>();

  public EntityResolver() {
    this(new ReactiveDag());
  }

  public EntityResolver(ReactiveDag dag) {
    this.versionPumper = dag.source(Long.MIN_VALUE);
  }

  private void updated() {
    this.versionPumper.mutate(l -> l + 1);
  }

  public ReactiveDag.SourceNode<Long> getVersion() {
    return versionPumper;
  }

  public List<Entity> getAll() {
    return new ArrayList<>(knownEntities.values());
  }

  public Optional<Entity> resolve(GUID localId) {
    return Optional.ofNullable(knownEntities.get(localId));
  }

  public @Nullable Entity add(Entity entity) {
    var oldEntity = knownEntities.put(entity.id, entity);
    if (oldEntity != entity) {
      updated();
    }
    return oldEntity;
  }

  public @Nullable Entity remove(GUID localId) {
    var entity = knownEntities.remove(localId);
    if (entity != null) {
      updated();
    }
    return entity;
  }
}
