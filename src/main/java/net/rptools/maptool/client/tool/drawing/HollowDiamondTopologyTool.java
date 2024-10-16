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
import java.awt.event.MouseMotionListener;
import java.awt.geom.Area;
import javax.swing.SwingUtilities;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.model.ZonePoint;

public class HollowDiamondTopologyTool extends AbstractTopologyDrawingTool
    implements MouseMotionListener {
  private static final long serialVersionUID = 7227397975734203085L;

  // TODO I would like line thickness to be decided by AbstractTopologyDrawingTool.
  private final float thickness = 2.f;
  protected Shape diamond;
  protected ZonePoint originPoint;

  public HollowDiamondTopologyTool() {}

  @Override
  protected boolean isBackgroundFill() {
    return false;
  }

  @Override
  public String getInstructions() {
    return "tool.isorectangletopology.instructions";
  }

  @Override
  public String getTooltip() {
    return "tool.isorectangletopologyhollow.tooltip";
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    paintTopologyOverlay(g, diamond);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    ZonePoint zp = getPoint(e);
    if (SwingUtilities.isLeftMouseButton(e)) {
      if (diamond == null) {
        originPoint = zp;
        diamond = GeometryUtil.createHollowDiamond(thickness, originPoint, originPoint);
      } else {
        diamond = GeometryUtil.createHollowDiamond(thickness, originPoint, zp);
        // diamond = createDiamond(originPoint, zp);

        if (diamond.getBounds().width == 0 || diamond.getBounds().height == 0) {
          diamond = null;
          renderer.repaint();
          return;
        }
        Area area = new Area(diamond);
        if (isEraser(e)) {
          getZone().removeTopology(area);
          MapTool.serverCommand()
              .removeTopology(getZone().getId(), area, getZone().getTopologyTypes());
        } else {
          getZone().addTopology(area);
          MapTool.serverCommand()
              .addTopology(getZone().getId(), area, getZone().getTopologyTypes());
        }
        renderer.repaint();
        // TODO: send this to the server

        diamond = null;
      }
      setIsEraser(isEraser(e));
    }
    super.mousePressed(e);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (diamond == null) {
      super.mouseDragged(e);
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);

    setIsEraser(isEraser(e));

    ZonePoint zp = getPoint(e);
    if (diamond != null) {
      diamond = GeometryUtil.createDiamond(originPoint, zp);
      renderer.repaint();
    }
  }

  /** Stop drawing a rectangle and repaint the zone. */
  @Override
  public void resetTool() {
    if (diamond != null) {
      diamond = null;
      renderer.repaint();
    } else {
      super.resetTool();
    }
  }
}
