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

import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppState;
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
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.FillFrameBuffer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.ImageAsset;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Meta.SwitchAlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Noise;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ZoneViewport;
import net.rptools.maptool.language.I18N;
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

      // TODO Grid

      // TODO Object stamps if Object layer enabled.

      // TODO Lights/lumens/auras if Token layer enabled

      // TODO Darkness

      compositeDrawings(builder, view, Zone.Layer.TOKEN, worldBounds);
      compositeDrawings(builder, view, Zone.Layer.GM, worldBounds);

      // TODO GM tokens if GM layer & Token layer is enabled.
      // TODO Regular tokens if Token layer is enabled.
      // TODO Unowned moves if Token layer is enabled.

      // TODO Text labels

      // TODO Fog of war

      // TODO VBL & Figure tokens if Token layer enabled (even though they aren't all Tokens).

      // TODO Owned moves if Token layer is enabled.

      // TODO General renderables if Token layer is enabled. Whatever these are.

      // TODO Vision overlay.

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
}
