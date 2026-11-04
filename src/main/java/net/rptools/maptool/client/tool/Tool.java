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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import javax.swing.JToggleButton;
import net.rptools.maptool.language.I18N;

public abstract class Tool extends JToggleButton implements ActionListener {

  private static final long serialVersionUID = -6365594431759083634L;

  private Toolbox toolbox;

  public Tool() {
    addActionListener(this);

    setToolTipText(I18N.getText(getTooltip()));
    setFocusable(false);
    setFocusPainted(false);
  }

  public boolean isAvailable() {
    return true;
  }

  protected Toolbox getToolbox() {
    return this.toolbox;
  }

  void setToolbox(Toolbox toolbox) {
    this.toolbox = toolbox;
  }

  public abstract String getTooltip();

  ////
  // ACTION LISTENER
  public abstract void actionPerformed(ActionEvent e);

  public void updateButtonState() {}
}
