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
package net.rptools.maptool.client.ui.htmlframe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jidesoft.docking.DockContext;
import com.jidesoft.docking.DockableFrame;
import com.jidesoft.docking.DockingManager;
import com.jidesoft.docking.event.DockableFrameAdapter;
import com.jidesoft.docking.event.DockableFrameEvent;
import com.twelvemonkeys.image.BufferedImageIcon;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.MacroLinkFunction;
import net.rptools.maptool.client.ui.MapToolFrame;
import net.rptools.maptool.client.ui.theme.Icons;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.ParserException;

/**
 * Represents a dockable frame holding an HTML panel. Can hold either an HTML3.2 (Swing) or a HTML5
 * (JavaFX) panel.
 */
@SuppressWarnings("serial")
public class HTMLFrame extends DockableFrame implements HTMLPanelContainer {
  /** The static map of the HTMLFrames. */
  private static final Map<String, HTMLFrame> frames = new HashMap<String, HTMLFrame>();

  /** The map of the macro callbacks. */
  private final Map<String, String> macroCallbacks = new HashMap<String, String>();

  /** The temporary status of the frame. A temporary frame isn't stored after being closed. */
  private boolean temporary;

  /** The value stored in the frame. */
  private Object value;

  /** Panel for HTML. */
  private HTMLPanelInterface panel;

  /** The name of the frame. */
  private final String name;

  /** Is the panel HTML5 or HTML3.2. */
  private boolean isHTML5;

  /**
   * Runs a javascript on a frame.
   *
   * @param name the name of the frame
   * @param script the script to run
   * @return true if the frame exists and can run the script, false otherwise
   */
  public static boolean runScript(String name, String script) {
    HTMLFrame frame = frames.get(name);
    return frame != null && frame.panel.runJavascript(script);
  }

  @Override
  public Map<String, String> macroCallbacks() {
    return macroCallbacks;
  }

  /**
   * Returns if the frame is visible or not.
   *
   * @param name The name of the frame.
   * @return true if the frame is visible.
   */
  static boolean isVisible(String name) {
    if (frames.containsKey(name)) {
      return frames.get(name).isVisible();
    }
    return false;
  }

  /**
   * Requests that the frame close.
   *
   * @param name The name of the frame.
   */
  static void close(String name) {
    if (frames.containsKey(name)) {
      frames.get(name).closeRequest();
    }
  }

  /**
   * Gets an unmodifiable set view of the names of all known frames.
   *
   * @return the frame names
   */
  public static Set<String> getFrameNames() {
    return Collections.unmodifiableSet(frames.keySet());
  }

