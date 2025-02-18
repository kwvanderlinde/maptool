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
package net.rptools.maptool.client.tool;

import java.awt.EventQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.language.I18N;

/** */
public class Toolbox {
  private ZoneRenderer currentRenderer;

  /** The selected tool, if any. Will be one of {@link #tools} or {@code null}. */
  private Tool currentTool;

  /** Contains all tools in the toolbox regardless of how they were registered. */
  private final List<Tool> tools = new ArrayList<>();

  /** Remembers which tool was registered for which class. Values are members of {@link #tools}. */
  private final Map<Class<? extends Tool>, Tool> toolMap =
      new HashMap<Class<? extends Tool>, Tool>();

  private final ButtonGroup buttonGroup = new ButtonGroup();

  public void updateTools() {
    for (Tool tool : tools) {
      tool.setEnabled(tool.isAvailable());
      tool.updateButtonState();
    }
  }

  public Tool getSelectedTool() {
    return currentTool;
  }

  @SuppressWarnings("unchecked")
  public <T extends Tool> T getTool(Class<? extends T> toolClass) {
    return (T) toolMap.get(toolClass);
  }

  public <T extends Tool> T createTool(Class<T> toolClass) {
    T tool;
    try {
      Constructor<T> constructor = toolClass.getDeclaredConstructor();
      tool = constructor.newInstance();

      addTool(tool);
      toolMap.put(toolClass, tool);

      return tool;
    } catch (InstantiationException e) {
      MapTool.showError(I18N.getText("msg.error.toolCannotInstantiate", toolClass.getName()), e);
    } catch (IllegalAccessException e) {
      MapTool.showError(
          I18N.getText("msg.error.toolNeedPublicConstructor", toolClass.getName()), e);
    } catch (NoSuchMethodException nsme) {
      MapTool.showError(
          I18N.getText("msg.error.toolNeedValidConstructor", toolClass.getName()), nsme);
    } catch (InvocationTargetException ite) {
      MapTool.showError(I18N.getText("msg.error.toolConstructorFailed", toolClass.getName()), ite);
    }
    return null;
  }

  /**
   * Add {@code tool} to the toolbox.
   *
   * <p>This tool will not be registered by its class, so methods like {@link #getTool(Class)} will
   * not be able to find it.
   *
   * @param tool The tool to add.
   */
  public void addTool(Tool tool) {
    tools.add(tool);
    if (tool.hasGroup()) {
      buttonGroup.add(tool);
    }
    tool.setToolbox(this);
  }

  public void setTargetRenderer(final ZoneRenderer renderer) {
    // Need to be synchronous with the timing of the invokes within this method
    EventQueue.invokeLater(
        () -> {
          // Disconnect the current tool from the current renderer
          detach();
          currentRenderer = renderer;
          attach();
        });
  }

  public void setSelectedTool(Class<? extends Tool> toolClass) {
    Tool tool = toolMap.get(toolClass);
    setSelectedTool(tool);
  }

  public void setSelectedTool(final Tool tool) {
    EventQueue.invokeLater(
        () -> {
          if (tool == currentTool) {
            return;
          }

          detach();
          var accepted = makeCurrent(tool);
          if (accepted) {
            attach();
          }
        });
  }

  private void attach() {
    if (currentTool != null) {
      if (currentRenderer != null) {
        // We have a renderer at this point so we can figure out the grid type and add its
        // keystrokes to the PointerTool.
        // currentTool.addGridBasedKeys(currentRenderer, true);
        currentTool.addListeners(currentRenderer);
        currentTool.attachTo(currentRenderer);

        if (currentTool instanceof ZoneOverlay) {
          currentRenderer.addOverlay((ZoneOverlay) currentTool);
        }
      }
    }
  }

  private void detach() {
    if (currentTool != null && currentRenderer != null) {
      currentTool.removeListeners(currentRenderer);
      // currentTool.addGridBasedKeys(currentRenderer, false);
      currentTool.detachFrom(currentRenderer);

      if (currentTool instanceof ZoneOverlay) {
        currentRenderer.removeOverlay((ZoneOverlay) currentTool);
      }
    }
  }

  private boolean makeCurrent(Tool tool) {
    if (tool == null || !tool.isAvailable()) {
      return false;
    }

    currentTool = tool;
    tool.setSelected(true);
    if (MapTool.getFrame() != null) {
      MapTool.getFrame().setStatusMessage(I18N.getText(currentTool.getInstructions()));
    }

    return true;
  }
}
