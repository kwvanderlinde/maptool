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
package net.rptools.maptool.client.tool.editing;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.tool.DefaultTool;
import net.rptools.maptool.client.tool.WallTopologyTool;
import net.rptools.maptool.client.tool.rig.Handle;
import net.rptools.maptool.client.tool.rig.Movable;
import net.rptools.maptool.client.tool.rig.WallTopologyRig;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.topology.Vertex;
import net.rptools.maptool.model.topology.Wall;
import net.rptools.maptool.model.topology.WallTopology;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.math.Vector2D;

public class WallTool extends DefaultTool implements ZoneOverlay {
  private WallTopology walls = new WallTopology();
  private final Map<Vertex, VertexElement> vertexElements = new HashMap<>();
  private final Map<Wall, WallElement> wallElements = new HashMap<>();

  private Point2D currentPosition =
      new Point2D.Double(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

  /** The current tool behaviour. Each operation enters a distinct mode so we don't cross-talk. */
  private ToolMode mode = new NilToolMode();

  public WallTool() {
    super();

    keyActionMap.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            mode.delete();
            renderer.repaint();
          }
        });
  }

  private void changeToolMode(ToolMode newMode) {
    mode.deactivate();
    mode = newMode;
    mode.activate();
  }

  @Override
  public String getTooltip() {
    return "tool.walltopology.tooltip";
  }

  @Override
  public String getInstructions() {
    return "tool.walltopology.instructions";
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  @Override
  protected void attachTo(ZoneRenderer renderer) {
    super.attachTo(renderer);
    currentPosition = new Point2D.Double(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    var rig = new WallTopologyRig(this::getHandleSelectDistance);
    rig.setWalls(getZone().getWalls());
    changeToolMode(new WallTopologyTool.BasicToolMode(this, rig));

    MapTool.getFrame().showControlPanel(controlPanel.getView().getRootComponent());

    new MapToolEventBus().getMainEventBus().register(this);
  }

  @Override
  protected void detachFrom(ZoneRenderer renderer) {
    new MapToolEventBus().getMainEventBus().unregister(this);

    MapTool.getFrame().removeControlPanel();

    changeToolMode(new NilToolMode());
    super.detachFrom(renderer);
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    // TODO Consider painting legacy maps.

    Graphics2D g2 = (Graphics2D) g.create();
    g2.transform(renderer.getViewModel().getZoneScale().toScreenTransform());
    SwingUtil.useAntiAliasing(g2);
    g2.setComposite(AlphaComposite.SrcOver);

    mode.paint(g2);
  }

  @Override
  protected void resetTool() {
    if (!mode.cancel()) {
      super.resetTool();
    }
    renderer.repaint();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    mode.mouseMoved(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    var skipDefault = mode.mouseDragged(updateCurrentPosition(e), getSnapMode(e), e);
    if (!skipDefault) {
      super.mouseDragged(e);
    }
    renderer.repaint();
  }

  @Override
  public void mousePressed(MouseEvent e) {
    super.mousePressed(e);
    mode.mousePressed(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    super.mouseClicked(e);
    mode.mouseClicked(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    super.mouseReleased(e);
    mode.mouseReleased(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  public sealed interface Element extends EditableElement {}

  public final class VertexElement implements Element {
    private final Vertex vertex;

    /**
     * Supports smooth move operations in the presence of snapping.
     *
     * <p>Will only be updated at the end of a move.
     */
    private final Point2D.Double originalPosition;

    public VertexElement(Vertex vertex) {
      this.vertex = vertex;
      this.originalPosition = new Point2D.Double();
      this.originalPosition.setLocation(vertex.position());
    }

    @Override
    public List<? extends EditableElement> getChildren() {
      return List.of();
    }

    public void moveTo(Point2D point) {
      vertex.position(point.getX(), point.getY());
    }

    @Override
    public void setDisplacement(double dx, double dy, Snap snapMode) {
      Point2D point =
          new Point2D.Double(originalPosition.getX() + dx, originalPosition.getY() + dy);
      point = snapMode.snap(point);
      this.vertex.position(point.getX(), point.getY());
    }

    @Override
    public void applyMove() {
      this.originalPosition.setLocation(this.vertex.position());
    }

    @Override
    public void delete() {
      walls.removeVertex(this.vertex);
      vertexElements.remove(this.vertex);
    }
  }

  public final class WallElement implements Element {
    private final Wall wall;
    private final LineSegment originalSegment;

    public WallElement(Wall wall) {
      this.wall = wall;
      this.originalSegment = walls.asLineSegment(wall);
    }

    public VertexElement getFrom() {
      return new VertexElement(walls.getFrom(wall));
    }

    public VertexElement getTo() {
      return new VertexElement(walls.getTo(wall));
    }

    @Override
    public List<? extends EditableElement> getChildren() {
      return List.of();
    }

    @Override
    public void setDisplacement(double dx, double dy, Snap snapMode) {
      var newFrom = new Point2D.Double(originalSegment.p0.x + dx, originalSegment.p0.y + dy);
      var newTo = new Point2D.Double(originalSegment.p1.x + dy, originalSegment.p1.y + dy);

      var snappedFrom = snapMode.snap(newFrom);
      var fromOffsetX = snappedFrom.getX() - newFrom.getX();
      var fromOffsetY = snappedFrom.getY() - newFrom.getY();

      var snappedTo = snapMode.snap(newTo);
      var toOffsetX = snappedTo.getX() - newTo.getX();
      var toOffsetY = snappedTo.getY() - newTo.getY();

      if ((fromOffsetX * fromOffsetX + fromOffsetY * fromOffsetY)
          < (toOffsetX * toOffsetX + toOffsetY * toOffsetY)) {
        getFrom().moveTo(new Point2D.Double(snappedFrom.getX(), snappedFrom.getY()));
        getTo()
            .moveTo(
                new Point2D.Double(
                    snappedFrom.getX() + (originalSegment.p1.x - originalSegment.p0.x),
                    snappedFrom.getY() + (originalSegment.p1.y - originalSegment.p0.y)));
      } else {
        getTo().moveTo(new Point2D.Double(snappedTo.getX(), snappedTo.getY()));
        getFrom()
            .moveTo(
                new Point2D.Double(
                    snappedTo.getX() + (originalSegment.p0.x - originalSegment.p1.x),
                    snappedTo.getY() + (originalSegment.p0.y - originalSegment.p1.y)));
      }
    }

    @Override
    public void applyMove() {
      originalSegment.setCoordinates(walls.asLineSegment(wall));
    }

    @Override
    public void delete() {
      walls.removeWall(wall);
      wallElements.remove(wall);
    }
  }

  /**
   * Base class for fully functional tools.
   *
   * <p>This keeps track of the tool itself along with the rig that the tool mode can use.
   */
  private abstract static class ToolModeBase implements WallTopologyTool.ToolMode {
    protected final WallTopologyTool tool;
    protected final WallTopologyRig rig;

    protected ToolModeBase(WallTopologyTool tool, WallTopologyRig rig) {
      this.tool = tool;
      this.rig = rig;
    }

    @Override
    public void activate() {}

    @Override
    public void deactivate() {}

    @Override
    public boolean cancel() {
      return false;
    }

    @Override
    public void delete() {}

    protected void deleteSelectedWalls() {
      var toBeDeleted = new ArrayList<>(tool.getSelectedWalls());
      tool.clearSelectedWalls();
      for (var wall : toBeDeleted) {
        wall.delete();
      }
      MapTool.serverCommand().replaceWalls(tool.getZone(), rig.commit());
    }

    /**
     * Applies the default behaviour of syncing the changed wall.
     *
     * @param wall The modified wall.
     */
    @Override
    public void onWallChanged(WallTopologyRig.MovableWall wall) {
      MapTool.serverCommand().updateWall(tool.getZone(), wall.getSource());
    }

    @Override
    public void mouseMoved(
        Point2D point, net.rptools.maptool.client.tool.rig.Snap snapMode, MouseEvent event) {}

    @Override
    public boolean mouseDragged(
        Point2D point, net.rptools.maptool.client.tool.rig.Snap snapMode, MouseEvent event) {
      return false;
    }

    @Override
    public void mousePressed(
        Point2D point, net.rptools.maptool.client.tool.rig.Snap snapMode, MouseEvent event) {}

    @Override
    public void mouseClicked(
        Point2D point, net.rptools.maptool.client.tool.rig.Snap snapMode, MouseEvent event) {}

    @Override
    public void mouseReleased(
        Point2D point, net.rptools.maptool.client.tool.rig.Snap snapMode, MouseEvent event) {}

    protected Paint getWallStrokePaint(WallTopologyRig.MovableWall wall) {
      if (tool.isSelectedWall(wall)) {
        return AppStyle.selectedWallOutlineColor;
      }
      return AppStyle.wallTopologyOutlineColor;
    }

    /**
     * Get a special paint for the handle if one is applicable.
     *
     * @param handle The handle to get the fill paint for.
     * @return The paint for the handle.
     */
    protected Paint getHandleFill(Handle<Vertex> handle) {
      return Color.white;
    }

    protected Paint getWallFill(Movable<Wall> wall) {
      return AppStyle.wallTopologyColor;
    }

    protected void paintHandle(Graphics2D g2, Point2D point, Paint fill) {
      var handleRadius = tool.getHandleRadius();
      var handleOutlineStroke =
          new BasicStroke(
              (float) (handleRadius / 4.), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
      var handleOutlineColor = AppStyle.wallTopologyOutlineColor;

      var shape =
          new Ellipse2D.Double(
              point.getX() - handleRadius,
              point.getY() - handleRadius,
              2 * handleRadius,
              2 * handleRadius);

      g2.setPaint(fill);
      g2.fill(shape);

      g2.setStroke(handleOutlineStroke);
      g2.setPaint(handleOutlineColor);
      g2.draw(shape);
    }

    @Override
    public void paint(Graphics2D g2) {
      var handleRadius = tool.getHandleRadius();

      Rectangle2D bounds = g2.getClipBounds().getBounds2D();
      // Pad the bounds by a bit so handles whose center is just outside will still show up.
      var padding = handleRadius;
      bounds.setRect(
          bounds.getX() - padding,
          bounds.getY() - padding,
          bounds.getWidth() + 2 * padding,
          bounds.getHeight() + 2 * padding);

      // region Wall decorations.
      // These are mere prototypes that sit at (0, 0). They will be instanced wherever they are
      // needed during painting.
      var directionalArrow = buildDirectionalArrowDecoration();
      var sourceDecoration = buildWallSourceDecoration();
      var targetDecoration = buildWallTargetDecoration();
      // endregion

      var wallStroke =
          new BasicStroke(
              (float) (2 * tool.getWallHalfWidth()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
      var wallOutlineColor = AppStyle.wallTopologyOutlineColor;
      var wallOutlineStroke =
          new BasicStroke(
              (float) (wallStroke.getLineWidth() * 1.5),
              wallStroke.getEndCap(),
              wallStroke.getLineJoin());
      var decorationStroke =
          new BasicStroke(1.5f, wallStroke.getEndCap(), wallStroke.getLineJoin());
      var walls = rig.getWallsIntersecting(bounds);
      for (var wall : walls) {
        var asSegment = wall.asLineSegment();
        var asVector = Vector2D.create(asSegment.p0, asSegment.p1);

        var lengthSquared = asVector.lengthSquared();
        if (lengthSquared <= 4 * handleRadius * handleRadius) {
          // The wall is so small it isn't worth drawing the wall or its decorations.
          continue;
        }

        var angle = asVector.angle();
        var normVector = asVector.normalize();

        {
          // Draw the wall itself.
          var shape = new Path2D.Double();
          shape.moveTo(asSegment.p0.getX(), asSegment.p0.getY());
          shape.lineTo(asSegment.p1.getX(), asSegment.p1.getY());

          // Draw it twice to get a black border effects without having to stroke the path.
          g2.setStroke(wallOutlineStroke);
          g2.setPaint(getWallStrokePaint(wall));
          g2.draw(shape);

          g2.setStroke(wallStroke);
          g2.setPaint(getWallFill(wall));
          g2.draw(shape);
        }

        // Next up: decorations
        g2.setStroke(decorationStroke);
        g2.setPaint(wallOutlineColor);
        {
          // Draw a tiny arrow head to indicate the target end of the wall.
          var preTransform = g2.getTransform();

          var point = normVector.multiply(-0.75 * handleRadius).translate(asSegment.p1);
          g2.translate(point.getX(), point.getY());
          g2.rotate(angle);

          g2.draw(targetDecoration);
          g2.fill(targetDecoration);

          g2.setTransform(preTransform);
        }
        if (wall.getSource().data().direction() != Wall.Direction.Both) {
          // Draw an arrow through the midpoint of the wall to indicate its direction.
          var preTransform = g2.getTransform();
          var wallMidpoint = asSegment.midPoint();
          g2.translate(wallMidpoint.getX(), wallMidpoint.getY());
          g2.rotate(angle);
          if (wall.getSource().data().direction() == Wall.Direction.Left) {
            g2.scale(-1, -1);
          }

          g2.draw(directionalArrow);

          g2.setTransform(preTransform);
        }

        if (lengthSquared > 12 * handleRadius * handleRadius) {
          // Draw a bar to indicate the source end of the wall.
          // This is optional if the wall is on the small side.
          var preTransform = g2.getTransform();
          var barCenter = normVector.multiply(1.5 * handleRadius).translate(asSegment.p0);
          g2.translate(barCenter.getX(), barCenter.getY());
          g2.rotate(angle);

          g2.draw(sourceDecoration);

          g2.setTransform(preTransform);
        }
      }

      var vertices = rig.getHandlesWithin(bounds);
      for (var handle : vertices) {
        paintHandle(g2, handle.getPosition(), getHandleFill(handle));
      }
    }

    /**
     * Builds a shape indicating the directionality of a wall.
     *
     * @return An arrow through (0, 0) pointing toward the positive y-axis.
     */
    protected Shape buildDirectionalArrowDecoration() {
      var result = new Path2D.Double();
      var arrowLength = 8 * tool.getWallHalfWidth();
      var arrowTip = new Point2D.Double(0, arrowLength * 1.5);
      result.moveTo(arrowTip.getX(), arrowTip.getY());
      result.lineTo(0, -arrowLength * 0.5);
      result.moveTo(arrowTip.getX(), arrowTip.getY());
      result.lineTo(-arrowLength * 0.25, arrowTip.getY() - arrowLength * 0.5);
      result.moveTo(arrowTip.getX(), arrowTip.getY());
      result.lineTo(arrowLength * 0.25, arrowTip.getY() - arrowLength * 0.5);
      return result;
    }

    protected Shape buildWallSourceDecoration() {
      var result = new Path2D.Double();
      var halfHeight = 4 * tool.getWallHalfWidth();
      result.moveTo(0, -halfHeight);
      result.lineTo(0, halfHeight);
      return result;
    }

    protected Shape buildWallTargetDecoration() {
      var result = new Path2D.Double();
      var halfHeight = 4 * tool.getWallHalfWidth();
      result.moveTo(0, 0);
      result.lineTo(-halfHeight, halfHeight);
      result.quadTo(-0.75 * halfHeight, 0, -halfHeight, -halfHeight);
      result.closePath();

      return result;
    }
  }
}
