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
import net.rptools.maptool.server.proto.TopologyMovementDirectionModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Like {@link DirectionModifier} but only allowing forcing both direction or being disabled.
 *
 * <p>This is since movement blocking does not yet support directional walls. This enum allows
 * movement blocking to be disabled just as for sights, lights, and auras while accepting the more
 * limited functionality.
 *
 * <p>If movement blocking does at some point support the same options, this type should be merged
 * with {@link DirectionModifier}.
 */
public enum MovementDirectionModifier {
  ForceBoth,
  Disabled;

  private static final Logger log = LogManager.getLogger(MovementDirectionModifier.class);

  @Override
  public String toString() {
    var key =
        switch (this) {
          case ForceBoth -> "DirectionModifier.ForceBoth";
          case Disabled -> "DirectionModifier.Disabled";
        };
    return I18N.getText(key);
  }

  public TopologyMovementDirectionModifier toDto() {
    return switch (this) {
      case ForceBoth -> TopologyMovementDirectionModifier.MovementForceBoth;
      case Disabled -> TopologyMovementDirectionModifier.MovementDisabled;
    };
  }

  public static MovementDirectionModifier fromDto(TopologyMovementDirectionModifier modifier) {
    return switch (modifier) {
      case MovementForceBoth -> ForceBoth;
      case MovementDisabled -> Disabled;
      case UNRECOGNIZED -> {
        log.error("Unrecognized wall direction modifier. Setting to default");
        yield ForceBoth;
      }
    };
  }
}
