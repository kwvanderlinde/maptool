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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;

public final class ExposeTool<StateT> extends AbstractDrawingLikeTool {
  // TODO Need to draw measurements as well.
  // TODO For some tools, alt means center the origin.

  private final String instructionKey;
  private final String tooltipKey;
  private final Strategy<StateT> strategy;

  /** The current state of the tool. If {@code null}, nothing is being drawn right now. */
  private @Nullable StateT state;

  private ZonePoint currentPoint = new ZonePoint(0, 0);

  public ExposeTool(String instructionKey, String tooltipKey, Strategy<StateT> strategy) {
    this.instructionKey = instructionKey;
    this.tooltipKey = tooltipKey;
    this.strategy = strategy;
  }

  @Override
  public String getInstructions() {
    return instructionKey;
  }

  @Override
  public String getTooltip() {
    return tooltipKey;
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  @Override
  protected boolean isLinearTool() {
    return strategy.isLinear();
  }

  /** If currently drawing, stop and clear it. */
  @Override
  protected void resetTool() {
    if (state != null) {
      state = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }

  private void submit(Shape shape) {
    if (!MapTool.getPlayer().isGM()) {
      MapTool.showError("msg.error.fogexpose");
      MapTool.getFrame().refresh();
      return;
    }

    Area area;
    if (shape instanceof Area tmpArea) {
      area = tmpArea;
    } else {
      // Fill the shape.
      area = new Area(shape);
    }

    Zone zone = getZone();
    Set<GUID> selectedToks = renderer.getSelectedTokenSet();

    if (isEraser()) {
      // TODO Bit weird that exposeFoW() command below updates the local client, but hideFoW() here
      //  does not. Consider fixing that.
      zone.hideArea(area, selectedToks);
      MapTool.serverCommand().hideFoW(zone.getId(), area, selectedToks);
    } else {
      MapTool.serverCommand().exposeFoW(zone.getId(), area, selectedToks);
    }
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    Graphics2D g2 = (Graphics2D) g.create();
    // TODO Ensure SrcOver composite?
    g2.translate(renderer.getViewOffsetX(), renderer.getViewOffsetY());
    g2.scale(renderer.getScale(), renderer.getScale());

    if (state != null) {
      var shape = strategy.getShape(state, currentPoint);
      // TODO Require non-null again.
      if (shape != null) {
        // TODO Fog-specific entries in AppStyle.
        var color = isEraser() ? AppStyle.topologyRemoveColor : AppStyle.topologyAddColor;
        g2.setColor(color);
        g2.fill(shape);

        // Render the outline just to make it stand out more.
        g2.setColor(Color.black);
        // This line is purely visual, so keep it a consistent thickness
        g2.setStroke(
            new BasicStroke(
                1 / (float) renderer.getScale(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g2.draw(shape);
      }
    }

    g2.dispose();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (state == null) {
      // We're not doing anything, so delegate to default behaviour.
      super.mouseDragged(e);
    } else if (strategy.isFreehand()) {
      // Extend the line.
      setIsEraser(isEraser(e));
      currentPoint = getPoint(e);
      strategy.pushPoint(state, currentPoint);
      renderer.repaint();
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    setIsEraser(isEraser(e));
    if (state != null) {
      currentPoint = getPoint(e);
      renderer.repaint();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    setIsEraser(isEraser(e));

    if (SwingUtilities.isLeftMouseButton(e)) {
      currentPoint = getPoint(e);

      if (state == null) {
        state = strategy.startNewAtPoint(currentPoint);
      } else if (!strategy.isFreehand()) {
        var shape = strategy.getShape(state, currentPoint);
        state = null;
        if (shape != null) {
          submit(shape);
        }
      }
      renderer.repaint();
    }
    // TODO Shouldn't we make sure it's a right-click?
    else if (state != null && !strategy.isFreehand()) {
      currentPoint = getPoint(e);
      strategy.pushPoint(state, currentPoint);
      renderer.repaint();
    }

    super.mousePressed(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (strategy.isFreehand() && SwingUtilities.isLeftMouseButton(e)) {
      currentPoint = getPoint(e);
      var shape = strategy.getShape(state, currentPoint);
      state = null;
      if (shape != null) {
        submit(shape);
      }
    }
  }
}
