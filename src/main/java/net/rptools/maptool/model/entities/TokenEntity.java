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
import net.rptools.maptool.model.entities.components.LayoutComponent;
import net.rptools.maptool.model.entities.components.PlacementComponent;
import net.rptools.maptool.model.entities.components.PogComponent;
import net.rptools.maptool.model.entities.reactive.ReactiveDag;

public final class TokenEntity extends Entity {
  public final ReactiveDag.SourceNode<LayoutComponent> layout;
  public final ReactiveDag.SourceNode<PogComponent> pog;

  public TokenEntity(LayoutComponent layout, PogComponent pog) {
    super();

    this.placement.set(
        new PlacementComponent( // Default to top-left being at (0, 0)
            new Point2D.Double(layout.bounds().getWidth() / 2., layout.bounds().getHeight() / 2.),
            0.,
            1.));
    this.layout = defineSource(LayoutComponent.class, layout);
    // TODO Pog should be downstream of image asset selection.
    this.pog = defineSource(PogComponent.class, pog);
  }
}
