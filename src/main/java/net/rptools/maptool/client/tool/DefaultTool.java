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

import java.awt.Point;
import java.awt.dnd.DragSource;
import java.awt.event.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.*;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.tool.layerselectiondialog.LayerSelectionDialog;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public abstract class DefaultTool extends Tool
    implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

  private static final Logger log = LoggerFactory.getLogger(DefaultTool.class);
  private final LayerSelectionDialog layerSelectionDialog =
      new LayerSelectionDialog(Zone.Layer.values(), this::selectedLayerChanged);

  /**
   * Holds all pressed keys.
   *
   * <p>Really, this is only used to check whether 'z' is held down so we can invert zoom direction.
   * No idea why.
   */
  private final Set<Character> keyDownSet = new HashSet<>();

  protected Map<KeyStroke, Action> keyActionMap = new HashMap<>();
  private Zone.Layer selectedLayer;
  private boolean isDraggingMap;

  /**
   * The origin point for a map drag, or {@code null} if there is no drag possible.
   *
   * <p>Will be non-null when a drag is possible (right button pressed). To check whether the drag
   * is actually happening, use {@link #isDraggingMap()}. This field will be non-{@code null}
   * whenever {@link #isDraggingMap} is {@code true}, but it is also possible to be non-{@code null}
   * even if {@link #isDraggingMap} is {@code false}.
   */
  private @Nullable Point mapDragStart;

  private int dragThreshold = DragSource.getDragThreshold();

  protected int mouseX;
  protected int mouseY;

  // TBD
  private boolean isTouchScreen = false;

  protected ZoneRenderer renderer;

  protected DefaultTool() {
    keyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new EscapeAction());
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD4, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(1, 0));
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD6, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(-1, 0));
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD8, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(0, 1));
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD2, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(0, -1));

    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(1, 0));
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(-1, 0));
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(0, 1));
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK),
        new ViewMovementKey(0, -1));

    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), new FlipTokenHorizontalActionListener());
    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.SHIFT_DOWN_MASK),
        new FlipTokenVerticalActionListener());
  }

  protected boolean isKeyDown(char key) {
    return keyDownSet.contains(key);
  }

  // region KeyListener

  public void keyPressed(KeyEvent e) {
    keyDownSet.add(e.getKeyChar());
  }

  public void keyReleased(KeyEvent e) {
    keyDownSet.remove(e.getKeyChar());
  }

  public void keyTyped(KeyEvent e) {}

  // endregion

  protected Zone.Layer getSelectedLayer() {
    return selectedLayer;
  }

  protected LayerSelectionDialog getLayerSelectionDialog() {
    return layerSelectionDialog;
  }

  protected void selectedLayerChanged(Zone.Layer layer) {
    selectedLayer = layer;
    if (renderer != null) {
      renderer.setActiveLayer(layer);
    }
  }

  public abstract String getInstructions();

  /**
   * Clears internal data to restore the tool to a starting state.
   *
   * <p>This method must repaint whatever it is being displayed upon.
   *
   * <p>The default implementation switches back to the default tool ({@link PointerTool}). Only
   * delegate if there is no other reset behaviour that was actioned.
   */
  protected void resetTool() {
    var frame = MapTool.getFrame();
    if (frame != null) {
      frame.getToolbox().setSelectedTool(PointerTool.class);
    }
  }

  /**
   * Called when the tool's button is clicked.
   *
   * @param e the event to be processed
   */
  @Override
  public void actionPerformed(ActionEvent e) {
    if (isSelected()) {
      getToolbox().setSelectedTool(this);
    }
  }

  protected void attachTo(ZoneRenderer renderer) {
    this.renderer = renderer;
    selectedLayer = renderer.getActiveLayer();
    layerSelectionDialog.setSelectedLayer(selectedLayer);

    renderer.addMouseListener(this);
    renderer.addMouseMotionListener(this);
    renderer.addMouseWheelListener(this);
    renderer.addKeyListener(this);
    renderer.setActionMap(createActionMap(keyActionMap));
    renderer.setInputMap(
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, createInputMap(keyActionMap));

    SwingUtilities.invokeLater(renderer::repaint);
  }

  protected void detachFrom(ZoneRenderer renderer) {
    if (renderer != this.renderer) {
      log.error(
          "Asked to detach from the wrong zone. Attached to {}, but request was for {}",
          this.renderer == null ? "null" : this.renderer.getZone().getId(),
          renderer.getZone().getId());
    }

    this.renderer = null;

    renderer.removeKeyListener(this);
    renderer.removeMouseListener(this);
    renderer.removeMouseMotionListener(this);
    renderer.removeMouseWheelListener(this);
    SwingUtilities.invokeLater(renderer::repaint);
  }

  private static InputMap createInputMap(Map<KeyStroke, Action> keyActionMap) {
    ComponentInputMap inputMap =
        new ComponentInputMap((JPanel) MapTool.getFrame().getContentPane());
    for (KeyStroke keyStroke : keyActionMap.keySet()) {
      inputMap.put(keyStroke, keyStroke.toString());
    }
    return inputMap;
  }

  private static ActionMap createActionMap(Map<KeyStroke, Action> keyActionMap) {
    ActionMap actionMap = new ActionMap();
    for (var keyAction : keyActionMap.entrySet()) {
      actionMap.put(keyAction.getKey().toString(), keyAction.getValue());
    }
    return actionMap;
  }

  protected boolean isDraggingMap() {
    return isDraggingMap;
  }

  /**
   * Stop dragging the map.
   *
   * <p>Useful if the default behaviour is interfering with a tool.
   */
  protected void cancelMapDrag() {
    mapDragStart = null;
    isDraggingMap = false;
  }

  protected Zone getZone() {
    return renderer.getZone();
  }

  ////
  // Mouse
  @Override
  public void mousePressed(MouseEvent e) {
    // Potential map dragging
    if (SwingUtilities.isRightMouseButton(e) && mapDragStart == null) {
      setDragStart(e.getX(), e.getY());
    }
  }

  /**
   * Set the location of the start of the drag
   *
   * @param x the x coordinate of the drag start
   * @param y the y coordinate of the drag start
   */
  private void setDragStart(int x, int y) {
    mapDragStart = new Point(x, y);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (isRightMouseButton(e) && mapDragStart != null) {
      if (isDraggingMap) {
        renderer.maybeForcePlayersView();
      }

      cancelMapDrag();
    }
  }

  // region MouseListener implementation

  @Override
  public void mouseClicked(MouseEvent e) {}

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {}

  // endregion

  // region MouseMotionListener implementation

  @Override
  public void mouseMoved(MouseEvent e) {
    if (renderer == null) {
      return;
    }
    mouseX = e.getX();
    mouseY = e.getY();

    CellPoint cp =
        getZone()
            .getGrid()
            .convert(
                new ScreenPoint(mouseX, mouseY)
                    .convertToZone(renderer.getViewModel().getZoneScale()));
    if (cp != null) {
      MapTool.getFrame().getCoordinateStatusBar().update(cp.x, cp.y);
    } else {
      MapTool.getFrame().getCoordinateStatusBar().clear();
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    int mX = e.getX();
    int mY = e.getY();
    CellPoint cellUnderMouse = renderer.getCellAt(new ScreenPoint(mX, mY));
    if (cellUnderMouse != null) {
      MapTool.getFrame().getCoordinateStatusBar().update(cellUnderMouse.x, cellUnderMouse.y);
    } else {
      MapTool.getFrame().getCoordinateStatusBar().clear();
    }
    // MAP MOVEMENT
    // Sometimes the mousePressed() event can come after the first mouseDragged() event when the
    // right button is pressed. So check that we are actually intending to drag the map.
    if (isRightMouseButton(e) && mapDragStart != null) {
      var mapDX = mX - mapDragStart.x;
      var mapDY = mY - mapDragStart.y;

      if (mapDX * mapDX + mapDY * mapDY > dragThreshold * dragThreshold) {
        isDraggingMap = true;
      }

      setDragStart(mX, mY);

      var viewModel = renderer.getViewModel();
      viewModel.setZoneScale(viewModel.getZoneScale().translated(mapDX, mapDY));
    }
  }

  // endregion

  // region MouseWheelListener implementation

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    // Fix for High Resolution Mouse Wheels
    if (e.getWheelRotation() == 0) {
      return;
    }

    // QUICK ROTATE
    if (SwingUtil.isShiftDown(e)) {
      Set<GUID> tokenGUIDSet = renderer.getSelectedTokenSet();
      if (tokenGUIDSet.isEmpty()) {
        return;
      }
      for (GUID tokenGUID : tokenGUIDSet) {
        Token token = getZone().getToken(tokenGUID);
        if (token == null) {
          continue;
        }
        if (!AppUtil.playerOwns(token)) {
          continue;
        }

        int facing = token.getFacing();
        if (SwingUtil.isControlDown(e)) {
          facing += e.getWheelRotation() > 0 ? 5 : -5;
        } else {
          facing =
              renderer
                  .getZone()
                  .getGrid()
                  .nextFacing(
                      facing,
                      AppPreferences.faceEdge.get(),
                      AppPreferences.faceVertex.get(),
                      e.getWheelRotation() <= 0);
        }

        token.setFacing(facing);

        MapTool.serverCommand().putToken(getZone().getId(), token);
      }

      renderer.repaint();
      return;
    }
    // ZOOM
    if (!AppState.isZoomLocked()) {
      boolean direction = e.getWheelRotation() < 0;
      direction = isKeyDown('z') == direction;

      var scale = renderer.getViewModel().getZoneScale();

      if (!e.isControlDown()) {
        if (direction) {
          scale = scale.zoomedOut(e.getX(), e.getY());
        } else {
          scale = scale.zoomedIn(e.getX(), e.getY());
        }
      } else if (!e.isAltDown()) {
        if (direction) {
          scale = scale.zoomedOutFine(e.getX(), e.getY());
        } else {
          scale = scale.zoomedInFine(e.getX(), e.getY());
        }
      } else {
        if (direction) {
          scale = scale.zoomedOutUltrafine(e.getX(), e.getY());
        } else {
          scale = scale.zoomedInUltrafine(e.getX(), e.getY());
        }
      }

      renderer.getViewModel().setZoneScale(scale);
      renderer.maybeForcePlayersView();
    }
  }

  // endregion

  /*
   * For touch screens, swap the events, easier to move map/draw by default
   */
  public boolean isLeftMouseButton(MouseEvent event) {
    if (isTouchScreen) return SwingUtilities.isRightMouseButton(event);
    else return SwingUtilities.isLeftMouseButton(event);
  }

  /*
   * For touch screens, swap the events, easier to move map/draw by default
   */
  public boolean isRightMouseButton(MouseEvent event) {
    if (isTouchScreen) return SwingUtilities.isLeftMouseButton(event);
    else return SwingUtilities.isRightMouseButton(event);
  }

  /*
   * Nothing do here for now...
   */
  public boolean isMiddleMouseButton(MouseEvent event) {
    return SwingUtilities.isMiddleMouseButton(event);
  }

  private class FlipTokenHorizontalActionListener extends AbstractAction {
    private static final long serialVersionUID = -6286351028470892136L;

    @Override
    public void actionPerformed(ActionEvent e) {
      renderer
          .getSelectedTokensList()
          .forEach(
              token -> {
                if (token != null && AppUtil.playerOwns(token)) {
                  MapTool.serverCommand().updateTokenProperty(token, Token.Update.flipX);
                }
              });
      MapTool.getFrame().refresh();
    }
  }

  private class FlipTokenVerticalActionListener extends AbstractAction {
    private static final long serialVersionUID = -6286351028470892137L;

    @Override
    public void actionPerformed(ActionEvent e) {
      renderer
          .getSelectedTokensList()
          .forEach(
              token -> {
                if (token != null && AppUtil.playerOwns(token)) {
                  MapTool.serverCommand().updateTokenProperty(token, Token.Update.flipY);
                }
              });
      MapTool.getFrame().refresh();
    }
  }

  /**
   * Perform the escape action on a tool.
   *
   * @author jgorrell
   */
  private class EscapeAction extends AbstractAction {

    private static final long serialVersionUID = -514197544905143826L;

    public void actionPerformed(ActionEvent e) {
      // This should probably go somewhere else like MapToolFrame...
      // but it was just too easy to plop down here to resist =)
      // although having it here will be a quick reminder about competing
      // Escape actions.
      if (MapTool.getFrame().isFullScreen()) {
        MapTool.getFrame().showWindowed();
      }
      resetTool();
    }
  }

  private class ViewMovementKey extends AbstractAction {
    private final int dx, dy;

    public ViewMovementKey(int x, int y) {
      dx = x;
      dy = y;
    }

    @Override
    public String toString() {
      return "[" + dx + "," + dy + "]";
    }

    public void actionPerformed(ActionEvent e) {
      if (renderer != null) {
        renderer.moveViewByCells(dx, dy);
      }
    }
  }
}
