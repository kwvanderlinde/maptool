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
package net.rptools.maptool.client.ui.zone.renderer.instructions;

import java.util.function.Consumer;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.model.Zone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InstructionSetBuilder {
  private static final Logger log = LogManager.getLogger(InstructionSetBuilder.class);

  private final Consumer<RenderInstruction> instructionSink;
  private final Zone zone;
  private final ZoneViewport viewport;

  public InstructionSetBuilder(
      Consumer<RenderInstruction> instructionSink, Zone zone, ZoneViewport viewport) {
    this.instructionSink = instructionSink;
    this.zone = zone;
    this.viewport = viewport;
  }

  public Zone getZone() {
    return zone;
  }

  public ZoneViewport getViewport() {
    return viewport;
  }

  public void unbufferedLayer(String layerName, ClipType clipType, Runnable action) {
    var timer = CodeTimer.get();
    timer.start("composite-%s", layerName);
    try {
      add(new RenderInstruction.Meta.StartUnbufferedLayer(layerName, clipType));
      try {
        action.run();
      } finally {
        add(new RenderInstruction.Meta.FinishLayer(layerName));
      }
    } finally {
      timer.stop("composite-%s", layerName);
    }
  }

  public void bufferedLayer(
      String layerName, ClipType clipType, BlendMode blendMode, double opacity, Runnable action) {
    var timer = CodeTimer.get();
    timer.start("composite-%s", layerName);
    try {
      add(new RenderInstruction.Meta.StartBufferedLayer(layerName, clipType, blendMode, opacity));
      try {
        action.run();
      } finally {
        add(new RenderInstruction.Meta.FinishLayer(layerName));
      }
    } finally {
      timer.stop("composite-%s", layerName);
    }
  }

  public void add(RenderInstruction instruction) {
    instructionSink.accept(instruction);
  }
}
