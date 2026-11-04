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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import net.rptools.maptool.client.MapTool;

public abstract class Tool implements KeyListener {
  public static final String ENABLED_PROPERTY = "enabled";
  public static final String SELECTED_PROPERTY = "selected";

  private final PropertyChangeSupport pcs;

  /**
   * Holds all pressed keys.
   *
   * <p>Really, this is only used to check whether 'z' is held down so we can invert zoom direction.
   * No idea why.
   */
  private final Set<Character> keyDownSet = new HashSet<Character>();

  protected Map<KeyStroke, Action> keyActionMap = new HashMap<KeyStroke, Action>();

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

  protected boolean isKeyDown(char key) {
    return keyDownSet.contains(key);
  }

  public boolean isAvailable() {
    return true;
  }

  public abstract String getTooltip();

  protected void addListeners(JComponent comp) {
    if (comp == null) {
      return;
    }
    if (this instanceof MouseListener) {
      comp.addMouseListener((MouseListener) this);
    }
    if (this instanceof MouseMotionListener) {
      comp.addMouseMotionListener((MouseMotionListener) this);
    }
    if (this instanceof MouseWheelListener) {
      comp.addMouseWheelListener((MouseWheelListener) this);
    }
    // Keystrokes
    comp.addKeyListener(this);
    comp.setActionMap(createActionMap(keyActionMap));
    comp.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, createInputMap(keyActionMap));
  }

  void removeListeners(JComponent comp) {
    if (comp == null) {
      return;
    }
    comp.removeKeyListener(this);

    if (this instanceof MouseListener) {
      comp.removeMouseListener((MouseListener) this);
    }
    if (this instanceof MouseMotionListener) {
      comp.removeMouseMotionListener((MouseMotionListener) this);
    }
    if (this instanceof MouseWheelListener) {
      comp.removeMouseWheelListener((MouseWheelListener) this);
    }
  }

  private InputMap createInputMap(Map<KeyStroke, Action> keyActionMap) {
    ComponentInputMap inputMap =
        new ComponentInputMap((JPanel) MapTool.getFrame().getContentPane());
    for (KeyStroke keyStroke : keyActionMap.keySet()) {
      inputMap.put(keyStroke, keyStroke.toString());
    }
    return inputMap;
  }

  private ActionMap createActionMap(Map<KeyStroke, Action> keyActionMap) {
    ActionMap actionMap = new ActionMap();
    for (var keyAction : keyActionMap.entrySet()) {
      actionMap.put(keyAction.getKey().toString(), keyAction.getValue());
    }
    return actionMap;
  }

  ////
  // KEY LISTENER
  public void keyPressed(KeyEvent e) {
    keyDownSet.add(e.getKeyChar());
  }

  public void keyReleased(KeyEvent e) {
    keyDownSet.remove(e.getKeyChar());
  }

  public void keyTyped(KeyEvent e) {}

  public void updateButtonState() {}
}
