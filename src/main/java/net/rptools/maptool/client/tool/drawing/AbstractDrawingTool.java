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
package net.rptools.maptool.client.tool.drawing;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolUtil;
import net.rptools.maptool.client.swing.colorpicker.ColorPicker;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.Zone.Layer;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.Pen;

// TODO This doc comment is insane and wrong.
// TODO Get your act together. This makes no sense as a base class for drawings, templates,
//  topology, and FoW exposure. The latter two rightly have no concept of a pen, and templates are
//  +grid-based. Btw, templates are completely busted on gridless maps.
/** Tool for drawing freehand lines. */
public abstract class AbstractDrawingTool extends AbstractDrawingLikeTool implements ZoneOverlay {

  private static final long serialVersionUID = 9121558405484986225L;

  private boolean isSnapToGridSelected;
  private boolean isEraseSelected;

  // TODO Color picker + layer selection should be drawing tool-specific, not applied to expose /
  // topology.
  @Override
  protected void attachTo(ZoneRenderer renderer) {
    super.attachTo(renderer);
    if (MapTool.getPlayer().isGM()) {
      MapTool.getFrame()
          .showControlPanel(MapTool.getFrame().getColorPicker(), getLayerSelectionDialog());
    } else {
      MapTool.getFrame().showControlPanel(MapTool.getFrame().getColorPicker());
    }
    renderer.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

    MapTool.getFrame().getColorPicker().setSnapSelected(isSnapToGridSelected);
    MapTool.getFrame().getColorPicker().setEraseSelected(isEraseSelected);
  }

  @Override
  protected void detachFrom(ZoneRenderer renderer) {
    MapTool.getFrame().removeControlPanel();
    renderer.setCursor(Cursor.getDefaultCursor());

    isSnapToGridSelected = MapTool.getFrame().getColorPicker().isSnapSelected();
    isEraseSelected = MapTool.getFrame().getColorPicker().isEraseSelected();

    super.detachFrom(renderer);
  }

  // TODO Even though drawing, expose, and topology tools all have this concept, they aren't really
  //  related.
  protected boolean isBackgroundFill() {
    boolean defaultValue = MapTool.getFrame().getColorPicker().isFillBackgroundSelected();
    return defaultValue;
  }

  // TODO Seems this method could be less used if we instead had a concept of start/stop where we
  //  check it once.
  protected boolean isEraser(MouseEvent e) {
    // Use the color picker as the default, but invert based on key state.
    var inverted = super.isEraser(e);
    boolean defaultValue = MapTool.getFrame().getColorPicker().isEraseSelected();
    if (inverted) {
      defaultValue = !defaultValue;
    }
    return defaultValue;
  }

  protected boolean isSnapToGrid(MouseEvent e) {
    // Use the color picker as the default, but invert based on key state.
    var inverted = super.isSnapToGrid(e);
    boolean defaultValue = MapTool.getFrame().getColorPicker().isSnapSelected();
    if (inverted) {
      // Invert from the color panel
      defaultValue = !defaultValue;
    }
    return defaultValue;
  }

  protected Pen getPen() {
    Pen pen = new Pen(MapTool.getFrame().getPen());
    pen.setEraser(isEraser());

    ColorPicker picker = MapTool.getFrame().getColorPicker();
    if (picker.isFillForegroundSelected()) {
      pen.setForegroundMode(Pen.MODE_SOLID);
    } else {
      pen.setForegroundMode(Pen.MODE_TRANSPARENT);
    }
    if (picker.isFillBackgroundSelected()) {
      pen.setBackgroundMode(Pen.MODE_SOLID);
    } else {
      pen.setBackgroundMode(Pen.MODE_TRANSPARENT);
    }
    pen.setSquareCap(picker.isSquareCapSelected());
    pen.setThickness(picker.getStrokeWidth());
    return pen;
  }

  @Override
  public abstract void paintOverlay(ZoneRenderer renderer, Graphics2D g);

  /**
   * Render a drawable on a zone. This method consolidates all of the calls to the server in one
   * place so that it is easier to keep them in sync.
   *
   * @param pen The pen used to draw.
   * @param drawable What is being drawn.
   */
  protected void completeDrawable(Pen pen, Drawable drawable) {
    var zone = getZone();

    if (!hasPaint(pen)) {
      return;
    }
    if (drawable.getBounds(zone) == null) {
      return;
    }
    if (MapTool.getPlayer().isGM()) {
      drawable.setLayer(getSelectedLayer());
    } else {
      drawable.setLayer(Layer.getDefaultPlayerLayer());
    }

    // Send new textures
    MapToolUtil.uploadTexture(pen.getPaint());
    MapToolUtil.uploadTexture(pen.getBackgroundPaint());

    // Tell the local/server to render the drawable.
    MapTool.serverCommand().draw(zone.getId(), pen, drawable);

    // Allow it to be undone
    zone.addDrawable(pen, drawable);
  }

  private boolean hasPaint(Pen pen) {
    return pen.getForegroundMode() != Pen.MODE_TRANSPARENT
        || pen.getBackgroundMode() != Pen.MODE_TRANSPARENT;
  }
}
