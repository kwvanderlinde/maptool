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
package net.rptools.maptool.client.ui.zone.renderer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.RenderPathWorker;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Represents a movement set */
public class SelectionSet {

  private final ZoneRenderer renderer;
  private final Logger log = LogManager.getLogger(SelectionSet.class);

  private final Set<GUID> selectionSet = new HashSet<GUID>();
  private final GUID keyToken;
  private final String playerId;
  private ZoneWalker walker;
  private final Token token;

  private Path<ZonePoint> gridlessPath;

  /** The initial location of the key token's drag anchor. */
  private final ZonePoint startPoint;

  /** The current location of the key token's drag anchor. */
  private final ZonePoint currentPoint;

  private RenderPathWorker renderPathTask;
  private ExecutorService renderPathThreadPool = Executors.newSingleThreadExecutor();

  /**
   * @param playerId The ID of the player performing the movement.
   * @param tokenGUID The ID of the leader token, i.e., the token that will pathfind.
   * @param selectionList The IDs of all tokens being moved.
   */
  public SelectionSet(
      ZoneRenderer renderer, String playerId, GUID tokenGUID, Set<GUID> selectionList) {
    this.renderer = renderer;
    selectionSet.addAll(selectionList);
    keyToken = tokenGUID;
    this.playerId = playerId;

    token = renderer.zone.getToken(tokenGUID);

    var anchorPoint = token.getDragAnchor(renderer.zone);

    startPoint = new ZonePoint(anchorPoint);
    currentPoint = new ZonePoint(anchorPoint);

    if (token.isSnapToGrid() && renderer.zone.getGrid().getCapabilities().isSnapToGridSupported()) {
      if (renderer.zone.getGrid().getCapabilities().isPathingSupported()) {
        CellPoint tokenPoint = renderer.zone.getGrid().convert(currentPoint);

        walker = renderer.zone.getGrid().createZoneWalker();
        walker.setFootprint(token.getFootprint(renderer.zone.getGrid()));
        walker.setWaypoints(tokenPoint, tokenPoint);
      }
    } else {
      gridlessPath = new Path<>();
      gridlessPath.appendWaypoint(currentPoint);
    }
  }

  public ZonePoint getKeyTokenDragAnchorPosition() {
    return currentPoint;
  }

  /**
   * @return path computation.
   */
  public @Nonnull Path<ZonePoint> getGridlessPath() {
    var result = gridlessPath.copy();
    result.appendWaypoint(currentPoint);
    return result;
  }

  public ZoneWalker getWalker() {
    return walker;
  }

  public GUID getKeyToken() {
    return keyToken;
  }

  public Set<GUID> getTokens() {
    return selectionSet;
  }

  public boolean contains(Token token) {
    return selectionSet.contains(token.getId());
  }

  /** Aborts the movement for this selection. */
  public void cancel() {
    walker.close();

    renderPathTask.cancel(true);
  }

  // This is called when movement is committed/done. It'll let the last thread either finish or
  // timeout
  public void renderFinalPath() {
    walker.close();

    if (renderer.zone.getGrid().getCapabilities().isPathingSupported()
        && token.isSnapToGrid()
        && renderPathTask != null) {

      log.trace("Waiting on Path Rendering... ");
      while (true) {
        try {
          renderPathTask.get();
          break;
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
          log.error("Error while waiting for task to finish", e);
          break;
        }
      }
    }
  }

  public void update(ZonePoint newAnchorPosition) {
    currentPoint.x = newAnchorPosition.x;
    currentPoint.y = newAnchorPosition.y;

    if (renderer.zone.getGrid().getCapabilities().isPathingSupported() && token.isSnapToGrid()) {
      CellPoint point = renderer.zone.getGrid().convert(currentPoint);
      // walker.replaceLastWaypoint(point, restrictMovement); // OLD WAY

      // New way threaded, off the swing UI thread...
      if (renderPathTask != null) {
        renderPathTask.cancel(true);
      }

      boolean restrictMovement =
          MapTool.getServerPolicy().isUsingAstarPathfinding() && token.getLayer().supportsWalker();

      Set<Token.TerrainModifierOperation> terrainModifiersIgnored =
          token.getTerrainModifiersIgnored();

      renderPathTask =
          new RenderPathWorker(
              walker, point, restrictMovement, terrainModifiersIgnored, token, renderer);
      renderPathThreadPool.execute(renderPathTask);
    }
  }

  /**
   * Add the waypoint if it is a new waypoint. If it is an old waypoint remove it.
   *
   * @param location The point where the waypoint is toggled.
   */
  public void toggleWaypoint(ZonePoint location) {
    if (walker != null && token.isSnapToGrid() && renderer.getZone().getGrid() != null) {
      walker.toggleWaypoint(renderer.getZone().getGrid().convert(location));
    } else {
      gridlessPath.appendWaypoint(location);
    }
  }

  /**
   * Retrieves the last waypoint, or if there isn't one then the start point of the first path
   * segment.
   *
   * @return the ZonePoint.
   */
  public ZonePoint getLastWaypoint() {
    ZonePoint zp;
    if (walker != null && token.isSnapToGrid() && renderer.getZone().getGrid() != null) {
      CellPoint cp = walker.getLastPoint();

      if (cp == null) {
        // log.info("cellpoint is null! FIXME! You have Walker class updating outside of
        // thread..."); // Why not save last waypoint to this class?
        cp = renderer.zone.getGrid().convert(token.getDragAnchor(renderer.zone));
        // log.info("So I set it to: " + cp);
      }

      zp = renderer.getZone().getGrid().convert(cp);
    } else {
      // Gridless path will never be empty if set.
      zp = gridlessPath.getWayPointList().getLast();
    }
    return zp;
  }

  public int getOffsetX() {
    return currentPoint.x - startPoint.x;
  }

  public int getOffsetY() {
    return currentPoint.y - startPoint.y;
  }

  public String getPlayerId() {
    return playerId;
  }
}
