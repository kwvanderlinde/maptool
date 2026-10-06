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

  public long bumpVersion() {
    return ++this.impl.version;
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
    mergeFrom(source.getScope());

    return new FlatMapNode<>(this, source, map);
  }

  // region Reactive node implementations

  // TODO Internally to ReactiveDag, assume all `Node<T>` are actually AbstractNode<T>.
  // TODO I guess we don't really need the Node<T> interface then, do we?

  public final class SourceNode<T> implements Node<T> {
    private T value;

    SourceNode(T initialValue) {
      this.value = initialValue;
    }

    @Override
    public ReactiveDag getScope() {
      return ReactiveDag.this;
    }

    @Override
    public T get() {
      return this.value;
    }

    public void set(T value) {
      this.value = value;
      getScope().bumpVersion();
    }

    @Override
    public void ensureUpdated(long globalVersion) {
      // Nothing to do except bump the version.
    }
  }

  public abstract class NonSourceNode<T> implements Node<T> {
    /**
     * The latest DAG version against which this node was validated or recomputed.
     *
     * <p>This initial version will never be held by the scope, so always triggers an update on the
     * first read.
     */
    protected long version = Long.MIN_VALUE;

    protected T value;

    @Override
    public ReactiveDag getScope() {
      return ReactiveDag.this;
    }
  }

  private final class ConstantNode<T> extends NonSourceNode<T> {
    private final T initialValue;

    ConstantNode(T initialValue) {
      this.initialValue = initialValue;
    }

    @Override
    public T get() {
      return initialValue;
    }

    @Override
    public void ensureUpdated(long globalVersion) {
      // Nothing to do.
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
    public T get() {
      ensureUpdated(getScope().getVersion());
      return value;
    }

    @Override
    public void ensureUpdated(long globalVersion) {
      if (version >= globalVersion) {
        // Up to date.
        return;
      }

      // TODO Non-recursive iteration would be baller.
      for (var dependency : dependencies) {
        dependency.ensureUpdated(globalVersion);
      }

      value = recompute();
      version = globalVersion;
    }
  }

  private final class FlatMapNode<T, U> extends NonSourceNode<U> {
    /** The parent DAG to which this node belongs. */
    private final ReactiveDag scope;

    /**
     * The latest DAG version against which this node was validated or recomputed.
     *
     * <p>This initial version will never be held by the scope, so always triggers an update on the
     * first read.
     */
    private long version = Long.MIN_VALUE;

    private U value;

    private final Node<T> source;
    private final Func1<T, NonSourceNode<U>> map;

    private NonSourceNode<U> selected;

    FlatMapNode(ReactiveDag scope, Node<T> source, Func1<T, NonSourceNode<U>> map) {
      this.scope = scope;
      this.source = source;
      this.map = map;
    }

    @Override
    public U get() {
      ensureUpdated(scope.getVersion());
      return value;
    }

    @Override
    public void ensureUpdated(long globalVersion) {
      if (version >= globalVersion) {
        return;
      }

      // First ensure the selector itself is current.
      source.ensureUpdated(globalVersion);
      T input = source.get();

      // The selected dependency may have changed.
      selected = map.apply(input);

      // Now ensure the selected node is current.
      selected.ensureUpdated(globalVersion);
      value = selected.get();

      version = globalVersion;
    }
  }

  // endregion
}
