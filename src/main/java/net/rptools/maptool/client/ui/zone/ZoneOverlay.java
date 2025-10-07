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

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSetBuilder;

/** */
public interface ZoneOverlay {

  public default void paintOverlay(ZoneRenderer renderer, Graphics2D g) {}

  public default void compositeOverlay(InstructionSetBuilder builder, Rectangle2D bounds) {
    compositeOverlay(builder);
  }

  public default void compositeOverlay(InstructionSetBuilder builder) {}
}
