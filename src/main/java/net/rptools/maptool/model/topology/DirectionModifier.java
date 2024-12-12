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

import net.rptools.maptool.language.I18N;
import net.rptools.maptool.server.proto.TopologyDirectionModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Why isn't DirectionModifer in WallTopology? It is specific to that. Same for
// MovementDirectionModifier.
public enum DirectionModifier {
  SameDirection,
  ReverseDirection,
  ForceBoth,
  Disabled;

  private static final Logger log = LogManager.getLogger(DirectionModifier.class);

  @Override
  public String toString() {
    var key =
        switch (this) {
          case SameDirection -> "DirectionModifier.SameDirection";
          case ReverseDirection -> "DirectionModifier.ReverseDirection";
          case ForceBoth -> "DirectionModifier.ForceBoth";
          case Disabled -> "DirectionModifier.Disabled";
        };
    return I18N.getText(key);
  }

  public TopologyDirectionModifier toDto() {
    return switch (this) {
      case SameDirection -> TopologyDirectionModifier.SameDirection;
      case ReverseDirection -> TopologyDirectionModifier.ReverseDirection;
      case ForceBoth -> TopologyDirectionModifier.ForceBoth;
      case Disabled -> TopologyDirectionModifier.Disabled;
    };
  }

  public static DirectionModifier fromDto(TopologyDirectionModifier modifier) {
    return switch (modifier) {
      case SameDirection -> SameDirection;
      case ReverseDirection -> ReverseDirection;
      case ForceBoth -> ForceBoth;
      case Disabled -> Disabled;
      case UNRECOGNIZED -> {
        log.error("Unrecognized wall direction modifier. Setting to default");
        yield SameDirection;
      }
    };
  }
}
