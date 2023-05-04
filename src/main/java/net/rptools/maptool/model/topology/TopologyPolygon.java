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
package net.rptools.maptool.model.topology;

import org.locationtech.jts.geom.Polygon;

/**
 * Represents a single piece of topology.
 *
 * <p>This is the most fundamental piece of topology, a polygon with polygonal holes.
 */
// TODO Use a converter so that we can serialize this as an array of arrays of points rather than
//  being tied to JTS.
public final class TopologyPolygon implements TopologyPrimitive {
  private Polygon polygon;
}
