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

import com.google.common.eventbus.Subscribe;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.MD5Key;
import net.rptools.lib.gdx.ConfigurablePool;
import net.rptools.maptool.client.*;
import net.rptools.maptool.client.events.RepaintZoneRequested;
import net.rptools.maptool.client.functions.TokenMoveFunctions;
import net.rptools.maptool.client.swing.GenericDialog;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.tool.PointerTool;
import net.rptools.maptool.client.tool.StampTool;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.ui.theme.Borders;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.LabelBackgrounds;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.client.ui.token.dialog.create.NewTokenDialog;
import net.rptools.maptool.client.ui.zone.*;
import net.rptools.maptool.client.ui.zone.gdx.GdxRenderer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.AlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.BlendMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ClipType;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSet;
import net.rptools.maptool.client.ui.zone.renderer.instructions.Paint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ZoneViewport;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.*;
import net.rptools.maptool.model.Zone.Layer;
import net.rptools.maptool.model.drawing.*;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.zones.*;
import net.rptools.maptool.util.GraphicsUtil;
import net.rptools.maptool.util.ImageManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** */
public class ZoneRenderer extends JComponent implements DropTargetListener {

  private static final long serialVersionUID = 3832897780066104884L;
  private static final Logger log = LogManager.getLogger(ZoneRenderer.class);

  private record BufferedLayerState(BufferedImage buffer, BlendMode blendMode, float opacity) {}

  private record LayerState(
      String name,
      Graphics2D layerRootG,
      Graphics2D currentG,
      @Nullable BufferedLayerState bufferedLayerState) {}

  /** DebounceExecutor for throttling repaint() requests. */
  private final DebounceExecutor repaintDebouncer;

  private final ZoneViewModel viewModel;

  // TODO Move this into ZoneViewModel
  /** Noise for mask on repeating tiles. */
  private DrawableNoise noise = null;

  /** Is the noise filter on for disrupting pattens in background tiled textures. */
  private boolean bgTextureNoiseFilterOn = false;

  /** The zone the ZoneRenderer was built from. */
  protected final Zone zone;

  /** The ZoneView constructed from the zone. */
  private final ZoneView zoneView;

  private final List<ZoneOverlay> overlayList = new ArrayList<>();
  private final Map<GUID, SelectionSet> selectionSetMap = new HashMap<>();

  // Optimizations
  final Map<GUID, BufferedImage> labelRenderingCache = new HashMap<>();

  private ScreenPoint pointUnderMouse;

  private boolean autoResizeStamp = false;

  /** Store previous view to restore to, e.g. after GM shows ctrl+shift+space pointer */
  private double previousScale;

  private ZonePoint previousZonePoint;

  private final ZoneCompositor compositor;
  private final EnumSet<Layer> disabledLayers = EnumSet.noneOf(Layer.class);

  private final List<LayerState> layerStack = new ArrayList<>();

  /**
   * Constructor for the ZoneRenderer from a zone.
   *
   * @param campaign The campaign that {@code zone} belongs to.
   * @param zone the zone of the ZoneRenderer
   */
  public ZoneRenderer(Campaign campaign, Zone zone) {
    if (zone == null) {
      throw new IllegalArgumentException("Zone cannot be null");
    }
    this.zone = zone;
    this.zoneView = new ZoneView(zone);
    this.viewModel = new ZoneViewModel(campaign, zone, zoneView);

    this.compositor = new ZoneCompositor(this);

    repaintDebouncer =
        new DebounceExecutor(1000 / AppPreferences.frameRateCap.get(), this::repaint);

    setFocusable(true);

    // DnD
    setTransferHandler(new TransferableHelper());
    try {
      getDropTarget().addDropTargetListener(this);
    } catch (TooManyListenersException e1) {
      // Should never happen because the transfer handler fixes this problem.
    }

    // Focus
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
          }

