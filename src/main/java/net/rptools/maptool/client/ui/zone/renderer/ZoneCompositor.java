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
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.client.ui.zone.ZoneViewModel;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ClipType;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSet;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSetBuilder;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.BoxedString;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.ClearScreen;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ZoneViewport;
import net.rptools.maptool.model.Zone;

/**
 * The Zone Compositor is responsible for providing the Zone Renderer with what needs to be
 * rendered. Within a given map region what objects exist that need to be drawn. Basically "What's
 * on screen?"
 */
public class ZoneCompositor {
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
      // TODO Map board

      // TODO Object drawables

      // TODO Grid

      // TODO Object stamps if Object layer enabled.

      // TODO Lights/lumens/auras if Token layer enabled

      // TODO Darkness

      // TODO Token drawables
      // TODO Only do GM layer if Token layer is also enabled.
      // TODO GM drawables

      // TODO GM tokens if GM layer & Token layer is enabled.
      // TODO Regular tokens if Token layer is enabled.
      // TODO Unowned moves if Token layer is enabled.

      // TODO Text labels

      // TODO Fog of war

      // TODO VBL & Figure tokens if Token layer enabled (even though they aren't all Tokens).

      // TODO Owned moves if Token layer is enabled.

      // TODO General renderables if Token layer is enabled. Whatever these are.

      // TODO Vision overlay.

      // TODO Zone overlays

      // TODO Coordinates

      // TODO Light source icons, if Token layer is enabled, is GM view & option is enabled.

      // TODO Debug rendering.
    }

    return new InstructionSet(viewport, ImmutableList.copyOf(instructions), clips);
  }
}
