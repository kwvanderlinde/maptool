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

public abstract class ReactiveNode<T> {
  /** The parent DAG to which this node belongs. */
  protected final ReactiveDag scope;

  /**
   * The latest scope version against which this node was validated or recomputed.
   *
   * <p>This initial version will never be held by the scope, so always triggers an update on the
   * first read.
   */
  protected long version = Long.MIN_VALUE;

  protected T value;

  ReactiveNode(ReactiveDag scope) {
    this.scope = scope;
  }

  protected abstract T recompute();

  public abstract List<ReactiveNode<?>> getParents();

  public T get() {
    ensureUpdated(scope.getVersion());
    return value;
  }

  protected void ensureUpdated(long globalVersion) {
    if (version >= globalVersion) {
      // Up to date.
      return;
    }

    // TODO Non-recursive iteration would be baller.
    for (var dependency : getParents()) {
      dependency.ensureUpdated(globalVersion);
    }

    value = recompute();
    version = globalVersion;
  }

  public static <T1, U> ReactiveNonSource<U> map(ReactiveNode<T1> n1, Func1<T1, U> func) {
    return n1.scope.map(n1, func);
  }

  public static <T1, T2, U> ReactiveNonSource<U> map(
      ReactiveNode<T1> n1, ReactiveNode<T2> n2, Func2<T1, T2, U> func) {
    return n1.scope.map(n1, n2, func);
  }

  public static <T1, T2, T3, U> ReactiveNonSource<U> map(
      ReactiveNode<T1> n1, ReactiveNode<T2> n2, ReactiveNode<T3> n3, Func3<T1, T2, T3, U> func) {
    return n1.scope.map(n1, n2, n3, func);
  }

  public static <T1, T2, T3, T4, U> ReactiveNonSource<U> map(
      ReactiveNode<T1> n1,
      ReactiveNode<T2> n2,
      ReactiveNode<T3> n3,
      ReactiveNode<T4> n4,
      Func4<T1, T2, T3, T4, U> func) {
    return n1.scope.map(n1, n2, n3, n4, func);
  }

  public static <T1, T2, T3, T4, T5, U> ReactiveNonSource<U> map(
      ReactiveNode<T1> n1,
      ReactiveNode<T2> n2,
      ReactiveNode<T3> n3,
      ReactiveNode<T4> n4,
      ReactiveNode<T5> n5,
      Func5<T1, T2, T3, T4, T5, U> func) {
    return n1.scope.map(n1, n2, n3, n4, n5, func);
  }
}
