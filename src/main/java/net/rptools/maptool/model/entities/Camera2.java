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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import net.rptools.maptool.model.entities.components.CameraComponent;
import net.rptools.maptool.model.entities.components.PlacementComponent;
import net.rptools.maptool.model.entities.reactive.ReactiveNonSource;
import net.rptools.maptool.model.entities.reactive.ReactiveSource;

public class Camera2 extends Entity {
  public ReactiveSource<PlacementComponent> placement;
  public ReactiveNonSource<CameraComponent> camera;

  {
    placement =
        defineSource(
            PlacementComponent.class, new PlacementComponent(new Point2D.Double(0, 0), 0., 1.));
    camera =
        derive(
            CameraComponent.class,
            placement,
            placement -> {
              var transform = new AffineTransform();
              transform.rotate(-placement.rotation());
              transform.scale(1. / placement.scale(), 1. / placement.scale());
              transform.translate(-placement.position().getX(), -placement.position().getY());
              return new CameraComponent(transform);
            });
  }
}
