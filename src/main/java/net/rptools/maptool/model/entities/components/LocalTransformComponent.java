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
package net.rptools.maptool.model.entities.components;

import com.badlogic.ashley.core.Component;
import java.awt.geom.AffineTransform;

/**
 * The transformation required to place an entity relative to its parent.
 *
 * <p>The transform is whatever it takes to convert from a normalized box ([-0.5, 0.5] x [0.5, 0.5])
 * the final position.
 *
 * @param transform
 */
public record LocalTransformComponent(AffineTransform transform) implements Component {}
