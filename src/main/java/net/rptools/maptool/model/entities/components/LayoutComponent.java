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

import java.awt.geom.Rectangle2D;

/**
 * Defines an entity is what could be called "model space".
 *
 * <p>In this space, the entity is positioned at (0, 0). Other components, like {@link
 * PlacementComponent} define how to transform the entity, particularly by moving its origin and
 * axis. For most entities, this means the bounds should be symmetrical around (0, 0), but that is
 * not a hard requirement.
 *
 * <p>This allows defining the fundamental layout properties of the entity without worrying about it
 * location or other transformations on the map.
 *
 * @param bounds The bounds of the entity without any transformation applied.
 */
public record LayoutComponent(Rectangle2D bounds) implements Component {}
