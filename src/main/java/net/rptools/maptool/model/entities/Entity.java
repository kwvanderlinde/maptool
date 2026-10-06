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
import java.util.HashMap;
import java.util.Map;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.entities.components.CameraComponent;
import net.rptools.maptool.model.entities.components.Component;
import net.rptools.maptool.model.entities.components.LayoutComponent;
import net.rptools.maptool.model.entities.components.LocalId;
import net.rptools.maptool.model.entities.components.LocalTransformComponent;
import net.rptools.maptool.model.entities.components.ParentComponent;
import net.rptools.maptool.model.entities.components.PlacementComponent;
import net.rptools.maptool.model.entities.components.PogComponent;
import net.rptools.maptool.model.entities.components.WorldTransformComponent;
import net.rptools.maptool.model.entities.reactive.Func1;
import net.rptools.maptool.model.entities.reactive.Func2;
import net.rptools.maptool.model.entities.reactive.Func3;
import net.rptools.maptool.model.entities.reactive.Func4;
import net.rptools.maptool.model.entities.reactive.Func5;
import net.rptools.maptool.model.entities.reactive.Node;
import net.rptools.maptool.model.entities.reactive.NonSourceNode;
import net.rptools.maptool.model.entities.reactive.ReactiveDag;
import org.jspecify.annotations.Nullable;

public class Entity {
  public static Entity spawnCamera() {
    var cameraEntity = new Entity();
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

  public static Entity spawnToken(LayoutComponent layout, PogComponent pog) {
    var entity = new Entity();
    entity.placement.set(
        new PlacementComponent( // Default to top-left being at (0, 0)
            new Point2D.Double(layout.bounds().getWidth() / 2., layout.bounds().getHeight() / 2.),
            0.,
            1.));
    entity.defineSource(LayoutComponent.class, layout);
    // TODO Pog should be downstream of image asset selection.
    entity.defineSource(PogComponent.class, pog);

    return entity;
  }

  private final ReactiveDag dag;
  private final Map<Class<?>, ReactiveDag.SourceNode<?>> sourceComponentMap;
  private final Map<Class<?>, Node<?>> componentMap;

  public final ReactiveDag.SourceNode<LocalId> id;
  public final ReactiveDag.SourceNode<PlacementComponent> placement;
  public final NonSourceNode<LocalTransformComponent> localTransform;
  public final NonSourceNode<WorldTransformComponent> worldTransform;

  // Dynamic dependencies.
  public final ReactiveDag.SourceNode<@Nullable ParentComponent> parent;

  {
    dag = new ReactiveDag();
    sourceComponentMap = new HashMap<>();
    componentMap = new HashMap<>();

    id = defineSource(LocalId.class, new LocalId(GUID.random()));
    parent = defineSource(ParentComponent.class, null);
    // Special transform to fallback to when there is no parent.
    NonSourceNode<WorldTransformComponent> noParentWorldTransform =
        dag.createConstant(new WorldTransformComponent(new AffineTransform()));
    NonSourceNode<WorldTransformComponent> parentWorldTransform =
        dag.flatMap(
            parent,
            parent -> {
              return parent == null ? noParentWorldTransform : parent.parent().worldTransform;
            });

    // The default placement numbrs aren't really meaningful.
    placement =
        defineSource(
            PlacementComponent.class, new PlacementComponent(new Point2D.Double(0., 0.), 0., 1.));

    localTransform =
        derive(
            LocalTransformComponent.class,
            placement,
            placement -> {
              var localTransform = new AffineTransform();
              localTransform.translate(placement.position().getX(), placement.position().getY());
              localTransform.rotate(placement.rotation());
              localTransform.scale(placement.scale(), placement.scale());
              return new LocalTransformComponent(localTransform);
            });
    worldTransform =
        derive(
            WorldTransformComponent.class,
            localTransform,
            parentWorldTransform,
            (localTransform, parentTransform) -> {
              var worldTransform = new AffineTransform(localTransform.transform());

              worldTransform.preConcatenate(parentTransform.transform());

              return new WorldTransformComponent(worldTransform);
            });
  }

  public final <T extends Record & Component> @Nullable T getValue(Class<T> type) {
    var node = get(type);
    if (node == null) {
      return null;
    }
    return node.get();
  }

  private <ValueT extends Record & Component, NodeT extends Node<ValueT>> NodeT register(
      Class<ValueT> type, NodeT node) {
    componentMap.put(type, node);
    return node;
  }

  private <ValueT extends Record & Component, NodeT extends ReactiveDag.SourceNode<ValueT>>
      NodeT registerSource(Class<ValueT> type, NodeT node) {
    sourceComponentMap.put(type, node);
    return register(type, node);
  }

  public final <T extends Record & Component> @Nullable Node<T> get(Class<T> type) {
    return (Node<T>) componentMap.get(type);
  }

  public final <T extends Record & Component> @Nullable ReactiveDag.SourceNode<T> getSource(Class<T> type) {
    return (ReactiveDag.SourceNode<T>) sourceComponentMap.get(type);
  }

  public final <ValueT extends Record & Component> ReactiveDag.SourceNode<ValueT> defineSource(
      Class<ValueT> type, ValueT initial) {
    return registerSource(type, dag.createSource(initial));
  }

  public final <T1, U extends Record & Component> NonSourceNode<U> derive(
      Class<U> type, Node<T1> n1, Func1<T1, U> func) {
    return this.register(type, dag.map(n1, func));
  }

  public final <T1, T2, U extends Record & Component> NonSourceNode<U> derive(
      Class<U> type, Node<T1> n1, Node<T2> n2, Func2<T1, T2, U> func) {
    return this.register(type, dag.map(n1, n2, func));
  }

  public final <T1, T2, T3, U extends Record & Component> NonSourceNode<U> derive(
      Class<U> type, Node<T1> n1, Node<T2> n2, Node<T3> n3, Func3<T1, T2, T3, U> func) {
    return this.register(type, dag.map(n1, n2, n3, func));
  }

  public final <T1, T2, T3, T4, U extends Record & Component> NonSourceNode<U> derive(
      Class<U> type,
      Node<T1> n1,
      Node<T2> n2,
      Node<T3> n3,
      Node<T4> n4,
      Func4<T1, T2, T3, T4, U> func) {
    return this.register(type, dag.map(n1, n2, n3, n4, func));
  }

  public final <T1, T2, T3, T4, T5, U extends Record & Component> NonSourceNode<U> derive(
      Class<U> type,
      Node<T1> n1,
      Node<T2> n2,
      Node<T3> n3,
      Node<T4> n4,
      Node<T5> n5,
      Func5<T1, T2, T3, T4, T5, U> func) {
    return this.register(type, dag.map(n1, n2, n3, n4, n5, func));
  }
}
