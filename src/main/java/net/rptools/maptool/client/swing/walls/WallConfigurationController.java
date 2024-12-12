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
package net.rptools.maptool.client.swing.walls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JComboBox;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.topology.DirectionModifier;
import net.rptools.maptool.model.topology.DirectionModifierType;
import net.rptools.maptool.model.topology.WallTopology.Wall;

public class WallConfigurationController {
  private final WallConfigurationView view;
  private @Nullable Zone modelZone;
  private @Nonnull Wall model;

  public WallConfigurationController() {
    this.view = new WallConfigurationView();

    this.modelZone = null;
    this.model = new Wall();

    var directionSelect = view.getDirectionSelect();
    directionSelect.addActionListener(
        e -> {
          var direction = directionSelect.getItemAt(directionSelect.getSelectedIndex());
          if (direction != null && !direction.equals(this.model.data().direction())) {
            this.model.data().direction(direction);
            wallUpdated();
          }
        });

    var movementModifierSelect = view.getMovementModifier();
    movementModifierSelect.addActionListener(
        e -> {
          var modifier =
              movementModifierSelect.getItemAt(movementModifierSelect.getSelectedIndex());
          if (modifier != null && !modifier.equals(this.model.data().movementModifier())) {
            this.model.data().movementModifier(modifier);
            wallUpdated();
          }
        });

    for (var type : DirectionModifierType.values()) {
      final var input = getModifierInput(type);
      input.addActionListener(
          e -> {
            var modifier = input.getItemAt(input.getSelectedIndex());
            if (modifier != null && !modifier.equals(this.model.data().directionModifier(type))) {
              this.model.data().directionModifier(type, modifier);
              wallUpdated();
            }
          });
    }
  }

  public WallConfigurationView getView() {
    return view;
  }

  private JComboBox<DirectionModifier> getModifierInput(DirectionModifierType type) {
    return switch (type) {
      case Sight -> view.getSightModifier();
      case Light -> view.getLightModifier();
      case Aura -> view.getAuraModifier();
    };
  }

  private void wallUpdated() {
    if (modelZone != null) {
      MapTool.serverCommand().updateWall(modelZone, this.model);
    }
  }

  public void unbind() {
    // Preserve the current settings in a new prototype wall.
    var prototype = new Wall();
    prototype.data().set(model.data());
    bind(null, prototype);
  }

  public void bind(@Nullable Zone zone, @Nonnull Wall model) {
    // Avoid events firing during binding.
    this.modelZone = null;
    this.model = new Wall();

    this.view.getDirectionSelect().setSelectedItem(model.data().direction());
    this.view.getMovementModifier().setSelectedItem(model.data().movementModifier());
    for (var type : DirectionModifierType.values()) {
      getModifierInput(type).setSelectedItem(model.data().directionModifier(type));
    }

    // Now that the state is set, remember which wall we're bound to.
    this.modelZone = zone;
    this.model = model;
  }

  public @Nonnull Wall getModel() {
    return model;
  }
}
