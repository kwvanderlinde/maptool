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

import com.github.weisj.jsvg.util.ColorUtil;
import com.google.common.collect.ImmutableList;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.rptools.lib.AwtUtil;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.MD5Key;
import net.rptools.lib.StringUtil;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.DeveloperOptions;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.tool.drawing.ExposeTool;
import net.rptools.maptool.client.ui.theme.Borders;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.token.AbstractFlowShapeTokenOverlay;
import net.rptools.maptool.client.ui.token.AbstractShapeTokenOverlay;
import net.rptools.maptool.client.ui.token.BarTokenOverlay;
import net.rptools.maptool.client.ui.token.BooleanTokenOverlay;
import net.rptools.maptool.client.ui.token.ColorDotTokenOverlay;
import net.rptools.maptool.client.ui.token.CornerImageTokenOverlay;
import net.rptools.maptool.client.ui.token.DrawnBarTokenOverlay;
import net.rptools.maptool.client.ui.token.FlowImageTokenOverlay;
import net.rptools.maptool.client.ui.token.ImageTokenOverlay;
import net.rptools.maptool.client.ui.token.MultipleImageBarTokenOverlay;
import net.rptools.maptool.client.ui.token.ShadedTokenOverlay;
import net.rptools.maptool.client.ui.token.SingleImageBarTokenOverlay;
import net.rptools.maptool.client.ui.token.TwoImageBarTokenOverlay;
import net.rptools.maptool.client.ui.token.TwoToneBarTokenOverlay;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.client.ui.zone.ZoneViewModel;
import net.rptools.maptool.client.ui.zone.compositor.HaloCompositor;
import net.rptools.maptool.client.ui.zone.renderer.instructions.AlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.BlendMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ClipType;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSet;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSetBuilder;
import net.rptools.maptool.client.ui.zone.renderer.instructions.LabelFactory;
import net.rptools.maptool.client.ui.zone.renderer.instructions.Paint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Border;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.BoxedString;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.ClearScreen;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Fill;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.FillFrameBuffer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Icon;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.ImageAsset;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Meta.SetClipType;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Meta.SwitchAlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Noise;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Stroke;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ZoneViewport;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Grid;
import net.rptools.maptool.model.GridlessGrid;
import net.rptools.maptool.model.HexGridHorizontal;
import net.rptools.maptool.model.HexGridVertical;
import net.rptools.maptool.model.IsometricGrid;
import net.rptools.maptool.model.SquareGrid;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.model.drawing.DrawnElement;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.maptool.util.ImageManager;
import net.rptools.maptool.util.TokenUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Zone Compositor is responsible for providing the Zone Renderer with what needs to be
 * rendered. Within a given map region what objects exist that need to be drawn. Basically "What's
 * on screen?"
 */
public class ZoneCompositor {
  private static final Logger log = LogManager.getLogger(ZoneCompositor.class);
  private static final Color COLOR_CLEAR = new Color(0, 0, 0, 0);

  /** An arrow facing horizontally to the positive x-axis, with its point at (0, 0). */
  private static final Path2D UNIT_ARROW;

  // TODO Capitalize
  private static final ArrayList<Color> figureFillColours = new ArrayList<>();

  static {
    // Facing arrow
    {
      final double tailX = -0.25;
      final double dovetailX = -0.15;
      final double tailY = .35;

      UNIT_ARROW = new Path2D.Double();
      UNIT_ARROW.moveTo(0, 0);
      UNIT_ARROW.lineTo(tailX, -tailY);
      UNIT_ARROW.lineTo(dovetailX, 0);
      UNIT_ARROW.lineTo(tailX, tailY);
      UNIT_ARROW.closePath();
    }

    // For figures, different shades for the facing arrow.
    {
      for (int i = 0; i <= 90; i++) {
        figureFillColours.add(new Color(1 - 0.5f / 90f * i, 1 - 0.5f / 90f * i, 0));
      }
      for (int i = 89; i >= 0; i--) {
        figureFillColours.add(figureFillColours.get(i));
      }
    }
  }

  private final List<RenderInstruction> instructions = new ArrayList<>();
  private final ZoneRenderer renderer;
  private final ZoneViewModel viewModel;
  private final ZoneView zoneView;
  private final Zone zone;
  private final Campaign campaign;

  private final HaloCompositor haloCompositor;

  // region Temporary facing arrow state

  // TODO Why not look this up in the composite loop? Is it because we have to do it for many
  //  tokens? If that' the case, we should instead use a cached preference.
  private Color fillColour = AppPreferences.facingArrowBGColour.get();
  private Color borderColour = AppPreferences.facingArrowBorderColour.get();

  // endregion

  {
    AppPreferences.facingArrowBGColour.onChange(color -> fillColour = color);
    AppPreferences.facingArrowBorderColour.onChange(color -> borderColour = color);
  }

  public ZoneCompositor(ZoneRenderer renderer) {
    this.renderer = renderer;
    this.viewModel = renderer.getViewModel();
    this.zoneView = renderer.getZoneView();
    this.zone = renderer.getZone();
    this.campaign = viewModel.getCampaign();

    this.haloCompositor = new HaloCompositor(campaign, zone);
  }

  /**
   * @param view
   * @return
   */
  public InstructionSet produceInstructions(PlayerView view) {
    instructions.clear();

    var visibility = renderer.getZoneView().getVisibility(view);
    var clips = new EnumMap<ClipType, Area>(ClipType.class);
    if (!view.isGMView()) {
      clips.put(ClipType.NoClipping, null);
      clips.put(ClipType.VisibleArea, visibility.visibleArea());
      if (zone.hasFog()) {
        clips.put(ClipType.ExposedArea, visibility.exposedArea());
        clips.put(ClipType.ClearArea, visibility.clearArea());
      } else {
        clips.put(ClipType.ExposedArea, null);
        clips.put(ClipType.ClearArea, zoneView.isUsingVision() ? visibility.visibleArea() : null);
      }
    }

    // TODO Respect bounds. E.g., don't add drawings or tokens that are completely outside of the
    //  bounds.
    var zoneScale = viewModel.getZoneScale();
    var viewportRect = viewModel.getViewport();
    var viewport = new ZoneViewport(viewportRect.getWidth(), viewportRect.getHeight(), zoneScale);
    var builder = new InstructionSetBuilder(instructions::add, renderer.getZone(), viewport);

    // TODO Shan't we take this from the ZoneViewModel#getViewSize()?
    var screenBounds = new Rectangle2D.Double(0, 0, renderer.getWidth(), renderer.getHeight());
    var worldBounds = viewport.getWorldSpaceBounds();
    var playerView = viewModel.getPlayerView();
    var delayedCompositing = new ArrayList<Runnable>();

    var loadingProgress = viewModel.getLoadingStatus();
    if (loadingProgress.isPresent()) {
      builder.unbufferedLayer(
          "loading",
          ClipType.NoClipping,
          () -> {
            builder.add(new ClearScreen(Color.black));
            builder.add(
                new BoxedString(
                    screenBounds.getCenterX(), screenBounds.getCenterY(), loadingProgress.get()));
          });
      viewModel.repaintNeeded();
    } else if (MapTool.getCampaign().isBeingSerialized()) {
      builder.unbufferedLayer(
          "serializing",
          ClipType.NoClipping,
          () -> {
            builder.add(new ClearScreen(Color.black));
            builder.add(
                new BoxedString(
                    screenBounds.getCenterX(), screenBounds.getCenterY(), "    Please Wait    "));
          });
    } else {
      compositeBoard(builder);

      compositeDrawings(builder, view, Zone.Layer.BACKGROUND, worldBounds);
      compositeTokens(
          builder,
          delayedCompositing,
          viewport,
          view,
          Zone.Layer.BACKGROUND,
          false,
          viewModel.getTokenPositionsForLayer(Zone.Layer.BACKGROUND));

      compositeDrawings(builder, view, Zone.Layer.OBJECT, worldBounds);

      compositeGrid(builder, viewport, zone.getGrid(), new Color(zone.getGridColor(), false));

      compositeTokens(
          builder,
          delayedCompositing,
          viewport,
          view,
          Zone.Layer.OBJECT,
          false,
          viewModel.getTokenPositionsForLayer(Zone.Layer.OBJECT));

      compositeLights(builder, view);
      compositeLumens(builder, view);
      compositeAuras(builder, view);

      compositeDarkness(builder, view);

      compositeDrawings(builder, view, Zone.Layer.TOKEN, worldBounds);
      compositeDrawings(builder, view, Zone.Layer.GM, worldBounds);

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

      compositeTokens(
          builder,
          delayedCompositing,
          viewport,
          view,
          Zone.Layer.GM,
          false,
          viewModel.getTokenPositionsForLayer(Zone.Layer.GM));
      compositeTokens(
          builder,
          delayedCompositing,
          viewport,
          view,
          Zone.Layer.TOKEN,
          false,
          viewModel.getTokenPositionsForLayer(Zone.Layer.TOKEN));

      compositeStacks(builder, viewport, view);

      compositeUnownedMovement(builder, delayedCompositing, view, clips.get(ClipType.ClearArea));

      compositeTextLabels(builder);

      compositeFog(builder, viewport, view);

      var vblTokens =
          zone.getTokensAlwaysVisible().stream()
              .map(t -> viewModel.getTokenPositions().get(t.getId()))
              .filter(Objects::nonNull)
              .toList();
      compositeTokens(
          builder, delayedCompositing, viewport, view, Zone.Layer.TOKEN, true, vblTokens);

      var figureTokens =
          zone.getFigureTokens().stream()
              .sorted(zone.getFigureZOrderComparator())
              .map(t -> viewModel.getTokenPositions().get(t.getId()))
              .filter(Objects::nonNull)
              .toList();
      compositeTokens(
          builder, delayedCompositing, viewport, view, Zone.Layer.TOKEN, true, figureTokens);

      compositeOwnedMovement(builder, delayedCompositing, view, clips.get(ClipType.ClearArea));

      compositeVisionOverlay(builder, viewport, view);

      for (var overlay : renderer.getOverlays()) {
        builder.unbufferedLayer(
            String.format("overlay-%s", overlay.getClass().getCanonicalName()),
            ClipType.NoClipping,
            () -> {
              overlay.compositeOverlay(builder, worldBounds);
            });
      }

      compositeCoordinates(builder, viewport, zone.getGrid());

      compositeLightSourceIcons(builder, viewport, view);

      // Finally, put out any labels that have been delayed, e.g., from rendering tokens.
      if (!delayedCompositing.isEmpty()) {
        builder.unbufferedLayer(
            "delayedRendering",
            ClipType.NoClipping,
            () -> {
              for (var action : delayedCompositing) {
                action.run();
              }
            });
      }

      compositeDebugShapes(builder, viewport);

      // TODO Notes weren't part of renderZone(), so should actually be done separately otherwise
      //  they will end up in screenshots and such.
      {
        var notes = new ArrayList<String>();
        if (!AppPreferences.mapVisibilityWarning.get()
            && (!zone.isVisible() && playerView.isGMView())) {
          notes.add(I18N.getText("zone.map_not_visible"));
        }
        if (AppState.isShowAsPlayer()) {
          notes.add(I18N.getText("zone.player_view"));
        }

        if (!notes.isEmpty()) {
          builder.unbufferedLayer(
              "notes",
              ClipType.NoClipping,
              () -> {
                int noteVPos = 20;
                for (var note : notes) {
                  builder.add(new BoxedString(screenBounds.getCenterX(), noteVPos, note));
                  noteVPos += 20;
                }
              });
        }
      }
    }

    return new InstructionSet(viewport, ImmutableList.copyOf(instructions), clips);
  }

