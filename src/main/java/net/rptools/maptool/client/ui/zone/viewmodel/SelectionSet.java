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
package net.rptools.maptool.client.ui.zone.viewmodel;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.rptools.maptool.client.ui.zone.RenderPathWorker;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Represents a movement set */
public class SelectionSet {

  private final Logger log = LogManager.getLogger(SelectionSet.class);

  private final Zone zone;
  private final Set<GUID> selectionSet = new HashSet<GUID>();
  private final GUID keyToken;
  private final String playerId;
  private ZoneWalker walker;
  private final Token token;
  private Path<ZonePoint> gridlessPath;
  /** Pixel distance (x) from keyToken's origin. */
  private int offsetX;
  /** Pixel distance (y) from keyToken's origin. */
  private int offsetY;
  // private boolean restrictMovement = true;
  private RenderPathWorker renderPathTask;
  private ExecutorService renderPathThreadPool = Executors.newSingleThreadExecutor();

  public SelectionSet(Zone zone, String playerId, GUID tokenGUID, Set<GUID> selectionList) {
    this.zone = zone;
    selectionSet.addAll(selectionList);
    keyToken = tokenGUID;
    this.playerId = playerId;

    token = zone.getToken(tokenGUID);

    if (token.isSnapToGrid() && zone.getGrid().getCapabilities().isSnapToGridSupported()) {
      if (zone.getGrid().getCapabilities().isPathingSupported()) {
        CellPoint tokenPoint = zone.getGrid().convert(new ZonePoint(token.getX(), token.getY()));

        walker = zone.getGrid().createZoneWalker();
        walker.setFootprint(token.getFootprint(zone.getGrid()));
        walker.setWaypoints(tokenPoint, tokenPoint);
      }
    } else {
      gridlessPath = new Path<ZonePoint>();
      gridlessPath.addPathCell(new ZonePoint(token.getX(), token.getY()));
    }
  }

  /** @return path computation. */
  public Path<ZonePoint> getGridlessPath() {
    return gridlessPath;
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

  // This is called when movement is committed/done. It'll let the last thread either finish or
  // timeout
  public void renderFinalPath() {
    if (zone.getGrid().getCapabilities().isPathingSupported()
        && token.isSnapToGrid()
        && renderPathTask != null) {
      while (!renderPathTask.isDone()) {
        log.trace("Waiting on Path Rendering... ");
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void setOffset(int x, int y, boolean restrictMovement, Runnable onComplete) {
    offsetX = x;
    offsetY = y;

    ZonePoint zp = new ZonePoint(token.getX() + x, token.getY() + y);
    if (zone.getGrid().getCapabilities().isPathingSupported() && token.isSnapToGrid()) {
      CellPoint point = zone.getGrid().convert(zp);
      // walker.replaceLastWaypoint(point, restrictMovement); // OLD WAY

      // New way threaded, off the swing UI thread...
      if (renderPathTask != null) {
        renderPathTask.cancel(true);
      }

      Set<Token.TerrainModifierOperation> terrainModifiersIgnored =
          token.getTerrainModifiersIgnored();

      renderPathTask =
          new RenderPathWorker(
              walker,
              point,
              restrictMovement,
              terrainModifiersIgnored,
              token.getTransformedTopology(Zone.TopologyType.WALL_VBL),
              token.getTransformedTopology(Zone.TopologyType.HILL_VBL),
              token.getTransformedTopology(Zone.TopologyType.PIT_VBL),
              token.getTransformedTopology(Zone.TopologyType.MBL),
              onComplete);
      renderPathThreadPool.execute(renderPathTask);
    } else {
      if (gridlessPath.getCellPath().size() > 1) {
        gridlessPath.replaceLastPoint(zp);
      } else {
        gridlessPath.addPathCell(zp);
      }
    }
  }

  /**
   * Add the waypoint if it is a new waypoint. If it is an old waypoint remove it.
   *
   * @param location The point where the waypoint is toggled.
   */
  public void toggleWaypoint(ZonePoint location) {
    if (walker != null && token.isSnapToGrid() && zone.getGrid() != null) {
      walker.toggleWaypoint(zone.getGrid().convert(location));
    } else {
      gridlessPath.addWayPoint(location);
      gridlessPath.addPathCell(location);
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
    if (walker != null && token.isSnapToGrid() && zone.getGrid() != null) {
      CellPoint cp = walker.getLastPoint();

      if (cp == null) {
        // log.info("cellpoint is null! FIXME! You have Walker class updating outside of
        // thread..."); // Why not save last waypoint to this class?
        cp = zone.getGrid().convert(new ZonePoint(token.getX(), token.getY()));
        // log.info("So I set it to: " + cp);
      }

      zp = zone.getGrid().convert(cp);
    } else {
      zp = gridlessPath.getLastJunctionPoint();
    }
    return zp;
  }

  public int getOffsetX() {
    return offsetX;
  }

  public int getOffsetY() {
    return offsetY;
  }

  public String getPlayerId() {
    return playerId;
  }
}
