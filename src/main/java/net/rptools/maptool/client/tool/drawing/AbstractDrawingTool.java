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
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.List;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolUtil;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.swing.colorpicker.ColorPicker;
import net.rptools.maptool.client.tool.DefaultTool;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.Zone.Layer;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.Pen;

// TODO This doc comment is insane and wrong.
// TODO Get your act together. This makes no sense as a base class for drawings, templates,
//  topology, and FoW exposure. The latter two rightly have no concept of a pen, and templates are
//  +grid-based. Btw, templates are completely busted on gridless maps.
/** Tool for drawing freehand lines. */
public abstract class AbstractDrawingTool extends DefaultTool implements ZoneOverlay {

  private static final long serialVersionUID = 9121558405484986225L;

  private boolean isEraser;
  private boolean isSnapToGridSelected;
  private boolean isEraseSelected;

  protected AffineTransform getPaintTransform(ZoneRenderer renderer) {
    AffineTransform transform = new AffineTransform();
    transform.translate(renderer.getViewOffsetX(), renderer.getViewOffsetY());
    transform.scale(renderer.getScale(), renderer.getScale());
    return transform;
  }

  protected void paintTransformed(Graphics2D g, ZoneRenderer renderer, Drawable drawing, Pen pen) {
    AffineTransform transform = getPaintTransform(renderer);
    AffineTransform oldTransform = g.getTransform();
    g.transform(transform);
    drawing.draw(renderer.getZone(), g, pen);
    g.setTransform(oldTransform);
  }

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

  protected void setIsEraser(boolean eraser) {
    isEraser = eraser;
  }

  protected boolean isEraser() {
    return isEraser;
  }

  protected boolean isBackgroundFill() {
    boolean defaultValue = MapTool.getFrame().getColorPicker().isFillBackgroundSelected();
    return defaultValue;
  }

  protected boolean isEraser(MouseEvent e) {
    boolean defaultValue = MapTool.getFrame().getColorPicker().isEraseSelected();
    if (SwingUtil.isShiftDown(e)) {
      // Invert from the color panel
      defaultValue = !defaultValue;
    }
    return defaultValue;
  }

  protected boolean isSnapToGrid(MouseEvent e) {
    boolean defaultValue = MapTool.getFrame().getColorPicker().isSnapSelected();
    if (SwingUtil.isControlDown(e)) {
      // Invert from the color panel
      defaultValue = !defaultValue;
    }
    return defaultValue;
  }

  protected boolean isSnapToCenter(MouseEvent e) {
    boolean defaultValue = false;
    if (e.isAltDown()) {
      defaultValue = true;
    }
    return defaultValue;
  }

  protected Pen getPen() {
    Pen pen = new Pen(MapTool.getFrame().getPen());
    pen.setEraser(isEraser);

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

  protected ZonePoint getPoint(MouseEvent e) {
    ScreenPoint sp = new ScreenPoint(e.getX(), e.getY());
    ZonePoint zp = sp.convertToZoneRnd(renderer);
    if (isSnapToCenter(e) && this instanceof AbstractLineTool) {
      // Only line tools will snap to center as the Alt key for rectangle, diamond and oval
      // is used for expand from center.
      zp = renderer.getCellCenterAt(sp);
    } else if (isSnapToGrid(e)) {
      zp = renderer.getZone().getNearestVertex(zp);
    }
    return zp;
  }

  protected Area getTokenTopology(Zone.TopologyType topologyType) {
    List<Token> topologyTokens = getZone().getTokensWithTopology(topologyType);

    Area tokenTopology = new Area();
    for (Token topologyToken : topologyTokens) {
      tokenTopology.add(topologyToken.getTransformedTopology(topologyType));
    }

    return tokenTopology;
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
