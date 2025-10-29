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
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.client.ui.zone.ZoneViewModel;
import net.rptools.maptool.client.ui.zone.renderer.instructions.AlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.BlendMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ClipType;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSet;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSetBuilder;
import net.rptools.maptool.client.ui.zone.renderer.instructions.Paint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.BoxedString;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.ClearScreen;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Fill;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.FillFrameBuffer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.ImageAsset;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Meta.SwitchAlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Noise;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Stroke;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ZoneViewport;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Grid;
import net.rptools.maptool.model.GridlessGrid;
import net.rptools.maptool.model.HexGridHorizontal;
import net.rptools.maptool.model.HexGridVertical;
import net.rptools.maptool.model.IsometricGrid;
import net.rptools.maptool.model.SquareGrid;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.DrawnElement;
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

  private final List<RenderInstruction> instructions = new ArrayList<>();
  private final ZoneRenderer renderer;
  private final ZoneViewModel viewModel;
  private final ZoneView zoneView;
  private final Zone zone;

  public ZoneCompositor(ZoneRenderer renderer) {
    this.renderer = renderer;
    this.viewModel = renderer.getViewModel();
    this.zoneView = renderer.getZoneView();
    this.zone = renderer.getZone();
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
      // TODO Background stamps if Background layer is enabled.

      compositeDrawings(builder, view, Zone.Layer.OBJECT, worldBounds);

      compositeGrid(builder, viewport, zone.getGrid(), new Color(zone.getGridColor(), false));

      // TODO Object stamps if Object layer enabled.

      // TODO Lights/lumens/auras if Token layer enabled

      // TODO Darkness

      compositeDrawings(builder, view, Zone.Layer.TOKEN, worldBounds);
      compositeDrawings(builder, view, Zone.Layer.GM, worldBounds);

      // TODO GM tokens if GM layer & Token layer is enabled.
      // TODO Regular tokens if Token layer is enabled.
      // TODO Unowned moves if Token layer is enabled.

      // TODO Text labels

      compositeFog(builder, viewport, view);

      // TODO VBL & Figure tokens if Token layer enabled (even though they aren't all Tokens).

      // TODO Owned moves if Token layer is enabled.

      // TODO General renderables if Token layer is enabled. Whatever these are.

      compositeVisionOverlay(builder, viewport, view);

      for (var overlay : renderer.getOverlays()) {
        builder.unbufferedLayer(
            String.format("overlay-%s", overlay.getClass().getCanonicalName()),
            ClipType.NoClipping,
            () -> {
              overlay.compositeOverlay(builder, worldBounds);
            });
      }

      // TODO Coordinates

      // TODO Light source icons, if Token layer is enabled, is GM view & option is enabled.

      // TODO Debug rendering.

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
                          BasicStroke.CAP_ROUND,
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
                        BasicStroke.CAP_ROUND,
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
          var visibility = zoneView.getVisibility(view);
          Area softFogArea = visibility.softFogArea();
          Area clearArea = visibility.clearArea();

          var hardFogPaint = zone.getFogPaint();
          var extraHardFogOpacity = view.isGMView() ? .6f : 1f;
          var softFogOpacity = AppPreferences.fogOverlayOpacity.get() / 255.;

          builder.add(new SwitchAlphaMode(AlphaMode.SrcOnly));
          builder.add(new FillFrameBuffer(Paint.of(hardFogPaint), extraHardFogOpacity));

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
}
