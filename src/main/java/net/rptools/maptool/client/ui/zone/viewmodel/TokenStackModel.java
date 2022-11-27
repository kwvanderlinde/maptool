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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import net.rptools.maptool.model.Token;

public class TokenStackModel {
  private Map<Token, Set<Token>> tokenStackMap = null;

  public TokenStackModel() {}

  public void removeAllStacks() {
    tokenStackMap = null;
  }

  public @Nonnull Iterable<Token> getStackTops() {
    if (tokenStackMap == null) {
      return Collections.emptyList();
    }
    return tokenStackMap.keySet();
  }

  public @Nonnull Set<Token> getStack(Token stackTop) {
    if (tokenStackMap == null) {
      return Collections.emptySet();
    }

    final var stack = tokenStackMap.get(stackTop);
    if (stack == null) {
      return Collections.emptySet();
    }

    return stack;
  }

  public void setTokenAsCovered(Token coveringToken, Token coveredToken) {
    if (tokenStackMap == null) {
      tokenStackMap = new HashMap<>();
    }

    final var coveringTokenStack =
        tokenStackMap.computeIfAbsent(
            coveringToken,
            token -> {
              final var set = new HashSet<Token>();
              set.add(token);
              return set;
            });
    coveringTokenStack.add(coveredToken);

    if (tokenStackMap.containsKey(coveredToken)) {
      // The covered token is no longer top of the stack, so merge the stacks.
      final var stackSet = tokenStackMap.remove(coveredToken);
      coveringTokenStack.addAll(stackSet);
    }
  }
}
