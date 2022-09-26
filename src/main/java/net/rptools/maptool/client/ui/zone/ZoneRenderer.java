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
package net.rptools.maptool.client.ui.zone;

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.AbstractPoint;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Label;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.TokenFootprint;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.dnd.DropTargetListener;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** */
/* TODO Whole-part relationship. Separate each layer into its own renderer. The layer is responsible for deciding what
 *  to render, but can pass back to the ZoneRenderer for actual rendering. And, actually, the first pass can just do the
 *  rendering in the layer, and then we can figure out how we want to pass the rendering itself back to the
 *  ZoneRenderer. Keep in mind that the goal is to eventually have a Swing-based renderer and a LibGDX-based renderer,
 *  and we don't want to duplicate business logic between them.
 * TODO Simplify clipping and visibility. For a player view, nothing gets rendered under hard FoW (except certain
 *  configured tokens), most things gets rendered under soft FoW (except tokens I think), and everything gets rendered
 *  in broad daylight (depending on current token view). In general, I think our business logic should deal just with
 *  the visible area (hard and soft), while clipping is dealt with by the graphics library (Graphics2D for swing, I
 *  guess a stencil for LibGDX).
 * TODO A ZoneRenderer needs to expose a JComponent for integration with Swing. For this class, that is itself. For
 *  LibGDX, it would return a Jogl something something. At the same time, we want to move ZoneRenderer to an interface
 *  with a few methods as needed for interaction - one of those methods returns the JComponent corresponding to the
 *  implementation.
 */
  // TODO Actually do drop target here or does it not make sense? I think it has to be on the JComponent.
public interface ZoneRenderer extends DropTargetListener, Comparable<ZoneRenderer> {
  // TODO Remove any unused methods.

  JComponent asSwingComponent();

  /**
   * Forces the zone to be rerendered.
   */
  void repaint();

  Zone getZone();

  void setAutoResizeStamp(boolean value);

  boolean isAutoResizeStamp();

  void showPath(Token token, boolean show);

  /**
   * If token is not null, center on it, set the active layer to it, select it, and request focus.
   *
   * @param token the token to center on
   */
  void centerOn(Token token);

  ZonePoint getCenterPoint();

  boolean isPathShowing(Token token);

  void clearShowPaths();

  /**
   * Resets the token panels, fire onTokenSelection, repaints. The impersonation panel is only reset
   * if no token is currently impersonated.
   * TODO Add a listener to register actions that needs to happen instead of hardcoding it.
   */
  void updateAfterSelection();

  Scale getZoneScale();

  void setZoneScale(Scale scale);

  /**
   * I _hate_ this method. But couldn't think of a better way to tell the drawable renderer that a
   * new image had arrived TODO: FIX THIS ! Perhaps add a new app listener for when new images show
   * up, add the drawable renderer as a listener
   */
  void flushDrawableRenderer();

  ScreenPoint getPointUnderMouse();

  void setMouseOver(Token token);

  void addMoveSelectionSet(String playerId, GUID keyToken, Set<GUID> tokenList, boolean clearLocalSelected);

  boolean hasMoveSelectionSetMoved(GUID keyToken, ZonePoint point);

  void updateMoveSelectionSet(GUID keyToken, ZonePoint offset);

  void toggleMoveSelectionSetWaypoint(GUID keyToken, ZonePoint location);

  ZonePoint getLastWaypoint(GUID keyToken);

  void removeMoveSelectionSet(GUID keyToken);

  /**
   * Commit the move of the token selected
   *
   * @param keyTokenId the token ID of the key token
   */
  void commitMoveSelectionSet(GUID keyTokenId);

  boolean isTokenMoving(Token token);

  void centerOn(ZonePoint point);

  void centerOn(CellPoint point);

  /**
   * Remove the token from: tokenLocationCache, flipImageMap, opacityImageMap, replacementImageMap,
   * labelRenderingCache. Set the visibleScreenArea, tokenStackMap, drawableLights, drawableAuras to
   * null. Flush the fog. Flush the token from the zoneView.
   *
   * @param token the token to flush
   */
  void flush(Token token);

  /** @return the ZoneView */
  ZoneView getZoneView();

  /** Clear internal caches and backbuffers */
  void flush();

  void flushLight();

  void flushFog();

  void addOverlay(ZoneOverlay overlay);

  void removeOverlay(ZoneOverlay overlay);

  void moveViewBy(int dx, int dy);

  void moveViewByCells(int dx, int dy);

  Rectangle getBounds();

  int getX();

  int getY();

  void setBounds(Rectangle bounds);

  Dimension getSize();

  int getWidth();

  int getHeight();

  void zoomReset(int x, int y);

  void zoomIn(int x, int y);

  void zoomOut(int x, int y);

  void setView(int x, int y, double scale);

  void enforceView(int x, int y, double scale, int gmWidth, int gmHeight);

  void restoreView();

  void forcePlayersView();

