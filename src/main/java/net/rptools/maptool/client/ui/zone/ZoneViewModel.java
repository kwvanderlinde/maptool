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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.CollectionUtil;
import net.rptools.lib.MD5Key;
import net.rptools.lib.StringUtil;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.entities.BoardComponent;
import net.rptools.maptool.client.entities.BorderShapeComponent;
import net.rptools.maptool.client.entities.DecorationShapeComponent;
import net.rptools.maptool.client.entities.DrawableSetComponent;
import net.rptools.maptool.client.entities.Entity;
import net.rptools.maptool.client.entities.EraserComponent;
import net.rptools.maptool.client.entities.FilledShapeComponent;
import net.rptools.maptool.client.entities.GridComponent;
import net.rptools.maptool.client.entities.ModelEntityId;
import net.rptools.maptool.client.entities.Paint;
import net.rptools.maptool.client.entities.SpriteComponent;
import net.rptools.maptool.client.events.RepaintZoneRequested;
import net.rptools.maptool.client.events.ZoneLoaded;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.AttachedLightSource;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.HexGrid;
import net.rptools.maptool.model.LightSource;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.AbstractTemplate;
import net.rptools.maptool.model.drawing.DrawableNoise;
import net.rptools.maptool.model.drawing.DrawablesGroup;
import net.rptools.maptool.model.drawing.DrawnElement;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.util.GraphicsUtil;
import net.rptools.maptool.util.ImageManager;
import net.rptools.maptool.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data used for rendering.
 *
 * <p>This is deliberately not much encapsulated.
 */
public class ZoneViewModel {
  private static final Logger log = LoggerFactory.getLogger(ZoneViewModel.class);

  /**
   * Represents the selectable bounds of a token.
   *
   * <p>Obsoletes various other TokenPosition types that are either screen-based or inconsistent.
   */
  public record TokenPosition(Token token, Rectangle2D footprintBounds, Area transformedBounds) {
    public static TokenPosition fromToken(Token token, Zone zone) {
      Rectangle2D footprintBounds = token.getFootprintBounds(zone);

      final Area transformedBounds = new Area(footprintBounds);
      if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
        // facing defaults to down or -90 degrees
        double centerX = footprintBounds.getCenterX() - token.getAnchorX();
        double centerY = footprintBounds.getCenterY() - token.getAnchorY();
        var at =
            AffineTransform.getRotateInstance(
                Math.toRadians(token.getFacingInDegrees()), centerX, centerY);

        transformedBounds.transform(at);
      }

