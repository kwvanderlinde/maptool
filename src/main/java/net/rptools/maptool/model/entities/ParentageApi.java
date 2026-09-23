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

  public void setParentTo(@NonNull Entity child, @NonNull Entity parent) {
    // In case the child is already parented...
    removeParentFrom(child);

    child.add(new ParentComponent(parent));

    // Make sure the parent entity has a child buffer ready to go.
    var buffer = childMapper.get(parent);
    if (buffer == null) {
      buffer = new ChildBufferComponent();
      parent.add(buffer);
    }

    // Make sure the parent knows about the child.
    if (!buffer.children.contains(child, true)) {
      buffer.children.add(child);
    }
  }

  public void removeParentFrom(@NonNull Entity child) {
    var parentComponent = parentMapper.get(child);
    if (parentComponent == null) {
      // No parentage.
      return;
    }
    var parentEntity = parentComponent.parent();

    // Unregister the child from the parent.
    var buffer = childMapper.get(parentEntity);
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
