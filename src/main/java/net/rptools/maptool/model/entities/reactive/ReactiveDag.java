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
package net.rptools.maptool.model.entities.reactive;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class ReactiveDag {
  /**
   * Incremented for any direct value change to a node.
   *
   * <p>Only source nodes can be directly updated, so the version can also be seen as the number of
   * external modifications to the graph.
   *
   * <p>Wraparound is not a concern. Even if we increment every clock cycle on a 6 GHz processor,
   * this will take almost 100 years to overflow.
   */
  private long version = Long.MIN_VALUE + 1;

  private final List<Node<?>> nodes = new CopyOnWriteArrayList<>();

  /**
   * Merge the contents and state of {@code other} into {@code this},
   *
   * @implNote Afterward, both {@code this} will contain all the nodes from {@code other}, and
   *     {@code other} will be empty (unless {@code other == this}, in which case this is a no-op).
   * @param other The DAG to merge into this DAG.
   */
  private void mergeFrom(ReactiveDag other) {
    if (other == this) {
      // Already merged.
      return;
    }

    this.version = Math.max(this.version, other.version);
    // The nodes from `other` must now point to `this`.
    for (var node : other.nodes) {
      node.scope = this;
    }
    // Allow discovering all those nodes as well.
    this.nodes.addAll(other.nodes);

    // Now reset `other`.
    other.version = Long.MIN_VALUE + 1;
    other.nodes.clear();
  }

  private void bumpVersion() {
    ++this.version;
  }

  private long getVersion() {
    return version;
  }

  private <T, NodeT extends Node<T>> NodeT adopt(NodeT node) {
    nodes.add(node);
    return node;
  }

  public <T> SourceNode<T> source(T initialValue) {
    return adopt(new SourceNode<>(this, initialValue));
  }

  public <T> NonSourceNode<T> constant(T value) {
    return adopt(new ConstantNode<>(this, value));
  }

  public <T1, U> NonSourceNode<U> map(Node<T1> n1, Func1<T1, U> func) {
    mergeFrom(n1.getScope());

    return adopt(new TransformNode<>(this, List.of(n1), () -> func.apply(n1.get())));
  }

  public <T1, T2, U> NonSourceNode<U> map(Node<T1> n1, Node<T2> n2, Func2<T1, T2, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());

    return adopt(new TransformNode<>(this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get())));
  }

  public <T1, T2, T3, U> NonSourceNode<U> map(
      Node<T1> n1, Node<T2> n2, Node<T3> n3, Func3<T1, T2, T3, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());

    return adopt(
        new TransformNode<>(this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get())));
  }

  public <T1, T2, T3, T4, U> NonSourceNode<U> map(
      Node<T1> n1, Node<T2> n2, Node<T3> n3, Node<T4> n4, Func4<T1, T2, T3, T4, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());
    mergeFrom(n4.getScope());

    return adopt(
        new TransformNode<>(
            this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get(), n4.get())));
  }

  public <T1, T2, T3, T4, T5, U> NonSourceNode<U> map(
      Node<T1> n1,
      Node<T2> n2,
      Node<T3> n3,
      Node<T4> n4,
      Node<T5> n5,
      Func5<T1, T2, T3, T4, T5, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());
    mergeFrom(n4.getScope());
    mergeFrom(n5.getScope());

    return adopt(
        new TransformNode<>(
            this,
            List.of(n1, n2),
            () -> func.apply(n1.get(), n2.get(), n3.get(), n4.get(), n5.get())));
  }

  public <T> NonSourceNode<T> flatten(Node<? extends Node<T>> nested) {
    return adopt(new FlattenNode<>(this, nested));
  }

  // region Reactive node implementations

  public abstract static sealed class Node<T> permits SourceNode, NonSourceNode {
    private ReactiveDag scope;

    protected Node(ReactiveDag scope) {
      this.scope = scope;
    }

    public final ReactiveDag getScope() {
      return scope;
    }

    public final T get() {
      return ensureUpdated(getScope().getVersion());
    }

    // This is a leaky part of the abstraction, so don't use it if you don't have to.
    protected abstract T ensureUpdated(long globalVersion);
  }

  public static final class SourceNode<T> extends Node<T> {
    private T value;

    SourceNode(ReactiveDag scope, T initialValue) {
      super(scope);
      this.value = initialValue;
    }

    public void set(T value) {
      this.value = value;
      getScope().bumpVersion();
    }

    @Override
    public T ensureUpdated(long globalVersion) {
      // Nothing to do: value only updates explicitly in `#set()`.
      return value;
    }
  }

  public abstract static non-sealed class NonSourceNode<T> extends Node<T> {
    /**
     * The latest DAG version against which this node was validated or recomputed.
     *
     * <p>This initial version will never be held by the scope, so always triggers an update on the
     * first read.
     */
    protected long version = Long.MIN_VALUE;

    /** The last computed value of the node. */
    protected T value;

    protected NonSourceNode(ReactiveDag dag) {
      super(dag);
    }
  }

  private static final class ConstantNode<T> extends NonSourceNode<T> {
    ConstantNode(ReactiveDag dag, T initialValue) {
      super(dag);
      this.value = initialValue;
    }

    @Override
    public T ensureUpdated(long globalVersion) {
      // Nothing to do.
      return this.value;
    }
  }

  private static final class TransformNode<T> extends NonSourceNode<T> {
    private final List<Node<?>> dependencies;
    private final Supplier<T> valueSupplier;

    TransformNode(ReactiveDag dag, List<Node<?>> dependencies, Supplier<T> valueSupplier) {
      super(dag);
      this.dependencies = dependencies;
      this.valueSupplier = valueSupplier;
    }

    private T recompute() {
      return valueSupplier.get();
    }

    @Override
    public T ensureUpdated(long globalVersion) {
      if (version < globalVersion) {
        for (var dependency : dependencies) {
          var ignored = dependency.ensureUpdated(globalVersion);
        }

        value = recompute();
        version = globalVersion;
      }

      return value;
    }
  }

  private static final class FlattenNode<T> extends NonSourceNode<T> {
    private final Node<? extends Node<T>> source;

    FlattenNode(ReactiveDag dag, Node<? extends Node<T>> source) {
      super(dag);
      this.source = source;
    }

    @Override
    public T ensureUpdated(long globalVersion) {
      if (version < globalVersion) {
        // The selected dependency may have changed, so apply the selector.
        var selected = source.ensureUpdated(globalVersion);
        // Make sure the selected node is in the same graph.
        getScope().mergeFrom(selected.getScope());
        // Now sure the selected node is current.
        value = selected.ensureUpdated(globalVersion);

        version = globalVersion;
      }

      return value;
    }
  }

  // endregion
}
