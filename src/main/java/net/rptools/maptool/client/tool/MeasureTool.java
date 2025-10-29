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

import com.google.common.collect.Iterables;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.ImageCursor;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSetBuilder;
import net.rptools.maptool.client.ui.zone.renderer.instructions.Paint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.BoxedString;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Stroke;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.ZonePoint;

/** */
public class MeasureTool extends DefaultTool implements ZoneOverlay {

  public static final String CURSOR_NAME = "Measure tool";

  private ZoneWalker walker;
  private Path<ZonePoint> gridlessPath;
  private ZonePoint currentGridlessPoint;

  private static Cursor measureCursor;
  private static javafx.scene.Cursor measureCursorFX;

  public MeasureTool() {
    measureCursor =
        Toolkit.getDefaultToolkit()
            .createCustomCursor(
                RessourceManager.getImage(Images.MEASURE), new Point(2, 28), CURSOR_NAME);
    Platform.runLater(
        () ->
            measureCursorFX = new ImageCursor(RessourceManager.getFxImage(Images.MEASURE), 2, 28));
  }

  public static Cursor getMeasureCursor() {
    return measureCursor;
  }

  public static javafx.scene.Cursor getMeasureCursorFX() {
    return measureCursorFX;
  }

  @Override
  protected void attachTo(ZoneRenderer renderer) {
    renderer.setCursor(measureCursor);
    super.attachTo(renderer);
  }

  @Override
  protected void detachFrom(ZoneRenderer renderer) {
    renderer.setCursor(Cursor.getDefaultCursor());
    super.detachFrom(renderer);
  }

  @Override
  public String getTooltip() {
    return "tool.measure.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.measure.instructions";
  }

  @Override
  public void compositeOverlay(InstructionSetBuilder builder) {
    if (walker != null) {
      builder.addPath(walker.getPath(), builder.getZone().getGrid().getDefaultFootprint());
      ScreenPoint sp = walker.getLastPoint().convertToScreen(renderer);

      int y = (int) sp.y - 10;
      int x = (int) sp.x + (int) (renderer.getScaledGridSize() / 2);
      builder.add(new BoxedString(x, y, Double.toString(walker.getDistance())));
    } else if (gridlessPath != null) {
      // distance
      double c = 0;
      var path2D = new Path2D.Double();
      ZonePoint lastZP = null;
      for (ZonePoint zp :
          Iterables.concat(gridlessPath.getCellPath(), List.of(currentGridlessPoint))) {
        if (lastZP == null) {
          path2D.moveTo(zp.x, zp.y);
        } else {
          path2D.lineTo(zp.x, zp.y);
          int a = lastZP.x - zp.x;
          int b = lastZP.y - zp.y;
          c += Math.sqrt(a * a + b * b);
        }
        lastZP = zp;
      }
      assert lastZP != null : "Our non-empty iterable was empty!";

      c /= builder.getZone().getGrid().getSize();
      c *= builder.getZone().getUnitsPerCell();

      builder.add(new Stroke(path2D, Paint.of(Color.black), new BasicStroke(1.f), 1.));

      String distance = NumberFormat.getInstance().format(c);
      ScreenPoint sp =
          builder.getViewport().zoneScale().toScreenSpace(new Point2D.Double(lastZP.x, lastZP.y));
      builder.add(new BoxedString(sp.x, sp.y - 20, distance));
    }
  }

  @Override
  protected void installKeystrokes(Map<KeyStroke, Action> actionMap) {
    super.installKeystrokes(actionMap);

    actionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false),
        new AbstractAction() {
          public void actionPerformed(ActionEvent e) {
            // Waypoint
            if (walker != null) {
              CellPoint cp =
                  renderer
                      .getZone()
                      .getGrid()
                      .convert(
                          new ScreenPoint(mouseX, mouseY)
                              .convertToZone(renderer.getViewModel().getZoneScale()));
              walker.toggleWaypoint(cp);
            } else if (gridlessPath != null) {
              gridlessPath.appendWaypoint(currentGridlessPoint);
            }
          }
        });
  }

  ////
  // MOUSE LISTENER
  @Override
  public void mousePressed(java.awt.event.MouseEvent e) {
    ZoneRenderer renderer = (ZoneRenderer) e.getSource();

    if (SwingUtilities.isLeftMouseButton(e)) {
      if (renderer.getZone().getGrid().getCapabilities().isPathingSupported()) {
        CellPoint cellPoint = renderer.getCellAt(new ScreenPoint(e.getX(), e.getY()));
        walker = renderer.getZone().getGrid().createZoneWalker();
        walker.addWaypoints(cellPoint, cellPoint);
      } else {
        currentGridlessPoint =
            new ScreenPoint(e.getX(), e.getY())
                .convertToZone(renderer.getViewModel().getZoneScale());
        gridlessPath = new Path<>();
        gridlessPath.appendWaypoint(currentGridlessPoint);
      }
      renderer.repaint();
    } else {
      super.mousePressed(e);
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    super.mouseReleased(e);

    ZoneRenderer renderer = (ZoneRenderer) e.getSource();

    if (SwingUtilities.isLeftMouseButton(e)) {
      if (walker != null) {
        walker.close();
      }
      walker = null;
      gridlessPath = null;
      currentGridlessPoint = null;
      renderer.repaint();
    }
  }

  ////
  // MOUSE MOTION LISTENER
  @Override
  public void mouseDragged(MouseEvent e) {
    if (SwingUtilities.isRightMouseButton(e)) {
      super.mouseDragged(e);
    } else {
      ZoneRenderer renderer = (ZoneRenderer) e.getSource();
      if (walker != null && renderer.getZone().getGrid().getCapabilities().isPathingSupported()) {
        CellPoint cellPoint = renderer.getCellAt(new ScreenPoint(e.getX(), e.getY()));
        walker.replaceLastWaypoint(cellPoint);
      } else if (gridlessPath != null) {
        currentGridlessPoint =
            new ScreenPoint(e.getX(), e.getY())
                .convertToZone(renderer.getViewModel().getZoneScale());
      }
      renderer.repaint();
    }
  }
}
