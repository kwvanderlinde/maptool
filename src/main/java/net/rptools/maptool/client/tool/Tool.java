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
package net.rptools.maptool.client.tool;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public abstract class Tool {
  public static final String ENABLED_PROPERTY = "enabled";
  public static final String SELECTED_PROPERTY = "selected";

  private final PropertyChangeSupport pcs;

  private boolean enabled = false;
  private boolean selected = false;

  public Tool() {
    pcs = new PropertyChangeSupport(this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    pcs.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    pcs.removePropertyChangeListener(listener);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    var previous = this.enabled;
    this.enabled = enabled;
    pcs.firePropertyChange(new PropertyChangeEvent(this, ENABLED_PROPERTY, previous, this.enabled));
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    if (!isAvailable()) {
      selected = false;
    }

    var previous = this.selected;
    this.selected = selected;
    pcs.firePropertyChange(
        new PropertyChangeEvent(this, SELECTED_PROPERTY, previous, this.selected));
  }

  public boolean isAvailable() {
    return true;
  }

  public abstract String getTooltip();

  public void updateButtonState() {}
}
