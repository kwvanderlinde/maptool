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

import java.awt.geom.Area;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import javax.swing.SwingWorker;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.AbstractPoint;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Abstract away the asynchrony regarding SelectionSets. The caller should just be able to
//  "get results", while internally we expose results if ready, otherwise wait an acceptable time
//  before yielding control.
public class MovementModel {
  private final Zone zone;
  private final Map<GUID, MovementSet> movementSetMap = new HashMap<>();

  public MovementModel(Zone zone) {
    this.zone = zone;
  }

  public Set<MovementSet> getOwnedMovementSets(Player player) {
    final Set<MovementSet> ownedMovementSet = new HashSet<>();
    for (final var movementSet : movementSetMap.values()) {
      if (movementSet.getPlayerId().equals(player.getName())) {
        ownedMovementSet.add(movementSet);
      }
    }
    return ownedMovementSet;
  }

  public Set<MovementSet> getUnownedMovementSets(Player player) {
    final Set<MovementSet> unownedMovementSet = new HashSet<>();
    for (final var movementSet : movementSetMap.values()) {
      if (!movementSet.getPlayerId().equals(player.getName())) {
        unownedMovementSet.add(movementSet);
      }
    }
    return unownedMovementSet;
  }

  public boolean isMoving(Token token) {
    for (MovementSet set : movementSetMap.values()) {
      if (set.contains(token)) {
        return true;
      }
    }
    return false;
  }

  public void addMovementSet(
      String playerId, GUID keyToken, Set<GUID> tokenList, boolean usePathfinding) {
    movementSetMap.put(
        keyToken, new MovementSet(zone, playerId, keyToken, tokenList, usePathfinding));
  }

  public boolean hasMovementSetMoved(GUID keyToken, ZonePoint point) {
    final var set = movementSetMap.get(keyToken);
    if (set == null) {
      return false;
    }
    Token token = zone.getToken(keyToken);
    int x = point.x - token.getX();
    int y = point.y - token.getY();

    return set.offsetX != x || set.offsetY != y;
  }

  public void setMovementSetPosition(
      GUID keyToken, ZonePoint position, Runnable onPathfindingComplete) {
    final var set = movementSetMap.get(keyToken);
    if (set == null) {
      return;
    }
    Token token = zone.getToken(keyToken);
    set.setOffset(position.x - token.getX(), position.y - token.getY(), onPathfindingComplete);
  }

  public void toggleWaypoint(GUID keyToken, ZonePoint location) {
    final var set = movementSetMap.get(keyToken);
    if (set == null) {
      return;
    }
    set.toggleWaypoint(location);
  }

  public ZonePoint getLastWaypoint(GUID keyToken) {
    final var set = movementSetMap.get(keyToken);
    if (set == null) {
      return null;
    }
    return set.getLastWaypoint();
  }

  public @Nullable MovementSet removeMovementSet(GUID keyToken) {
    return movementSetMap.remove(keyToken);
  }

  /** Represents a movement set */
  // TODO There are actually two entirely different types depending on pathfinding:
  //  1. Walker-based
  //  2. Gridless path-based.
  public static class MovementSet {

    private final Logger log = LogManager.getLogger(MovementSet.class);

    private final Zone zone;
    private final Set<GUID> selectionSet = new HashSet<>();
    private final GUID keyTokenId;
    private final Token keyToken;
    private final String playerId;
    private final boolean restrictMovement;

    private ZoneWalker walker;
    private Path<ZonePoint> gridlessPath;
    /** Pixel distance (x) from keyToken's origin. */
    private int offsetX;
    /** Pixel distance (y) from keyToken's origin. */
    private int offsetY;

    private RenderPathWorker renderPathTask;
    // TODO Perhaps a thread pool would be better than allocated a new executor per move?
    private final ExecutorService renderPathThreadPool = Executors.newSingleThreadExecutor();

