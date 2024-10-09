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
package net.rptools.maptool.client.ui.zone.renderer;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;
import net.rptools.maptool.client.ui.token.AbstractTokenOverlay;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.TokenFootprint;
import org.javatuples.Pair;

// TODO This should obviate the need for:
// - tokenLocationCache
// - tokenImageMap
public record TokenRenderInstruction(
    Token token,
    boolean clipIt,
    TokenFootprint footprint,
    TokenLocation location,
    Rectangle bounds,
    BufferedImage image,
    float opacity,
    Rectangle boundsForStatesAndBars,
    List<Pair<Object, AbstractTokenOverlay>> stateAndBarOverlays,
    @Nullable Path<?> path) {}
