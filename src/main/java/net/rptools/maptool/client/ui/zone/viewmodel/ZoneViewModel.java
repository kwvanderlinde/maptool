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

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ZoneViewModel {
  private final CodeTimer timer;
  private final Zone zone;

  private final Map<GUID, SelectionSet> selectionSetMap = new HashMap<>();

  public ZoneViewModel(CodeTimer timer, Zone zone) {
    this.timer = timer;
    this.zone = zone;
  }


  public void addMoveSelectionSet(String playerId, GUID keyToken, Set<GUID> tokenList) {
    selectionSetMap.put(keyToken, new SelectionSet(zone, playerId, keyToken, tokenList));
  }

  public boolean hasMoveSelectionSetMoved(GUID keyToken, ZonePoint point) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return false;
    }
    Token token = zone.getToken(keyToken);
    int x = point.x - token.getX();
    int y = point.y - token.getY();

    return set.getOffsetX() != x || set.getOffsetY() != y;
  }

  public void setMoveSelectionSetOffset(GUID keyToken, ZonePoint offset, boolean restrictMovement, Runnable onPathfindingComplete) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return;
    }
    Token token = zone.getToken(keyToken);
    set.setOffset(
            offset.x - token.getX(),
            offset.y - token.getY(),
            restrictMovement,
            onPathfindingComplete);
  }

  public void toggleMoveSelectionSetWaypoint(GUID keyToken, ZonePoint location) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return;
    }
    set.toggleWaypoint(location);
  }

  public ZonePoint getLastWaypoint(GUID keyToken) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return null;
    }
    return set.getLastWaypoint();
  }

  public SelectionSet removeMoveSelectionSet(GUID keyToken) {
    SelectionSet set = selectionSetMap.remove(keyToken);
    return set;
  }

  public boolean isTokenMoving(Token token) {
    for (SelectionSet set : selectionSetMap.values()) {
      if (set.contains(token)) {
        return true;
      }
    }
    return false;
  }

  public Set<SelectionSet> getOwnedMovementSet(Player player) {
    Set<SelectionSet> movementSet = new HashSet<>();
    for (SelectionSet selection : selectionSetMap.values()) {
      if (selection.getPlayerId().equals(player.getName())) {
        movementSet.add(selection);
      }
    }
    return movementSet;
  }

  public Set<SelectionSet> getUnOwnedMovementSet(Player player) {
    Set<SelectionSet> movementSet = new HashSet<>();
    for (SelectionSet selection : selectionSetMap.values()) {
      if (!selection.getPlayerId().equals(player.getName())) {
        movementSet.add(selection);
      }
    }
    return movementSet;
  }

}