    public MovementSet(
        Zone zone,
        String playerId,
        GUID tokenGUID,
        Set<GUID> selectionList,
        boolean restrictMovement) {
      this.zone = zone;
      selectionSet.addAll(selectionList);
      keyTokenId = tokenGUID;
      keyToken = zone.getToken(tokenGUID);
      this.playerId = playerId;
      this.restrictMovement = restrictMovement;

      if (keyToken.isSnapToGrid() && zone.getGrid().getCapabilities().isSnapToGridSupported()) {
        if (zone.getGrid().getCapabilities().isPathingSupported()) {
          CellPoint tokenPoint =
              zone.getGrid().convert(new ZonePoint(keyToken.getX(), keyToken.getY()));

          walker = zone.getGrid().createZoneWalker();
          walker.setFootprint(keyToken.getFootprint(zone.getGrid()));
          walker.setWaypoints(tokenPoint, tokenPoint);
        }
      } else {
        gridlessPath = new Path<>();
        gridlessPath.addPathCell(new ZonePoint(keyToken.getX(), keyToken.getY()));
      }
    }

    public Path<? extends AbstractPoint> getPath() {
      if (walker != null) {
        return walker.getPath();
      }
      return gridlessPath;
    }

    /** @return path computation. */
    public @Nullable Path<ZonePoint> getGridlessPath() {
      return gridlessPath;
    }

    public @Nullable ZoneWalker getWalker() {
      return walker;
    }

    public GUID getKeyTokenId() {
      return keyTokenId;
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
          && keyToken.isSnapToGrid()
          && renderPathTask != null) {
        // TODO Surely we can do better than a sleepy busy wait?
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

    public void setOffset(int x, int y, Runnable onRenderDone) {
      offsetX = x;
      offsetY = y;

      ZonePoint zp = new ZonePoint(keyToken.getX() + x, keyToken.getY() + y);
      if (zone.getGrid().getCapabilities().isPathingSupported() && keyToken.isSnapToGrid()) {
        CellPoint point = zone.getGrid().convert(zp);

        // New way threaded, off the swing UI thread...
        if (renderPathTask != null) {
          renderPathTask.cancel(true);
        }

        Set<Token.TerrainModifierOperation> terrainModifiersIgnored =
            keyToken.getTerrainModifiersIgnored();

        renderPathTask =
            new RenderPathWorker(
                walker,
                point,
                restrictMovement,
                terrainModifiersIgnored,
                keyToken.getTransformedTopology(Zone.TopologyType.WALL_VBL),
                keyToken.getTransformedTopology(Zone.TopologyType.HILL_VBL),
                keyToken.getTransformedTopology(Zone.TopologyType.PIT_VBL),
                keyToken.getTransformedTopology(Zone.TopologyType.MBL),
                onRenderDone);
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
      if (walker != null && keyToken.isSnapToGrid() && zone.getGrid() != null) {
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
      if (walker != null && keyToken.isSnapToGrid() && zone.getGrid() != null) {
        CellPoint cp = walker.getLastPoint();

        if (cp == null) {
          // log.info("cellpoint is null! FIXME! You have Walker class updating outside of
          // thread..."); // Why not save last waypoint to this class?
          cp = zone.getGrid().convert(new ZonePoint(keyToken.getX(), keyToken.getY()));
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

  private static class RenderPathWorker extends SwingWorker<Void, Void> {
    private final Runnable onDone;
    private final ZoneWalker walker;
    private final CellPoint endPoint;
    private final boolean restrictMovement;
    private final Set<Token.TerrainModifierOperation> terrainModifiersIgnored;
    private final Area tokenWallVbl;
    private final Area tokenHillVbl;
    private final Area tokenPitVbl;
    private final Area tokenMbl;

    public RenderPathWorker(
        ZoneWalker walker,
        CellPoint endPoint,
        boolean restrictMovement,
        Set<Token.TerrainModifierOperation> terrainModifiersIgnored,
        Area tokenWallVbl,
        Area tokenHillVbl,
        Area tokenPitVbl,
        Area tokenMbl,
        Runnable onDone) {
      this.walker = walker;
      this.endPoint = endPoint;
      this.restrictMovement = restrictMovement;
      this.onDone = onDone;
      this.terrainModifiersIgnored = terrainModifiersIgnored;
      this.tokenWallVbl = tokenWallVbl;
      this.tokenHillVbl = tokenHillVbl;
      this.tokenPitVbl = tokenPitVbl;
      this.tokenMbl = tokenMbl;
    }

    @Override
    protected Void doInBackground() {
      walker.replaceLastWaypoint(
          endPoint,
          restrictMovement,
          terrainModifiersIgnored,
          tokenWallVbl,
          tokenHillVbl,
          tokenPitVbl,
          tokenMbl);
      return null;
    }

    @Override
    protected void done() {
      onDone.run();
    }
  }
}
