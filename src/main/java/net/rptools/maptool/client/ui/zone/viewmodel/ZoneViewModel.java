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
package net.rptools.maptool.client.ui.zone.viewmodel;

import javax.annotation.Nonnull;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.ui.zone.ZoneRenderer;
import net.rptools.maptool.model.Zone;

/**
 * Represents the user-facing aspects of a zone beyond the domain model.
 *
 * <p>This class is responsible for such things as being the source of truth on the exposed area,
 * which tokens are visible, which tokens are stacked, etc. It does not deal with any rendering
 * itself.
 */
// TODO Rename for clarity
public class ZoneViewModel {
  private final CodeTimer timer;
  private final @Nonnull Zone zone;
  private final MovementModel movementModel;
  private final TokenStackModel tokenStackModel;
  private final TokenLocationModel tokenLocationModel;

  // TODO Do not depend on zoneRenderer, but encapsulate the necessary bits in a model.
  public ZoneViewModel(CodeTimer timer, @Nonnull Zone zone, @Nonnull ZoneRenderer zoneRenderer) {
    this.timer = timer;
    this.zone = zone;
    movementModel = new MovementModel(zone);
    tokenStackModel = new TokenStackModel();
    tokenLocationModel = new TokenLocationModel(timer, zone, zoneRenderer::getZoneScale);
  }

  public void update() {
  }

  public MovementModel getMovementModel() {
    return movementModel;
  }

  public TokenStackModel getTokenStackModel() {
    return tokenStackModel;
  }

  public TokenLocationModel getTokenLocationModel() {
    return tokenLocationModel;
  }
}
