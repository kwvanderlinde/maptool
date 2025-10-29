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

import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSetBuilder;
import net.rptools.maptool.client.ui.zone.renderer.instructions.Paint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Stroke;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.AbstractTemplate;
import net.rptools.maptool.model.drawing.LineCellTemplate;
import net.rptools.maptool.model.drawing.Pen;

/**
 * Draw the effected area of a spell area type of line.
 *
 * @author naciron
 */
public class LineCellTemplateTool extends RadiusCellTemplateTool {

  /*---------------------------------------------------------------------------------------------
   * Instance Variables
   *-------------------------------------------------------------------------------------------*/

  /**
   * Has the anchoring point been set? When false, the anchor point is being placed. When true, the
   * area of effect is being drawn on the display.
   */
  private boolean pathAnchorSet;

  /*---------------------------------------------------------------------------------------------
   * Constructor
   *-------------------------------------------------------------------------------------------*/

  public LineCellTemplateTool() {}

  /*---------------------------------------------------------------------------------------------
   * Overridden RadiusTemplateTool Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public String getTooltip() {
    return "tool.LineCellTemplate.tooltip";
  }

  @Override
  public String getInstructions() {
    // No reason to create new instructions
    return "tool.linetemplate.instructions";
  }

  @Override
  protected AbstractTemplate createBaseTemplate() {
    return new LineCellTemplate();
  }

  @Override
  protected void resetTool(ZonePoint aVertex) {
    super.resetTool(aVertex);
    pathAnchorSet = false;
  }

  /*---------------------------------------------------------------------------------------------
   * Overridden AbstractDrawingTool Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public void compositeOverlay(InstructionSetBuilder builder) {
    if (painting) {
      Pen pen = getPenForOverlay();
      ZonePoint vertex = template.getVertex();
      ZonePoint pathVertex = ((LineCellTemplate) template).getPathVertex();

      builder.addDrawable(template, pen);

      builder.add(
          new Stroke(
              makeCursorShapeAt(vertex, template.getCursorType()),
              Paint.of(pen.getPaint()),
              pen.getStroke(),
              pen.getOpacity()));
      if (pathVertex != null) {
        builder.add(
            new Stroke(
                makeCursorShapeAt(pathVertex, template.getCursorType()),
                Paint.of(pen.getPaint()),
                pen.getStroke(),
                pen.getOpacity()));

        compositeRadius(builder, vertex);
      }
    }
  }

  @Override
  protected int getRadiusAtMouse(MouseEvent aE) {
    int radius = super.getRadiusAtMouse(aE) + 1;
    return Math.max(0, radius - 1);
  }

  @Override
  public void mousePressed(MouseEvent aE) {
    if (!painting) return;

    if (SwingUtilities.isLeftMouseButton(aE)) {

      // Need to set the anchor?
      controlOffset = null;
      if (!anchorSet) {
        anchorSet = true;
        return;
      } // endif
      if (!pathAnchorSet) {
        LineCellTemplate lt = (LineCellTemplate) template;
        ZonePoint pathVertex = lt.getPathVertex();
        ZonePoint vertex = lt.getVertex();
        // If the anchor vertex and path anchor vertex are the same, the line is invalid, so do not
        // allow.
        if ((vertex != null) && !vertex.equals(pathVertex)) {
          pathAnchorSet = true;
        }
        return;
      } // endif
    } // endif

    // Let the radius code finish the template
    super.mousePressed(aE);
  }

  @Override
  protected void handleMouseMovement(MouseEvent e) {
    // Setting anchor point?
    LineCellTemplate lt = (LineCellTemplate) template;
    ZonePoint vertex = lt.getVertex();

    if (!anchorSet) {
      setCellAtMouse(e, vertex);
      controlOffset = null;

      // Let control move the anchor
    } else if (!pathAnchorSet && SwingUtil.isControlDown(e)) {
      handleControlOffset(e, vertex);

      // Setting path anchor?
    } else if (!pathAnchorSet) {
      template.setRadius(getRadiusAtMouse(e));
      controlOffset = null;

      ZonePoint pathVertex = getCellAtMouse(e);
      lt.setPathVertex(pathVertex);
      renderer.repaint();

      // Let control move the path anchor
    } else if (SwingUtil.isControlDown(e)) {
      ZonePoint pathVertex = lt.getPathVertex();
      handleControlOffset(e, pathVertex);
      lt.setPathVertex(pathVertex);

      // Set the final radius
    } else {
      template.setRadius(getRadiusAtMouse(e));
      renderer.repaint();
      controlOffset = null;
    } // endif
  }
}
