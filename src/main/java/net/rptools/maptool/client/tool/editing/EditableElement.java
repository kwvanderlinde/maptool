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
package net.rptools.maptool.client.tool.editing;

import java.util.List;

public interface EditableElement {
  // TODO Should we also allow getting the parent?
  List<? extends EditableElement> getChildren();

  /**
   * Temporary move action.
   *
   * @param dx
   * @param dy
   * @param snap
   */
  void setDisplacement(double dx, double dy, Snap snap);

  /** Finishes the move action, applying the latest displacement for good. */
  void applyMove();

  void delete();
}
