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

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.TokenFootprint;

public record MovementRenderInstruction(
    Token token,
    // TODO Possibly pair up footprint and path. Is the same true for TokenRenderInstruction?
    TokenFootprint footprint,
    Rectangle bounds,
    // TODO showBlockedMoves() suggests nullability, but surely the only point is to have a path.
    //  Perhaps if the path is not ready, yet?
    //  Oh, if not the key token.
    @Nullable Path<?> path,
    BufferedImage image,
    @Nullable Double distanceTravaledToShow,
    @Nullable String playerName) {}