  private void compositeBoard(InstructionSetBuilder builder) {
    builder.unbufferedLayer(
        "board",
        ClipType.NoClipping,
        () -> {
          if (zone.drawBoard()) {
            builder.add(new SwitchAlphaMode(AlphaMode.SrcOnly));

            var backgroundPaint = Paint.of(zone.getBackgroundPaint());
            builder.add(new FillFrameBuffer(backgroundPaint));

            // Only apply the noise if the feature is on and the background a textured paint
            if (renderer.isBgTextureNoiseFilterOn() && backgroundPaint instanceof Paint.Texture) {
              builder.add(new Noise(renderer.getNoise()));
            }
          }

          if (zone.getMapAssetId() != null) {
            builder.add(new SwitchAlphaMode(AlphaMode.SrcOver));

            AffineTransform transform = new AffineTransform();
            transform.translate(zone.getBoardX(), zone.getBoardY());
            transform.scale(zone.getImageScaleX(), zone.getImageScaleY());

            builder.add(new ImageAsset(zone.getMapAssetId(), transform));
          }
        });
  }

  private void compositeDrawings(
      InstructionSetBuilder builder, PlayerView view, Zone.Layer layer, Rectangle2D worldBounds) {
    if (!renderer.shouldRenderLayer(layer, view)) {
      return;
    }
    // Special case: GM layer is like a subset of the Token layer. So turn off the GM layer when the
    // Token layer is turned off.
    if (Zone.Layer.GM.equals(layer) && !renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
      return;
    }

    List<DrawnElement> drawnElements = new ArrayList<>(zone.getDrawnElements(layer));
    drawnElements.removeIf(
        element -> {
          var drawable = element.getDrawable();
          var pen = element.getPen();
          var drawingBounds = drawable.getBounds(zone).getBounds2D();
          if (pen.getPaint() != null) {
            // Need to extend the bounds by the pen thickness.
            var thickness = pen.getThickness();
            drawingBounds.setRect(
                drawingBounds.getMinX() - thickness,
                drawingBounds.getMinY() - thickness,
                drawingBounds.getWidth() + 2 * thickness,
                drawingBounds.getHeight() + 2 * thickness);
          }
          return !worldBounds.intersects(drawingBounds);
        });

    if (drawnElements.isEmpty()) {
      return;
    }

    // Note: can't queue up individual drawables, since deletions are modeled as drawables layered
    // overtop of the areas they delete. So we must buffer this layer.
    builder.bufferedLayer(
        "drawings",
        ClipType.ExposedArea,
        BlendMode.AlphaSrcOver,
        1.,
        () -> {
          builder.add(new ClearScreen(COLOR_CLEAR));
          builder.add(new SwitchAlphaMode(AlphaMode.SrcOver));

          builder.addDrawnElements(drawnElements);
        });
  }

