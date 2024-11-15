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

import com.google.common.eventbus.Subscribe;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.tool.drawing.TopologyTool;
import net.rptools.maptool.client.tool.rig.Handle;
import net.rptools.maptool.client.tool.rig.Movable;
import net.rptools.maptool.client.tool.rig.Snap;
import net.rptools.maptool.client.tool.rig.WallTopologyRig;
import net.rptools.maptool.client.ui.zone.ZoneOverlay;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.topology.WallTopology;
import net.rptools.maptool.model.zones.TopologyChanged;

public class WallTopologyTool extends DefaultTool implements ZoneOverlay {
  private Point2D currentPosition =
      new Point2D.Double(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

  /** The current tool behaviour. Each operation enters a distinct mode so we don't cross-talk. */
  private ToolMode behaviour = new NilBehaviour();

  private final TopologyTool.MaskOverlay maskOverlay = new TopologyTool.MaskOverlay();

  private double getHandleRadius() {
    return 4.f;
  }

  private double getHandleSelectDistance() {
    var handleSelectDistance = getHandleRadius();
    var scale = renderer.getScale();
    if (scale < 1) {
      return handleSelectDistance / renderer.getScale();
    }
    return handleSelectDistance;
  }

  private double getWallSelectDistance() {
    // Wall select distance doesn't have to be identical to the handle select distance. We can tweak
    // this for better UX if it helps.
    return getHandleSelectDistance();
  }

  @Override
  public String getTooltip() {
    return "Draw some walls";
  }

  @Override
  public String getInstructions() {
    return "Point-and-click";
  }

  @Override
  public boolean isAvailable() {
    return MapTool.getPlayer().isGM();
  }

  @Override
  protected void attachTo(ZoneRenderer renderer) {
    super.attachTo(renderer);
    currentPosition = new Point2D.Double(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    var rig = new WallTopologyRig(getHandleSelectDistance(), getWallSelectDistance());
    rig.setWalls(getZone().getWalls());
    changeBehaviour(new BasicBehaviour(this, rig));

    new MapToolEventBus().getMainEventBus().register(this);
  }

  @Override
  protected void detachFrom(ZoneRenderer renderer) {
    new MapToolEventBus().getMainEventBus().unregister(this);
    changeBehaviour(new NilBehaviour());
    super.detachFrom(renderer);
  }

  @Override
  public void paintOverlay(ZoneRenderer renderer, Graphics2D g) {
    // Paint legacy masks. This isn't strictly necessary, but I want to do it so that users can
    // trace walls over masks if converting by hand.
    maskOverlay.paintOverlay(renderer, g);

    Graphics2D g2 = (Graphics2D) g.create();
    SwingUtil.useAntiAliasing(g2);
    g2.setComposite(AlphaComposite.SrcAtop);
    g2.translate(renderer.getViewOffsetX(), renderer.getViewOffsetY());
    g2.scale(renderer.getScale(), renderer.getScale());

    behaviour.paint(g2);
  }

  @Override
  protected void resetTool() {
    if (!behaviour.cancel()) {
      super.resetTool();
    }
    renderer.repaint();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    behaviour.mouseMoved(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (behaviour.shouldAllowMapDrag(e)) {
      super.mouseDragged(e);
    }
    behaviour.mouseMoved(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  @Override
  public void mousePressed(MouseEvent e) {
    super.mousePressed(e);
    behaviour.mousePressed(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    super.mouseReleased(e);
    behaviour.mouseReleased(updateCurrentPosition(e), getSnapMode(e), e);
    renderer.repaint();
  }

  private void changeBehaviour(ToolMode newBehaviour) {
    behaviour.deactivate();
    behaviour = newBehaviour;
    behaviour.activate();
  }

  private Point2D getCurrentPosition() {
    return currentPosition;
  }

  private Point2D updateCurrentPosition(MouseEvent e) {
    return currentPosition = ScreenPoint.convertToZone2d(renderer, e.getX(), e.getY());
  }

  private Snap getSnapMode(MouseEvent e) {
    if (SwingUtil.isControlDown(e)) {
      return Snap.fine(getZone().getGrid());
    }
    return Snap.none();
  }

  @Subscribe
  private void onTopologyChanged(TopologyChanged event) {
    var rig = new WallTopologyRig(getHandleSelectDistance(), getWallSelectDistance());
    rig.setWalls(getZone().getWalls());
    changeBehaviour(new BasicBehaviour(this, rig));
  }

  private interface ToolMode {
    void activate();

    void deactivate();

    /**
     * Cancels the current tool mode.
     *
     * @return {@code true} if the tool mode has its own cancel behaviour; {@code false} if the
     *     regular behaviour (revert to pointer tool) should apply.
     */
    boolean cancel();

    boolean shouldAllowMapDrag(MouseEvent e);

    void mouseMoved(Point2D point, Snap snapMode, MouseEvent event);

    void mousePressed(Point2D point, Snap snapMode, MouseEvent event);

    void mouseReleased(Point2D point, Snap snapMode, MouseEvent event);

    void paint(Graphics2D g2);
  }

  private static final class NilBehaviour implements ToolMode {
    @Override
    public void activate() {}

    @Override
    public void deactivate() {}

    @Override
    public boolean cancel() {
      return false;
    }

    @Override
    public void mouseMoved(Point2D point, Snap snapMode, MouseEvent event) {}

    @Override
    public void mousePressed(Point2D point, Snap snapMode, MouseEvent event) {}

    @Override
    public void mouseReleased(Point2D point, Snap snapMode, MouseEvent event) {}

    @Override
    public boolean shouldAllowMapDrag(MouseEvent e) {
      return true;
    }

    @Override
    public void paint(Graphics2D g2) {}
  }

  private abstract static class ToolModeBase implements ToolMode {
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
    public void mouseMoved(Point2D point, Snap snapMode, MouseEvent event) {}

    @Override
    public void mousePressed(Point2D point, Snap snapMode, MouseEvent event) {}

    @Override
    public void mouseReleased(Point2D point, Snap snapMode, MouseEvent event) {}

    @Override
    public boolean shouldAllowMapDrag(MouseEvent e) {
      return true;
    }

    /**
     * Get a special paint for the handle if one is applicable.
     *
     * @param handle The handle to get the fill paint for.
     * @return The paint for the handle, or {@code null} if this behaviour has no opinion.
     */
    protected Paint getHandleFill(Handle<WallTopology.Vertex> handle) {
      return Color.white;
    }

    protected BasicStroke getHandleStroke() {
      return new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    protected Paint getWallFill(Movable<WallTopology.Wall> wall) {
      return AppStyle.wallTopologyColor;
    }

    protected void paintHandle(Graphics2D g2, Point2D point, Paint fill) {
      var handleRadius = tool.getHandleRadius();
      var handleOutlineStroke = getHandleStroke();
      var handleOutlineColor = Color.black;

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

      var wallStroke = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
      var wallOutlineColor = AppStyle.wallTopologyOutlineColor;
      var wallOutlineStroke =
          new BasicStroke(
              wallStroke.getLineWidth() + 2f, wallStroke.getEndCap(), wallStroke.getLineJoin());
      var walls = rig.getWallsWithin(bounds);
      for (var wall : walls) {
        var wallFill = getWallFill(wall);
        // Draw it twice to get a black border effect. It's proven itself to be cheaper than using
        // `Stroke.createStokedShape()` when there are many walls.
        var from = wall.getSource().from().getPosition();
        var to = wall.getSource().to().getPosition();
        var shape = new Path2D.Double();
        shape.moveTo(from.getX(), from.getY());
        shape.lineTo(to.getX(), to.getY());

        if (from.distance(to) > 4 * handleRadius) {
          // Draw a fun little arrow indicating the wall direection.
          // theta increases clockwise.
          var theta = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
          var arrowSize = handleRadius;
          var vertex =
              new Point2D.Double(
                  to.getX() - 4 * handleRadius * Math.cos(theta),
                  to.getY() - 4 * handleRadius * Math.sin(theta));
          shape.moveTo(vertex.getX(), vertex.getY());
          shape.lineTo(
              vertex.getX() - arrowSize * Math.cos(theta - Math.PI / 4),
              vertex.getY() - arrowSize * Math.sin(theta - Math.PI / 4));
          shape.moveTo(vertex.getX(), vertex.getY());
          shape.lineTo(
              vertex.getX() - arrowSize * Math.cos(theta + Math.PI / 4),
              vertex.getY() - arrowSize * Math.sin(theta + Math.PI / 4));
        }

        g2.setStroke(wallOutlineStroke);
        g2.setPaint(wallOutlineColor);
        g2.draw(shape);
        g2.setStroke(wallStroke);
        g2.setPaint(wallFill);
        g2.draw(shape);
      }

      var vertices = rig.getHandlesWithin(bounds);
      for (var handle : vertices) {
        paintHandle(g2, handle.getPosition(), getHandleFill(handle));
      }

      if (false) {
        var currentPoint = tool.getCurrentPosition();
        var handleSelectDistance = tool.getHandleSelectDistance();
        var handleSelector =
            new Ellipse2D.Double(
                currentPoint.getX() - handleSelectDistance,
                currentPoint.getY() - handleSelectDistance,
                2 * handleSelectDistance,
                2 * handleSelectDistance);
        g2.setPaint(new Color(0, 0, 0, 64));
        g2.fill(handleSelector);
        g2.setPaint(new Color(0, 0, 0, 255));
        g2.setStroke(
            new BasicStroke(
                (float) (1 / tool.renderer.getScale()),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g2.draw(handleSelector);
      }
    }
  }

  private static final class BasicBehaviour extends ToolModeBase {
    // The hovered handle. This is the candidate for any pending mouse event. E.g., a mouse pressed
    // can start a drag operation on it.
    private @Nullable WallTopologyRig.Element<?> currentElement;
    private @Nullable Point2D potentialSplitPoint;

    public BasicBehaviour(WallTopologyTool tool, WallTopologyRig rig) {
      super(tool, rig);
    }

    @Override
    public void activate() {
      currentElement = rig.getNearbyElement(tool.getCurrentPosition()).orElse(null);
    }

    @Override
    public void mouseMoved(Point2D point, Snap snapMode, MouseEvent event) {
      currentElement = rig.getNearbyElement(point).orElse(null);

      if (event.isAltDown() && currentElement instanceof WallTopologyRig.MovableWall movableWall) {
        potentialSplitPoint = rig.getSplitPoint(movableWall, point);
      } else {
        potentialSplitPoint = null;
      }
    }

    @Override
    public void mousePressed(Point2D point, Snap snapMode, MouseEvent event) {
      if (SwingUtilities.isLeftMouseButton(event)) {
        if (currentElement == null) {
          // Grabbed blank space. Start a new wall, unless the delete modifier is held.
          if (!SwingUtil.isShiftDown(event)) {
            var newHandle = rig.addDegenerateWall(snapMode.snap(point));
            tool.changeBehaviour(
                new DragVertexBehaviour(tool, rig, newHandle, newHandle.getPosition(), true));
          }
        } else if (SwingUtil.isShiftDown(event)) {
          currentElement.delete();
          MapTool.serverCommand().replaceWalls(tool.getZone(), rig.commit());
          currentElement = rig.getNearbyElement(tool.getCurrentPosition()).orElse(null);
        } else if (event.isAltDown()) {
          // Start drawing a new wall using the current handle or wall.
          switch (currentElement) {
            case WallTopologyRig.MovableVertex movableVertex -> {
              var newVertex =
                  rig.addControlPoint(movableVertex.getSource(), movableVertex.getPosition());
              tool.changeBehaviour(new DragVertexBehaviour(tool, rig, newVertex, point, true));
            }
            case WallTopologyRig.MovableWall movableWall -> {
              var wallSplit = rig.splitAt(movableWall, point);
              var dragHandle = rig.addControlPoint(wallSplit.getSource(), wallSplit.getPosition());
              tool.changeBehaviour(new DragVertexBehaviour(tool, rig, dragHandle, point, false));
            }
          }
        } else {
          // No special modifiers. Grab the handle, i.e., start a drag.
          switch (currentElement) {
            case WallTopologyRig.MovableVertex movableVertex -> tool.changeBehaviour(
                new DragVertexBehaviour(tool, rig, movableVertex, point, false));
            case WallTopologyRig.MovableWall movableWall -> tool.changeBehaviour(
                new DragWallBehaviour(tool, rig, movableWall, point));
          }
        }
      }
    }

    @Override
    public Paint getHandleFill(Handle<WallTopology.Vertex> handle) {
      // TODO Blue if alt is down.
      if (currentElement != null && currentElement.isForSameElement(handle)) {
        return Color.green;
      }
      return super.getHandleFill(handle);
    }

    @Override
    protected Paint getWallFill(Movable<WallTopology.Wall> wall) {
      if (currentElement != null && currentElement.isForSameElement(wall)) {
        return AppStyle.selectedWallTopologyColor;
      }
      return super.getWallFill(wall);
    }

    @Override
    public void paint(Graphics2D g2) {
      super.paint(g2);

      if (potentialSplitPoint != null) {
        paintHandle(g2, potentialSplitPoint, Color.blue);
      }
    }
  }

  private abstract static class DragBehaviour<T extends Movable<?>> extends ToolModeBase {
    // Flag used to avoid adding degenerate walls if the user randomly clicks nowhere.
    protected boolean nonTrivialChange;
    protected final Point2D originalMousePoint;
    protected T movable;

    protected DragBehaviour(
        WallTopologyTool tool, WallTopologyRig rig, T movable, Point2D originalMousePoint) {
      super(tool, rig);
      this.nonTrivialChange = false;
      this.movable = movable;
      this.originalMousePoint = originalMousePoint;
    }

    protected void setCurrentHandle(T newHandle, Point2D mousePoint) {
      this.originalMousePoint.setLocation(mousePoint);
      this.movable = newHandle;
    }

    @Override
    public final boolean cancel() {
      // Revert to the original.
      rig.setWalls(tool.getZone().getWalls());
      tool.changeBehaviour(new BasicBehaviour(tool, rig));
      return true;
    }

    @Override
    public void mouseReleased(Point2D point, Snap snapMode, MouseEvent event) {
      if (SwingUtilities.isLeftMouseButton(event)) {
        complete(point);
      }
    }

    @Override
    public void mouseMoved(Point2D point, Snap snapMode, MouseEvent event) {
      nonTrivialChange = true;
      movable.displace(
          point.getX() - originalMousePoint.getX(),
          point.getY() - originalMousePoint.getY(),
          snapMode);
      afterMove(event);
    }

    @Override
    public Paint getHandleFill(Handle<WallTopology.Vertex> handle) {
      if (this.movable.isForSameElement(handle)) {
        return Color.green;
      }
      return super.getHandleFill(handle);
    }

    protected final void complete(Point2D mousePoint) {
      if (!nonTrivialChange) {
        cancel();
        return;
      }

      movable.applyMove();
      beforeCommit();
      MapTool.serverCommand().replaceWalls(tool.getZone(), rig.commit());
      tool.changeBehaviour(new BasicBehaviour(tool, rig));
    }

    protected abstract void beforeCommit();

    protected abstract void afterMove(MouseEvent event);
  }

  private static final class DragWallBehaviour extends DragBehaviour<Movable<WallTopology.Wall>> {
    public DragWallBehaviour(
        WallTopologyTool tool,
        WallTopologyRig rig,
        Movable<WallTopology.Wall> wall,
        Point2D originalMousePoint) {
      super(tool, rig, wall, originalMousePoint);
    }

    @Override
    public boolean shouldAllowMapDrag(MouseEvent e) {
      return true;
    }

    @Override
    protected void afterMove(MouseEvent event) {
      // Nothing to do.
    }

    @Override
    protected void beforeCommit() {}

    @Override
    protected Paint getWallFill(Movable<WallTopology.Wall> wall) {
      if (wall.isForSameElement(this.movable)) {
        return AppStyle.selectedWallTopologyColor;
      }
      return super.getWallFill(wall);
    }
  }

  private static final class DragVertexBehaviour
      extends DragBehaviour<WallTopologyRig.MovableVertex> {
    private @Nullable WallTopologyRig.Element<?> connectTo;

    /**
     * The constant displacement from the mouse to the vertex. Will remain constant even as we add
     * vertices or toggle snap-to-grid on or off.
     */
    private final double offsetX, offsetY;

    public DragVertexBehaviour(
        WallTopologyTool tool,
        WallTopologyRig rig,
        WallTopologyRig.MovableVertex handle,
        Point2D originalMousePoint,
        boolean cancelIfTrivial) {
      super(tool, rig, handle, originalMousePoint);

      this.offsetX = handle.getPosition().getX() - originalMousePoint.getX();
      this.offsetY = handle.getPosition().getY() - originalMousePoint.getY();

      this.nonTrivialChange = !cancelIfTrivial;
    }

    private void findConnectToHandle(MouseEvent event) {
      connectTo = null;

      if (event.isAltDown()) {
        // Add some leniency so the snapping feels good.
        var extraSpace = 4.f;
        connectTo =
            rig.getNearbyElement(
                    tool.getCurrentPosition(),
                    extraSpace,
                    (WallTopologyRig.Element<?> other) -> {
                      switch (other) {
                        case WallTopologyRig.MovableVertex movableVertex -> {
                          return !movable.isForSameElement(movableVertex);
                        }
                        case WallTopologyRig.MovableWall movableWall -> {
                          return !movable.isForSameElement(movableWall.getFrom())
                              && !movable.isForSameElement(movableWall.getTo());
                        }
                      }
                    })
                .orElse(null);

        switch (connectTo) {
          case null -> {
            /* Nothing to do */
          }
          case WallTopologyRig.MovableVertex movableVertex -> {
            // Snap the handle to the vertex it would connect to.
            movable.moveTo(movableVertex.getPosition());
          }
          case WallTopologyRig.MovableWall movableWall -> {
            // Snap the handle to where we would split the wall.
            movable.moveTo(rig.getSplitPoint(movableWall, tool.getCurrentPosition()));
          }
        }
      }
    }

    @Override
    public boolean shouldAllowMapDrag(MouseEvent e) {
      // Map drag conflicts with our extend action.
      return false;
    }

    @Override
    public void mousePressed(Point2D point, Snap snapMode, MouseEvent event) {
      if (SwingUtilities.isRightMouseButton(event)) {
        nonTrivialChange = true;
        var snapped = snapMode.snap(point);
        tryMerge();

        var newHandle = this.rig.addControlPoint(movable.getSource(), snapped);

        // Maintain the original offset regardless of what the actual cursor position is now.
        var fakeMousePosition =
            new Point2D.Double(
                newHandle.getPosition().getX() - offsetX, newHandle.getPosition().getY() - offsetY);

        setCurrentHandle(newHandle, fakeMousePosition);
        findConnectToHandle(event);
      }
    }

    @Override
    protected void afterMove(MouseEvent event) {
      findConnectToHandle(event);
    }

    @Override
    protected void beforeCommit() {
      tryMerge();
    }

    @Override
    protected Paint getWallFill(Movable<WallTopology.Wall> wall) {
      if (connectTo != null && connectTo.isForSameElement(wall)) {
        return AppStyle.selectedWallTopologyColor;
      }
      return super.getWallFill(wall);
    }

    @Override
    public Paint getHandleFill(Handle<WallTopology.Vertex> handle) {
      if (connectTo != null) {
        // Both the connecting handle and current handle should show as connecting, i.e., blue.
        if (movable.isForSameElement(handle) || connectTo.isForSameElement(handle)) {
          return Color.blue;
        }
      }
      return super.getHandleFill(handle);
    }

    private void tryMerge() {
      movable.applyMove();

      switch (connectTo) {
        case null -> {
          /* Do nothing */
        }
        case WallTopologyRig.MovableVertex movableVertex -> {
          var newHandle = rig.mergeVertices(movableVertex, movable);
          // Current handle's vertex may have just been eliminated. Use the returned one instead.
          this.setCurrentHandle(newHandle, newHandle.getPosition());
        }
        case WallTopologyRig.MovableWall movableWall -> {
          // Split the wall, then merge with the new vertex.
          var splitVertex = rig.splitAt(movableWall, movable.getPosition());
          var newVertex = rig.mergeVertices(splitVertex, movable);
          // Vertex may have just been eliminated. Use the returned one instead.
          this.setCurrentHandle(newVertex, newVertex.getPosition());
        }
      }
    }
  }
}
