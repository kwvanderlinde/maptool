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

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import net.rptools.maptool.model.entities.components.ChildBufferComponent;
import net.rptools.maptool.model.entities.components.ParentComponent;
import org.jspecify.annotations.NonNull;

public class ParentageApi implements Api {
  private final ComponentMapper<ParentComponent> parentMapper =
      ComponentMapper.getFor(ParentComponent.class);
  private final ComponentMapper<ChildBufferComponent> childMapper =
      ComponentMapper.getFor(ChildBufferComponent.class);

  public Entity getParent(@NonNull Entity entity) {
    var parentComponent = parentMapper.get(entity);
    return parentComponent == null ? null : parentComponent.parent();
  }

  /**
   * Checks if {@code possibleAncestor} is an ancestor of {@code child} (including if they are the
   * same).
   *
   * @param entity
   * @param possibleAncestor
   * @return
   */
  public boolean isAncestorOf(@NonNull Entity possibleAncestor, @NonNull Entity entity) {
    var current = entity;
    while (current != null) {
      if (current == possibleAncestor) {
        return true;
      }

      current = getParent(current);
    }

    return false;
  }

  public void setParentTo(@NonNull Entity child, @NonNull Entity newParent) {
    // First, ensure we have no cycles.
    if (isAncestorOf(child, newParent)) {
      throw new IllegalArgumentException("Parent change would create a cycle.");
    }

    // In case the child is already parented, we need to update the original parent.
    removeParentFrom(child);

    // Now we can properly add the new parent.
    child.add(new ParentComponent(newParent));

    // Make sure the parent entity has a child buffer ready to go.
    var buffer = childMapper.get(newParent);
    if (buffer == null) {
      buffer = new ChildBufferComponent();
      newParent.add(buffer);
    }

    // Make sure the parent knows about the child.
    if (!buffer.children.contains(child, true)) {
      buffer.children.add(child);
    }
  }

  public void removeParentFrom(@NonNull Entity child) {
    var parent = getParent(child);
    if (parent == null) {
      // No parentage.
      return;
    }

    // Unregister the child from the parent.
    var buffer = childMapper.get(parent);
    if (buffer != null) {
      buffer.children.removeValue(child, true);
    }
  }

  @Override
  public void afterSpawn(@NonNull Entity entity) {}

  @Override
  public void beforeDestroy(@NonNull Entity entity) {
    // Reparent any of entity's children under entity's parent, if there is one.
    var parentComponent = entity.remove(ParentComponent.class);
    var buffer = entity.remove(ChildBufferComponent.class);

    if (buffer != null) {
      for (var child : buffer.children) {
        if (parentComponent == null) {
          child.remove(ParentComponent.class);
        } else {
          setParentTo(child, parentComponent.parent());
        }
      }
    }
  }
}