  private void compositeTokens(
      InstructionSetBuilder builder,
      List<Runnable> delayedCompositing,
      ZoneViewport viewport,
      PlayerView view,
      Zone.Layer layer,
      boolean figuresOnly,
      List<ZoneViewModel.TokenPosition> tokenPositions) {
    if (tokenPositions.isEmpty()) {
      // No point setting up a layer for nothing.
      return;
    }

    var timer = CodeTimer.get();
    var campaign = MapTool.getCampaign();

    var debug = new Path2D.Double();

    if (!renderer.shouldRenderLayer(layer, view)) {
      return;
    }
    // Special case: GM layer is like a subset of the Token layer. So turn off the GM layer when the
    // Token layer is turned off.
    if (Zone.Layer.GM.equals(layer) && !renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
      return;
    }

    // Tokens can be clipped to the visible area. But stamps will never be clipped since they count
    // as "part of the map". FoW might cover them afterward, but here we won't clip them.
    var considerClipping = !view.isGMView() && zoneView.isUsingVision() && layer.isTokenLayer();
    builder.unbufferedLayer(
        "tokens",
        ClipType.NoClipping,
        () -> {
          var tokenIdUnderMouse = viewModel.getTokenUnderMouse();

          List<ZoneViewModel.TokenPosition> tokenPostProcessing =
              new ArrayList<>(tokenPositions.size());
          for (var tokenPosition : tokenPositions) {
            var token = tokenPosition.token();
            var isTokenUnderMouse = token.getId().equals(tokenIdUnderMouse);

            // region Precheck
            timer.start("token-list-1");
            try {
              if (figuresOnly
                  && token.getShape() != Token.TokenShape.FIGURE
                  && !token.isAlwaysVisible()) {
                continue;
              }
              if (token.getLayer().isStampLayer() && viewModel.isTokenMoving(token.getId())) {
                continue;
              }
              if (!viewModel.getVisibleTokens(token.getLayer()).contains(token.getId())) {
                // Token not on screen or otherwise not visible.
                continue;
              }
            } finally {
              timer.stop("token-list-1");
            }
            // endregion

            Area tokenClip = null;
            if (considerClipping) {
              if (isTokenInNeedOfClipping(
                  token, tokenPosition.transformedBounds(), view.isGMView())) {
                tokenClip = zoneView.getVisibility(view).visibleArea();

                if (token.getShape() == Token.TokenShape.FIGURE || token.isAlwaysVisible()) {
                  Area cellBounds =
                      zone.getGrid().getTokenCellArea(tokenPosition.transformedBounds());
                  cellBounds.intersect(tokenClip);
                  tokenClip = cellBounds;
                }
              }
            }

            // region Path
            timer.start("renderTokens:ShowPath");
            if (viewModel.isPathShowing(token.getId()) && token.getLastPath() != null) {
              builder.addPath(token.getLastPath(), token.getFootprint(zone.getGrid()));
            }
            timer.stop("renderTokens:ShowPath");
            // endregion

            MD5Key tokenImageId = token.getTokenImageAssetId(campaign);

            // region Update ISO image size
            timer.start("token-list-1b");
            // Adds zr as observer so we can repaint once the image is ready. Fixes #1700.
            BufferedImage tokenImage = ImageManager.getImage(tokenImageId, renderer);
            timer.stop("token-list-1b");

            // TODO This section is completely bogus. It exists only to to modify the token size in
            // case
            //  it is isometric. That, of course, is complete bullshit since the token should know
            // its own
            //  size, or be updated when set to isometric or when the image loads. Doing this in the
            //  render loop makes no sense.
            //  I understand it used to be used for more than this, but it still needs to go.
            //  Using the renderer as image observer is also an important detail, but again this
            // should be
            //  part of - or hook into - the image loading system.
            timer.start("token-list-5a");
            if (token.getIsFlippedIso() && zone.getGrid().getType().isIsometric()) {
              int newSize = (tokenImage.getWidth() + tokenImage.getHeight());
              token.setWidth(newSize);
              token.setHeight(newSize / 2);
            }
            timer.stop("token-list-5a");
            // endregion

            builder.withCustomClip(
                tokenClip,
                false,
                () -> {
                  haloCompositor.compositeHalos(builder, tokenPosition, view);

                  // region Token image
                  // Use opacity to indicate that token is moving
                  float tokenOpacity =
                      token.getTokenOpacity()
                          * (viewModel.isTokenMoving(token.getId()) ? 0.5f : 1f);
                  // Finally composite the token image
                  timer.start("token-list-7");
                  {
                    timer.increment("TokenRenderer-renderToken");
                    timer.start("TokenRenderer-renderToken");

                    timer.start("TokenRenderer-paintTokenImage");
                    var imageTransform =
                        TokenUtil.getRenderTransform(
                            zone,
                            token,
                            new Dimension(tokenImage.getWidth(), tokenImage.getHeight()),
                            tokenPosition.footprintBounds());
                    builder.add(new ImageAsset(tokenImageId, imageTransform, tokenOpacity));
                    timer.stop("TokenRenderer-paintTokenImage");

                    timer.stop("TokenRenderer-renderToken");
                  }
                  timer.stop("token-list-7");
                  // endregion

                  timer.start("token-list-9");
                  // Set up the graphics so that the overlay can just be painted.
                  Rectangle2D tokenBounds =
                      viewport
                          .zoneScale()
                          .toScreenSpace(tokenPosition.transformedBounds().getBounds2D());
                  // TODO Use the token bounds as a clipping region.
                  //      Graphics2D locG =
                  //              (Graphics2D)
                  //                      tokenG.create(
                  //                              (int) tokenBounds.getX(),
                  //                              (int) tokenBounds.getY(),
                  //                              (int) tokenBounds.getWidth(),
                  //                              (int) tokenBounds.getHeight());
                  Rectangle bounds =
                      new Rectangle(
                          0, 0, (int) tokenBounds.getWidth(), (int) tokenBounds.getHeight());

                  // Check each of the state values
                  // TODO This approach does not respect the order that states were set on the
                  // token. I
                  //  personally think this is a big win, but 1.18.5 behaviour and the original code
                  //  suggest that we should respect the state order as set on the token.
                  //  We can move to the latter by iterating token state keys rather than the
                  // campaign
                  //  token state map
                  Map<Integer, Integer> flowStateCounterByGridSize =
                      new TreeMap<>(Integer::compareTo);
                  Function<Integer, Rectangle2D> flowPositionLookup =
                      gridSize -> {
                        var flowIndex = flowStateCounterByGridSize.merge(gridSize, 1, Integer::sum);
                        // The index to return is the previous one that we just incremented.
                        --flowIndex;

                        var cellCount = gridSize * gridSize;
                        if (flowIndex >= cellCount) {
                          log.warn(
                              "Overlapping states in grid size {} at {} on token {}",
                              gridSize,
                              flowIndex,
                              token.getName());
                          flowIndex %= gridSize;
                        }

                        var column = flowIndex % gridSize;
                        var row = flowIndex / gridSize;

                        var cellBounds = new Rectangle2D.Double();
                        cellBounds.setRect(tokenPosition.footprintBounds());
                        cellBounds.width /= gridSize;
                        cellBounds.height /= gridSize;
                        cellBounds.x =
                            tokenPosition.footprintBounds().getMaxX()
                                - cellBounds.width * (column + 1);
                        cellBounds.y =
                            tokenPosition.footprintBounds().getMaxY()
                                - cellBounds.height * (row + 1);

                        return cellBounds;
                      };

                  for (var entry : campaign.getTokenStatesMap().entrySet()) {
                    Object stateValue = token.getState(entry.getKey());
                    BooleanTokenOverlay overlay = entry.getValue();
                    // TODO I do believe this cannot happen.
                    // if (stateValue instanceof AbstractTokenOverlay) {
                    //   overlay = (AbstractTokenOverlay) stateValue;
                    // }
                    if (overlay == null
                        || overlay.isMouseover() && !isTokenUnderMouse
                        || !overlay.showPlayer(token, MapTool.getPlayer())) {
                      continue;
                    }

                    compositeBooleanState(
                        builder,
                        overlay,
                        stateValue,
                        tokenPosition.footprintBounds(),
                        token.getTokenOpacity(),
                        flowPositionLookup);
                  }
                  timer.stop("token-list-9");

                  timer.start("token-list-10");
                  for (var entry : campaign.getTokenBarsMap().entrySet()) {
                    Object barValue = token.getState(entry.getKey());
                    BarTokenOverlay overlay = entry.getValue();
                    if (overlay == null
                        || overlay.isMouseover() && !isTokenUnderMouse
                        || !overlay.showPlayer(token, MapTool.getPlayer())) {
                      continue;
                    }

                    compositeBarState(
                        builder,
                        overlay,
                        barValue,
                        tokenPosition.footprintBounds(),
                        token.getTokenOpacity());
                  }
                  // TODO locG.dispose();
                  timer.stop("token-list-10");

                  timer.start("token-list-8");
                  compositeFacingArrow(builder, tokenPosition);
                  timer.stop("token-list-8");
                });

            timer.start("token-list-11");
            // Keep track of which tokens have been drawn for post-processing on them later
            // (such as selection borders and names/labels)
            if (viewModel.getActiveLayer().equals(token.getLayer())) {
              tokenPostProcessing.add(tokenPosition);
            }
            timer.stop("token-list-11");
          }
          viewModel.setDebugShape(ZoneViewModel.DebugType.TokenClips, debug);

          var debugShapes = new Path2D.Double();
          var labelFactory = new LabelFactory();

          // TODO What does it actually mean to belong to tokenPostProcessing?
          //  Is this the same as saying "token is on screen and visible in current view"?
          var selectionModel = renderer.getSelectionModel();
          for (ZoneViewModel.TokenPosition position : tokenPostProcessing) {
            var token = position.token();
            var isTokenUnderMouse = token.getId().equals(tokenIdUnderMouse);

            // Count moving tokens as "selected" so that a border is drawn around them.
            boolean isSelected =
                selectionModel.isSelected(token.getId()) || viewModel.isTokenMoving(token.getId());
            if (isSelected) {
              compositeSelectionBox(builder, position);
              // Remove labels from the cache if the corresponding tokens are deselected
            } else if (!AppState.isShowTokenNames()) {
              // TODO I don't think I'll have a "label rendering cache". Instead, just queue up a
              // label.
              //  labelRenderingCache.remove(token.getId());
            }

            // Token names and labels
            boolean showCurrentTokenLabel = AppState.isShowTokenNames() || isTokenUnderMouse;
            // if policy does not auto-reveal FoW, check if fog covers the token (slow)
            // TODO Why auto-reveal on movement? Doesn't that imply that we should continually
            // expose at
            //  current position?
            if (showCurrentTokenLabel
                && !view.isGMView()
                && (!zoneView.isUsingVision()
                    || !MapTool.getServerPolicy().isAutoRevealOnMovement())
                && !zone.isTokenVisible(token)) {
              showCurrentTokenLabel = false;
            }
            if (showCurrentTokenLabel) {
              int offset = 3; // Keep it from tramping on the token border.

              var tokenBounds = position.transformedBounds().getBounds2D();

              String name = token.getName();
              if (view.isGMView()
                  && token.getGMName() != null
                  && !StringUtil.isEmpty(token.getGMName())) {
                name += " (" + token.getGMName() + ")";
              }

              var screenTopCenter =
                  viewport
                      .zoneScale()
                      .toScreenSpace(
                          new Point2D.Double(tokenBounds.getCenterX(), tokenBounds.getMaxY()));
              screenTopCenter.y += offset;
              var nameLabel = labelFactory.getMapImageLabel(token, name, screenTopCenter);

              debugShapes.append(
                  viewport.zoneScale().toWorldSpace(nameLabel.screenBounds()), false);

              var tokenLabel = token.getLabel();
              if (tokenLabel != null) {
                tokenLabel = tokenLabel.trim();
                if (!tokenLabel.isEmpty()) {
                  var screenTopCenter2 =
                      new ScreenPoint(
                          nameLabel.screenBounds().getCenterX(),
                          nameLabel.screenBounds().getMaxY());
                  screenTopCenter2.y += 4;

                  var labelLabel =
                      labelFactory.getMapImageLabel(token, tokenLabel, screenTopCenter2);
                  delayedCompositing.add(() -> builder.addLabel(labelLabel));

                  debugShapes.append(
                      viewport.zoneScale().toWorldSpace(labelLabel.screenBounds()), false);
                }
              }

              delayedCompositing.add(() -> builder.addLabel(nameLabel));
            }
          }

          viewModel.setDebugShape(ZoneViewModel.DebugType.LabelBounds, debugShapes);
        });
  }