          @Override
          public void mouseExited(MouseEvent e) {
            pointUnderMouse = null;
          }
        });
    addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            pointUnderMouse = new ScreenPoint(e.getX(), e.getY());
          }
        });

    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            // Pooled buffers are no longer valid after a resize, so discard them all.
            bufferPool.clear();
            repaintDebouncer.dispatch();
          }
        });

    new MapToolEventBus().getMainEventBus().register(this);
  }

  public void setFrameRateCap(int cap) {
    this.repaintDebouncer.setDelay(1000 / cap);
  }

  public void setAutoResizeStamp(boolean value) {
    this.autoResizeStamp = value;
  }

  public boolean isAutoResizeStamp() {
    return autoResizeStamp;
  }

  /**
   * If token is not null, center on it, set the active layer to it, select it, and request focus.
   *
   * @param token the token to center on
   */
  public void centerOnAndSetSelected(Token token) {
    if (token == null) {
      return;
    }

    viewModel.setZoneScale(
        viewModel.getZoneScale().centeredOn(token.getX(), token.getY(), getSize()));
    viewModel.setActiveLayer(token.getLayer());
    MapTool.getFrame()
        .getToolbox()
        .setSelectedTool(!token.getLayer().isStampLayer() ? PointerTool.class : StampTool.class);

    viewModel.getSelectionModel().replaceSelection(Collections.singletonList(token.getId()));
    requestFocusInWindow();
  }

  public ZonePoint getCenterPoint() {
    return new ScreenPoint(getSize().width / 2d, getSize().height / 2d)
        .convertToZone(viewModel.getZoneScale());
  }

  public ScreenPoint getPointUnderMouse() {
    return pointUnderMouse;
  }

  @Override
  public boolean isOpaque() {
    return false;
  }

  public void addMoveSelectionSet(String playerId, GUID keyToken, Set<GUID> tokenList) {
    // I'm not supposed to be moving a token when someone else is already moving it
    selectionSetMap.put(keyToken, new SelectionSet(this, playerId, keyToken, tokenList));
    repaintDebouncer.dispatch(); // Jamz: Seems to have no affect?
  }

  public @Nullable ZonePoint getKeyTokenDragAnchorPosition(GUID keyToken) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return null;
    }
    return set.getKeyTokenDragAnchorPosition();
  }

  public boolean hasMoveSelectionSetMoved(GUID keyToken, ZonePoint dragAnchorPosition) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return false;
    }

    return !set.getKeyTokenDragAnchorPosition().equals(dragAnchorPosition);
  }

  public void updateMoveSelectionSet(GUID keyToken, ZonePoint latestPoint) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return;
    }
    set.update(latestPoint);
    repaintDebouncer.dispatch(); // Jamz: may cause flicker when using AI
  }

  public Map<GUID, SelectionSet> getSelectionSetMap() {
    return selectionSetMap;
  }

  public void toggleMoveSelectionSetWaypoint(GUID keyToken, ZonePoint location) {
    SelectionSet set = selectionSetMap.get(keyToken);
    if (set == null) {
      return;
    }
    set.toggleWaypoint(location);
    repaintDebouncer.dispatch();
  }

  public void removeMoveSelectionSet(GUID keyToken) {
    SelectionSet set = selectionSetMap.remove(keyToken);
    if (set == null) {
      return;
    }
    set.cancel();
    repaintDebouncer.dispatch();
  }

  /**
   * Commit the move of the token selected
   *
   * @param keyTokenId the token ID of the key token
   */
  public void commitMoveSelectionSet(GUID keyTokenId) {
    SelectionSet set = selectionSetMap.remove(keyTokenId);
    if (set == null) {
      return;
    }
    // Let the last thread finish rendering the path if A* Pathfinding is on
    set.renderFinalPath();

    MapTool.serverCommand().stopTokenMove(getZone().getId(), keyTokenId);
    Token keyToken = new Token(zone.getToken(keyTokenId), true);

    /*
     * Lee: if the lead token is snapped-to-grid and has not moved, every follower should return to where they were. Flag set at PointerTool and StampTool's stopTokenDrag() Handling the rest here.
     */
    Set<GUID> selectionSet = set.getTokens();

    boolean stg = false;
    if (set.getWalker() != null) {
      if (set.getWalker().getDistance() >= 0) {
        stg = true;
      }
    } else {
      stg = true;
    }

    // Lee: check only matters for snap-to-grid
    if (stg) {
      CodeTimer.using(
          "ZoneRenderer.commitMoveSelectionSet",
          moveTimer -> {
            moveTimer.setThreshold(1);

            moveTimer.start("setup");

            var changedMaskTopologyTypes = EnumSet.noneOf(Zone.TopologyType.class);

            Path<? extends AbstractPoint> path =
                set.getWalker() != null ? set.getWalker().getPath() : set.getGridlessPath();
            // Jamz: add final path render here?

            List<GUID> filteredTokens = new ArrayList<>();
            moveTimer.stop("setup");

            moveTimer.start("each-token");
            for (GUID tokenGUID : selectionSet) {
              Token token = zone.getToken(tokenGUID);
              // If the token has been deleted, the GUID will still be in the
              // set but getToken() will return null.
              if (token == null) {
                continue;
              }

              var tokenPath = path.derive(zone.getGrid(), keyToken, token);
              token.setLastPath(tokenPath);

              // This is the last *anchor* point.
              var lastPoint = tokenPath.getWayPointList().getLast();
              var endPoint =
                  switch (lastPoint) {
                    case CellPoint cp -> {
                      // Anchor at the cell center.
                      var grid = zone.getGrid();
                      var zp = grid.convert(cp);
                      var centerOffset = grid.getCenterOffset();
                      zp.x += (int) centerOffset.x;
                      zp.y += (int) centerOffset.y;
                      yield zp;
                    }
                    case ZonePoint zp -> zp;
                  };
              token.moveDragAnchorTo(zone, endPoint);
              log.debug("Token end pos: {}, {}", token.getX(), token.getY());

              flush(token);
              MapTool.serverCommand().putToken(zone.getId(), token);

              // Only add certain tokens to the list to process in the move Macro function(s).
              if (token.getLayer().supportsWalker() && token.isVisible()) {
                filteredTokens.add(tokenGUID);
              }

              changedMaskTopologyTypes.addAll(token.getMaskTopologyTypes());
            }
            moveTimer.stop("each-token");

            moveTimer.start("onTokenMove");
            if (!filteredTokens.isEmpty()) {
              // give onTokenMove a chance to reject each token's movement.
              // to avoid re-scanning for handlers on each token we pass in all the tokens at once
              List<Token> tokensToCheck =
                  filteredTokens.stream().map(zone::getToken).collect(Collectors.toList());
              List<Token> tokensDenied =
                  TokenMoveFunctions.callForIndividualTokenMoveVetoes(path, tokensToCheck);
              for (Token token : tokensDenied) {
                denyMovement(token);
              }
            }
            moveTimer.stop("onTokenMove");

            moveTimer.start("onMultipleTokensMove");
            // Multiple tokens, the list of tokens, and call onMultipleTokensMove() macro function.
            if (filteredTokens.size() > 1) {
              // now determine if the macro returned false and if so, revert each token's move to
              // the last path.
              boolean moveDenied = TokenMoveFunctions.callForMultiTokenMoveVeto(filteredTokens);
              if (moveDenied) {
                for (GUID tokenGUID : filteredTokens) {
                  Token token = zone.getToken(tokenGUID);
                  denyMovement(token);
                }
              }
            }
            moveTimer.stop("onMultipleTokensMove");

            moveTimer.start("updateTokenTree");
            MapTool.getFrame().updateTokenTree();
            moveTimer.stop("updateTokenTree");

            if (!changedMaskTopologyTypes.isEmpty()) {
              zone.tokenMaskTopologyChanged(changedMaskTopologyTypes);
            }
          });
    } else {
      for (GUID tokenGUID : selectionSet) {
        denyMovement(zone.getToken(tokenGUID));
      }
    }
  }

  /**
   * Undo the last movement.
   *
   * @param token the token for which we undo the movement
   */
  private void denyMovement(final Token token) {
    Path<?> path = token.getLastPath();
    if (path != null) {
      ZonePoint zp;
      if (path.getCellPath().getFirst() instanceof CellPoint) {
        zp = zone.getGrid().convert((CellPoint) path.getCellPath().getFirst());
      } else {
        zp = (ZonePoint) path.getCellPath().getFirst();
      }
      // Relocate
      token.setX(zp.x);
      token.setY(zp.y);

      // Do it again to cancel out the last move position
      token.setX(zp.x);
      token.setY(zp.y);

      // No more last path
      token.setLastPath(null);
      MapTool.serverCommand().putToken(zone.getId(), token);

      // Cache clearing
      flush(token);
    }
  }

  public boolean isTokenMoving(Token token) {
    return viewModel.isTokenMoving(token.getId());
  }

  /**
   * Remove the token from: {@link #labelRenderingCache}. Flush the token from {@link #zoneView}.
   *
   * @param token the token to flush
   */
  public void flush(Token token) {
    // This method can be called from a non-EDT thread so if that happens, make sure we synchronize
    // with the EDT.
    labelRenderingCache.remove(token.getId());

    zoneView.flush(token);
  }

  /**
   * @return the ZoneView
   */
  public ZoneView getZoneView() {
    return zoneView;
  }

  public ZoneViewModel getViewModel() {
    return viewModel;
  }

  public SelectionModel getSelectionModel() {
    return viewModel.getSelectionModel();
  }

  /** Clear internal caches and back-buffers */
  public void flush() {
    viewModel.flush();

    if (zone.getBackgroundPaint() instanceof DrawableTexturePaint) {
      ImageManager.flushImage(((DrawableTexturePaint) zone.getBackgroundPaint()).getAssetId());
    }
    ImageManager.flushImage(zone.getMapAssetId());

    zoneView.flushFog();
  }

  /** Flush the {@link #zoneView} and repaint. */
  public void flushLight() {
    zoneView.flush();
    repaintDebouncer.dispatch();
  }

  /** Set flushFog to true, visibleScreenArea to null, and repaints */
  public void flushFog() {
    repaintDebouncer.dispatch();
  }

  /**
   * @return the Zone
   */
  public Zone getZone() {
    return zone;
  }

  public List<ZoneOverlay> getOverlays() {
    return Collections.unmodifiableList(overlayList);
  }

  public void addOverlay(ZoneOverlay overlay) {
    overlayList.add(overlay);
    repaintDebouncer.dispatch();
  }

  public void removeOverlay(ZoneOverlay overlay) {
    overlayList.remove(overlay);
    repaintDebouncer.dispatch();
  }

  public void moveViewByCells(int dx, int dy) {
    var zoneScale = viewModel.getZoneScale();

    int gridSize = (int) (zone.getGrid().getSize() * zoneScale.getScale());

    int rawXOffset = zoneScale.getOffsetX() + dx * gridSize;
    int rawYOffset = zoneScale.getOffsetY() + dy * gridSize;

    int snappedXOffset = rawXOffset - rawXOffset % gridSize;
    int snappedYOffset = rawYOffset - rawYOffset % gridSize;

    viewModel.setZoneScale(zoneScale.withOffset(snappedXOffset, snappedYOffset));
  }

  public void enforceView(int x, int y, double scale, int gmWidth, int gmHeight) {
    int width = getWidth();
    int height = getHeight();

    if ((width * gmHeight) < (height * gmWidth)) {
      // Our aspect ratio is narrower than server's, so fit to width
      scale = scale * width / gmWidth;
    } else {
      // Our aspect ratio is shorter than server's, so fit to height
      scale = scale * height / gmHeight;
    }

    previousScale = viewModel.getZoneScale().getScale();
    previousZonePoint = getCenterPoint();

    viewModel.setZoneScale(
        viewModel.getZoneScale().withCenteredScale(scale, getSize()).centeredOn(x, y, getSize()));
  }

  public void restoreView() {
    viewModel.setZoneScale(
        viewModel
            .getZoneScale()
            .withCenteredScale(previousScale, getSize())
            .centeredOn(previousZonePoint.x, previousZonePoint.y, getSize()));
  }

  public void forcePlayersView() {
    ZonePoint zp =
        new ScreenPoint(getWidth() / 2d, getHeight() / 2d).convertToZone(viewModel.getZoneScale());
    MapTool.serverCommand()
        .enforceZoneView(
            getZone().getId(),
            zp.x,
            zp.y,
            viewModel.getZoneScale().getScale(),
            getWidth(),
            getHeight());
  }

  public void maybeForcePlayersView() {
    if (AppState.isPlayerViewLinked() && MapTool.getPlayer().isGM()) {
      forcePlayersView();
    }
  }

  public BufferedImage getMiniImage(int size) {
    return null;
  }

  @Override
  public void paintComponent(Graphics g) {
    CodeTimer.using(
        "ZoneRenderer.renderZone",
        timer -> {
          timer.setThreshold(1, TimeUnit.MICROSECONDS);
          timer.setReportingUnit(TimeUnit.MICROSECONDS);

          var instructionSet = updateZone(null);
          GdxRenderer.getInstance().renderInstructionSet.set(instructionSet);

          if (!viewModel.isUsingGdxRenderer()) {
            timer.start("paintComponent");
            Graphics2D g2d = (Graphics2D) g;

            timer.start("paintComponent:invalidateBufferPool");
            var newConfiguration = g2d.getDeviceConfiguration();
            if (!configuration.equals(newConfiguration)) {
              configuration = newConfiguration;
              // Need new buffers.
              bufferPool.clear();
            }
            timer.stop("paintComponent:invalidateBufferPool");

            BufferedImage buffer = bufferPool.obtain();
            try {
              final var bufferG2d = buffer.createGraphics();
              // Keep the clip to avoid rendering more than we have to.
              bufferG2d.setClip(g2d.getClip());

              renderZoneInternal(bufferG2d, instructionSet);

              int noteVPos = 20;
              bufferG2d.setFont(AppStyle.labelFont);
              if (MapTool.getFrame().areFullScreenToolsShown()) {
                noteVPos += 40;
              }
              if (!AppPreferences.mapVisibilityWarning.get()
                  && (!zone.isVisible() && getPlayerView().isGMView())) {
                GraphicsUtil.drawBoxedString(
                    bufferG2d, I18N.getText("zone.map_not_visible"), getSize().width / 2, noteVPos);
                noteVPos += 20;
              }
              if (AppState.isShowAsPlayer()) {
                GraphicsUtil.drawBoxedString(
                    bufferG2d, I18N.getText("zone.player_view"), getSize().width / 2, noteVPos);
              }

              timer.start("paintComponent:renderBuffer");
              bufferG2d.dispose();
              g2d.setComposite(AlphaComposite.Src);
              g2d.drawImage(buffer, null, 0, 0);
              timer.stop("paintComponent:renderBuffer");
            } finally {
              bufferPool.free(buffer);
            }
            timer.stop("paintComponent");
          }
        });
  }

  public PlayerView getPlayerView() {
    return viewModel.getPlayerView();
  }

  /**
   * The returned {@link PlayerView} contains a list of tokens that includes either all selected
   * tokens that this player owns and that have their <code>HasSight</code> checkbox enabled, or all
   * owned tokens that have <code>HasSight</code> enabled.
   *
   * @param role the player role
   * @param selected whether to get the view of selected tokens, or all owned
   * @return the player view
   */
  public PlayerView makePlayerView(Player.Role role, boolean selected) {
    return viewModel.makePlayerView(role, selected);
  }

  public void restoreLayers() {
    disabledLayers.clear();
  }

  public void disableLayer(Layer layer) {
    disabledLayers.add(layer);
  }

  public boolean shouldRenderLayer(Layer layer, PlayerView view) {
    return !disabledLayers.contains(layer) && (layer.isVisibleToPlayers() || view.isGMView());
  }

  private InstructionSet updateZone(@Nullable PlayerView imposedView) {
    final var timer = CodeTimer.get();

    timer.start("update");
    viewModel.update();
    timer.stop("update");

    var view = Objects.requireNonNullElseGet(imposedView, this::getPlayerView);

    final var renderInstructions = compositor.produceInstructions(view);

    return renderInstructions;
  }

  /**
   * This is the top-level method of the rendering pipeline that coordinates all other calls. {@link
   * #paintComponent(Graphics)} calls this method, then adds the two optional strings, "Map not
   * visible to players" and "Player View" as appropriate.
   *
   * @param g2d Graphics2D object normally passed in by {@link #paintComponent(Graphics)}
   * @param imposedView PlayerView object that describes whether the view is a Player or GM view.
   *     Pass {@code null} to use the current view.
   */
  public void renderZone(Graphics2D g2d, @Nullable PlayerView imposedView) {
    var instructionSet = updateZone(imposedView);
    renderZoneInternal(g2d, instructionSet);
  }

  /**
   * This is the top-level method of the rendering pipeline that coordinates all other calls. {@link
   * #paintComponent(Graphics)} calls this method, then adds the two optional strings, "Map not
   * visible to players" and "Player View" as appropriate.
   *
   * @param g2d Graphics2D object normally passed in by {@link #paintComponent(Graphics)}
   * @param instructionSet The instructions from the compositor that need to be rendered.
   */
  private void renderZoneInternal(Graphics2D g2d, InstructionSet instructionSet) {
    final var timer = CodeTimer.get();

    g2d = (Graphics2D) g2d.create();

    var viewport = instructionSet.viewport();
    Rectangle viewRect = new Rectangle(getSize().width, getSize().height);

    g2d.setFont(AppStyle.labelFont);
    SwingUtil.useAntiAliasing(g2d);

    // much of the raster code assumes the user clip is set
    if (g2d.getClipBounds() == null) {
      g2d.setClip(0, 0, viewRect.width, viewRect.height);
    }

    g2d.setPaint(Color.black);
    g2d.fillRect(viewRect.x, viewRect.y, viewRect.width, viewRect.height);

    AffineTransform worldToScreen = viewport.zoneScale().toScreenTransform();
    final Dimension size = getSize();
    var instructions = instructionSet.instructions();
    processInstructions(g2d, viewport, instructions, instructionSet.clips());

    timer.start("overlays");
    for (ZoneOverlay overlay : overlayList) {
      timer.start("overlays: %s", overlay.getClass().getSimpleName());
      overlay.paintOverlay(this, g2d);
      timer.stop("overlays: %s", overlay.getClass().getSimpleName());
    }
    timer.stop("overlays");
  }

  private java.awt.Paint resolveAwtPaint(
      Paint paint, double offsetX, double offsetY, double scale, ImageObserver... observers) {
    var timer = CodeTimer.get();
    timer.start("resolvePaint");
    try {
      return switch (paint) {
        case Paint.Color color -> {
          yield new Color(color.argb8888(), true);
        }
        case Paint.Texture texture -> {
          BufferedImage image = ImageManager.getImage(texture.assetId(), observers);
          if (image == ImageManager.TRANSFERING_IMAGE) {
            log.warn("Paint asset://{} not resolved", texture.assetId());
          }
          yield new TexturePaint(
              image,
              new Rectangle2D.Double(
                  offsetX,
                  offsetY,
                  image.getWidth() * scale * texture.imageScale(),
                  image.getHeight() * scale * texture.imageScale()));
        }
      };
    } finally {
      timer.stop("resolvePaint");
    }
  }

  private java.awt.Paint resolveAwtPaint(Paint paint, Scale zoneScale, ImageObserver... observers) {
    return resolveAwtPaint(
        paint, zoneScale.getOffsetX(), zoneScale.getOffsetY(), zoneScale.getScale(), observers);
  }

  private java.awt.Paint resolveAwtPaint(Paint paint, ImageObserver... observers) {
    return resolveAwtPaint(paint, 0, 0, 1, observers);
  }

  private void processInstructions(
      Graphics2D rootG,
      ZoneViewport viewport,
      List<RenderInstruction> instructions,
      Map<ClipType, Area> clips) {
    layerStack.clear();

    var timer = CodeTimer.get();
    timer.increment("instructions", instructions.size(), new Object[0]);

    AffineTransform worldToScreen = viewport.zoneScale().toScreenTransform();

    final Dimension size = getSize();
    LayerState currentLayer = new LayerState("<root>", rootG, (Graphics2D) rootG.create(), null);
    Shape clipToRestore = null;
    for (var instruction : instructions) {
      final var timerLayer = currentLayer.name;
      timer.increment("instructions-%s", 1, instruction.getClass().getCanonicalName());
      timer.start("layer-%s[%s]", timerLayer, instruction);

      switch (instruction) {
        case RenderInstruction.Meta.StartBufferedLayer(
            String layerName,
            ClipType clipType,
            BlendMode blendMode,
            double opacity) -> {
          timer.increment("layer-%s-render", 1, layerName);
          timer.start("layer-%s-render", layerName);

          layerStack.add(currentLayer);

          var buffer = bufferPool.obtain();
          var layerRootG = buffer.createGraphics();
          layerRootG.setComposite(AlphaComposite.SrcOver);
          currentLayer =
              new LayerState(
                  layerName,
                  layerRootG,
                  (Graphics2D) layerRootG.create(),
                  new BufferedLayerState(
                      //  TODO Switch to a regular pool rather than a precached set.
                      buffer, blendMode, (float) opacity));

          currentLayer.currentG.setClip(new Rectangle(0, 0, buffer.getWidth(), buffer.getHeight()));

          var clip = clips.get(clipType);
          if (clip != null) {
            var oldTransform = currentLayer.currentG().getTransform();
            currentLayer.currentG().transform(worldToScreen);
            currentLayer.currentG().clip(clip);
            currentLayer.currentG().setTransform(oldTransform);
          }
        }
        case RenderInstruction.Meta.StartUnbufferedLayer(String layerName, ClipType clipType) -> {
          timer.increment("layer-%s-render", 1, layerName);
          timer.start("layer-%s-render", layerName);

          layerStack.add(currentLayer);

          var layerRootG = (Graphics2D) currentLayer.currentG().create();
          layerRootG.setComposite(AlphaComposite.SrcOver);
          currentLayer =
              new LayerState(layerName, layerRootG, (Graphics2D) layerRootG.create(), null);

          var clip = clips.get(clipType);
          if (clip != null) {
            var oldTransform = currentLayer.currentG().getTransform();
            currentLayer.currentG().transform(worldToScreen);
            currentLayer.currentG().clip(clip);
            currentLayer.currentG().setTransform(oldTransform);
          }
        }
        case RenderInstruction.Meta.FinishLayer(String layerName) -> {
          if (layerStack.isEmpty()) {
            log.error("Tried to finish layer {}, but there are no layers right now!", layerName);
            break;
          }

          if (!currentLayer.name().equals(layerName)) {
            log.error(
                "Tried to finish layer {}, but layer {} is still active!",
                layerName,
                currentLayer.name());
            break;
          }
          timer.stop("layer-%s-render", layerName);
          var poppedLayer = currentLayer;
          currentLayer = layerStack.removeLast();

          poppedLayer.layerRootG().dispose();
          poppedLayer.currentG().dispose();

          var bufferedLayerState = poppedLayer.bufferedLayerState();
          if (bufferedLayerState != null) {
            timer.increment("layer-%s-blit", 1, layerName);
            timer.start("layer-%s-blit", layerName);

            float layerOpacity = bufferedLayerState.opacity();
            var blitComposite =
                switch (bufferedLayerState.blendMode()) {
                  // TODO Does the Swing renderer need to distinguish between the two alpha cases?
                  case AlphaSrcOver -> AlphaComposite.SrcOver.derive(layerOpacity);
                  case StraightAlphaSrcOver -> AlphaComposite.SrcOver.derive(layerOpacity);
                  case Brighten -> LightingComposite.OverlaidLights;
                  case SrcOnly -> AlphaComposite.Src.derive(layerOpacity);
                };

            // Needs to be blended down.
            var g = (Graphics2D) currentLayer.currentG().create();
            try {
              g.setComposite(blitComposite);
              g.drawImage(bufferedLayerState.buffer(), 0, 0, this);
            } finally {
              g.dispose();
            }

            bufferPool.free(bufferedLayerState.buffer());
            timer.stop("layer-%s-blit", layerName);
          }
        }
        case RenderInstruction.Meta.SwitchAlphaMode(AlphaMode mode) -> {
          var composite =
              switch (mode) {
                case Clear -> AlphaComposite.Clear;
                case SrcOnly -> AlphaComposite.Src;
                case SrcOver -> AlphaComposite.SrcOver;
                case Screen -> LightingComposite.BlendedLights;
              };
          currentLayer.currentG().setComposite(composite);
        }
        case RenderInstruction.Meta.SetCustomClip(Area clip, boolean invert) -> {
          if (clipToRestore != null) {
            log.error(
                "Unexpected instruction: found another SetCustomClip without intervening ClearCustomClip.");
            break;
          } else {
            clipToRestore = currentLayer.currentG().getClip();
          }

          var oldTransform = currentLayer.currentG().getTransform();
          currentLayer.currentG().transform(worldToScreen);
          if (invert) {
            var inverted = new Area(currentLayer.currentG().getClip());
            inverted.subtract(clip);
            currentLayer.currentG().setClip(inverted);
          } else {
            currentLayer.currentG().clip(clip);
          }
          currentLayer.currentG().setTransform(oldTransform);
        }
        case RenderInstruction.Meta.ClearCustomClip() -> {
          if (clipToRestore == null) {
            log.error("Unexpected instruction: ClearCustomClip without previous SetCustomClip.");
            break;
          }
          currentLayer.currentG().setClip(clipToRestore);
          clipToRestore = null;
        }
        case RenderInstruction.Meta.SetClipType(ClipType clipType) -> {
          var clip = clips.get(clipType);
          if (clip == null) {
            currentLayer.currentG().setClip(null);
          } else {
            var oldTransform = currentLayer.currentG().getTransform();
            currentLayer.currentG().transform(worldToScreen);
            currentLayer.currentG().setClip(clip);
            currentLayer.currentG().setTransform(oldTransform);
          }
        }
        case RenderInstruction.ClearScreen(Color clearColor) -> {
          var clsG = (Graphics2D) currentLayer.layerRootG().create();
          try {
            clsG.setComposite(AlphaComposite.Src);
            clsG.setPaint(clearColor);
            clsG.fillRect(0, 0, size.width, size.height);
          } finally {
            clsG.dispose();
          }
        }
        case RenderInstruction.FillFrameBuffer(Paint paint, double opacity) -> {
          var fillG = (Graphics2D) currentLayer.layerRootG().create();
          try {
            AppPreferences.renderQuality.get().setRenderingHints(fillG);

            var composite = fillG.getComposite();
            if (composite instanceof AlphaComposite alphaComposite) {
              composite = alphaComposite.derive((float) opacity);
              fillG.setComposite(composite);
            }

            // Background texture
            java.awt.Paint awtPaint = resolveAwtPaint(paint, viewport.zoneScale(), this);
            fillG.setPaint(awtPaint);
            fillG.fillRect(0, 0, size.width, size.height);
          } finally {
            fillG.dispose();
          }
        }
        case RenderInstruction.Noise(DrawableNoise noise) -> {
          AppPreferences.renderQuality.get().setRenderingHints(currentLayer.currentG());
          currentLayer.currentG().setPaint(noise.getPaint(viewport.zoneScale()));
          currentLayer.currentG().fillRect(0, 0, size.width, size.height);
        }
        case RenderInstruction.ImageAsset(
            MD5Key id,
            Rectangle2D preTransformBounds,
            AffineTransform transform,
            double opacity) -> {
          var imageG = (Graphics2D) currentLayer.currentG().create();
          try {
            var composite = imageG.getComposite();
            if (opacity < 1 && composite instanceof AlphaComposite alphaComposite) {
              composite = alphaComposite.derive((float) opacity);
              imageG.setComposite(composite);
            }

            AppPreferences.renderQuality.get().setRenderingHints(imageG);
            BufferedImage image = ImageManager.getImage(id, this);

            AffineTransform fullTransform = new AffineTransform(worldToScreen);
            fullTransform.concatenate(transform);

            var bounds = preTransformBounds;
            if (bounds != null) {
              fullTransform.translate(bounds.getMinX(), bounds.getMinY());
              fullTransform.scale(
                  bounds.getWidth() / image.getWidth(), bounds.getHeight() / image.getHeight());
            }

            imageG.drawImage(image, fullTransform, this);
          } finally {
            imageG.dispose();
          }
        }
        case RenderInstruction.Icon(Images resource, Rectangle2D worldBounds) -> {
          var iconG = (Graphics2D) currentLayer.currentG().create();
          try {
            iconG.transform(worldToScreen);

            var image = RessourceManager.getImage(resource);

            var at = new AffineTransform();
            at.translate(
                worldBounds.getCenterX() - worldBounds.getWidth() / 2.,
                worldBounds.getCenterY() - worldBounds.getHeight() / 2.);
            at.scale(
                worldBounds.getWidth() / image.getWidth(),
                worldBounds.getHeight() / image.getHeight());
            iconG.drawImage(image, at, null);
          } finally {
            iconG.dispose();
          }
        }
        case RenderInstruction.Border(
            Borders resource,
            Rectangle2D worldBounds,
            double rotation,
            Point2D rotateAround) -> {
          var borderG = (Graphics2D) currentLayer.currentG().create();
          try {
            var rotateAroundScreen = viewport.zoneScale().toScreenSpace(rotateAround);
            borderG.rotate(rotation, rotateAroundScreen.x, rotateAroundScreen.y);

            var borderResource = RessourceManager.getBorder(resource);
            var screenBounds = viewport.zoneScale().toScreenSpace(worldBounds);
            borderResource.paintAround(
                borderG,
                (int) screenBounds.getX(),
                (int) screenBounds.getY(),
                (int) screenBounds.getWidth(),
                (int) screenBounds.getHeight());
          } finally {
            borderG.dispose();
          }
        }
        case RenderInstruction.BoxedString(
            Point2D center,
            String text,
            LabelBackgrounds background,
            Color foreground) -> {
          var backgroundImageLabel = RessourceManager.getLabelBackground(background);
          GraphicsUtil.drawBoxedString(
              currentLayer.currentG(),
              text,
              (int) center.getX(),
              (int) center.getY(),
              SwingUtilities.CENTER,
              backgroundImageLabel,
              foreground);
        }
        case RenderInstruction.Fill(Shape shape, Paint paint, double opacity) -> {
          var drawingsG = (Graphics2D) currentLayer.currentG().create();
          try {
            drawingsG.transform(worldToScreen);

            var composite = drawingsG.getComposite();
            if (composite instanceof AlphaComposite alphaComposite) {
              composite = alphaComposite.derive((float) opacity);
              drawingsG.setComposite(composite);
            }

            drawingsG.setPaint(resolveAwtPaint(paint, this));
            drawingsG.fill(shape);
          } finally {
            drawingsG.dispose();
          }
        }
        case RenderInstruction.Stroke(
            Shape shape,
            Paint paint,
            BasicStroke stroke,
            double opacity) -> {
          var drawingsG = (Graphics2D) currentLayer.currentG().create();
          try {
            drawingsG.transform(worldToScreen);

            var composite = drawingsG.getComposite();
            if (composite instanceof AlphaComposite alphaComposite) {
              composite = alphaComposite.derive((float) opacity);
              drawingsG.setComposite(composite);
            }

            drawingsG.setPaint(resolveAwtPaint(paint, this));
            drawingsG.setStroke(stroke);
            drawingsG.draw(shape);
          } finally {
            drawingsG.dispose();
          }
        }
        case RenderInstruction.Text(
            String text,
            Font font,
            Rectangle2D screenBounds,
            Color foreground,
            RenderInstruction.Text.Decoration decoration) -> {
          var labelG = (Graphics2D) currentLayer.currentG().create();
          try {
            labelG.setFont(font);
            var fm = labelG.getFontMetrics();
            int strWidth = SwingUtilities.computeStringWidth(fm, text);
            int strHeight = fm.getAscent() - fm.getDescent() - fm.getLeading();

            // TODO Support left & right justification as well.
            //  For now assume centered text.
            double stringY = screenBounds.getCenterY() + strHeight / 2.;
            double stringX = screenBounds.getCenterX() - strWidth / 2.;

            if (decoration == RenderInstruction.Text.Decoration.Shadow) {
              labelG.setColor(Color.black);
              labelG.drawString(text, (int) stringX - 1, (int) stringY - 1);
              labelG.drawString(text, (int) stringX + 1, (int) stringY - 1);
              labelG.drawString(text, (int) stringX - 1, (int) stringY + 1);
              labelG.drawString(text, (int) stringX + 1, (int) stringY + 1);
            }
            labelG.setColor(foreground);
            labelG.drawString(text, (int) stringX, (int) stringY);
          } finally {
            labelG.dispose();
          }
        }
      }

      timer.stop("layer-%s[%s]", timerLayer, instruction);
    }
  }

  private GraphicsConfiguration configuration =
      GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice()
          .getDefaultConfiguration();

  /**
   * Cache of images for rendering overlays.
   *
   * <p>Minimum size is set to three: one for the main buffer that the entire zone is drawn two, and
   * two to handle most layer compositions, e.g., for drawing groups. There is room to grow for
   * cases like drawing groups that can be recursively nested.
   */
  private final ConfigurablePool<BufferedImage> bufferPool =
      new ConfigurablePool<>(
          3,
          10,
          new ConfigurablePool.PoolSupplier<>() {
            @Override
            public BufferedImage get() {
              CodeTimer.get().increment("buffered-image-get");

              return configuration.createCompatibleImage(
                  getWidth(), getHeight(), Transparency.TRANSLUCENT);
            }

            @Override
            public void reset(BufferedImage object) {
              CodeTimer.get().increment("buffered-image-reset");

              // Nothing to do.
            }

            @Override
            public void discard(BufferedImage object) {
              CodeTimer.get().increment("buffered-image-discard");

              // Nothing to do. Will be garbage collected.
            }
          });

  /**
   * Get a list of tokens currently visible on the screen. The list is ordered by location starting
   * in the top left and going to the bottom right.
   *
   * @return the token list
   */
  public List<Token> getTokensOnScreen() {
    List<Token> list = new ArrayList<>();

    // Always assume tokens, for now
    for (ZoneViewModel.TokenPosition location : getTokenPositions(getActiveLayer())) {
      list.add(location.token());
    }

    // Sort by location on screen, top left to bottom right
    list.sort(
        (o1, o2) -> {
          if (o1.getY() < o2.getY()) {
            return -1;
          }
          if (o1.getY() > o2.getY()) {
            return 1;
          }
          return Integer.compare(o1.getX(), o2.getX());
        });
    return list;
  }

  public @Nonnull Layer getActiveLayer() {
    return viewModel.getActiveLayer();
  }

  /**
   * Get the token locations for the given layer, creates an empty list if there are no locations
   * for the given layer
   */
  private List<ZoneViewModel.TokenPosition> getTokenPositions(Layer layer) {
    return viewModel.getTokenPositionsForLayer(layer);
  }

  public Set<GUID> getSelectedTokenSet() {
    return viewModel.getSelectionModel().getSelectedTokenIds();
  }

  /**
   * Convenience method to return a set of tokens filtered by ownership.
   *
   * @param tokenSet the set of GUIDs to filter
   * @return the set of GUIDs
   */
  public Set<GUID> getOwnedTokens(Set<GUID> tokenSet) {
    Set<GUID> ownedTokens = new LinkedHashSet<>();
    if (tokenSet != null) {
      for (GUID guid : tokenSet) {
        Token token = zone.getToken(guid);
        if (token == null || !AppUtil.playerOwns(token)) {
          continue;
        }
        ownedTokens.add(guid);
      }
    }
    return ownedTokens;
  }

  /**
   * A convenience method to get selected tokens that actually exist.
   *
   * @return List of tokens
   */
  public List<Token> getSelectedTokensList() {
    return new ArrayList<>(viewModel.getSelectedTokenList());
  }

  /**
   * Verifies if a token is selectable based on existence, visibility and ownership.
   *
   * @param tokenGUID the token
   * @return whether the token is selectable
   */
  public boolean isTokenSelectable(GUID tokenGUID) {
    if (tokenGUID == null) {
      return false; // doesn't exist
    }
    Token token = zone.getToken(tokenGUID);
    if (token == null) {
      return false; // doesn't exist
    }
    if (!zone.isTokenVisible(token)) {
      return AppUtil.playerOwns(token); // can't own or see
    }
    return true;
  }

  /**
   * Gets the tokens inside the viewport.
   *
   * <p>This is a convenience method for {@link #getTokenIdsInBounds(Rectangle)} that supplies the
   * viewport as the rectangle.
   *
   * @return A list of token IDs for tokens whose footprint intersects with the current viewport.
   */
  public List<GUID> getTokenIdsOnScreen() {
    return getTokenIdsInBounds(getBounds());
  }

  /**
   * Gets the tokens inside a rectangle.
   *
   * @param screenRect the bounds in which to look for tokens.
   * @return A list of token IDs for tokens whose footprint intersects with {@code screenRect}.
   */
  public List<GUID> getTokenIdsInBounds(Rectangle screenRect) {
    var rect = viewModel.getZoneScale().toWorldSpace(screenRect);

    final var tokens = new ArrayList<GUID>();
    for (ZoneViewModel.TokenPosition position : getTokenPositions(getActiveLayer())) {
      if (rect.intersects(position.transformedBounds().getBounds())) {
        tokens.add(position.token().getId());
      }
    }
    return tokens;
  }

  public void cycleSelectedToken(int direction) {
    List<Token> visibleTokens = getTokensOnScreen();
    int newSelection = 0;

    if (visibleTokens.isEmpty()) {
      return;
    }
    if (viewModel.getSelectionModel().isAnyTokenSelected()) {
      // Find the first selected token on the screen
      for (int i = 0; i < visibleTokens.size(); i++) {
        Token token = visibleTokens.get(i);
        if (!isTokenSelectable(token.getId())) {
          continue;
        }
        if (viewModel.getSelectionModel().isSelected(token.getId())) {
          newSelection = i;
          break;
        }
      }
      // Pick the next
      newSelection += direction;
    }
    if (newSelection < 0) {
      newSelection = visibleTokens.size() - 1;
    }
    if (newSelection >= visibleTokens.size()) {
      newSelection = 0;
    }

    // Make the selection
    viewModel
        .getSelectionModel()
        .replaceSelection(Collections.singletonList(visibleTokens.get(newSelection).getId()));
  }

  /**
   * Returns the token at screen location x, y (not cell location).
   *
   * @param x screen location x
   * @param y screen location y
   * @return the token
   */
  public @Nullable Token getTokenAt(int x, int y) {
    var zonePoint = viewModel.getZoneScale().toWorldSpace(x, y);

    List<ZoneViewModel.TokenPosition> positionList =
        new ArrayList<>(getTokenPositions(getActiveLayer()));
    Collections.reverse(positionList);
    for (ZoneViewModel.TokenPosition location : positionList) {
      if (location.transformedBounds().contains(zonePoint)) {
        return location.token();
      }
    }
    return null;
  }

  public @Nullable Token getMarkerAt(int x, int y) {
    var zonePoint = viewModel.getZoneScale().toWorldSpace(x, y);

    List<ZoneViewModel.TokenPosition> positionList =
        new ArrayList<>(viewModel.getMarkerPositions());
    Collections.reverse(positionList);
    for (ZoneViewModel.TokenPosition position : positionList) {
      if (position.transformedBounds().contains(zonePoint)) {
        return position.token();
      }
    }
    return null;
  }

  public List<Token> getTokenStackAt(int x, int y) {
    var tokenStackMap = viewModel.getTokenStackMap();

    Token token = getTokenAt(x, y);
    if (token == null || !tokenStackMap.containsKey(token.getId())) {
      return null;
    }
    List<Token> tokenList = new ArrayList<>(tokenStackMap.get(token.getId()));
    tokenList.sort(Token.COMPARE_BY_NAME);
    return tokenList;
  }

  /**
   * Since the map can be scaled, this is a convenience method to find out what cell is at this
   * location.
   *
   * @param screenPoint Find the cell for this point.
   * @return The cell coordinates of the passed screen point.
   */
  public CellPoint getCellAt(ScreenPoint screenPoint) {
    ZonePoint zp = screenPoint.convertToZone(viewModel.getZoneScale());
    return zone.getGrid().convert(zp);
  }

  /**
   * Converts a screen point to the center point of the corresponding grid cell.
   *
   * @param sp the screen point
   * @return ZonePoint with the coordinates of the center of the grid cell.
   */
  public ZonePoint getCellCenterAt(ScreenPoint sp) {
    Grid grid = getZone().getGrid();
    CellPoint cp = getCellAt(sp);
    Point2D.Double p2d = grid.getCellCenter(cp);
    return new ZonePoint((int) p2d.getX(), (int) p2d.getY());
  }

  public double getScaledGridSize() {
    // Optimize: only need to calc this when grid size or scale changes
    return viewModel.getZoneScale().getScale() * zone.getGrid().getSize();
  }

  /** This makes sure that any image updates get refreshed. This could be a little smarter. */
  @Override
  public boolean imageUpdate(Image img, int infoFlags, int x, int y, int w, int h) {
    repaintDebouncer.dispatch();
    return super.imageUpdate(img, infoFlags, x, y, w, h);
  }

  // DROP TARGET LISTENER
  /*
   * (non-Javadoc)
   *
   * @see java.awt.dnd.DropTargetListener#dragEnter(java.awt.dnd. DropTargetDragEvent )
   */
  @Override
  public void dragEnter(DropTargetDragEvent dtde) {}

  /*
   * (non-Javadoc)
   *
   * @see java.awt.dnd.DropTargetListener#dragExit(java.awt.dnd.DropTargetEvent)
   */
  @Override
  public void dragExit(DropTargetEvent dte) {}

  /*
   * (non-Javadoc)
   *
   * @see java.awt.dnd.DropTargetListener#dragOver (java.awt.dnd.DropTargetDragEvent)
   */
  @Override
  public void dragOver(DropTargetDragEvent dtde) {}

  /**
   * Adds tokens at a given zone point coordinates.
   *
   * @param tokens the list of tokens to add
   * @param zp the zone point where to add the tokens
   * @param configureTokens the list indicating if each token is to be configured
   * @param showDialog whether to display a token edit dialog
   */
  public void addTokens(
      List<Token> tokens, ZonePoint zp, List<Boolean> configureTokens, boolean showDialog) {
    GridCapabilities gridCaps = zone.getGrid().getCapabilities();
    boolean isGM = MapTool.getPlayer().isGM();
    List<String> failedPaste = new ArrayList<>(tokens.size());
    List<GUID> selectThese = new ArrayList<>(tokens.size());

    ScreenPoint sp = viewModel.getZoneScale().toScreenSpace(zp.x, zp.y);
    Point dropPoint = new Point((int) sp.x, (int) sp.y);
    SwingUtilities.convertPointToScreen(dropPoint, this);
    int tokenIndex = 0;
    for (Token token : tokens) {
      boolean configureToken = configureTokens.get(tokenIndex++);

      // Get the snap to grid value for the current preferences and abilities
      token.setSnapToGrid(
          gridCaps.isSnapToGridSupported() && AppPreferences.tokensStartSnapToGrid.get());
      if (token.isSnapToGrid()) {
        zp = zone.getGrid().convert(zone.getGrid().convert(zp));
      }
      token.setX(zp.x);
      token.setY(zp.y);

      // Set the image properties
      if (configureToken) {
        BufferedImage image = ImageManager.getImageAndWait(token.getImageAssetId());
        token.setWidth(image.getWidth(null));
        token.setHeight(image.getHeight(null));
        token.setFootprint(zone.getGrid(), zone.getGrid().getDefaultFootprint());
        token.guessAndSetShape();
      }

      // Always set the layer
      token.setLayer(getActiveLayer());

      // He who drops, owns, if there are no players already set
      // and if there are already players set, add the current one to the list.
      // (Cannot use AppUtil.playerOwns() since that checks 'isStrictTokenManagement' and we want
      // real ownership here.)
      if (!isGM && (!token.hasOwners() || !token.isOwner(MapTool.getPlayer().getName()))) {
        token.addOwner(MapTool.getPlayer().getName());
      }

      // Token type
      Rectangle size = token.getFootprintBounds(zone);
      switch (getActiveLayer()) {
        case TOKEN:
          // Players can't drop invisible tokens
          token.setVisible(!isGM || AppPreferences.newTokensVisible.get());
          if (AppPreferences.tokensStartFreesize.get()) {
            token.setSnapToScale(false);
          }
          break;
        case BACKGROUND:
          token.setShape(Token.TokenShape.TOP_DOWN);

          token.setSnapToScale(!AppPreferences.backgroundsStartFreesize.get());
          token.setSnapToGrid(AppPreferences.backgroundsStartSnapToGrid.get());
          token.setVisible(AppPreferences.newBackgroundsVisible.get());

          // Center on drop point
          if (!token.isSnapToScale() && !token.isSnapToGrid()) {
            token.setX(token.getX() - size.width / 2);
            token.setY(token.getY() - size.height / 2);
          }
          break;
        case OBJECT:
          token.setShape(Token.TokenShape.TOP_DOWN);

          token.setSnapToScale(!AppPreferences.objectsStartFreesize.get());
          token.setSnapToGrid(AppPreferences.objectsStartSnapToGrid.get());
          token.setVisible(AppPreferences.newObjectsVisible.get());

          // Center on drop point
          if (!token.isSnapToScale() && !token.isSnapToGrid()) {
            token.setX(token.getX() - size.width / 2);
            token.setY(token.getY() - size.height / 2);
          }
          break;
      }

      // This looks redundant. But calling getType() retrieves the type of
      // the Token and returns NPC if the type can't be determined (raw image,
      // corrupted token file, etc.). So retrieving it and then turning around and
      // setting it ensures it has a valid value without necessarily changing what
      // it was. :)
      Token.Type type = token.getType();
      token.setType(type);

      // Token type
      if (isGM) {
        // Check the name (after Token layer is set as name relies on layer)
        Token tokenNameUsed = zone.getTokenByName(token.getName());
        token.setName(MapToolUtil.nextTokenId(zone, token, tokenNameUsed != null));

        if (getActiveLayer() == Layer.TOKEN) {
          if (AppPreferences.showDialogOnNewToken.get() || showDialog) {
            NewTokenDialog dialog = new NewTokenDialog(token, dropPoint.x, dropPoint.y);
            if (dialog.showDialog().equals(GenericDialog.DENY)) {
              continue;
            }
          }
        }
      } else {
        /* Player dropped, ensure it's a PC token
        (Why? Couldn't a Player drop an RPTOK that represents an NPC, such as for a summoned monster?
        Unfortunately, we can't know at this point whether the original input was an RPTOK or not.)
        */
        token.setType(Token.Type.PC);

        /* For Players, check to see if the name is already in use. If it is already in use, make
        sure the current Player owns the token being duplicated (to avoid subtle ways of manipulating someone else's
        token!).
         */
        Token tokenNameUsed = zone.getTokenByName(token.getName());
        if (tokenNameUsed != null) {
          if (!AppUtil.playerOwns(tokenNameUsed)) {
            failedPaste.add(token.getName());
            continue;
          }
          String newName = MapToolUtil.nextTokenId(zone, token, tokenNameUsed != null);
          token.setName(newName);
        }
      }
      // Make sure all the assets are transferred
      for (MD5Key id : token.getAllImageAssets()) {
        Asset asset = AssetManager.getAsset(id);
        if (asset == null) {
          log.error("Could not find image for asset: " + id);
          continue;
        }
        MapToolUtil.uploadAsset(asset);
      }
      // Set all macros to "Allow players to edit macro", because the macros are not trusted
      if (!isGM) {
        Map<Integer, MacroButtonProperties> mbpMap = token.getMacroPropertiesMap(false);
        for (MacroButtonProperties mbp : mbpMap.values()) {
          if (!mbp.getAllowPlayerEdits()) {
            mbp.setAllowPlayerEdits(true);
          }
        }
      }

      // Save the token and tell everybody about it
      MapTool.serverCommand().putToken(zone.getId(), token);
      selectThese.add(token.getId());
    }
    // For convenience, select them
    viewModel.getSelectionModel().replaceSelection(selectThese);

    if (!isGM) {
      String msg = I18N.getText("Token.dropped.byPlayer", zone.getName(), MapTool.getPlayer());
      MapTool.addMessage(TextMessage.gm(null, msg));
    }
    if (!failedPaste.isEmpty()) {
      String message = I18N.getText("Token.error.unableToPaste", failedPaste);
      TextMessage msg = TextMessage.gmMe(null, message);
      MapTool.addMessage(msg);
    }
    // Copy them to the clipboard so that we can quickly copy them onto the map
    AppActions.copyTokens(tokens);
    AppActions.updateActions();
    requestFocusInWindow();
  }

  /*
   * (non-Javadoc)
   *
   * @see java.awt.dnd.DropTargetListener#drop (java.awt.dnd.DropTargetDropEvent)
   */
  @Override
  public void drop(DropTargetDropEvent dtde) {
    if (MapTool.getPlayer().isGM() || !MapTool.getServerPolicy().getDisablePlayerAssetPanel()) {
      ZonePoint zp =
          new ScreenPoint((int) dtde.getLocation().getX(), (int) dtde.getLocation().getY())
              .convertToZone(viewModel.getZoneScale());
      TransferableHelper th = (TransferableHelper) getTransferHandler();
      List<Token> tokens = th.getTokens();
      if (tokens != null && !tokens.isEmpty()) {
        addTokens(tokens, zp, th.getConfigureTokens(), false);
      }
    }
  }

  public List<Token> getVisibleTokens() {
    var visibleTokenSet = viewModel.getVisibleTokens(Layer.TOKEN);

    List<Token> tokenList = new ArrayList<>(visibleTokenSet.size());
    for (GUID id : visibleTokenSet) {
      tokenList.add(zone.getToken(id));
    }
    return tokenList;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.awt.dnd.DropTargetListener#dropActionChanged (java.awt.dnd.DropTargetDragEvent)
   */
  @Override
  public void dropActionChanged(DropTargetDragEvent dtde) {}

  @Subscribe
  private void onRepaintRequested(RepaintZoneRequested event) {
    if (event.zone() != this.zone) {
      return;
    }

    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onTokensAdded(TokensAdded event) {
    if (event.zone() != this.zone) {
      return;
    }

    for (Token token : event.tokens()) {
      flush(token);
    }
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onTokensRemoved(TokensRemoved event) {
    if (event.zone() != this.zone) {
      return;
    }

    for (Token token : event.tokens()) {
      flush(token);
    }
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onTokensChanged(TokensChanged event) {
    if (event.zone() != this.zone) {
      return;
    }

    for (Token token : event.tokens()) {
      flush(token);
    }
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onFogChanged(FogChanged event) {
    if (event.zone() != this.zone) {
      return;
    }

    zoneView.flushFog();
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  private void onTopologyChanged() {
    flushFog();
    flushLight();
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onTopologyChanged(WallTopologyChanged event) {
    if (event.zone() != this.zone) {
      return;
    }
    onTopologyChanged();
  }

  @Subscribe
  private void onTopologyChanged(MaskTopologyChanged event) {
    if (event.zone() != this.zone) {
      return;
    }
    onTopologyChanged();
  }

  @Subscribe
  private void onDrawableAdded(DrawableAdded event) {
    if (event.zone() != this.zone) {
      return;
    }
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onDrawableRemoved(DrawableRemoved event) {
    if (event.zone() != this.zone) {
      return;
    }
    MapTool.getFrame().updateTokenTree(); // for any event
    repaintDebouncer.dispatch();
  }

  @Subscribe
  private void onBoardChanged(BoardChanged event) {
    if (event.zone() != this.zone) {
      return;
    }
    repaintDebouncer.dispatch();
  }

  // Should this be moved to GridRenderer? No. Lots of things depend on the grid.
  @Subscribe
  private void onGridChanged(GridChanged event) {
    if (event.zone() != this.zone) {
      return;
    }

    repaintDebouncer.dispatch();
  }

  /**
   * Our goal with this method (which overrides the parent's method) is to create a custom mouse
   * pointer that represents a group of tokens selected on the map. The idea is to provide some
   * feedback to the user that they have more than one token selected at the current time.
   *
   * <p>Unfortunately, while our custom cursor appears to be created correctly, it is never properly
   * applied as the mouse pointer so there is no visual effect, hence it is currently commented out
   * by using an "if (false)" around the code block.
   *
   * @param cursor the cursor to set.
   * @see Component#setCursor(Cursor)
   */
  @SuppressWarnings("unused")
  @Override
  public void setCursor(Cursor cursor) {
    if (false && cursor == Cursor.getDefaultCursor()) {
      custom = createCustomCursor("image/cursor.png", "Group");
      cursor = custom;
    }
    // Overlay and ZoneRenderer should have same cursor
    super.setCursor(cursor);
    MapTool.getFrame().getOverlayPanel().setOverlayCursor(cursor);
  }

  private Cursor custom = null;

  /**
   * Create a custom cursor.
   *
   * @param resource the String corresponding to the buffered image.
   * @param tokenName the name of the token, to be displayed by the cursor.
   * @return the created cursor.
   */
  public Cursor createCustomCursor(String resource, String tokenName) {
    Cursor c = null;
    try {
      BufferedImage img = ImageIO.read(MapTool.class.getResourceAsStream(resource));
      Font font = AppStyle.labelFont;
      Graphics2D z = (Graphics2D) this.getGraphics();
      z.setFont(font);
      FontRenderContext frc = z.getFontRenderContext();
      TextLayout tl = new TextLayout(tokenName, font, frc);
      Rectangle textBox = tl.getPixelBounds(null, 0, 0);

      // Create a larger BufferedImage to hold both the existing cursor and a token name.

      // Use the largest of the image width or string width, and the height of the image + the
      // height
      // of the string to represent the bounding box of the 'arrow+tokenName'
      Rectangle bounds =
          new Rectangle(Math.max(img.getWidth(), textBox.width), img.getHeight() + textBox.height);
      BufferedImage cursor =
          new BufferedImage(bounds.width, bounds.height, Transparency.TRANSLUCENT);
      Graphics2D g2d = cursor.createGraphics();
      g2d.setFont(font);
      g2d.setComposite(z.getComposite());
      g2d.setStroke(z.getStroke());
      g2d.setPaintMode();
      z.dispose();

      Object oldAA = SwingUtil.useAntiAliasing(g2d);
      g2d.drawImage(
          img, new AffineTransform(1f, 0f, 0f, 1f, 0, 0), null); // Draw the arrow at 1:1 resolution
      g2d.translate(0, img.getHeight() + textBox.height / 2);
      g2d.setColor(Color.BLACK);
      GraphicsUtil.drawBoxedString(
          g2d, tokenName, 0, 0, SwingUtilities.LEFT); // The text draw here is not nearly
      // as nice looking as normal
      g2d.dispose();
      c = Toolkit.getDefaultToolkit().createCustomCursor(cursor, new Point(0, 0), tokenName);
      SwingUtil.restoreAntiAliasing(g2d, oldAA);

      img.flush(); // Try to be friendly about memory usage. ;-)
      cursor.flush();
    } catch (Exception ignored) {
    }
    return c;
  }

  public @Nullable DrawableNoise getNoise() {
    return noise;
  }

  /**
   * Returns the alpha level used to apply the noise to background repeating textures.
   *
   * @return the alpha level used to apply the noise.
   */
  public float getNoiseAlpha() {
    return noise.getNoiseAlpha();
  }

  /**
   * Returns the seed value used to generate the noise that is applied to the background repeating
   * images.
   *
   * @return the seed value used to generate the noise.
   */
  public long getNoiseSeed() {
    return noise.getNoiseSeed();
  }

  /**
   * Sets the seed value and alpha level used for the noise applied to repeating background
   * textures.
   *
   * @param seed The seed value used to generate the noise to be applied.
   * @param alpha The alpha level to apply the noise.
   */
  public void setNoiseValues(long seed, float alpha) {
    noise.setNoiseValues(seed, alpha);
  }

  /**
   * Returns if the setting for applying background noise to textures is on or off.
   *
   * @return <code>true</code> if noise will be applied to repeating background textures, otherwise
   *     <code>false</code>
   */
  public boolean isBgTextureNoiseFilterOn() {
    return bgTextureNoiseFilterOn;
  }

  /**
   * Turn on / off application of noise to repeated background textures.
   *
   * @param on <code>true</code> to turn on, <code>false</code> to turn off.
   */
  public void setBgTextureNoiseFilterOn(boolean on) {
    bgTextureNoiseFilterOn = on;
    if (on) {
      noise = new DrawableNoise();
    } else {
      noise = null;
    }
  }
}
