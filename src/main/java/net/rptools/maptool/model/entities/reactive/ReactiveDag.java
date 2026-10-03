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

public class ReactiveDag {
  private ReactiveDagImpl impl = new ReactiveDagImpl();

  /**
   * Merge the contenxt of {@code other} into {@code this}, and make them point to the same impl.
   *
   * @param other The DAG to merge into this DAG.
   */
  private void mergeFrom(ReactiveDag other) {
    if (other == null) {
      final var i = 0; // trap
      return;
    }
    if (other.impl == this.impl) {
      // Already merged.
      return;
    }

    impl.mergeFromAndEmpty(other.impl);
    other.impl = this.impl;
  }

  public long bumpVersion() {
    return this.impl.bumpVersion();
  }

  public long getVersion() {
    return impl.getVersion();
  }

  public <T> ReactiveSource<T> createRoot(T initialValue) {
    return new ReactiveSource<>(this, initialValue);
  }

  public <T1, U> NonSourceNode<U> map(Node<T1> n1, Func1<T1, U> func) {
    mergeFrom(n1.getScope());

    return new ReactiveTransform<>(this, List.of(n1), () -> func.apply(n1.get()));
  }

  public <T1, T2, U> NonSourceNode<U> map(Node<T1> n1, Node<T2> n2, Func2<T1, T2, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());

    return new ReactiveTransform<>(this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get()));
  }

  public <T1, T2, T3, U> NonSourceNode<U> map(
      Node<T1> n1, Node<T2> n2, Node<T3> n3, Func3<T1, T2, T3, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());

    return new ReactiveTransform<>(
        this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get()));
  }

  public <T1, T2, T3, T4, U> NonSourceNode<U> map(
      Node<T1> n1, Node<T2> n2, Node<T3> n3, Node<T4> n4, Func4<T1, T2, T3, T4, U> func) {
    mergeFrom(n1.getScope());
    mergeFrom(n2.getScope());
    mergeFrom(n3.getScope());
    mergeFrom(n4.getScope());

    return new ReactiveTransform<>(
        this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get(), n4.get()));
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

    return new ReactiveTransform<>(
        this, List.of(n1, n2), () -> func.apply(n1.get(), n2.get(), n3.get(), n4.get(), n5.get()));
  }

  public <T, U> NonSourceNode<U> flatMap(Node<T> source, Func1<T, NonSourceNode<U>> map) {
    mergeFrom(source.getScope());

    return new ReactiveFlatMap<>(this, source, map);
  }
}
