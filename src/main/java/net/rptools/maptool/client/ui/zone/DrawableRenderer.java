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
package net.rptools.maptool.client.ui.zone;

import java.awt.Graphics;
import java.awt.Rectangle;
import net.rptools.maptool.client.entities.DrawableSetComponent;

/** */
public interface DrawableRenderer {
  /**
   * Renders the drawables in {@code component}.
   *
   * <p>For performance, the rendering is cached and will only be invalidated if {@link #setDirty()}
   * has been called since the previous call to {@code renderDrawables()} on this renderer.
   *
   * @param g The graphics context to render to.
   * @param component The container of drawble entities to render.
   * @param viewport The bounds of the renderer.
   * @param scale The scale at which the zone is currently displayed.
   */
  void renderDrawables(
      Graphics g, DrawableSetComponent component, Rectangle viewport, double scale);

  /** Invalidates cached render results. */
  void flush();

  /**
   * Marks the renderer as dirty.
   *
   * <p>This causes {@link #flush()} to automatically be called the next time {@link
   * #renderDrawables(Graphics, DrawableSetComponent, Rectangle, double)} is called.
   */
  void setDirty();
}
