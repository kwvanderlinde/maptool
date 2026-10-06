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
  /** The core of the {@link ReactiveDag}, which is merely a flyweight. */
  private static final class ReactiveDagImpl {
    /**
     * Incremented for any direct value change to a node.
     *
     * <p>Only source nodes can be directly updated, so the version can also be seen as the number
     * of external modifications to the graph.
     *
     * <p>Wraparound is not a concern. Even if we increment every clock cycle on a 6 GHz processor,
     * this will take almost 100 years to overflow.
     */
    private long version = Long.MIN_VALUE + 1;

    // TODO Do we actually need to know the root nodes?
    private final List<SourceNode<?>> roots = new CopyOnWriteArrayList<>();
  }

  private ReactiveDagImpl impl = new ReactiveDagImpl();

  /**
   * Merge the content and state of {@code other} into {@code this},
   *
   * @implNote Afterward, both {@code this} and {@code other} using the same underlying impl object.
   * @param other The DAG to merge into this DAG.
   */
  private void mergeFrom(ReactiveDag other) {
    if (other.impl == this.impl) {
      // Already merged.
      return;
    }

    // Move all nodes and state from `other` into `this`.
    this.impl.version = Math.max(this.impl.version, other.impl.version);
    this.impl.roots.addAll(other.impl.roots);
    // Reset `other`.
    other.impl.version = Long.MIN_VALUE + 1;
    other.impl.roots.clear();

    other.impl = this.impl;
  }

  private void bumpVersion() {
    ++this.impl.version;
  }

  public long getVersion() {
    return impl.version;
  }

  public <T> SourceNode<T> createSource(T initialValue) {
    return new SourceNode<>(initialValue);
  }

  public <T> NonSourceNode<T> createConstant(T value) {
    return new ConstantNode<>(value);
  }

  public <T1, U> NonSourceNode<U> map(Node<T1> n1, Func1<T1, U> func) {
    mergeFrom(n1.getScope());

    return new TransformNode<>(List.of(n1), () -> func.apply(n1.get()));
  }

  public <T1, T2, U> NonSourceNode<U> map(Node<T1> n1, Node<T2> n2, Func2<T1, T2, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());

    return new TransformNode<>(List.of(n1, n2), () -> func.apply(n1.get(), n2.get()));
  }

  public <T1, T2, T3, U> NonSourceNode<U> map(
      Node<T1> n1, Node<T2> n2, Node<T3> n3, Func3<T1, T2, T3, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());

    return new TransformNode<>(List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get()));
  }

  public <T1, T2, T3, T4, U> NonSourceNode<U> map(
      Node<T1> n1, Node<T2> n2, Node<T3> n3, Node<T4> n4, Func4<T1, T2, T3, T4, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());
    mergeFrom(n4.getScope());

    return new TransformNode<>(
        List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get(), n4.get()));
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

    return new TransformNode<>(
        List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get(), n4.get(), n5.get()));
  }

  public <T, U> NonSourceNode<U> flatMap(Node<T> source, Func1<T, NonSourceNode<U>> map) {
    return flatten(map(source, map));
  }

  public <T> NonSourceNode<T> flatten(Node<? extends Node<T>> nested) {
    return new FlattenNode<>(nested);
  }

  // region Reactive node implementations

  public abstract sealed class Node<T> permits SourceNode, NonSourceNode {
    public final ReactiveDag getScope() {
      return ReactiveDag.this;
    }

    public final T get() {
      return ensureUpdated(getScope().getVersion());
    }

    // This is a leaky part of the abstraction, so don't use it if you don't have to.
    protected abstract T ensureUpdated(long globalVersion);
  }

  public final class SourceNode<T> extends Node<T> {
    private T value;

    SourceNode(T initialValue) {
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

  public abstract non-sealed class NonSourceNode<T> extends Node<T> {
    /**
     * The latest DAG version against which this node was validated or recomputed.
     *
     * <p>This initial version will never be held by the scope, so always triggers an update on the
     * first read.
     */
    protected long version = Long.MIN_VALUE;

    /** The last computed value of the node. */
    protected T value;
  }

  private final class ConstantNode<T> extends NonSourceNode<T> {
    ConstantNode(T initialValue) {
      this.value = initialValue;
    }

    @Override
    public T ensureUpdated(long globalVersion) {
      // Nothing to do.
      return this.value;
    }
  }

  private final class TransformNode<T> extends NonSourceNode<T> {
    private final List<Node<?>> dependencies;
    private final Supplier<T> valueSupplier;

    TransformNode(List<Node<?>> dependencies, Supplier<T> valueSupplier) {
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

  private final class FlattenNode<T> extends NonSourceNode<T> {
    private final Node<? extends Node<T>> source;

    FlattenNode(Node<? extends Node<T>> source) {
      this.source = source;
    }

    @Override
    public T ensureUpdated(long globalVersion) {
      if (version < globalVersion) {
        // The selected dependency may have changed, so apply the selector.
        var selected = source.ensureUpdated(globalVersion);
        // Now sure the selected node is current.
        value = selected.ensureUpdated(globalVersion);

        version = globalVersion;
      }

      return value;
    }
  }

  // endregion
}
