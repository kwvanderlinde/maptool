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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.model;

import javax.annotation.Nonnull;

/**
 * References a part of a {@link Zone} along with the {@code Zone} itself.
 *
 * <p>There are plenty of operations that can't be done without knowing the parent zone. Bundling
 * them together makes it easy to pass the pair around where needed.
 *
 * @param zone The zone containing {@code token}.
 * @param entity The referenced entity.
 */
public record ZoneEntity<T>(@Nonnull Zone zone, @Nonnull T entity) {}