  void maybeForcePlayersView();

  BufferedImage getMiniImage(int size);

  PlayerView getPlayerView();

  /**
   * The returned {@link PlayerView} contains a list of tokens that includes all selected tokens
   * that this player owns and that have their <code>HasSight</code> checkbox enabled.
   *
   * @param role the player role
   * @return the player view
   */
  PlayerView getPlayerView(Player.Role role);

  /**
   * The returned {@link PlayerView} contains a list of tokens that includes either all selected
   * tokens that this player owns and that have their <code>HasSight</code> checkbox enabled, or all
   * owned tokens that have <code>HasSight</code> enabled.
   *
   * @param role the player role
   * @param selected whether to get the view of selected tokens, or all owned
   * @return the player view
   */
  PlayerView getPlayerView(Player.Role role, boolean selected);

  Rectangle fogExtents();

  /**
   * Get a bounding box, in Zone coordinates, of all the elements in the zone. This method was
   * created by copying renderZone() and then replacing each bit of rendering with a routine to
   * simply aggregate the extents of the object that would have been rendered.
   *
   * @param view the player view
   * @return a new Rectangle with the bounding box of all the elements in the Zone
   */
  Rectangle zoneExtents(PlayerView view);

  void invalidateCurrentViewCache();

  // TODO Decide which render methods - if any - should be public. Should they all just be handled
  //  internally. E.g., SwingZoneRenderer does whatever it needs to `paintComponent`. I guess we
  //  should have some structure to avoid code duplication, but that may be something for render
  //  layers and `AbstractZoneRenderer`.

  void renderZone(Graphics2D g2d, PlayerView view);

  CodeTimer getCodeTimer();

  Area getVisibleArea(Token token);

  boolean isLoading();

  // TODO Why does an outsider need to do this? MeasureTool... Need a way to submit things without directly rendering.
  void renderPath(Graphics2D g, Path<? extends AbstractPoint> path, TokenFootprint footprint);

  void drawText(String text, int x, int y);

  Shape getShape();

  void setShape(Shape shape);

  Shape getShape2();

  void setShape2(Shape shape);

  void drawShape(Shape shape, int x, int y);

  void showBlockedMoves(Graphics2D g, ZonePoint point, double angle, BufferedImage image, float size);

  void highlightCell(Graphics2D g, ZonePoint point, BufferedImage image, float size);

  void addDistanceText(Graphics2D g, ZonePoint point, float size, double distance, double distanceWithoutTerrain);

  List<Token> getTokensOnScreen();

  Zone.Layer getActiveLayer();

  void setActiveLayer(Zone.Layer layer);

  Set<GUID> getSelectedTokenSet();

  void setKeepSelectedTokenSet(boolean keep);

  /**
   * Convenience method to return a set of tokens filtered by ownership.
   *
   * @param tokenSet the set of GUIDs to filter
   * @return the set of GUIDs
   */
  Set<GUID> getOwnedTokens(Set<GUID> tokenSet);

  /**
   * A convenience method to get selected tokens ordered by name
   *
   * @return List of tokens
   */
  List<Token> getSelectedTokensList();

  /**
   * Verifies if a token is selectable based on existence, visibility and ownership.
   *
   * @param tokenGUID the token
   * @return whether the token is selectable
   */
  boolean isTokenSelectable(GUID tokenGUID);

  /**
   * Removes a token from the selected set.
   *
   * @param tokenGUID the token to remove from the selection
   */
  void deselectToken(GUID tokenGUID);

  /**
   * Adds a token from the selected set, if token is selectable.
   *
   * @param tokenGUID the token to add to the selection
   * @return false if nothing was done because the token wasn't selectable, true otherwise
   */
  boolean selectToken(GUID tokenGUID);

  /**
   * Add tokens to the selection.
   *
   * @param tokens the collection of tokens to add
   */
  void selectTokens(Collection<GUID> tokens);

  /**
   * Selects the tokens inside a selection rectangle.
   *
   * @param rect the selection rectangle
   */
  void selectTokens(Rectangle rect);

  /** Clears the set of selected tokens. */
  void clearSelectedTokens();

  /**
   * Returns true if the given token is the only one selected, and the selection is valid.
   *
   * @param token the token
   * @return true if the selectedTokenSet size is 1 and contains the token, false otherwise
   */
  boolean isOnlyTokenSelected(Token token);

  /**
   * Returns true if the given token is selected, there is more than one token selected, and the
   * token can be selected.
   *
   * @param token the token
   * @return true if the selectedTokenSet size is greater than 1 and contains the token, false
   *     otherwise
   */
  boolean isSubsetSelected(Token token);

  /**
   * Reverts the token selection. If the previous selection is empty, keeps reverting until it is
   * non-empty. Fires onTokenSelection events.
   */
  void undoSelectToken();

  void cycleSelectedToken(int direction);

  boolean playerOwnsAllSelected();