  /**
   * Returns whether the token should be clipped to its cell area, depending on its bounds, the
   * view, and the visible screen area.
   *
   * @param token the token that could be clipped
   * @param tokenCellArea the cell area corresponding to the bounds of the token
   * @param isGMView whether it is the view of a GM
   * @return true if the token is need of clipping, false otherwise
   */
  private boolean isTokenInNeedOfClipping(Token token, Area tokenCellArea, boolean isGMView) {
    // can view everything or zone is not using vision = no clipping needed
    if (isGMView || !zoneView.isUsingVision()) {
      return false;
    }

    var visibleArea = viewModel.getVisibleArea();
    if (visibleArea.isEmpty()) {
      // No clipping if there is no visible screen area.
      return false;
    }

    // If the token is a figure and its center is visible then no clipping
    if (token.getShape() == Token.TokenShape.FIGURE
        && zone.getGrid().checkCenterRegion(tokenCellArea.getBounds(), visibleArea)) {
      return false;
    }

    // Jamz: Always Visible tokens will get rendered fully to place on top of FoW
    // if we can see a portion of the stamp/token, defaults to 2/9ths, don't clip at all
    if (token.isAlwaysVisible()
        && zone.getGrid()
            .checkRegion(
                tokenCellArea.getBounds(), visibleArea, token.getAlwaysVisibleTolerance())) {
      return false;
    }

    // clipping needed
    return true;
  }

  private void compositeSelectionBox(
      InstructionSetBuilder builder, ZoneViewModel.TokenPosition position) {
    var token = position.token();

    final Borders border;
    if (MapTool.getServerPolicy().isUseIndividualFOW()
        && token.getLayer().supportsVision()
        && zoneView.isUsingVision()
        && MapTool.getFrame().getToolbox().getSelectedTool() instanceof ExposeTool<?>) {
      border = Borders.FOW_TOOLS;
    } else if (!AppUtil.playerOwns(token)) {
      border = Borders.GREEN;
    } else if (viewModel.getHighlightCommonMacros().contains(token.getId())) {
      border = Borders.HIGHLIGHT;
    } else if (token.getLayer().isStampLayer()) {
      border = Borders.BLUE;
    } else {
      border = Borders.RED;
    }

    var footprint = position.footprintBounds();

    double rotation = 0.;
    var rotateAround =
        new Point2D.Double(
            footprint.getCenterX() - token.getAnchorX(),
            footprint.getCenterY() - token.getAnchorY());
    if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
      // Rotated
      // facing defaults to down, or -90  degrees
      rotation = Math.toRadians(token.getFacingInDegrees());
    }

