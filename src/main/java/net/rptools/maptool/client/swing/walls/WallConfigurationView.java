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

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import net.rptools.maptool.model.topology.DirectionModifier;
import net.rptools.maptool.model.topology.MovementDirectionModifier;
import net.rptools.maptool.model.topology.WallTopology.WallDirection;

public class WallConfigurationView {
  private JPanel mainPanel;
  private JComboBox<WallDirection> direction;
  private JComboBox<DirectionModifier> sightModifier;
  private JComboBox<DirectionModifier> lightModifier;
  private JComboBox<DirectionModifier> auraModifier;
  private JComboBox<MovementDirectionModifier> movementModifier;

  public WallConfigurationView() {
    direction.setModel(new DefaultComboBoxModel<>(WallDirection.values()));
    sightModifier.setModel(new DefaultComboBoxModel<>(DirectionModifier.values()));
    lightModifier.setModel(new DefaultComboBoxModel<>(DirectionModifier.values()));
    auraModifier.setModel(new DefaultComboBoxModel<>(DirectionModifier.values()));
    movementModifier.setModel(new DefaultComboBoxModel<>(MovementDirectionModifier.values()));
  }

  public JPanel getRootComponent() {
    return mainPanel;
  }

  public JComboBox<WallDirection> getDirectionSelect() {
    return direction;
  }

  public JComboBox<DirectionModifier> getSightModifier() {
    return sightModifier;
  }

  public JComboBox<DirectionModifier> getLightModifier() {
    return lightModifier;
  }

  public JComboBox<DirectionModifier> getAuraModifier() {
    return auraModifier;
  }

  public JComboBox<MovementDirectionModifier> getMovementModifier() {
    return movementModifier;
  }
}
