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

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.tool.DefaultTool;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.Pen;

// TODO Rename this. Base class for drawing, expose, topology tools.
// TODO Avoid ZoneOverlay in favour of renderers.
public abstract class AbstractDrawingLikeTool extends DefaultTool implements ZoneOverlay {
  private boolean isEraser;

  // region TODO Avoid this by world-space renderer.
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

  // endregion

  // TODO Common functionality to set the eraser state on starting event or something.

  protected void setIsEraser(boolean eraser) {
    isEraser = eraser;
  }

  protected boolean isEraser() {
    return isEraser;
  }

  // TODO Seems this method could be less used if we instead had a concept of start/stop where we
  //  check it once.
  protected boolean isEraser(MouseEvent e) {
    return SwingUtil.isShiftDown(e);
  }

  protected boolean isSnapToGrid(MouseEvent e) {
    return SwingUtil.isControlDown(e);
  }

  protected boolean isSnapToCenter(MouseEvent e) {
    return e.isAltDown();
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
}
