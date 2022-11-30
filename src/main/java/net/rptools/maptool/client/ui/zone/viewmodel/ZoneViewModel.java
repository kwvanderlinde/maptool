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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneRenderer;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.player.Player;

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
  private @Nonnull PlayerView view;
  private final TopologyModel topologyModel;
  private final MovementModel movementModel;
  private final TokenStackModel tokenStackModel;
  private final TokenLocationModel tokenLocationModel;
  private final SelectionModel selectionModel;
  private final LightingModel lightingModel;

  // TODO Do not depend on zoneRenderer, but encapsulate the necessary bits in a model.
  public ZoneViewModel(CodeTimer timer, @Nonnull Zone zone, @Nonnull ZoneRenderer zoneRenderer) {
    this.timer = timer;
    this.zone = zone;
    view = new PlayerView(MapTool.getPlayer().getEffectiveRole(), Collections.emptyList());
    topologyModel = new TopologyModel(zone);
    movementModel = new MovementModel(zone);
    tokenStackModel = new TokenStackModel();
    tokenLocationModel = new TokenLocationModel(timer, zone, zoneRenderer::getZoneScale);
    selectionModel = new SelectionModel(zone, tokenLocationModel);
    lightingModel = new LightingModel(zone, topologyModel);
  }

  public void update() {
    view = makePlayerView(MapTool.getPlayer().getEffectiveRole(), true);
  }

  public TopologyModel getTopologyModel() {
    return topologyModel;
  }

  public LightingModel getLightingModel() {
    return lightingModel;
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

  public SelectionModel getSelectionModel() {
    return selectionModel;
  }

  public PlayerView getPlayerView() {
    return view;
  }

  public PlayerView makePlayerView() {
    return makePlayerView(MapTool.getPlayer().getEffectiveRole(), true);
  }

  /**
   * The returned {@link PlayerView} contains a list of tokens that includes either all selected
   * tokens that this player owns and that have their <code>HasSight</code> checkbox enabled, or all
   * owned tokens that have <code>HasSight</code> enabled.
   *
   * @param role the player role
   * @param selected whether to get the view of selected tokens, or all owned
   * @return the player view
   */
  public PlayerView makePlayerView(Player.Role role, boolean selected) {
    List<Token> selectedTokens = Collections.emptyList();
    if (selected) {
      selectedTokens =
          selectionModel.getSelectedTokenSet().stream()
              .map(zone::getToken)
              .filter(Objects::nonNull)
              .filter(Token::getHasSight)
              .filter(AppUtil::playerOwns)
              .toList();
    }
    if (selectedTokens.isEmpty()) {
      // if no selected token qualifying for view, use owned tokens or player tokens with sight
      final boolean checkOwnership =
          MapTool.getServerPolicy().isUseIndividualViews() || MapTool.isPersonalServer();
      selectedTokens =
          checkOwnership
              ? zone.getOwnedTokensWithSight(MapTool.getPlayer())
              : zone.getPlayerTokensWithSight();
    }
    return new PlayerView(role, selectedTokens);
  }
}
