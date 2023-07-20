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
package net.rptools.maptool.model.zones;

import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nonnull;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;

/**
 * Fired when a zone's fog of war (FOW) has changed.
 *
 * @param zone The zone whose FOW has changed.
 * @param isGlobal If true, the zone's global FOW has changed. Otherwise a specific token's FOW has
 *     changed.
 * @param tokens The token's whose FOW has changed. If not empty, {@code isGlobal} must be {@code
 *     false}.
 */
public record FogChanged(Zone zone, boolean isGlobal, @Nonnull Collection<Token> tokens) {
  public static FogChanged global(Zone zone) {
    return new FogChanged(zone, true, Collections.emptyList());
  }

  public static FogChanged forTokens(Zone zone, @Nonnull Collection<Token> tokens) {
    return new FogChanged(zone, false, tokens);
  }
}
