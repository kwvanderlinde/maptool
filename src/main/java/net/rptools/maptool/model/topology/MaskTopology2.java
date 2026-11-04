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
package net.rptools.maptool.model.topology;

import java.util.EnumSet;
import net.rptools.maptool.model.Zone;
import org.locationtech.jts.geom.Polygon;

public class MaskTopology2 {
  public static final class Chunk {
    public Polygon polygon;
    public EnumSet<Zone.TopologyType> types;
  }
}
