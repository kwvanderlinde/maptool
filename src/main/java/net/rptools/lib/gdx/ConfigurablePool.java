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
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.lib.gdx;

import com.badlogic.gdx.utils.Pool;

public class ConfigurablePool<T> extends Pool<T> {
  public interface PoolSupplier<T> {
    T get();

    void reset(T object);

    void discard(T object);
  }

  private final PoolSupplier<T> supplier;

  public ConfigurablePool(int initialCapacity, int max, PoolSupplier<T> supplier) {
    super(initialCapacity, max);
    this.supplier = supplier;
  }

  @Override
  protected T newObject() {
    return supplier.get();
  }

  @Override
  protected void reset(T object) {
    supplier.reset(object);
  }

  @Override
  protected void discard(T object) {
    supplier.discard(object);
  }
}
