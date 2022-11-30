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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;

public class SelectionModel {
  private final @Nonnull Zone zone;
  // TODO Looks like we always push and pop at the front instead of the rear. Why?
  private final @Nonnull List<Set<GUID>> selectedTokenSetHistory = new ArrayList<>();
  private final @Nonnull Set<GUID> selectedTokenSet = new LinkedHashSet<>();
  private final TokenLocationModel tokenLocationModel;

  public SelectionModel(@Nonnull Zone zone, TokenLocationModel tokenLocationModel) {
    this.zone = zone;
    this.tokenLocationModel = tokenLocationModel;
  }

  public @Nonnull Set<GUID> getSelectedTokenSet() {
    // TODO Return unmodifiable?
    return selectedTokenSet;
  }

  public boolean isSelected(GUID tokenId) {
    return selectedTokenSet.contains(tokenId);
  }

  public boolean isSelectable(GUID tokenGuid) {
    if (tokenGuid == null) {
      return false; // doesn't exist
    }
    final var token = zone.getToken(tokenGuid);
    if (token == null) {
      return false; // doesn't exist
    }
    if (!zone.isTokenVisible(token)) {
      // TODO I would rather be explicit about which players we are checking against.
      return AppUtil.playerOwns(token); // can't own or see
    }
    return true;
  }

  public boolean selectTokens(Collection<GUID> tokens) {
    stashSelectedSetInHistory();

    boolean anyAdded = false;
    for (GUID tokenGUID : tokens) {
      if (!isSelectable(tokenGUID)) {
        continue;
      }
      selectedTokenSet.add(tokenGUID);
      anyAdded = true;
    }

    return anyAdded;
  }

  public void deselectTokens(Collection<GUID> tokens) {
    stashSelectedSetInHistory();
    selectedTokenSet.removeAll(tokens);
  }

  public boolean deselectAllTokens() {
    stashSelectedSetInHistory();

    final var changed = !selectedTokenSet.isEmpty();
    selectedTokenSet.clear();
    return changed;
  }

  public void removeTokensFromSelectedSet(Set<GUID> tokens) {
    selectedTokenSet.removeAll(tokens);
  }

  public void stashSelectedSetInHistory() {
    // don't add empty selections to history
    if (selectedTokenSet.size() == 0) {
      return;
    }
    Set<GUID> history = new HashSet<>(selectedTokenSet);

    // TODO If, say, the previous set is identical, don't need to add it again.
    selectedTokenSetHistory.add(0, history);

    // limit the history to a certain size
    if (selectedTokenSetHistory.size() > 20) {
      selectedTokenSetHistory.subList(20, selectedTokenSetHistory.size() - 1).clear();
    }
  }

  /**
   * Reverts the token selection. If the previous selection is empty, keeps reverting until it is
   * non-empty.
   */
  public void undoSelectToken(Zone.Layer activeLayer) {
    selectedTokenSet.clear();
    while (!selectedTokenSetHistory.isEmpty()) {
      selectedTokenSet.addAll(selectedTokenSetHistory.remove(0));

      // user may have deleted some of the tokens that are contained in the selection history.
      // There could also be tokens in another than the current layer which we don't want to go
      // back to.
      // find them and filter them otherwise the selectionSet will have orphaned GUIDs and
      // they will cause NPE
      Set<GUID> invalidTokenSet = new HashSet<>();
      for (final var guid : selectedTokenSet) {
        final var token = zone.getToken(guid);
        if (token == null || token.getLayer() != activeLayer) {
          invalidTokenSet.add(guid);
        }
      }
      selectedTokenSet.removeAll(invalidTokenSet);

      if (!selectedTokenSet.isEmpty()) break;
    }
  }

  public void cycleSelectedToken(Zone.Layer layer, int direction) {
    final var visibleTokens =
        tokenLocationModel
            .getTokensOnLayer(layer)
            .sorted(
                (o1, o2) -> {
                  // Sort by location on screen, top left to bottom right
                  if (o1.getY() < o2.getY()) {
                    return -1;
                  }
                  if (o1.getY() > o2.getY()) {
                    return 1;
                  }
                  return Integer.compare(o1.getX(), o2.getX());
                })
            .collect(Collectors.toCollection(ArrayList::new));

    Set<GUID> selectedTokenSet = getSelectedTokenSet();
    int newSelection = 0;

    if (visibleTokens.size() == 0) {
      return;
    }
    if (selectedTokenSet.size() > 0) {
      // Find the first selected token on the screen
      for (int i = 0; i < visibleTokens.size(); i++) {
        Token token = visibleTokens.get(i);
        if (!isSelectable(token.getId())) {
          continue;
        }
        if (getSelectedTokenSet().contains(token.getId())) {
          newSelection = i;
          break;
        }
      }
      // Pick the next
      newSelection += direction;
    }
    if (newSelection < 0) {
      newSelection = visibleTokens.size() - 1;
    }
    if (newSelection >= visibleTokens.size()) {
      newSelection = 0;
    }

    // Make the selection
    stashSelectedSetInHistory();
    selectedTokenSet.clear();
    selectedTokenSet.add(visibleTokens.get(newSelection).getId());
  }
}