      return new TokenPosition(token, footprintBounds, transformedBounds);
    }
  }

  public enum RenderLayer {
    AboveBoard,
    AboveGrid,
    AboveLights,
    AboveFog,
  }

  private final Zone zone;
  private final ImageObserver imageObserver;

  // region These are updated externally.

  private boolean boardEnabled = true;
  private final EnumSet<Zone.Layer> disabledLayers = EnumSet.noneOf(Zone.Layer.class);
  private @Nonnull Zone.Layer activeLayer = Zone.Layer.getDefaultPlayerLayer();
  private Scale zoneScale = new Scale();
  private final ZoneView zoneView;
  private final SelectionModel selectionModel;
  private final List<GUID> highlightCommonMacros = new ArrayList<>();
  private @Nullable DrawableNoise noise = null;

  // endregion

  // region These are updated at the start of each render via `update()`.

  private boolean isUsingGdxRenderer = false;

  /** The status message describing how loaded the zone is, or {@code null} if fully loaded. */
  private @Nullable String loadingProgress = "";

  private PlayerView playerView = new PlayerView(Player.Role.PLAYER);
  private final Rectangle2D viewport = new Rectangle2D.Double();
  private Area visibleArea = new Area();

  private final Set<GUID> movingTokens = new HashSet<>();

  private final Map<GUID, TokenPosition> tokenPositions = new HashMap<>();
  private final Set<GUID> onScreenTokens = new HashSet<>();
  private final Map<Zone.Layer, List<TokenPosition>> tokenPositionsByLayer =
      CollectionUtil.newFilledEnumMap(Zone.Layer.class, l -> new LinkedList<>());

  private final List<Marker> markerList = new ArrayList<>();
  private final Map<Token, Set<Token>> tokenStackMap = new HashMap<>();

  private final Map<Zone.Layer, Set<GUID>> visibleTokensByLayer =
      CollectionUtil.newFilledEnumMap(Zone.Layer.class, layer -> new HashSet<>());

  private final List<Point2D> lightPositions = new ArrayList<>();

  // endregion

  public final EntityManager entityManager;
  // TODO Layers should be unnecessary. Just represent the grid as a component on an entity, etc.
  public final EnumMap<RenderLayer, List<Entity>> entitiesInZOrder =
      CollectionUtil.newFilledEnumMap(RenderLayer.class, l -> new ArrayList<>());

  public ZoneViewModel(
      Zone zone, ZoneView zoneView, SelectionModel selectionModel, ImageObserver imageObserver) {
    this.zone = zone;
    this.zoneView = zoneView;
    this.selectionModel = selectionModel;
    this.imageObserver = imageObserver;
    this.entityManager = new EntityManager(zone.getId());
  }

  public void repaintNeeded() {
    new MapToolEventBus().getMainEventBus().post(new RepaintZoneRequested(zone));
  }

  /** Marks the zone as not loaded, so that it ensures once again that all assets are loaded. */
  public void flush() {
    loadingProgress = "";
  }

  public boolean isUsingGdxRenderer() {
    return isUsingGdxRenderer;
  }

  /**
   * Gets a string describing how much of the zone is loaded.
   *
   * <p>If the zone is fully loaded, the result will be empty.
   *
   * @return An optional containing the loading status, or the empty optional if the zone is loaded.
   */
  public Optional<String> getLoadingStatus() {
    return Optional.ofNullable(loadingProgress);
  }

  public void restoreLayers() {
    boardEnabled = true;
    disabledLayers.clear();
  }

  public void disableBoard() {
    boardEnabled = false;
  }

  public boolean isBoardEnabled() {
    return boardEnabled;
  }

  public void disableLayer(Zone.Layer layer) {
    disabledLayers.add(layer);
  }

  public boolean isLayerEnabled(Zone.Layer layer) {
    return !disabledLayers.contains(layer);
  }

  public boolean shouldRenderLayer(Zone.Layer layer) {
    return isLayerEnabled(layer) && (layer.isVisibleToPlayers() || playerView.isGMView());
  }

  public Scale getZoneScale() {
    return zoneScale;
  }

  public void setZoneScale(Scale scale) {
    if (!this.zoneScale.equals(scale)) {
      this.zoneScale = scale;
      MapTool.getFrame().getZoneRenderer(zone).invalidateCurrentViewCache();
      MapTool.getFrame().getZoomStatusBar().update();
      repaintNeeded();
      // TODO Should we be calling renderer.maybeForcePlayersView() here?
    }
  }

  public @Nullable DrawableNoise getNoise() {
    return noise;
  }

  public void setNoise(@Nullable DrawableNoise noise) {
    this.noise = noise;
  }

  public Rectangle2D getViewport() {
    return new Rectangle2D.Double(
        viewport.getMinX(), viewport.getMinY(), viewport.getWidth(), viewport.getHeight());
  }

  public Area getVisibleArea() {
    return visibleArea;
  }

  public PlayerView getPlayerView() {
    return playerView;
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
    List<Token> selectedTokens = null;
    if (selected && selectionModel.isAnyTokenSelected()) {
      selectedTokens = new ArrayList<>(getSelectedTokenList());
      selectedTokens.removeIf(token -> !token.getHasSight() || !AppUtil.playerOwns(token));
    }
    if (selectedTokens == null || selectedTokens.isEmpty()) {
      // if no selected token qualifying for view, use owned tokens or player tokens with sight
      final boolean checkOwnership =
          MapTool.getServerPolicy().isUseIndividualViews() || MapTool.isPersonalServer();
      selectedTokens =
          checkOwnership
              ? zone.getOwnedTokensWithSight(MapTool.getPlayer())
              : zone.getPlayerTokensWithSight();
    }
    if (selectedTokens == null || selectedTokens.isEmpty()) {
      return new PlayerView(role);
    }

    return new PlayerView(role, selectedTokens);
  }

  public Map<GUID, TokenPosition> getTokenPositions() {
    return Collections.unmodifiableMap(tokenPositions);
  }

  public Set<GUID> getOnScreenTokens() {
    return Collections.unmodifiableSet(onScreenTokens);
  }

  public List<TokenPosition> getTokenPositionsForLayer(Zone.Layer layer) {
    return Collections.unmodifiableList(
        tokenPositionsByLayer.getOrDefault(layer, Collections.emptyList()));
  }

  public @Nullable Marker getMarkerAt(int x, int y) {
    var zonePoint = zoneScale.toWorldSpace(x, y);
    for (var marker : markerList.reversed()) {
      if (marker.bounds().contains(zonePoint)) {
        return marker;
      }
    }
    return null;
  }

  public Map<Token, Set<Token>> getTokenStackMap() {
    return Collections.unmodifiableMap(tokenStackMap);
  }

  public List<Token> getSelectedTokenList() {
    var tokens = new ArrayList<Token>();
    for (GUID g : selectionModel.getSelectedTokenIds()) {
      final var token = zone.getToken(g);
      if (token != null) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  public Set<GUID> getVisibleTokens(Zone.Layer layer) {
    return Collections.unmodifiableSet(visibleTokensByLayer.get(layer));
  }

  public boolean isTokenMoving(GUID tokenId) {
    return movingTokens.contains(tokenId);
  }

  public List<GUID> getHighlightCommonMacros() {
    return Collections.unmodifiableList(highlightCommonMacros);
  }

  public void setHighlightCommonMacros(List<Token> affectedTokens) {
    highlightCommonMacros.clear();
    affectedTokens.stream().map(Token::getId).forEach(highlightCommonMacros::add);
  }

  public List<Point2D> getLightPositions() {
    return Collections.unmodifiableList(lightPositions);
  }

  public void update() {
    updateIsUsingGdxRenderer();
    updateIsLoading();
    updateViewport();
    updatePlayerView();
    updateVisibleArea();
    updateMovingTokens();
    updateTokenPositions();
    updateMarkerList();
    updateTokenStacks();
    updateVisibleTokens();
    updateLightPosition();
    updateEntities();
  }

  // What follows are "systems".

  /** Updates {@link #entitiesInZOrder} based on everything else. */
  private void updateEntities() {
    // TODO Update all entities, but only add to `list` if they need to be rendered.

    entitiesInZOrder.values().forEach(List::clear);

    var viewport = getViewport();
    var list = entitiesInZOrder.get(RenderLayer.AboveBoard);
    if (isBoardEnabled()) {
      var boardEntity = entityManager.getBoardEntity();
      boardEntity.getPosition().setLocation(viewport.getCenterX(), viewport.getCenterY());
      boardEntity.getBounds().setRect(viewport);

      boardEntity.setComponent(new BoardComponent(Paint.of(zone.getBackgroundPaint()), noise));
      list.add(boardEntity);

      if (zone.getMapAssetId() != null) {
        // Image is needed to calculate bounds, otherwise we could skip this lookup here.
        var mapImage = ImageManager.getImage(zone.getMapAssetId(), imageObserver);

        var mapEntity = entityManager.getMapEntity();
        mapEntity.getPosition().setLocation(zone.getBoardX(), zone.getBoardY());
        mapEntity
            .getBounds()
            .setFrame(
                zone.getBoardX(),
                zone.getBoardY(),
                mapImage.getWidth() * zone.getImageScaleX(),
                mapImage.getHeight() * zone.getImageScaleY());

        var transform = new AffineTransform();
        transform.translate(zone.getBoardX(), zone.getBoardY());
        transform.scale(zone.getImageScaleX(), zone.getImageScaleY());
        mapEntity.setComponent(new SpriteComponent(zone.getMapAssetId(), transform, 1.));
        list.add(mapEntity);
      }
    }
    if (shouldRenderLayer(Zone.Layer.BACKGROUND)) {
      var entity = entityManager.getBackgroundDrawables();
      compositeDrawablesAsEntities(entity, zone.getDrawnElements(Zone.Layer.BACKGROUND));
      list.add(entity);

      compositeTokensAsEntities(
          Zone.Layer.BACKGROUND, zone.getTokensOnLayer(Zone.Layer.BACKGROUND, false), list);
    }
    if (shouldRenderLayer(Zone.Layer.OBJECT)) {
      var entity = entityManager.getObjectDrawables();
      compositeDrawablesAsEntities(entity, zone.getDrawnElements(Zone.Layer.OBJECT));
      list.add(entity);
    }

    if (AppState.isShowGrid()) {
      var grid = zone.getGrid();
      var gridEntity = entityManager.getGridEntity();
      gridEntity.getPosition().setLocation(viewport.getCenterX(), viewport.getCenterY());
      gridEntity.getBounds().setRect(viewport);
      gridEntity.setComponent(
          new GridComponent(
              grid.getType(),
              grid.getSize(),
              grid instanceof HexGrid hexGrid ? hexGrid.getSecondDimension() : grid.getSize(),
              grid.getOffsetX(),
              grid.getOffsetY()));
      list.add(gridEntity);
    }

    list = entitiesInZOrder.get(RenderLayer.AboveGrid);
    if (shouldRenderLayer(Zone.Layer.OBJECT)) {
      compositeTokensAsEntities(
          Zone.Layer.OBJECT, zone.getTokensOnLayer(Zone.Layer.OBJECT, false), list);
    }

    // TODO Represent lights, lumens, auras, and darkness as entities.

    /*
     * The following sections used to handle rendering of the Hidden (i.e. "GM") layer followed by
     * the Token layer. The problem was that we want all drawables to appear below all tokens, and
     * the old configuration performed the rendering in the following order:
     *
     * <ol>
     *   <li>Render Hidden-layer tokens
     *   <li>Render Hidden-layer drawables
     *   <li>Render Token-layer drawables
     *   <li>Render Token-layer tokens
     * </ol>
     *
     * That's fine for players, but clearly wrong if the view is for the GM. We now use:
     *
     * <ol>
     *   <li>Render Token-layer drawables // Player-drawn images shouldn't obscure GM's images?
     *   <li>Render Hidden-layer drawables // GM could always use "View As Player" if needed?
     *   <li>Render Hidden-layer tokens
     *   <li>Render Token-layer tokens
     * </ol>
     */
    list = entitiesInZOrder.get(RenderLayer.AboveLights);
    if (shouldRenderLayer(Zone.Layer.TOKEN)) {
      {
        var entity = entityManager.getTokenDrawables();
        compositeDrawablesAsEntities(entity, zone.getDrawnElements(Zone.Layer.TOKEN));
        list.add(entity);
      }

      if (shouldRenderLayer(Zone.Layer.GM)) {
        {
          var entity = entityManager.getGmDrawables();
          compositeDrawablesAsEntities(entity, zone.getDrawnElements(Zone.Layer.GM));
          list.add(entity);
        }
        // TODO GM tokens as entities
      }

      // TODO Token tokens as entities

      // TODO Unowned movement as entities
    }

    // TODO Fog as entities

    list = entitiesInZOrder.get(RenderLayer.AboveFog);
    if (shouldRenderLayer(Zone.Layer.TOKEN)) {
      // TODO VBL tokens as entities (again)

      // TODO Figure tokens as entities (again)

      // TODO Owned movement as entities

      // TODO "Renderables", i.e., labels
    }

    // TODO Next comes overlays, which I don't think require entities descriptions.
    //  By the same token, lights and fog should not be entities either, we just need a way to
    //  identify where in the order to put them during rendering.
  }

  private void compositeDrawablesAsEntities(Entity parentEntity, List<DrawnElement> drawnElements) {
    // TODO Only keep child entities that intersect the viewport.

    var childEntities = new ArrayList<Entity>();
    for (var element : drawnElements) {
      var drawable = element.getDrawable();
      var pen = element.getPen();
      var drawingBounds = drawable.getBounds(zone).getBounds2D();

      var entity =
          entityManager.ensureEntityFor(
              new ModelEntityId(ModelEntityId.Kind.Drawing, element.getDrawable().getId()));

      // TODO For groups, union all child bounds. For non-groups, use the pen-based logic.
      if (pen.getPaint() != null) {
        var thickness = pen.getThickness();
        drawingBounds.setRect(
            drawingBounds.getMinX() - thickness,
            drawingBounds.getMinY() - thickness,
            drawingBounds.getWidth() + 2 * thickness,
            drawingBounds.getHeight() + 2 * thickness);
      }
      entity.getPosition().setLocation(drawingBounds.getCenterX(), drawingBounds.getCenterY());
      entity.getBounds().setRect(drawingBounds);

      if (drawable instanceof DrawablesGroup group) {
        // Not a regular drawable
        entity.removeComponent(EraserComponent.class);
        entity.removeComponent(BorderShapeComponent.class);
        entity.removeComponent(DecorationShapeComponent.class);

        if (!group.getDrawableList().isEmpty()) {
          compositeDrawablesAsEntities(entity, group.getDrawableList());
          childEntities.add(entity);
        }
      } else {
        childEntities.add(entity);

        // Not a group.
        entity.removeComponent(DrawableSetComponent.class);

        var stroke = pen.getStroke();
        var area = pen.getBackgroundPaint() == null ? null : drawable.getArea(zone);
        Shape border = pen.getPaint() == null ? null : drawable.getBorder(zone);
        Shape decorations =
            (pen.getPaint() == null || !(drawable instanceof AbstractTemplate template))
                ? null
                : template.getDecorationsToStroke(zone);

        // TODO Reuse any existing components. Will only be important once we need to potentially
        //  remesh things for GDX.
        if (pen.isEraser()) {
          entity.removeComponent(FilledShapeComponent.class);
          entity.removeComponent(BorderShapeComponent.class);
          entity.removeComponent(DecorationShapeComponent.class);

          var combinedArea = area == null ? new Area() : area;
          if (border != null) {
            combinedArea = new Area(combinedArea);
            combinedArea.add(new Area(stroke.createStrokedShape(border)));
          }
          entity.setComponent(new EraserComponent(combinedArea));
        } else {
          entity.removeComponent(EraserComponent.class);

          var fillOpacity = pen.getOpacity();
          if (drawable instanceof AbstractTemplate) {
            fillOpacity *= AbstractTemplate.DEFAULT_BG_ALPHA;
          }

          if (area == null) {
            entity.removeComponent(FilledShapeComponent.class);
          } else {
            entity.setComponent(
                new FilledShapeComponent(area, Paint.of(pen.getBackgroundPaint()), fillOpacity));
          }
          if (border == null) {
            entity.removeComponent(BorderShapeComponent.class);
          } else {
            entity.setComponent(
                new BorderShapeComponent(
                    border, Paint.of(pen.getPaint()), stroke, pen.getOpacity()));
          }
          if (decorations == null) {
            entity.removeComponent(DecorationShapeComponent.class);
          } else {
            entity.setComponent(
                new DecorationShapeComponent(
                    decorations, Paint.of(pen.getPaint()), stroke, pen.getOpacity()));
          }
        }
      }
    }

    if (!childEntities.isEmpty()) {
      parentEntity.setComponent(new DrawableSetComponent(childEntities));
    }
  }

  private void compositeTokensAsEntities(
      Zone.Layer layer, List<Token> tokens, List<Entity> output) {
    // TODO React to token events (TokenCreated, etc) to remove tokens from ECS?
    // TODO Even though we update all tokens, don't add them to `output` if they are not on-screen.
    //  Would be nice if paths still get rendered even if the token is off-screen, though. But
    //  that's an existing bug to fix.

    var timer = CodeTimer.get();
    // TODO Sprites need to potentially be clipped. Should we add this to the SpriteComponent, or
    //  somehow instruct the renderer to bound a set of operations by a clip? The latter would be
    //  more efficient as we could set once, do operations, then continue.

    // Note: original used layer.supportsVision() instead of isTokenLayer(), but that was mistaken.
    // Tokens can be clipped to the visible area. But stamps will never be clipped since they count
    // as "part of the map". FoW might cover them afterward, but here we won't clip them.
    var softFowClippingEnabled =
        !playerView.isGMView() && zoneView.isUsingVision() && layer.isTokenLayer();
    for (var token : tokens) {
      TokenPosition position;
      timer.start("token-list-1");
      try {
        // TODO Properly support figures and always visible tokens.
        final var figuresOnly = false;
        if (figuresOnly
            && !(token.getShape() == Token.TokenShape.FIGURE || token.isAlwaysVisible())) {
          continue;
        }
        if (layer.isStampLayer() && isTokenMoving(token.getId())) {
          // Stamps do not use a "ghost" when being dragged.
          continue;
        }
        position = getTokenPositions().get(token.getId());
        if (position == null) {
          // Unknown token?
          continue;
        }
        if (!getVisibleTokens(layer).contains(token.getId())) {
          // Token not on screen or otherwise not visible.
          continue;
        }
      } finally {
        timer.stop("token-list-1");
      }

      // TODO Use the token's canonical position.
      var entity =
          entityManager.ensureEntityFor(new ModelEntityId(ModelEntityId.Kind.Token, token.getId()));
      entity
          .getPosition()
          .setLocation(
              position.footprintBounds().getCenterX(), position.footprintBounds().getCenterY());
      entity.getBounds().setRect(position.footprintBounds().getBounds2D());
      output.add(entity);

      if (softFowClippingEnabled && isTokenInNeedOfClipping(position, playerView)) {
        // TODO How to represent the clip?
        // TODO Clip to visible area and cell bounds (though I don't like the latter).
      } else {
        // TODO How to represent the not-clip?
      }

      // TODO Output the token's last path if available.

      MD5Key tokenImageId = token.getImageAssetId();
      // TODO I hate that we need the resolved image just to figure out bounds and such.
      BufferedImage image = ImageManager.getImage(tokenImageId, imageObserver);

      // Update the token dimensions. TODO Gross. The token's width/height aren't even used
      //  rendering, are they?
      if (token.getIsFlippedIso() && zone.getGrid().getType().isIsometric()) {
        int newSize = (image.getWidth() + image.getHeight());
        token.setWidth(newSize);
        token.setHeight(newSize / 2);
      }

      // TODO Output the token's halo.

      // Use opacity to indicate that token is moving
      var opacity = token.getTokenOpacity() * (isTokenMoving(token.getId()) ? 0.5 : 1.0);
      // TODO Transform should be relative to the entity's canonical position.
      var imageTransform =
          TokenUtil.getRenderTransform(
              zone,
              token,
              new Dimension(image.getWidth(), image.getHeight()),
              position.footprintBounds());
      entity.setComponent(new SpriteComponent(tokenImageId, imageTransform, opacity));

      // TODO Output the token's states and bars.

      // TODO Output the token's facing arrow

      // TODO If on the active layer:
      //  1. Tokens need selection boxes, drawn above other tokens on the same layer.
      //  2. Tokens need labels, drawn above everything else regardless of layer.
    }
  }

  private boolean isTokenInNeedOfClipping(TokenPosition position, PlayerView view) {
    // Can view everything or zone is not using vision => no clipping needed
    if (view.isGMView() || !zoneView.isUsingVision()) {
      return false;
    }

    var visibleArea = getVisibleArea();
    if (visibleArea.isEmpty()) {
      // No clipping if there is no visible area.
      return false;
    }

    var tokenCellArea = position.transformedBounds();

    // If the token is a figure and its center is visible then no clipping
    if (position.token().getShape() == Token.TokenShape.FIGURE
        && zone.getGrid().checkCenterRegion(tokenCellArea.getBounds(), visibleArea)) {
      return false;
    }

    // Jamz: Always Visible tokens will get rendered fully to place on top of FoW
    // if we can see a portion of the stamp/token, defaults to 2/9ths, don't clip at all
    if (position.token().isAlwaysVisible()
        && zone.getGrid()
            .checkRegion(
                tokenCellArea.getBounds(),
                visibleArea,
                position.token().getAlwaysVisibleTolerance())) {
      return false;
    }

    // clipping needed
    return true;
  }

  /** Updates {@link #isUsingGdxRenderer}. */
  private void updateIsUsingGdxRenderer() {
    isUsingGdxRenderer = false;
  }

  /**
   * If the zone is not already loaded, updates the {@link #loadingProgress} and emits {@link
   * ZoneLoaded} if it becomes loaded.
   */
  private void updateIsLoading() {
    if (loadingProgress == null) {
      // We're done, until the cache is cleared
      return;
    }

    ImageObserver observer =
        Objects.requireNonNullElseGet(
            MapTool.getFrame().getZoneRenderer(this.zone),
            () -> (ImageObserver) (img, infoflags, x, y, width, height) -> false);

    // Get a list of all the assets in the zone
    Set<MD5Key> assetSet = zone.getAllAssetIds();
    assetSet.remove(null); // remove bad data

    // Make sure they are loaded
    int downloadCount = 0;
    int cacheCount = 0;
    boolean loaded = true;
    for (MD5Key id : assetSet) {
      // Have we gotten the actual data yet ?
      Asset asset = AssetManager.getAsset(id);
      if (asset == null) {
        AssetManager.getAssetAsynchronously(id);
        loaded = false;
        continue;
      }
      downloadCount++;

      // Have we loaded the image into memory yet ?
      Image image = ImageManager.getImage(asset.getMD5Key(), observer);
      if (image == null || image == ImageManager.TRANSFERING_IMAGE) {
        loaded = false;
        continue;
      }
      cacheCount++;
    }

    if (loaded) {
      // Indicate that loading is finished.
      loadingProgress = null;

      // Notify the token tree that it should update
      MapTool.getFrame().updateTokenTree();
      new MapToolEventBus().getMainEventBus().post(new ZoneLoaded(zone));
    } else {
      loadingProgress =
          String.format(
              " Loading Map '%s' - %d/%d Loaded %d/%d Cached",
              zone.getDisplayName(), downloadCount, assetSet.size(), cacheCount, assetSet.size());
    }
  }

  /** Updates {@link #viewport} based on {@link #zoneScale}. */
  private void updateViewport() {
    var renderer = MapTool.getFrame().getZoneRenderer(this.zone);
    if (renderer == null) {
      // No viewport.
      viewport.setFrame(0, 0, 0, 0);
      return;
    }

    var screenBounds = new Rectangle2D.Double(0, 0, renderer.getWidth(), renderer.getHeight());
    viewport.setFrame(zoneScale.toWorldSpace(screenBounds));
  }

  /** Updates {@link #visibleArea} based on {@link #playerView}. */
  private void updateVisibleArea() {
    visibleArea = zoneView.getVisibility(playerView).visibleArea();
  }

  /** Updates {@link #playerView}. */
  private void updatePlayerView() {
    playerView = makePlayerView(MapTool.getPlayer().getEffectiveRole(), true);
  }

  /** Clears and populates {@link #tokenPositions} and {@link #tokenPositionsByLayer}. */
  private void updateTokenPositions() {
    tokenPositions.clear();

    for (var layer : Zone.Layer.values()) {
      var layerList = tokenPositionsByLayer.get(layer);
      layerList.clear();

      // Note the order: the top most token is at the end of the list.
      var tokens = zone.getTokensOnLayer(layer);
      for (var token : tokens) {
        if ((!token.isVisible() || !token.getLayer().isVisibleToPlayers())
            && !this.playerView.isGMView()) {
          continue;
        }
        if (token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token)) {
          continue;
        }

        var tokenPosition = TokenPosition.fromToken(token, zone);

        tokenPositions.put(token.getId(), tokenPosition);
        layerList.add(tokenPosition);
      }
    }
  }

  /**
   * Updates {@link #markerList} based on {@link #playerView}, {@link #tokenPositionsByLayer}, and
   * {@link #visibleTokensByLayer}
   */
  private void updateMarkerList() {
    // TODO Mark markers with a Marker component so we don't need to maintain a separate list.
    //  In fact we don't even strictly need a list. What we want to do is to be able to query
    //  entities by a point (so some spatial partitioning, KDTree or the like) and grab the topmost
    //  one with a matching compnoent.

    var isGM = playerView.isGMView();

    markerList.clear();

    for (var entry : tokenPositionsByLayer.entrySet()) {
      var layer = entry.getKey();
      if (!layer.isMarkerLayer()) {
        // No markers in this layer, so don't bother iterating over them.
        continue;
      }

      for (var tokenPosition : entry.getValue()) {
        var token = tokenPosition.token();
        if (!visibleTokensByLayer.get(layer).contains(token.getId())) {
          // No value in storing markers the player can't see.
          continue;
        }

        var marker =
            new Marker(
                tokenPosition.transformedBounds(),
                token.getName(),
                isGM && !StringUtil.isEmpty(token.getGMName()) ? token.getGMName() : null,
                StringUtil.isEmpty(token.getNotes())
                    ? null
                    : new Notes(token.getNotes(), token.getNotesType()),
                StringUtil.isEmpty(token.getGMNotes()) || !isGM
                    ? null
                    : new Notes(token.getGMNotes(), token.getGmNotesType()),
                token.getPortraitImage());

        // A GM sees a marker if it has any notes or a portrait.
        // A player sees a marker only if it has player notes.
        if (marker.playerNotes() != null
            || marker.imageKey() != null
            || (isGM && marker.gmNotes() != null)) {
          markerList.add(marker);
        }
      }
    }
  }

  /** Updates {@link #tokenStackMap} based on {@link #tokenPositionsByLayer}. */
  private void updateTokenStacks() {
    tokenStackMap.clear();
    var tokenPositions = tokenPositionsByLayer.get(Zone.Layer.TOKEN);
    for (var tokenPosition : tokenPositions) {
      var token = tokenPosition.token();
      Set<Token> tokenStackSet = new HashSet<>();

      for (var otherPosition : tokenPositions) {
        if (tokenPosition.token().getId().equals(otherPosition.token().getId())) {
          // Don't self-stack.
          continue;
        }

        // Are we covering anyone ?
        if (!tokenPosition.footprintBounds().contains(otherPosition.footprintBounds())) {
          continue;
        }

        tokenStackSet.add(otherPosition.token());

        var reverse = tokenStackMap.get(otherPosition.token());
        if (reverse != null) {
          // The other token also covers tokens, so incorporate them into this one.
          tokenStackSet.addAll(reverse);
          tokenStackMap.remove(otherPosition.token());
        }
      }

      if (!tokenStackSet.isEmpty()) {
        tokenStackSet.add(token);
        tokenStackMap.put(token, tokenStackSet);
      }
    }
  }

  /**
   * Updates {@link #onScreenTokens} and {@link #visibleTokensByLayer} based on {@link
   * #tokenPositionsByLayer}, {@link #viewport}, {@link #playerView}, and {@link #visibleArea}.
   */
  private void updateVisibleTokens() {
    double scale = zoneScale.getScale();

    onScreenTokens.clear();

    for (var layer : Zone.Layer.values()) {
      var tokenPositions = tokenPositionsByLayer.get(layer);

      var visibleTokens = visibleTokensByLayer.get(layer);
      visibleTokens.clear();
      for (var tokenPosition : tokenPositions) {
        // First make sure it is on screen.
        if (!tokenPosition.transformedBounds().intersects(viewport)) {
          continue;
        }

        onScreenTokens.add(tokenPosition.token().getId());

        // Then make sure it is in revealed area (for players).
        if (!playerView.isGMView()
            && layer.supportsVision()
            && zoneView.isUsingVision()
            && !GraphicsUtil.intersects(tokenPosition.transformedBounds(), visibleArea)) {
          continue;
        }

        var bounds = tokenPosition.transformedBounds().getBounds2D();
        if (bounds.getWidth() * scale < 1 || bounds.getHeight() * scale < 1) {
          continue;
        }

        visibleTokens.add(tokenPosition.token().getId());
      }
    }
  }

  /** Updates {@link #movingTokens}. */
  private void updateMovingTokens() {
    movingTokens.clear();

    var renderer = MapTool.getFrame().getZoneRenderer(this.zone);
    if (renderer == null) {
      return;
    }

    for (final var selectionSet : renderer.getSelectionSetMap().values()) {
      movingTokens.addAll(selectionSet.getTokens());
    }
  }

  /**
   * Updates {@link #lightPositions} based on {@link #playerView}, {@link #tokenPositions}, and
   * {@link #onScreenTokens}.
   */
  private void updateLightPosition() {
    lightPositions.clear();

    if (!AppState.isShowLightSources() || !playerView.isGMView()) {
      return;
    }

    for (TokenPosition position : tokenPositions.values()) {
      var token = position.token();

      if (!token.hasLightSources()) {
        continue;
      }
      if (!onScreenTokens.contains(token.getId())) {
        continue;
      }

      boolean foundNormalLight = false;
      for (AttachedLightSource attachedLightSource : token.getLightSources()) {
        LightSource lightSource = attachedLightSource.resolve(token, MapTool.getCampaign());
        if (lightSource != null && lightSource.getType() == LightSource.Type.NORMAL) {
          foundNormalLight = true;
          break;
        }
      }
      if (foundNormalLight) {
        var bounds = position.transformedBounds().getBounds2D();
        lightPositions.add(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()));
      }
    }
  }
}
