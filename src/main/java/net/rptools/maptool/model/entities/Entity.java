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
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.entities.components.Component;
import net.rptools.maptool.model.entities.components.LocalId;
import net.rptools.maptool.model.entities.components.ParentComponent;
import net.rptools.maptool.model.entities.reactive.Func1;
import net.rptools.maptool.model.entities.reactive.Func2;
import net.rptools.maptool.model.entities.reactive.Func3;
import net.rptools.maptool.model.entities.reactive.Func4;
import net.rptools.maptool.model.entities.reactive.Func5;
import net.rptools.maptool.model.entities.reactive.ReactiveNode;
import net.rptools.maptool.model.entities.reactive.ReactiveNonSource;
import net.rptools.maptool.model.entities.reactive.ReactiveSource;
import org.jspecify.annotations.Nullable;

public class Entity {
  private final Map<Class<?>, ReactiveSource<?>> sourceComponentMap;
  private final Map<Class<?>, ReactiveNode<?>> componentMap;

  public final ReactiveSource<LocalId> id;
  public final ReactiveSource<@Nullable ParentComponent> parent;

  {
    sourceComponentMap = new HashMap<>();
    componentMap = new HashMap<>();

    id = defineSource(LocalId.class, new LocalId(GUID.random()));
    parent = defineSource(ParentComponent.class, null);
  }

  public final <T extends Record & Component> @Nullable T getValue(Class<T> type) {
    var node = get(type);
    if (node == null) {
      return null;
    }
    return node.get();
  }

  private <ValueT extends Record & Component, NodeT extends ReactiveNode<ValueT>> NodeT register(
      Class<ValueT> type, NodeT node) {
    componentMap.put(type, node);
    return node;
  }

  private <ValueT extends Record & Component, NodeT extends ReactiveSource<ValueT>>
      NodeT registerSource(Class<ValueT> type, NodeT node) {
    sourceComponentMap.put(type, node);
    return register(type, node);
  }

  public final <T extends Record & Component> @Nullable ReactiveNode<T> get(Class<T> type) {
    return (ReactiveNode<T>) componentMap.get(type);
  }

  public final <T extends Record & Component> @Nullable ReactiveSource<T> getSource(Class<T> type) {
    return (ReactiveSource<T>) sourceComponentMap.get(type);
  }

  public final <ValueT extends Record & Component> ReactiveSource<ValueT> defineSource(
      Class<ValueT> type, ValueT initial) {
    return registerSource(type, new ReactiveSource<>(initial));
  }

  public final <T1, U extends Record & Component> ReactiveNonSource<U> derive(
      Class<U> type, ReactiveNode<T1> n1, Func1<T1, U> func) {
    return this.register(type, n1.getScope().map(n1, func));
  }

  public final <T1, T2, U extends Record & Component> ReactiveNonSource<U> derive(
      Class<U> type, ReactiveNode<T1> n1, ReactiveNode<T2> n2, Func2<T1, T2, U> func) {
    return this.register(type, n1.getScope().map(n1, n2, func));
  }

  public final <T1, T2, T3, U extends Record & Component> ReactiveNonSource<U> derive(
      Class<U> type,
      ReactiveNode<T1> n1,
      ReactiveNode<T2> n2,
      ReactiveNode<T3> n3,
      Func3<T1, T2, T3, U> func) {
    return this.register(type, n1.getScope().map(n1, n2, n3, func));
  }

  public final <T1, T2, T3, T4, U extends Record & Component> ReactiveNonSource<U> derive(
      Class<U> type,
      ReactiveNode<T1> n1,
      ReactiveNode<T2> n2,
      ReactiveNode<T3> n3,
      ReactiveNode<T4> n4,
      Func4<T1, T2, T3, T4, U> func) {
    return this.register(type, n1.getScope().map(n1, n2, n3, n4, func));
  }

  public final <T1, T2, T3, T4, T5, U extends Record & Component> ReactiveNonSource<U> derive(
      Class<U> type,
      ReactiveNode<T1> n1,
      ReactiveNode<T2> n2,
      ReactiveNode<T3> n3,
      ReactiveNode<T4> n4,
      ReactiveNode<T5> n5,
      Func5<T1, T2, T3, T4, T5, U> func) {
    return this.register(type, n1.getScope().map(n1, n2, n3, n4, n5, func));
  }
}