    builder.add(new Border(border, position.footprintBounds(), rotation, rotateAround));
  }

  private void compositeFacingArrow(
      InstructionSetBuilder builder, ZoneViewModel.TokenPosition position) {
    var timer = CodeTimer.get();
    var token = position.token();
    var tokenShape = token.getShape();

    timer.start("FacingArrowRenderer-preCheck");
    try {
      if (!token.hasFacing()) {
        return;
      }
      final var forceFacing = AppPreferences.forceFacingArrow.get();
      if (!forceFacing) {
        if (Token.TokenShape.TOP_DOWN.equals(tokenShape)) {
          return;
        }
        if (Token.TokenShape.FIGURE.equals(tokenShape) && token.getHasImageTable()) {
          return;
        }
      }
    } finally {
      timer.stop("FacingArrowRenderer-preCheck");
    }

    var facing = token.getFacing();
    var footprintBounds = position.footprintBounds();

    timer.start("FacingArrowRenderer-paintArrow");
    try {
      final var isIsometric = zone.getGrid().getType().isIsometric();

      timer.start("FacingArrowRenderer-calculateTransform");
      int angle = Math.floorMod(facing + (isIsometric ? 45 : 0), 360);
      AffineTransform transform =
          buildArrowTransform(tokenShape, footprintBounds, angle, isIsometric);
      timer.stop("FacingArrowRenderer-calculateTransform");

      timer.start("FacingArrowRenderer-transformArrow");
      Shape facingArrow = transform.createTransformedShape(UNIT_ARROW);
      timer.stop("FacingArrowRenderer-transformArrow");

      timer.start("FacingArrowRenderer-fill");
      var arrowColor =
          Token.TokenShape.FIGURE.equals(tokenShape) && angle <= 180
              ? figureFillColours.get(angle)
              : fillColour;
      builder.add(new Fill(facingArrow, Paint.of(arrowColor), 1.));
      timer.stop("FacingArrowRenderer-fill");

      timer.start("FacingArrowRenderer-draw");
      // TODO How much does this even matter? These are tiny arrows. The stroke control is
      //  interesting, maybe we should do that for every stroke?
      //  tokenG.setRenderingHints(ImageUtil.getRenderingHintsQuality());
      builder.add(new Stroke(facingArrow, Paint.of(borderColour), new BasicStroke(0.85f), 1.));
      timer.stop("FacingArrowRenderer-draw");
    } catch (Exception e) {
      log.error("Failed to paint facing arrow.", e);
    } finally {
      timer.stop("FacingArrowRenderer-paintArrow");
    }
  }

  private static AffineTransform buildArrowTransform(
      Token.TokenShape shape, Rectangle2D footprintBounds, int angle, boolean isIsometric) {
    double radFacing = Math.toRadians(angle);

    AffineTransform transform = new AffineTransform();
    transform.translate(footprintBounds.getCenterX(), footprintBounds.getCenterY());
    if (isIsometric) {
      transform.scale(1.0, 0.5);
    }
    transform.rotate(-radFacing);

    double distanceToPoint = footprintBounds.getWidth() / 2;
    if (Token.TokenShape.SQUARE.equals(shape) && !isIsometric) {
      if (angle >= 45 && angle <= 135 || angle >= 225 && angle <= 315) { // Top or bottom face.
        distanceToPoint = footprintBounds.getHeight() / 2 / Math.abs(Math.sin(radFacing));
      } else { // Left or right face
        distanceToPoint = footprintBounds.getWidth() / 2 / Math.abs(Math.cos(radFacing));
      }
    }
    transform.translate(distanceToPoint, 0);

    var size = footprintBounds.getWidth() / 2d;
    transform.scale(size, size);
    return transform;
  }

  private void compositeStacks(
      InstructionSetBuilder builder, ZoneViewport viewport, PlayerView view) {
    if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
      return;
    }

    boolean hideTSI = AppPreferences.hideTokenStackIndicator.get();
    if (hideTSI) {
      return;
    }

    var tokenStackIds = viewModel.getTokenStackMap().keySet();
    if (tokenStackIds.isEmpty()) {
      return;
    }

    builder.unbufferedLayer(
        "stackIndicators",
        ClipType.VisibleArea,
        () -> {
          for (GUID tokenId : tokenStackIds) {
            var position = viewModel.getTokenPositions().get(tokenId);
            if (position == null) {
              // Shouldn't happen, but should handle the case anyway.
              continue;
            }
            if (!viewModel.getOnScreenTokens().contains(tokenId)) {
              // Don't draw indicator for offscreen tokens.
              continue;
            }

            var halfIconSize = 6;
            var screenBounds =
                viewport.zoneScale().toScreenSpace(position.transformedBounds().getBounds2D());
            var imageScreenBounds =
                new Rectangle2D.Double(
                    screenBounds.getMaxX() - 2 * halfIconSize + 2,
                    screenBounds.getMinY() - 2,
                    2 * halfIconSize,
                    2 * halfIconSize);
            var worldBounds = viewport.zoneScale().toWorldSpace(imageScreenBounds);
            builder.add(new Icon(Images.ZONE_RENDERER_STACK_IMAGE, worldBounds));
          }
        });
  }

  private void compositeBooleanState(
      InstructionSetBuilder builder,
      BooleanTokenOverlay overlay,
      Object value,
      Rectangle2D tokenBounds,
      double tokenOpacity,
      Function<Integer, Rectangle2D> getNextCellBoundsForGrid) {
    /*TODO The original additionally clipped to the token bounds. But our current clipping support
       does not permit an additional level of clipping.
       Rectangle2D tokenBounds =
           viewModel
                   .getZoneScale()
                   .toScreenSpace(tokenPosition.transformedBounds().getBounds2D());
       I also don't like the clipping. It should be up to the state to decide where to draw, and it
       shouldn't be an issue for it to transgress the tokne's bound bit a little.
    */
    if (!FunctionUtil.getBooleanValue(value)) {
      return;
    }

    var overlayOpacity = tokenOpacity * overlay.getOpacity() / 100.;

    switch (overlay) {
      case AbstractFlowShapeTokenOverlay flowShapeTokenOverlay -> {
        var color = flowShapeTokenOverlay.getColor();
        var cellBounds = getNextCellBoundsForGrid.apply(flowShapeTokenOverlay.getGrid());
        var shape = flowShapeTokenOverlay.getShape(cellBounds);
        builder.add(new Fill(shape, Paint.of(color), overlayOpacity));
      }
      case AbstractShapeTokenOverlay shapeTokenOverlay -> {
        var color = shapeTokenOverlay.getColor();
        var stroke = shapeTokenOverlay.getStroke();
        var shape = shapeTokenOverlay.getShape(tokenBounds);
        builder.add(new Stroke(shape, Paint.of(color), stroke, overlayOpacity));
      }
      case ColorDotTokenOverlay colorDotTokenOverlay -> {
        var shape = colorDotTokenOverlay.getShape(tokenBounds);
        builder.add(new Fill(shape, Paint.of(colorDotTokenOverlay.getColor()), overlayOpacity));
      }
      case ImageTokenOverlay imageTokenOverlay -> {
        var imageId = imageTokenOverlay.getAssetId();
        var image = ImageManager.getImage(imageId, renderer);
        var imageBounds = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        AwtUtil.fitInto(imageBounds, tokenBounds);

        var transform = new AffineTransform();
        builder.add(new ImageAsset(imageId, imageBounds, transform, overlayOpacity));
      }
      case CornerImageTokenOverlay cornerImageTokenOverlay -> {
        var imageId = cornerImageTokenOverlay.getAssetId();
        var image = ImageManager.getImage(imageId, renderer);
        Rectangle2D imageBounds = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        AwtUtil.fitInto(imageBounds, tokenBounds);

        imageBounds.setRect(cornerImageTokenOverlay.getBounds(imageBounds));

        var transform = new AffineTransform();
        builder.add(new ImageAsset(imageId, imageBounds, transform, overlayOpacity));
      }
      case FlowImageTokenOverlay flowImageTokenOverlay -> {
        var imageId = flowImageTokenOverlay.getAssetId();
        var image = ImageManager.getImage(imageId, renderer);
        var imageBounds = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        AwtUtil.fitInto(imageBounds, tokenBounds);

        imageBounds.setRect(getNextCellBoundsForGrid.apply(flowImageTokenOverlay.getGrid()));

        var transform = new AffineTransform();
        builder.add(new ImageAsset(imageId, imageBounds, transform, overlayOpacity));
      }
      case ShadedTokenOverlay shadedTokenOverlay -> {
        builder.add(new Fill(tokenBounds, Paint.of(shadedTokenOverlay.getColor()), overlayOpacity));
      }
      default -> {
        log.error("Unrecognized boolean overlay type: {}", overlay.getClass().getCanonicalName());
      }
    }
  }

  private void compositeBarState(
      InstructionSetBuilder builder,
      BarTokenOverlay overlay,
      Object value,
      Rectangle2D tokenBounds,
      double tokenOpacity) {
    /*TODO The original additionally clipped to the token bounds. But our current clipping support
      does not permit an additional level of clipping.
      Rectangle2D tokenBounds =
           viewModel
                   .getZoneScale()
                   .toScreenSpace(tokenPosition.transformedBounds().getBounds2D());
    */

    switch (overlay) {
      case MultipleImageBarTokenOverlay multipleImageBarTokenOverlay -> {}
      case SingleImageBarTokenOverlay singleImageBarTokenOverlay -> {}
      case TwoToneBarTokenOverlay twoToneBarTokenOverlay -> {}
      case TwoImageBarTokenOverlay twoImageBarTokenOverlay -> {}
      case DrawnBarTokenOverlay drawnBarTokenOverlay -> {}
      default -> {
        log.error("Unrecognized bar overlay type: {}", overlay.getClass().getCanonicalName());
      }
    }
  }

  private void compositeGrid(
      InstructionSetBuilder builder, ZoneViewport viewport, Grid grid, Color gridColor) {
    if (!AppState.isShowGrid()) {
      return;
    }
    if (grid.getSize() * viewport.zoneScale().getScale() < ZoneRendererConstants.MIN_GRID_SIZE) {
      return;
    }

    builder.unbufferedLayer(
        "grid",
        ClipType.NoClipping,
        () -> {
          var path =
              switch (grid) {
                case HexGridVertical hexVertical ->
                    buildHexGridPath(
                        viewport,
                        false,
                        grid.getSize(),
                        grid.getSecondDimension(),
                        grid.getOffsetX(),
                        grid.getOffsetY());
                case HexGridHorizontal hexHorizontal ->
                    buildHexGridPath(
                        viewport,
                        true,
                        grid.getSize(),
                        grid.getSecondDimension(),
                        grid.getOffsetX(),
                        grid.getOffsetY());
                case SquareGrid square ->
                    buildSquareGridPath(
                        viewport, grid.getSize(), grid.getOffsetX(), grid.getOffsetY());
                case IsometricGrid isometric ->
                    buildIsometricGridPath(
                        viewport, grid.getSize(), grid.getOffsetX(), grid.getOffsetY());
                case GridlessGrid gridless -> new Path2D.Double();
                default -> new Path2D.Double();
              };

          var contrast = new Color(ImageUtil.negativeColourInt(gridColor.getRGB()));
          var cap = grid.getType().isIsometric() ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT;
          var gridColors =
              List.of(
                  gridColor,
                  ColorUtil.withAlpha(gridColor, 0.14f),
                  ColorUtil.withAlpha(contrast, 0.04f),
                  ColorUtil.withAlpha(contrast, 0.05f));
          var gridLineWeight = AppState.getGridLineWeight();
          var baseWidth = grid.getSize() / 50.;
          if (viewport.zoneScale().getScale() > 0.49f) {
            for (int i = 3; i > -1; i--) {
              builder.add(
                  new Stroke(
                      path,
                      Paint.of(gridColors.get(i)),
                      new BasicStroke(
                          (float) (baseWidth * (i + 1) * 0.5 * gridLineWeight),
                          cap,
                          BasicStroke.JOIN_MITER),
                      1.));
            }
          } else {
            builder.add(
                new Stroke(
                    path,
                    Paint.of(gridColors.get(0)),
                    new BasicStroke(
                        (float)
                            (baseWidth * gridLineWeight * 0.25 / viewport.zoneScale().getScale()),
                        cap,
                        BasicStroke.JOIN_MITER),
                    1.));
          }
        });
  }

  /**
   * A {@link BiConsumer}-like interface that accepts {@code double}.
   *
   * <p>Meant only for {@link #roundToPixel(ZoneViewport, double, double, PointConsumer)}.
   */
  @FunctionalInterface
  private interface PointConsumer {
    void accept(double x, double y);
  }

  /**
   * A utility method for adding points to grid shapes.
   *
   * <p>Because grids are thin, we need to be careful not to place them between pixels, especially
   * when using OpenGL. This method accepts a point, and will round it to the nearest pixel center.
   * Rather than return the result, it will be passed to {@code consumer} so this method can be
   * easily passed {@link Path2D#moveTo(double, double)} or {@link Path2D#lineTo(double, double)} as
   * a method reference.
   *
   * @param viewport
   * @param x
   * @param y
   * @param consumer
   */
  private static void roundToPixel(
      ZoneViewport viewport, double x, double y, PointConsumer consumer) {
    var roundedScreen = viewport.zoneScale().toScreenSpace(new Point2D.Double(x, y));
    // Snap to pixel center.
    roundedScreen.x = 0.5 + (int) roundedScreen.x;
    roundedScreen.y = 0.5 + (int) roundedScreen.y;
    var roundedWorld = viewport.zoneScale().toWorldSpace(roundedScreen);
    consumer.accept(roundedWorld.getX(), roundedWorld.getY());
  }

  private Shape buildHexGridPath(
      ZoneViewport viewport,
      boolean isHorizontal,
      int vSize,
      double uSize,
      int offsetX,
      int offsetY) {
    var bounds = viewport.getWorldSpaceBounds();

    /*
     * To understand the implementation, keep these facts in mind for a vertical hex:
     * 1. The `vSize` is the vertical distance between the top and bottom edges.
     * 2. The `uSize` is the horizontal distance between the left and right vertices.
     * 3. The left and right edges extend exactly `0.25 * diameter` horizontally and `0.5 * size`
     *    vertically.
     * 4. The V axis is vertical, the U axis is horizontal.
     *
     * For a horizontal hex, rotate the above by 90°.
     *
     * The relationships in (3) can make some of this seem a bit magical. It can help to draw a
     * picture for a full understanding.
     */

    double halfVWidth = vSize / 2.;
    double halfUWidth = uSize / 2.;

    double offsetU = isHorizontal ? offsetY : offsetX;
    double offsetV = isHorizontal ? offsetX : offsetY;
    double boundsMinU = isHorizontal ? bounds.getMinY() : bounds.getMinX();
    double boundsMinV = isHorizontal ? bounds.getMinX() : bounds.getMinY();
    double boundsSizeU = isHorizontal ? bounds.getHeight() : bounds.getWidth();
    double boundsSizeV = isHorizontal ? bounds.getWidth() : bounds.getHeight();

    double stepV = halfVWidth;
    double stepU = 3. * halfUWidth;

    // Start assuming vertical, swap if needed.
    double startU = boundsMinU + (offsetU - boundsMinU) % stepU;
    if (startU > boundsMinU) {
      startU -= stepU;
    }
    double endU = boundsMinU + boundsSizeU + stepU;

    // Odd and even steps are handled differently w.r.t. `u`, so two steps are one cycle.
    double startV = boundsMinV + (offsetV - boundsMinV) % (2 * stepV);
    if (startV > boundsMinV) {
      startV -= 2 * stepV;
    }
    double endV = boundsMinV + boundsSizeV;

    int count = 0;

    Path2D path = new Path2D.Double();
    for (double v = startV; v < endV; v += stepV) {
      double offsetU2 = (count++ & 1) == 0 ? 0 : -1.5 * halfUWidth;

      for (double u = startU; u < endU; u += stepU) {
        var x = isHorizontal ? v : u + offsetU2;
        var y = isHorizontal ? u + offsetU2 : v;

        if (isHorizontal) {
          roundToPixel(viewport, x + halfVWidth, y, path::moveTo);
          roundToPixel(viewport, x, y + 0.5 * halfUWidth, path::lineTo);
          roundToPixel(viewport, x, y + 1.5 * halfUWidth, path::lineTo);
          roundToPixel(viewport, x + halfVWidth, y + 2.0 * halfUWidth, path::lineTo);
        } else {
          roundToPixel(viewport, x, y + halfVWidth, path::moveTo);
          roundToPixel(viewport, x + 0.5 * halfUWidth, y, path::lineTo);
          roundToPixel(viewport, x + 1.5 * halfUWidth, y, path::lineTo);
          roundToPixel(viewport, x + 2.0 * halfUWidth, y + halfVWidth, path::lineTo);
        }
      }
    }

    return path;
  }

  private Shape buildSquareGridPath(ZoneViewport viewport, int size, int offsetX, int offsetY) {
    var bounds = viewport.getWorldSpaceBounds();
    double gridSize = size;

    double startX = bounds.getMinX() + (offsetX - bounds.getMinX()) % gridSize;
    if (startX > bounds.getMinX()) {
      startX -= gridSize;
    }
    double endX = startX + bounds.getWidth() + gridSize;

    double startY = bounds.getMinY() + (offsetY - bounds.getMinY()) % gridSize;
    if (startY > bounds.getMinY()) {
      startY -= gridSize;
    }
    double endY = startY + bounds.getHeight() + gridSize;

    Path2D path = new Path2D.Double();
    for (double y = startY; y <= endY; y += gridSize) {
      roundToPixel(viewport, startX, y, path::moveTo);
      roundToPixel(viewport, endX, y, path::lineTo);
    }
    for (double x = startX; x < endX; x += gridSize) {
      roundToPixel(viewport, x, startY, path::moveTo);
      roundToPixel(viewport, x, endY, path::lineTo);
    }

    return path;
  }

  private Shape buildIsometricGridPath(ZoneViewport viewport, int size, int offsetX, int offsetY) {
    var bounds = viewport.getWorldSpaceBounds();
    double isoHeight = size;
    double isoWidth = 2 * isoHeight;

    Path2D path = new Path2D.Double();

    double startX = bounds.getMinX() + (offsetX - bounds.getMinX()) % isoWidth;
    if (startX > bounds.getMinX()) {
      startX -= isoWidth;
    }
    double endX = startX + bounds.getWidth() + isoWidth;

    double startY = bounds.getMinY() + (offsetY - bounds.getMinY()) % isoHeight;
    if (startY > bounds.getMinY()) {
      startY -= isoHeight;
    }
    double endY = startY + bounds.getHeight() + isoHeight;

    int hatchSize = isoHeight > 10 ? (int) (isoHeight / 8) : 2;

    for (double y = startY; y < endY; y += isoHeight) {
      for (double x = startX; x < endX; x += isoWidth) {
        // Draw one hatch at the top of the current cell.
        roundToPixel(viewport, x - 2 * hatchSize, y - hatchSize, path::moveTo);
        roundToPixel(viewport, x + 2 * hatchSize, y + hatchSize, path::lineTo);
        roundToPixel(viewport, x - 2 * hatchSize, y + hatchSize, path::moveTo);
        roundToPixel(viewport, x + 2 * hatchSize, y - hatchSize, path::lineTo);

        // And another at the cell (+1, +1) from here.
        var x2 = x + isoWidth / 2;
        var y2 = y + isoHeight / 2;
        roundToPixel(viewport, x2 - 2 * hatchSize, y2 - hatchSize, path::moveTo);
        roundToPixel(viewport, x2 + 2 * hatchSize, y2 + hatchSize, path::lineTo);
        roundToPixel(viewport, x2 - 2 * hatchSize, y2 + hatchSize, path::moveTo);
        roundToPixel(viewport, x2 + 2 * hatchSize, y2 - hatchSize, path::lineTo);
      }
    }

    return path;
  }

  private void compositeCoordinates(
      InstructionSetBuilder builder, ZoneViewport viewport, Grid grid) {
    if (!AppState.isShowCoordinates()) {
      return;
    }
    if (!(grid instanceof SquareGrid squareGrid)) {
      // Only square grids have coordinate support for now.
      return;
    }

    var factory = new LabelFactory();

    builder.unbufferedLayer(
        "coordinates",
        ClipType.NoClipping,
        () -> {
          var bounds = viewport.getWorldSpaceBounds();

          var dummy = factory.getXCoordinateLabel("MMM", new ScreenPoint(0, 0));

          var topLeftZone = new Point2D.Double(bounds.getMinX(), bounds.getMinY());

          CellPoint topLeft =
              SquareGrid.convert(
                  topLeftZone.getX(),
                  topLeftZone.getY(),
                  grid.getSize(),
                  grid.getOffsetX(),
                  grid.getOffsetY());
          var topLeftCenter = squareGrid.getCellCenter(topLeft);

          // Make sure we don't overlap coordinates in the top-left corner.
          Point2D marginTopLeft =
              new ScreenPoint(
                  dummy.screenBounds().getWidth() + 10, dummy.screenBounds().getHeight());

          double nextAvailableScreenSpace = -1;
          for (double x = topLeftCenter.x; x < bounds.getMaxX(); x += grid.getSize(), ++topLeft.x) {
            String coord = Integer.toString(topLeft.x);

            var screenPosition = viewport.zoneScale().toScreenSpace(new Point2D.Double(x, 0));
            screenPosition.y = 0;

            var xLabel = factory.getXCoordinateLabel(coord, screenPosition);

            if (xLabel.screenBounds().getMinX() > marginTopLeft.getX()
                && xLabel.screenBounds().getMinX() > nextAvailableScreenSpace) {
              // TODO Original make a drop shadow by drawing four offsets of the string as black
              //  before changing to orange and drawing the final one.
              builder.add(xLabel);
              nextAvailableScreenSpace = xLabel.screenBounds().getMaxX() + 10;
            }
          }

          nextAvailableScreenSpace = -1;
          for (double y = topLeftCenter.y; y < bounds.getMaxY(); y += grid.getSize(), ++topLeft.y) {
            String coord = SquareGrid.decimalToAlphaCoord(topLeft.y);

            var leftCenter = viewport.zoneScale().toScreenSpace(new Point2D.Double(0, y));
            leftCenter.x = 10;

            var yLabel = factory.getYCoordinateLabel(coord, leftCenter);

            if (yLabel.screenBounds().getMinY() > marginTopLeft.getY()
                && yLabel.screenBounds().getMinY() > nextAvailableScreenSpace) {
              // TODO Original make a drop shadow by drawing four offsets of the string as black
              //  before changing to yellow and drawing the final one.
              builder.add(yLabel);
              nextAvailableScreenSpace = yLabel.screenBounds().getMaxY() + 10;
            }
          }
        });
  }

  private void compositeTextLabels(InstructionSetBuilder builder) {
    var labelLocations = viewModel.getLabelLocations();
    if (labelLocations.isEmpty()) {
      return;
    }

    builder.unbufferedLayer(
        "textLabels",
        ClipType.NoClipping,
        () -> {
          for (var labelLocation : labelLocations) {
            builder.addLabel(labelLocation);
          }
        });
  }

  private void compositeFog(InstructionSetBuilder builder, ZoneViewport viewport, PlayerView view) {
    if (!zone.hasFog()) {
      return;
    }

    // Fog of war intentionally hides lower layers behind it.
    // TODO This doesn't work when the fog is transparent. Personally, I think fog of war should set
    //  a black background before rendering the hard FoW on top of that. And it might make sense to
    //  clip lower layers to the exposed area.
    builder.bufferedLayer(
        "fogOfWar",
        ClipType.NoClipping,
        BlendMode.AlphaSrcOver,
        1.,
        () -> {

          // To avoid any accidental revealing of the underlying map when the fog texture has
          // transparency, render against a black background if not a GM.
          builder.add(new ClearScreen(view.isGMView() ? COLOR_CLEAR : Color.black));

          var visibility = zoneView.getVisibility(view);
          Area softFogArea = visibility.softFogArea();
          Area clearArea = visibility.clearArea();

          var hardFogPaint = zone.getFogPaint();
          var extraHardFogOpacity = view.isGMView() ? .6f : 1f;
          var softFogOpacity = AppPreferences.fogOverlayOpacity.get() / 255.;

          // Do SrcOver so we can blend with the black background if set.
          builder.add(new SwitchAlphaMode(AlphaMode.SrcOver));
          builder.add(new FillFrameBuffer(Paint.of(hardFogPaint), extraHardFogOpacity));

          builder.add(new SwitchAlphaMode(AlphaMode.SrcOnly));

          if (!softFogArea.isEmpty()) {
            builder.add(
                new Fill(
                    softFogArea,
                    Paint.of(new Color(0, 0, 0, Math.clamp((int) (255 * softFogOpacity), 0, 255))),
                    1.));
          }

          if (!clearArea.isEmpty()) {
            builder.add(new Fill(clearArea, Paint.of(COLOR_CLEAR), 1.));
          }

          // If there is no boundary between soft fog and visible area, there is no need for an
          // outline.
          if (!softFogArea.isEmpty() && !clearArea.isEmpty()) {
            builder.add(
                new Stroke(
                    clearArea,
                    Paint.of(Color.black),
                    new BasicStroke(1 / (float) viewport.zoneScale().getScale()),
                    1.));
          }
        });
  }

  private void compositeUnownedMovement(
      InstructionSetBuilder builder,
      List<Runnable> delayedCompositing,
      PlayerView view,
      Area clearArea) {
    if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
      return;
    }

    Set<SelectionSet> movementSet = new HashSet<>();
    for (SelectionSet selection : renderer.getSelectionSetMap().values()) {
      if (!selection.getPlayerId().equals(MapTool.getPlayer().getName())) {
        movementSet.add(selection);
      }
    }

    if (movementSet.isEmpty()) {
      return;
    }

    builder.unbufferedLayer(
        "unownedMovement",
        ClipType.ClearArea,
        () -> {
          compositeMovement(builder, delayedCompositing, view, movementSet, clearArea);
        });
  }

  private void compositeOwnedMovement(
      InstructionSetBuilder builder,
      List<Runnable> delayedCompositing,
      PlayerView view,
      Area clearArea) {
    if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
      return;
    }

    Set<SelectionSet> movementSet = new HashSet<>();
    for (SelectionSet selection : renderer.getSelectionSetMap().values()) {
      if (selection.getPlayerId().equals(MapTool.getPlayer().getName())) {
        movementSet.add(selection);
      }
    }

    if (movementSet.isEmpty()) {
      return;
    }

    builder.unbufferedLayer(
        "ownedMovement",
        ClipType.ClearArea,
        () -> {
          compositeMovement(builder, delayedCompositing, view, movementSet, clearArea);
        });
  }

  private void compositeMovement(
      InstructionSetBuilder builder,
      List<Runnable> delayedCompositing,
      PlayerView view,
      Set<SelectionSet> movementSets,
      Area clearArea) {
    var viewport = builder.getViewport();

    for (SelectionSet set : movementSets) {
      Token keyToken = zone.getToken(set.getKeyToken());
      if (keyToken == null) {
        // It was removed ?
        // TODO
        //  selectionSetMap.remove(set.getKeyToken());
        continue;
      }
      // Hide the hidden layer
      if (!keyToken.getLayer().isVisibleToPlayers() && !view.isGMView()) {
        continue;
      }
      Grid grid = zone.getGrid();
      ZoneWalker walker = set.getWalker();

      // Paint each travelling token and its path.
      for (GUID tokenGUID : set.getTokens()) {
        Token token = zone.getToken(tokenGUID);
        if (token == null) {
          // Perhaps deleted?
          continue;
        }

        var position = viewModel.getTokenPositions().get(token.getId());
        if (position == null) {
          // Token not visible to this player.
          continue;
        }

        // Show path only on the key token on token layer that are visible to the owner or gm while
        // fow and vision is on
        if (token == keyToken && token.getLayer().supportsWalker()) {
          builder.addPath(
              walker != null ? walker.getPath() : set.getGridlessPath(), token.getFootprint(grid));
        }

        // Show current Blocked Movement directions for A*
        if (walker != null && DeveloperOptions.Toggle.ShowAiDebugging.get()) {
          double iconWidth = grid.getCellWidth() / 4.;
          double iconHeight = grid.getCellHeight() / 4.;

          Map<CellPoint, Set<CellPoint>> blockedMovesByTarget = walker.getBlockedMoves();
          for (var entry : blockedMovesByTarget.entrySet()) {
            var targetPoint = entry.getKey();
            var blockedMoves = entry.getValue();

            for (CellPoint point : blockedMoves) {
              ZonePoint zp = grid.midZonePoint(point, targetPoint);
              Rectangle2D iconBounds =
                  new Rectangle2D.Double(
                      zp.x - iconWidth / 2., zp.y - iconHeight / 2., iconWidth, iconHeight);
              builder.add(new Icon(Images.ZONE_RENDERER_BLOCK_MOVE, iconBounds));
            }
          }
        }

        // We need a shifted version of the position, to wherever the token is being dragged.
        var newBounds =
            new Rectangle2D.Double(
                position.footprintBounds().getX() + set.getOffsetX(),
                position.footprintBounds().getY() + set.getOffsetY(),
                position.footprintBounds().getWidth(),
                position.footprintBounds().getHeight());
        var newArea = new Area(position.transformedBounds());
        newArea.transform(AffineTransform.getTranslateInstance(set.getOffsetX(), set.getOffsetY()));
        var newPosition = new ZoneViewModel.TokenPosition(token, newBounds, newArea);
        // Paint the token at newPosition.
        {
          // get token image, using image table if present
          MD5Key tokenImageId = token.getTokenImageAssetId(viewModel.getCampaign());
          BufferedImage renderImage = ImageManager.getImage(tokenImageId, renderer);
          var imageTransform =
              TokenUtil.getRenderTransform(
                  zone,
                  token,
                  new Dimension(renderImage.getWidth(), renderImage.getHeight()),
                  newPosition.footprintBounds());
          builder.add(new ImageAsset(tokenImageId, imageTransform, 1.));
        }

        // Other details.
        // Only draw these if the token is visible on screen where it is dragged to.
        if (token == keyToken
            && (AppUtil.playerOwns(token) || shouldShowMovementLabels(token, set, clearArea))
            && viewModel.getViewport().intersects(newPosition.footprintBounds())) {
          var screenBounds = viewport.zoneScale().toScreenSpace(newPosition.footprintBounds());

          var labelY = (int) screenBounds.getMaxY() + 10;
          var labelX = (int) screenBounds.getCenterX();

          if (token.getLayer().supportsWalker() && AppState.getShowMovementMeasurements()) {
            double distanceTraveled = calculateTraveledDistance(set);
            if (distanceTraveled >= 0) {
              String distance = NumberFormat.getInstance().format(distanceTraveled);
              var distanceLabelY = labelY;
              delayedCompositing.add(
                  () -> builder.add(new BoxedString(labelX, distanceLabelY, distance)));

              labelY += 20;
            }
          }
          if (set.getPlayerId() != null && !set.getPlayerId().isEmpty()) {
            var playerLabelY = labelY;
            delayedCompositing.add(
                () -> builder.add(new BoxedString(labelX, playerLabelY, set.getPlayerId())));
          }
        }
      }
    }
  }

  private boolean shouldShowMovementLabels(Token token, SelectionSet set, Area clearArea) {
    Rectangle tokenRectangle;
    if (set.getWalker() != null) {
      final var path = set.getWalker().getPath();
      if (path.getCellPath().isEmpty()) {
        return false;
      }
      final var lastPoint = path.getCellPath().getLast();
      final var grid = zone.getGrid();
      tokenRectangle = token.getFootprint(grid).getBounds(grid, lastPoint);
    } else {
      final var path = set.getGridlessPath();
      if (path.getCellPath().isEmpty()) {
        return false;
      }
      final var lastPoint = path.getCellPath().getLast();
      Rectangle tokBounds = token.getFootprintBounds(zone);
      tokenRectangle = new Rectangle();
      tokenRectangle.setBounds(
          lastPoint.x, lastPoint.y, (int) tokBounds.getWidth(), (int) tokBounds.getHeight());
    }

    return clearArea == null || clearArea.intersects(tokenRectangle);
  }

  private double calculateTraveledDistance(SelectionSet set) {
    ZoneWalker walker = set.getWalker();
    if (walker != null) {
      // This wouldn't be true unless token.isSnapToGrid() && grid.isPathingSupported()
      return walker.getDistance();
    }

    double distanceTraveled = 0;
    ZonePoint lastPoint = null;
    for (ZonePoint zp : set.getGridlessPath().getCellPath()) {
      if (lastPoint == null) {
        lastPoint = zp;
        continue;
      }
      int a = lastPoint.x - zp.x;
      int b = lastPoint.y - zp.y;
      distanceTraveled += Math.hypot(a, b);
      lastPoint = zp;
    }
    distanceTraveled /= zone.getGrid().getSize(); // Number of "cells"
    distanceTraveled *= zone.getUnitsPerCell(); // "actual" distance traveled
    return distanceTraveled;
  }

  private void compositeVisionOverlay(
      InstructionSetBuilder builder, ZoneViewport viewport, PlayerView view) {
    var tokenIdUnderMouse = viewModel.getTokenUnderMouse();
    if (tokenIdUnderMouse == null) {
      return;
    }

    var tokenPositionUnderMouse = viewModel.getTokenPositions().get(tokenIdUnderMouse);
    if (tokenPositionUnderMouse == null) {
      return;
    }

    var tokenUnderMouse = tokenPositionUnderMouse.token();

    boolean isOwner = AppUtil.playerOwns(tokenUnderMouse);
    boolean tokenIsPC = tokenUnderMouse.getType() == Token.Type.PC;
    boolean strictOwnership =
        MapTool.getServerPolicy() != null && MapTool.getServerPolicy().useStrictTokenManagement();
    boolean showVisionAndHalo = isOwner || view.isGMView() || (tokenIsPC && !strictOwnership);
    if (!showVisionAndHalo) {
      return;
    }

    builder.unbufferedLayer(
        "vision",
        ClipType.ExposedArea,
        () -> {
          // The vision of the token is not necessarily related to the current view.
          final var tokenView = new PlayerView(view.getRole(), List.of(tokenUnderMouse));
          Area currentTokenVisionArea = zoneView.getVisibleArea(tokenUnderMouse, tokenView);
          // Nothing to show.
          if (currentTokenVisionArea.isEmpty()) {
            return;
          }

          // TODO Original explicitly clipped with the exposed area if fog was enabled. But I kind
          // of like
          //  the new approach that relies on the renderer doing the clipping. This changes the
          // outline
          //  when a token can see an area that is also covered in hard FoW.

          // TODO For some reason the original does the fill after the stroke. Why not the other way
          //  around? Not that it matters much
          builder.add(
              new Stroke(
                  currentTokenVisionArea,
                  Paint.of(Color.white),
                  new BasicStroke(1 / (float) viewport.zoneScale().getScale()),
                  1.));

          @Nullable Color visionColor = tokenUnderMouse.getVisionOverlayColor();
          if (visionColor == null && AppPreferences.useHaloColorOnVisionOverlay.get()) {
            visionColor = tokenUnderMouse.getHaloColor();
          }
          if (visionColor != null) {
            builder.add(
                new Fill(
                    currentTokenVisionArea,
                    Paint.of(visionColor),
                    AppPreferences.haloOverlayOpacity.get() / 255.));
          }
        });
  }

  private void compositeLights(InstructionSetBuilder builder, PlayerView view) {
    var timer = CodeTimer.get();
    timer.start("compositeLights");
    try {
      if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
        return;
      }
      if (!AppState.isShowLights()) {
        return;
      }

      final var drawableLights = zoneView.getDrawableLights(view);
      if (drawableLights.isEmpty()) {
        return;
      }
      final var lightingStyle = zone.getLightingStyle();

      final var clip = ClipType.VisibleArea;
      final var blend =
          switch (lightingStyle) {
            case ENVIRONMENTAL -> BlendMode.Brighten;
            case OVERTOP -> BlendMode.StraightAlphaSrcOver;
          };
      final var clearColor =
          switch (lightingStyle) {
            case ENVIRONMENTAL -> Color.black;
            case OVERTOP -> COLOR_CLEAR;
          };
      final var opacity =
          switch (lightingStyle) {
            case ENVIRONMENTAL -> 1.;
            case OVERTOP -> AppPreferences.lightOverlayOpacity.get() / 255.f;
          };
      builder.bufferedLayer(
          "lights",
          clip,
          blend,
          opacity,
          () -> {
            builder.add(new ClearScreen(clearColor));
            builder.add(new SwitchAlphaMode(AlphaMode.Screen));

            for (var light : drawableLights) {
              builder.add(new Fill(light.getArea(), Paint.of(light.getPaint()), 1.));
            }
          });
    } finally {
      timer.stop("compositeLights");
    }
  }

  private void compositeAuras(InstructionSetBuilder builder, PlayerView view) {
    var timer = CodeTimer.get();
    timer.start("compositeAuras");
    try {
      if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
        return;
      }
      // TODO No AppState control for auras?

      final var drawableAuras = zoneView.getDrawableAuras(view);
      if (drawableAuras.isEmpty()) {
        return;
      }

      builder.unbufferedLayer(
          "auras",
          ClipType.VisibleArea,
          () -> {
            final var auraOpacity = AppPreferences.auraOverlayOpacity.get() / 255.f;

            for (var aura : drawableAuras) {
              builder.add(new Fill(aura.getArea(), Paint.of(aura.getPaint()), auraOpacity));
            }
          });
    } finally {
      timer.stop("compositeAuras");
    }
  }

  private void compositeLumens(InstructionSetBuilder builder, PlayerView view) {
    if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)) {
      return;
    }
    if (!AppState.isShowLumensOverlay()) {
      return;
    }

    var timer = CodeTimer.get();

    var overlayOpacity = AppPreferences.lumensOverlayOpacity.get() / 255.0f;
    var borderThickness = AppPreferences.lumensOverlayBorderThickness.get();
    var disjointLumensLevels = new ArrayList<>(zoneView.getDisjointObscuredLumensLevels(view));

    builder.bufferedLayer(
        "lumens",
        ClipType.NoClipping,
        BlendMode.AlphaSrcOver,
        1.,
        () -> {
          // At night, show any uncovered areas as dark. In daylight, show them as light (clear).
          builder.add(
              new ClearScreen(
                  zone.getVisionType() == Zone.VisionType.NIGHT
                      ? new Color(0.f, 0.f, 0.f, overlayOpacity)
                      : COLOR_CLEAR));

          builder.add(new SwitchAlphaMode(AlphaMode.SrcOnly));
          // For the rest of the rendering, we need to clip to the visible area.
          if (!view.isGMView()) {
            builder.add(new SetClipType(ClipType.VisibleArea));
          }

          // Note that we want the fills to have transparency, but the borders to be solid. So we
          // capture
          // opacity in the fill colors.

          // TODO Original did `new Color(0.f, 0.f, 0.f, 1.f)`. Any difference?
          var darknessFill = Paint.of(Color.black);
          var borderPaint = Paint.of(Color.black);

          timer.start("compositeLumensFills");
          for (final var lumensLevel : disjointLumensLevels) {
            final var lumensStrength = lumensLevel.lumensStrength();

            // Light is weaker than darkness, so do it first.
            float lightOpacity;
            float lightShade;
            if (lumensStrength == 0) {
              // This area represents daylight, so draw it as clear despite the low value.
              lightShade = 1.f;
              lightOpacity = 0;
            } else if (lumensStrength >= 100) {
              // Bright light, render mostly clear.
              lightShade = 1.f;
              lightOpacity = 1.f / 10.f;
            } else {
              lightShade = Math.max(0.f, Math.min(lumensStrength / 100.f, 1.f));
              lightShade *= lightShade;
              lightOpacity = 1.f;
            }

            builder.add(
                new Fill(
                    lumensLevel.lightArea(),
                    Paint.of(new Color(lightShade, lightShade, lightShade, lightOpacity)),
                    overlayOpacity));
            builder.add(new Fill(lumensLevel.darknessArea(), darknessFill, overlayOpacity));
          }
          timer.stop("compositeLumensFills");

          timer.start("compositeLumensBorders");
          if (borderThickness > 0) {
            var borderStroke =
                new BasicStroke(
                    (float) borderThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            for (var lumensLevel : disjointLumensLevels) {
              builder.add(new Stroke(lumensLevel.lightArea(), borderPaint, borderStroke, 1.));
            }
          }
          timer.stop("compositeLumensBorders");
        });
  }

  private void compositeDarkness(InstructionSetBuilder builder, PlayerView view) {
    if (view.isGMView()) {
      // Darkness shouldn't hide anything from GMs.
      return;
    }

    final Area darkness = zoneView.getIllumination(view).getDarkenedArea();
    if (darkness.isEmpty()) {
      // Nothing to do in this case.
      return;
    }

    builder.unbufferedLayer(
        "darkness",
        ClipType.NoClipping,
        () -> {
          builder.add(new Fill(darkness, Paint.of(Color.black), 1.));
        });
  }

  private void compositeLightSourceIcons(
      InstructionSetBuilder builder, ZoneViewport viewport, PlayerView view) {
    if (!renderer.shouldRenderLayer(Zone.Layer.TOKEN, view)
        || !view.isGMView()
        || !AppState.isShowLightSources()) {
      return;
    }

    builder.unbufferedLayer(
        "lightSourceIcons",
        ClipType.NoClipping,
        () -> {
          final var halfIconSize = 8.; // The light source icon is 16x16, so ...
          for (var point : viewModel.getLightPositions()) {
            var screenPoint = viewport.zoneScale().toScreenSpace(point);
            var screenBounds =
                new Rectangle2D.Double(
                    screenPoint.x - halfIconSize,
                    screenPoint.y - halfIconSize,
                    halfIconSize * 2,
                    halfIconSize * 2);
            var worldBounds = viewport.zoneScale().toWorldSpace(screenBounds);

            builder.add(new Icon(Images.LIGHT_SOURCE, worldBounds));
          }
        });
  }

  private void compositeDebugShapes(InstructionSetBuilder builder, ZoneViewport viewport) {
    var debugShapes = viewModel.getDebugShapes();
    if (debugShapes.isEmpty()) {
      return;
    }

    builder.unbufferedLayer(
        "debug",
        ClipType.NoClipping,
        () -> {
          // Keep the border a consistent thickness regardless of zoom level.
          var stroke = new BasicStroke((float) (1. / viewport.zoneScale().getScale()));

          for (var entry : debugShapes.entrySet()) {
            var borderColor = entry.getKey().color;
            var shape = entry.getValue();

            var fillColor = borderColor.darker();
            fillColor =
                new Color(
                    fillColor.getRed(),
                    fillColor.getGreen(),
                    fillColor.getBlue(),
                    // TODO Can't I set the below opacities to `1./3.`?
                    fillColor.getAlpha() / 3);
            var paint = Paint.of(fillColor);

            builder.add(new Fill(shape, paint, 1.));
            builder.add(new Stroke(shape, paint, stroke, 1.));
          }
        });
  }
}
