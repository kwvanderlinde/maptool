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
package net.rptools.maptool.client.events;

import net.rptools.maptool.client.MapToolClient;

/**
 * Event raised whenever the local client is disconnected from a server (local or remote).
 *
 * @param client The client that disconnected.
 * @param expected If {@code true}, the disconnection was at the request of the user and does
 *     indicate an error.
 */
public record LocalClientDisconnected(MapToolClient client, boolean expected) {}