  /**
   * Creates a new HTMLFrame and displays it or displays an existing frame. The width and height are
   * ignored for existing frames so that they will not override the size that the player may have
   * resized them to.
   *
   * @param name the name of the frame.
   * @param title the title of the frame.
   * @param tabTitle the title of the tab.
   * @param width the width of the frame in pixels.
   * @param height the height of the frame in pixels.
   * @param temp whether the frame should be temporary.
   * @param scrollReset whether the scrollbar should be reset.
   * @param isHTML5 whether it should use HTML5 (JavaFX) or HTML 3.2 (Swing).
   * @param val a value that can be returned by getFrameProperties().
   * @param html the html to display in the frame.
   * @return the HTMLFrame that is displayed.
   */
  public static HTMLFrame showFrame(
      String name,
      String title,
      String tabTitle,
      int width,
      int height,
      boolean temp,
      boolean scrollReset,
      boolean isHTML5,
      Object val,
      String html)
      throws ParserException {
    HTMLFrame frame;

    if (frames.containsKey(name)) {
      frame = frames.get(name);
      if (!frame.isVisible()) {
        frame.setVisible(true);
        frame.getDockingManager().showFrame(name);
      }
    } else {
      // Make sure there isn't a name conflict with the normal MT frames
      boolean isMtframeName =
          Stream.of(MapToolFrame.MTFrame.values())
                  .filter(e -> e.name().equals(name))
                  .findFirst()
                  .orElse(null)
              != null;
      if (isMtframeName) {
        String opt = isHTML5 ? "frame5" : "frame";
        throw new ParserException(I18N.getText("lineParser.optReservedName", opt, name));
      }

      // Only set size on creation so we don't override players resizing.
      Icon icon = RessourceManager.getSmallIcon(Icons.WINDOW_HTML);
      // Parse the data URI if one is passed.
      // TODO Allow passing an icon parameter.
      // TODO Also support keywords for built-in icons.
      // TODO Consider propagating the originating Lib icon to the frame.
      {
        String svgStr =
            "data:image/svg+xml,<?xml version=\"1.0\" encoding=\"UTF-8\"?>%0D%0A<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"100\" height=\"100\">%0D%0A<path d=\"M77.926,94.924H8.217C6.441,94.924,5,93.484,5,91.706V21.997c0-1.777,1.441-3.217,3.217-3.217h34.854 c1.777,0,3.217,1.441,3.217,3.217s-1.441,3.217-3.217,3.217H11.435v63.275h63.274V56.851c0-1.777,1.441-3.217,3.217-3.217 c1.777,0,3.217,1.441,3.217,3.217v34.855C81.144,93.484,79.703,94.924,77.926,94.924z\"/>%0D%0A<path d=\"M94.059,16.034L84.032,6.017c-1.255-1.255-3.292-1.255-4.547,0l-9.062,9.073L35.396,50.116 c-0.29,0.29-0.525,0.633-0.686,1.008l-7.496,17.513c-0.526,1.212-0.247,2.617,0.676,3.539c0.622,0.622,1.437,0.944,2.274,0.944 c0.429,0,0.858-0.086,1.276-0.257l17.513-7.496c0.375-0.161,0.719-0.397,1.008-0.686l35.026-35.026l9.073-9.062 C95.314,19.326,95.314,17.289,94.059,16.034z M36.286,63.79l2.928-6.821l3.893,3.893L36.286,63.79z M46.925,58.621l-5.469-5.469 L73.007,21.6l5.47,5.469L46.925,58.621z M81.511,24.034l-5.469-5.469l5.716-5.716l5.469,5.459L81.511,24.034z\"/>%0D%0A</svg>";
        final var b64Str =
            "data:image/png;base64,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";
        try {
          icon = buildFrameIcon(b64Str);
        } catch (IOException e) {
          // TODO Log it, but don't fail completely due to missing icon.
          throw new RuntimeException(e);
        }
      }

      frame = new HTMLFrame(name, width, height, isHTML5, icon);
      frames.put(name, frame);

      frame.getDockingManager().showFrame(name);
      // Jamz: why undock frames to center them?
      if (!frame.isDocked()) center(name);
    }
    frame.updateContents(html, title, tabTitle, temp, scrollReset, isHTML5, val);
    return frame;
  }

  private static Icon buildFrameIcon(String iconString) throws IOException {
    Icon defaultIcon = RessourceManager.getSmallIcon(Icons.WINDOW_HTML);

    Icon icon = defaultIcon;
    if (iconString.startsWith("data:")) {
      // After the "data:" part is media type and optional base64 extension. Actual data begins
      // after a comma.
      final var commaIndex = iconString.indexOf(",");
      final var data = iconString.substring(1 + commaIndex);
      final var isBase64 = "base64".equals(iconString.substring(commaIndex - 6, commaIndex));
      // Only support base64 for now.
      if (isBase64) {
        // Small icons are 16x16.
        final var size = RessourceManager.smallIconSize;
        final var imageData = Base64.getDecoder().decode(data);
        icon = new BufferedImageIcon(ImageIO.read(new ByteArrayInputStream(imageData)), size, size);
      }
    }

    return icon;
  }

