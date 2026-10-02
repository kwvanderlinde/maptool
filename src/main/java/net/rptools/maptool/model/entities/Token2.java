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
package net.rptools.maptool.model.entities;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import net.rptools.maptool.model.entities.components.LayoutComponent;
import net.rptools.maptool.model.entities.components.PlacementComponent;
import net.rptools.maptool.model.entities.components.PogComponent;
import net.rptools.maptool.model.entities.reactive.ReactiveSource;
import org.jspecify.annotations.Nullable;

public class Token2 {
  public ReactiveSource<PlacementComponent> placement;
  public ReactiveSource<LayoutComponent> layout;
  public ReactiveSource<@Nullable PogComponent> pog;

  {
    placement = new ReactiveSource<>(new PlacementComponent(new Point2D.Double(0, 0), 0., 1.));
    layout = new ReactiveSource<>(new LayoutComponent(new Rectangle2D.Double(0, 0, 0, 0)));
    // TODO Pog should be downstream of image asset selection.
    pog = new ReactiveSource<>(null);
  }
}
