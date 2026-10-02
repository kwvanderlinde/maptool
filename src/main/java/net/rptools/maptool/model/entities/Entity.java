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

import java.util.HashMap;
import java.util.Map;
import net.rptools.maptool.model.entities.components.Component;
import net.rptools.maptool.model.entities.reactive.ReactiveNode;
import org.jspecify.annotations.Nullable;

public abstract class Entity {
  private final Map<Class<?>, ReactiveNode<?>> componentMap = new HashMap<>();

  public final <T extends Record & Component> @Nullable T get(Class<T> type) {
    var node = (ReactiveNode<T>) componentMap.get(type);
    if (node == null) {
      return null;
    }

    return node.get();
  }

  protected final <ValueT extends Record & Component, NodeT extends ReactiveNode<ValueT>>
      NodeT register(NodeT node) {
    componentMap.put(node.get().getClass(), node);
    return node;
  }
}
