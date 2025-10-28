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
package net.rptools.maptool.client.ui.zone.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import javax.annotation.Nullable;
import net.rptools.maptool.client.ui.zone.renderer.instructions.BlendMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Interface to our customer shader. */
public class LayerShader implements Disposable {
  public static final int SOURCE_TEXTURE_INDEX = 0;
  public static final int CLIPPING_TEXTURE_INDEX = 1;
  public static final int DESTINATION_TEXTURE_INDEX = 2;
  private static final Logger log = LogManager.getLogger(LayerShader.class);
  private static final State defaultState = new State(BlendMode.AlphaSrcOver, 1.f, null, null);

  // TODO Also track the BlendFunction
  private record State(
      BlendMode blendMode,
      float opacity,
      @Nullable Texture destination,
      @Nullable Texture clipBuffer) {}

  private final ShaderProgram program;
  private final Batch batch;
  private final ArrayList<State> stateStack;
  private final Texture whitePixel;
  private final Texture clearPixel;

  // TODO Explode the current state into separate fields. Have separate internal methods for setting
  //  the associated uniforms or other behaviour.
  private State currentState;

  public LayerShader(Batch batch, Texture whitePixel, Texture clearPixel) {
    this.program =
        new ShaderProgram(
            Gdx.files.classpath("net/rptools/maptool/client/ui/zone/gdx/layerShader.vsh"),
            Gdx.files.classpath("net/rptools/maptool/client/ui/zone/gdx/layerShader.fsh"));
    this.batch = batch;
    this.stateStack = new ArrayList<>();
    this.whitePixel = whitePixel;
    this.clearPixel = clearPixel;

    applyState(defaultState);
  }

  @Override
  public void dispose() {
    program.dispose();
    stateStack.clear();
    applyState(defaultState);
  }

  public void start() {
    stateStack.clear();

    batch.setShader(program);
    program.setUniformi("u_dst", DESTINATION_TEXTURE_INDEX);
    program.setUniformi("u_clip", CLIPPING_TEXTURE_INDEX);

    applyState(defaultState);
  }

  public void save() {
    stateStack.add(currentState);
  }

  public void restore() {
    if (stateStack.isEmpty()) {
      log.error("Attempted to pop from an empty stack");
      return;
    }

    applyState(stateStack.removeLast());
  }

  // TOOD Also offer a method to set all state at once.

  public void setBlendMode(BlendMode blendMode) {
    applyState(
        new State(
            blendMode,
            currentState.opacity(),
            currentState.destination(),
            currentState.clipBuffer()));
  }

  public void setOpacity(float opacity) {
    applyState(
        new State(
            currentState.blendMode(),
            opacity,
            currentState.destination(),
            currentState.clipBuffer()));
  }

  public void setDestination(@Nullable Texture texture) {
    applyState(
        new State(
            currentState.blendMode(), currentState.opacity(), texture, currentState.clipBuffer()));
  }

  public void setClipBuffer(@Nullable Texture clipBuffer) {
    applyState(
        new State(
            currentState.blendMode(),
            currentState.opacity(),
            currentState.destination(),
            clipBuffer));
  }

  private void applyState(State state) {
    // If we change uniforms without flushing, existing buffered rendering will use the new values,
    // not the old.
    batch.flush();

    program.setUniformi("u_blendMode", getBlendModeShaderConstant(state.blendMode()));
    program.setUniformf("u_opacity", state.opacity());

    /*
     * The texture binding isn't really shader state because of how OpenGL expresses things, but
     * it's close enough.
     */
    var destTexture = state.destination() == null ? clearPixel : state.destination();
    destTexture.bind(DESTINATION_TEXTURE_INDEX);
    // TODO Do I need the following line if we're about to bind another one?
    // Avoid affecting the blending texture any further (OpenGL state machine)
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

    var clipTexture = state.clipBuffer() == null ? whitePixel : state.clipBuffer();
    clipTexture.bind(CLIPPING_TEXTURE_INDEX);
    // Avoid affecting the blending texture any further (OpenGL state machine)
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

    currentState = state;
  }

  private static int getBlendModeShaderConstant(BlendMode mode) {
    // These must match the definitions found in `layerShader.fsh`.
    return switch (mode) {
      case AlphaSrcOver -> 1;
      case StraightAlphaSrcOver -> 2;
      case Brighten -> 3;
      case SrcOnly -> 4;
    };
  }
}