  @Override
  public void setValue(Object val) {
    this.value = val;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public void setTemporary(boolean temp) {
    this.temporary = temp;
  }

  @Override
  public boolean getTemporary() {
    return this.temporary;
  }

  /**
   * Add an HTML panel to the frame.
   *
   * @param isHTML5 whether the panel supports HTML5
   */
  public void addHTMLPanel(boolean isHTML5) {
    if (isHTML5) {
      panel = new HTMLJFXPanel(this, new HTMLWebViewManager(this, "frame5", this.name));
    } else {
      panel = new HTMLPanel(this, true);
    }
    panel.addToContainer(this);
    panel.addActionListener(this);
  }

  /**
   * Create a new HTMLFrame.
   *
   * @param name the name of the frame
   * @param width the width of the frame
   * @param height the height of the frame
   * @param isHTML5 whether the frame is HTML5 (JavaFx)
   */
  private HTMLFrame(String name, int width, int height, boolean isHTML5, Icon icon) {
    super(name, icon);
    this.name = name;
    this.isHTML5 = isHTML5;
    width = width < 100 ? 400 : width;
    height = height < 50 ? 200 : height;
    setPreferredSize(new Dimension(width, height));

    addHTMLPanel(isHTML5);

    this.getContext().setInitMode(DockContext.STATE_FLOATING);

    /* Issue #2485
     * If the frame exists, then it's a placeholder frame that should be removed
     * Note: There should be no risk of MT frames being removed, as that is checked
     * for in showFrame() (the only place this constructor is called)
     */
    DockingManager dm = MapTool.getFrame().getDockingManager();
    if (dm.getFrame(name) != null) {
      // The frame needs to be shown before being removed otherwise the layout gets messed up
      dm.showFrame(name);
      dm.removeFrame(name, true);
    }
    /* /Issue #2485 */

    dm.addFrame(this);
    this.setVisible(true);
    addDockableFrameListener(
        new DockableFrameAdapter() {
          @Override
          public void dockableFrameHidden(DockableFrameEvent dockableFrameEvent) {
            closeRequest();
          }
        });
  }

  /**
   * Center a frame.
   *
   * @param name the name of the frame to center.
   */
  public static void center(String name) {
    if (!frames.containsKey(name)) {
      return;
    }
    HTMLFrame frame = frames.get(name);
    Dimension outerSize = MapTool.getFrame().getSize();

    int x = MapTool.getFrame().getLocation().x + (outerSize.width - frame.getWidth()) / 2;
    int y = MapTool.getFrame().getLocation().y + (outerSize.height - frame.getHeight()) / 2;

    Rectangle rect =
        new Rectangle(Math.max(x, 0), Math.max(y, 0), frame.getWidth(), frame.getHeight());
    MapTool.getFrame().getDockingManager().floatFrame(frame.getKey(), rect, true);
  }

  /**
   * Update the html content of the frame.
   *
   * @param html the html content
   * @param title the title of the frame
   * @param tabTitle the tabTitle of the frame
   * @param temp whether the frame is temporary
   * @param scrollReset whether the scrollbar should be reset
   * @param isHTML5 whether the frame should support HTML5 (JavaFX)
   * @param val the value to put in the frame
   */
  public void updateContents(
      String html,
      String title,
      String tabTitle,
      boolean temp,
      boolean scrollReset,
      boolean isHTML5,
      Object val) {
    if (this.isHTML5 != isHTML5) {
      this.isHTML5 = isHTML5;
      panel.removeFromContainer(this); // remove previous panel
      addHTMLPanel(isHTML5); // add new panel of the other HTML type
      this.revalidate();
    }
    macroCallbacks.clear();
    setTitle(title);
    setTabTitle(tabTitle);
    setTemporary(temp);
    setValue(val);
    panel.updateContents(html, scrollReset);
  }

  /** Run all callback macros for "onChangeSelection". */
  public static void doSelectedChanged() {
    for (HTMLFrame frame : frames.values()) {
      if (frame.isVisible()) {
        HTMLPanelContainer.selectedChanged(frame.macroCallbacks);
      }
    }
  }

  /** Run all callback macros for "onChangeImpersonated". */
  public static void doImpersonatedChanged() {
    for (HTMLFrame frame : frames.values()) {
      if (frame.isVisible()) {
        HTMLPanelContainer.impersonatedChanged(frame.macroCallbacks);
      }
    }
  }

  /**
   * Run all callback macros for "onChangeToken".
   *
   * @param token the token that changed.
   */
  public static void doTokenChanged(Token token) {
    if (token != null) {
      for (HTMLFrame frame : frames.values()) {
        if (frame.isVisible()) {
          HTMLPanelContainer.tokenChanged(token, frame.macroCallbacks);
        }
      }
    }
  }

  /**
   * Return a json with the width, height, title, temporary, and value of the frame
   *
   * @param name the name of the frame.
   * @return a json with the width, height, title, temporary, and value of the frame, if one was
   *     found
   */
  public static Optional<JsonObject> getFrameProperties(String name) {
    if (frames.containsKey(name)) {
      HTMLFrame frame = frames.get(name);
      JsonObject frameProperties = new JsonObject();
      DockContext dc = frame.getContext();

      frameProperties.addProperty("title", frame.getTitle());
      frameProperties.addProperty("tabtitle", frame.getTabTitle());
      frameProperties.addProperty("html5", FunctionUtil.getDecimalForBoolean(frame.isHTML5));
      frameProperties.addProperty(
          "temporary", FunctionUtil.getDecimalForBoolean(frame.getTemporary()));
      frameProperties.addProperty("visible", FunctionUtil.getDecimalForBoolean(frame.isVisible()));
      frameProperties.addProperty("docked", FunctionUtil.getDecimalForBoolean(frame.isDocked()));
      frameProperties.addProperty(
          "floating",
          FunctionUtil.getDecimalForBoolean(dc.isFloated())); // Always opposite of docked?
      frameProperties.addProperty(
          "autohide", FunctionUtil.getDecimalForBoolean(frame.isAutohide()));
      frameProperties.addProperty("height", frame.getHeight());
      frameProperties.addProperty("width", frame.getWidth());
      final var undockedBounds = dc.getUndockedBounds();
      // A frame docked prior to a Restore Layout will lose its undocked bounds, causing NPE here.
      if (undockedBounds != null) {
        // The x & y are screen coordinates.
        frameProperties.addProperty("undocked_x", undockedBounds.getX());
        frameProperties.addProperty("undocked_y", undockedBounds.getY());
        frameProperties.addProperty("undocked_h", undockedBounds.getHeight());
        frameProperties.addProperty("undocked_w", undockedBounds.getWidth());
      }
      // Many of the Frame/DockContext attributes shown in the JIDE javadocs don't seem to
      // get updated.  Docked height never changes but docked width does and matches Frame
      // width.  AutoHide Height/Width never change.

      Object frameValue = frame.getValue();
      if (frameValue == null) {
        frameValue = "";
      } else {
        if (frameValue instanceof String) {
          // try to convert to a number
          try {
            frameValue = new BigDecimal(frameValue.toString());
          } catch (Exception e) {
          }
        }
      }
      if (frameValue instanceof JsonElement) {
        frameProperties.add("value", (JsonElement) frameValue);
      }
      frameProperties.addProperty("value", frameValue.toString());

      return Optional.of(frameProperties);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void closeRequest() {
    MapTool.getFrame().getDockingManager().hideFrame(getKey());
    setVisible(false);
    panel.flush();

    if (getTemporary()) {
      MapTool.getFrame().getDockingManager().removeFrame(this.name, false);
      frames.remove(this.name);
      dispose();
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e instanceof HTMLActionEvent.FormActionEvent) {
      HTMLActionEvent.FormActionEvent fae = (HTMLActionEvent.FormActionEvent) e;
      MacroLinkFunction.runMacroLink(fae.getAction() + fae.getData());
    }
    if (e instanceof HTMLActionEvent.RegisterMacroActionEvent) {
      HTMLActionEvent.RegisterMacroActionEvent rmae = (HTMLActionEvent.RegisterMacroActionEvent) e;
      macroCallbacks.put(rmae.getType(), rmae.getMacro());
    }
    if (e instanceof HTMLActionEvent.ChangeTitleActionEvent) {
      String newTitle = ((HTMLActionEvent.ChangeTitleActionEvent) e).getNewTitle();
      this.setTitle(newTitle);
      this.setTabTitle(newTitle);
    }
    if (e instanceof HTMLActionEvent.MetaTagActionEvent) {
      HTMLActionEvent.MetaTagActionEvent mtae = (HTMLActionEvent.MetaTagActionEvent) e;
      if (mtae.getName().equalsIgnoreCase("onChangeToken")
          || mtae.getName().equalsIgnoreCase("onChangeSelection")
          || mtae.getName().equalsIgnoreCase("onChangeImpersonated")) {
        macroCallbacks.put(mtae.getName(), mtae.getContent());
      }
    }
    if (e.getActionCommand().equals("Close")) {
      closeRequest();
    }
  }
}