  Area getTokenBounds(Token token);

  Area getMarkerBounds(Token token);

  Rectangle getLabelBounds(Label label);

  Token getTokenAt(int x, int y);

  Token getMarkerAt(int x, int y);

  List<Token> getTokenStackAt(int x, int y);

  Label getLabelAt(int x, int y);

  int getViewOffsetX();

  int getViewOffsetY();

  void adjustGridSize(int delta);

  void moveGridBy(int dx, int dy);

  CellPoint getCellAt(ScreenPoint screenPoint);

  ZonePoint getCellCenterAt(ScreenPoint sp);

  void setScale(double scale);

  double getScale();

  double getScaledGridSize();

  boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h);

  void addTokens(List<Token> tokens, ZonePoint zp, List<Boolean> configureTokens, boolean showDialog);

  Set<GUID> getVisibleTokenSet();

  List<Token> getVisibleTokens();

  default int compareTo(@NotNull ZoneRenderer o) {
    // TODO Long.compare()?
    if (o != this) {
      return (int)(getZone().getCreationTime() - o.getZone().getCreationTime());
    }
    return 0;
  }

  // TODO What does highlighting macros have to do with zone rendering?
  List<Token> getHighlightCommonMacros();

  void setHighlightCommonMacros(List<Token> affectedTokens);

  void setCursor(Cursor cursor);

  Cursor createCustomCursor(String resource, String tokenName);

  float getNoiseAlpha();

  long getNoiseSeed();

  void setNoiseValues(long seed, float alpha);

  boolean isBgTextureNoiseFilterOn();

  void setBgTextureNoiseFilterOn(boolean on);

  enum LightOverlayClipStyle {
    CLIP_TO_VISIBLE_AREA,
    CLIP_TO_NOT_VISIBLE_AREA,
  }

  /** Represents a movement set */
  class SelectionSet {

    private final Logger log = LogManager.getLogger(SelectionSet.class);

    private final ZoneRenderer renderer;
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

    public SelectionSet(ZoneRenderer renderer, String playerId, GUID tokenGUID, Set<GUID> selectionList) {
      this.renderer = renderer;

      selectionSet.addAll(selectionList);
      keyToken = tokenGUID;
      this.playerId = playerId;

      token = renderer.getZone().getToken(tokenGUID);

      final var grid = renderer.getZone().getGrid();
      if (token.isSnapToGrid() && grid.getCapabilities().isSnapToGridSupported()) {
        if (grid.getCapabilities().isPathingSupported()) {
          CellPoint tokenPoint = grid.convert(new ZonePoint(token.getX(), token.getY()));

          walker = grid.createZoneWalker();
          walker.setFootprint(token.getFootprint(grid));
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
      if (renderer.getZone().getGrid().getCapabilities().isPathingSupported()
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

    public void setOffset(int x, int y) {
      offsetX = x;
      offsetY = y;

      ZonePoint zp = new ZonePoint(token.getX() + x, token.getY() + y);
      final var grid = renderer.getZone().getGrid();
      if (grid.getCapabilities().isPathingSupported()
              && token.isSnapToGrid()) {
        CellPoint point = grid.convert(zp);
        // walker.replaceLastWaypoint(point, restrictMovement); // OLD WAY

        // New way threaded, off the swing UI thread...
        if (renderPathTask != null) {
          renderPathTask.cancel(true);
        }

        boolean restictMovement = MapTool.getServerPolicy().isUsingAstarPathfinding();

        Set<Token.TerrainModifierOperation> terrainModifiersIgnored = token.getTerrainModifiersIgnored();

        // Skip AI Pathfinding if not on the token layer...
        if (!renderer.getActiveLayer().equals(Zone.Layer.TOKEN)) {
          restictMovement = false;
        }

        renderPathTask =
                new RenderPathWorker(
                        walker,
                        point,
                        restictMovement,
                        terrainModifiersIgnored,
                        token.getTransformedTopology(Zone.TopologyType.WALL_VBL),
                        token.getTransformedTopology(Zone.TopologyType.HILL_VBL),
                        token.getTransformedTopology(Zone.TopologyType.PIT_VBL),
                        token.getTransformedTopology(Zone.TopologyType.MBL),
                        renderer);
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
      final var grid = renderer.getZone().getGrid();
      if (walker != null && token.isSnapToGrid() && grid != null) {
        walker.toggleWaypoint(grid.convert(location));
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
      final var grid = renderer.getZone().getGrid();
      ZonePoint zp;
      if (walker != null && token.isSnapToGrid() && grid != null) {
        CellPoint cp = walker.getLastPoint();

        if (cp == null) {
          // log.info("cellpoint is null! FIXME! You have Walker class updating outside of
          // thread..."); // Why not save last waypoint to this class?
          cp = grid.convert(new ZonePoint(token.getX(), token.getY()));
          // log.info("So I set it to: " + cp);
        }

        zp = grid.convert(cp);
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
}
