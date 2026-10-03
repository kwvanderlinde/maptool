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
import java.util.function.Supplier;

public class ReactiveTransform<T> extends ReactiveNode<T> implements NonSourceNode<T> {
  private final List<Node<?>> dependencies;
  private final Supplier<T> valueSupplier;

  public ReactiveTransform(List<Node<?>> dependencies, Supplier<T> valueSupplier) {
    this(new ReactiveDag(), dependencies, valueSupplier);
  }

  ReactiveTransform(ReactiveDag scope, List<Node<?>> dependencies, Supplier<T> valueSupplier) {
    super(scope);
    this.dependencies = dependencies;
    this.valueSupplier = valueSupplier;
  }

  @Override
  protected T recompute() {
    return valueSupplier.get();
  }

  @Override
  public List<Node<?>> getParents() {
    return dependencies;
  }
}
