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

public class ReactiveFlatMap<T, U> implements NonSourceNode<U> {
  /** The parent DAG to which this node belongs. */
  protected final ReactiveDag scope;

  protected long version = Long.MIN_VALUE;
  protected U value;

  private final Node<T> source;
  private final Func1<T, NonSourceNode<U>> map;

  private NonSourceNode<U> selected;

  ReactiveFlatMap(ReactiveDag scope, Node<T> source, Func1<T, NonSourceNode<U>> map) {
    this.scope = scope;
    this.source = source;
    this.map = map;
  }

  @Override
  public ReactiveDag getScope() {
    return scope;
  }

  @Override
  public List<Node<?>> getParents() {
    return List.of(source, selected);
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
