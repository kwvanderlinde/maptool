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

public class ReactiveDagImpl {
  /**
   * Incremented for any direct value change.
   *
   * <p>In practice, only root nodes can be directly updated.
   *
   * <p>Wraparound is not a concern. Even if we increment every clock cycle on a 6 GHz processor,
   * this will take almost 100 years to overflow.
   */
  private long version = Long.MIN_VALUE + 1;

  private final List<SourceNode<?>> roots = new CopyOnWriteArrayList<>();

  public void mergeFromAndEmpty(ReactiveDagImpl other) {
    this.version = Math.max(this.version, other.version);
    this.roots.addAll(other.roots);

    // Reset other
    other.version = Long.MIN_VALUE + 1;
    other.roots.clear();
  }

  public long bumpVersion() {
    return ++version;
  }

  public long getVersion() {
    return version;
  }
}
