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

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.common.eventbus.Subscribe;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import javax.annotation.Nullable;
import javax.swing.*;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.MD5Key;
import net.rptools.lib.gdx.ConfigurablePool;
import net.rptools.maptool.client.*;
import net.rptools.maptool.client.events.ZoneActivated;
import net.rptools.maptool.client.swing.ImageBorder;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.client.ui.theme.Borders;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.LabelBackgrounds;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.client.ui.zone.gdx.drawing.SimpleDrawingRenderer;
import net.rptools.maptool.client.ui.zone.gdx.label.TextRenderer;
import net.rptools.maptool.client.ui.zone.renderer.instructions.AlphaMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.BlendMode;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ClipType;
import net.rptools.maptool.client.ui.zone.renderer.instructions.InstructionSet;
import net.rptools.maptool.client.ui.zone.renderer.instructions.Paint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction;
import net.rptools.maptool.client.ui.zone.renderer.instructions.ZoneViewport;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.drawing.DrawableNoise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * The coordinates in the model are y-down, x-left. The world coordinates are y-up, x-left. I moved
 * the world to the 4th quadrant of the coordinate system. So if you would draw a token t awt at
 * (x,y) you have to draw it at (x, -y - t.width)
 *
 * <p>
 */
public class GdxRenderer extends ApplicationAdapter {

  private static final Logger log = LogManager.getLogger(GdxRenderer.class);

  private static GdxRenderer _instance;

  private record RegionBorder(
      TextureRegion topRight,
      TextureRegion top,
      TextureRegion topLeft,
      TextureRegion left,
      TextureRegion bottomLeft,
      TextureRegion bottom,
      TextureRegion bottomRight,
      TextureRegion right) {}

  private static final class LayerState {
    public final String name;
    public final BlendMode blendMode;
    public final ClipType clipType;
    public final double opacity;
    public BlendFunction blendFunction;
    public FrameBuffer buffer;
    // TODO Make nullable to indicate whether a custom clip has been set. We'll still have it
    //  illegal to set more than one custom clip on a layer at one time.
    public @Nullable FrameBuffer maskBuffer;

    public LayerState(
        String name,
        ClipType clipType,
        BlendMode blendMode,
        double opacity,
        BlendFunction blendFunction,
        FrameBuffer buffer) {
      this.name = name;
      this.clipType = clipType;
      this.blendMode = blendMode;
      this.opacity = opacity;

      this.blendFunction = blendFunction;
      this.buffer = buffer;
      this.maskBuffer = null;
    }

    public void begin(Batch batch) {
      blendFunction.applyToBatch(batch);
      buffer.begin();
    }

    public void end(Batch batch) {
      batch.flush();
      // createScreenshot(layerName);
      buffer.end();
      BlendFunction.PREMULTIPLIED_ALPHA_SRC_OVER.applyToBatch(batch);
    }
  }

  // renderFog
  private final String ATLAS = "net/rptools/maptool/client/maptool.atlas";
  private final String FONT_NORMAL = "normalFont.ttf";
  private final String FONT_BOLD = "boldFont.ttf";

  private final String font = "NotoSansSymbols";

  public AtomicReference<InstructionSet> renderInstructionSet =
      new AtomicReference<>(
          new InstructionSet(new ZoneViewport(1, 1, new Scale()), List.of(), Map.of()));

  // zone specific resources
  private ZoneCache zoneCache;
  private ZoneViewport zoneViewport;
  private int offsetX = 0;
  private int offsetY = 0;
  private float zoom = 1.0f;
  private float stateTime = 0f;
  private boolean renderZone = false;

  private LayerShader layerShader;

  // general resources
  private OrthographicCamera cam;
  private OrthographicCamera hudCam;
  private PolygonSpriteBatch batch;
  private boolean initialized = false;
  private int width;
  private int height;
  private BitmapFont normalFont;
  private BitmapFont boldFont;
  private float boldFontScale = 0;

  private Pool<FrameBuffer> frameBufferPool;
  private Pool<FrameBuffer> maskBufferPool;

  private final Map<ClipType, FrameBuffer> clipBuffers = new EnumMap<>(ClipType.class);

  private com.badlogic.gdx.assets.AssetManager manager;

  private TextureAtlas atlas;
  private final Map<Images, TextureRegion> cachedImageResources = new HashMap<>();
  private final Map<String, RegionBorder> cachedBorders = new HashMap<>();

  private ShapeDrawer drawer;
  private TextRenderer textRenderer;
  private TextRenderer hudTextRenderer;
  private AreaRenderer areaRenderer;
  private SimpleDrawingRenderer simpleDrawingRenderer;

  private TextureRegion transferringAsset;
  private TextureRegion brokenAsset;
  // Same as transferring asset and brokenAsset, but used for paints that require an actual Texture
  // to support repeating patterns. These are less efficient than transferringAsset and brokenAsset,
  // so don't use them unless necessary!
  private Texture transferringAssetTexture;
  private Texture brokenAssetTexture;

  private Texture whitePixel;
  private Texture clearPixel;

  private final List<LayerState> layerStack = new ArrayList<>();

  // temorary objects. Stored here to avoid garbage collection;
  private final Vector3 tmpWorldCoord = new Vector3();
  private final Color tmpColor = new Color();
  private final FloatArray tmpFloat = new FloatArray();
  private final Vector2 tmpVector = new Vector2();
  private final Vector2 tmpVectorOut = new Vector2();
  private final Vector2 tmpVector0 = new Vector2();
  private final Vector2 tmpVector1 = new Vector2();
  private final Vector2 tmpVector2 = new Vector2();
  private final Matrix4 tmpMatrix = new Matrix4();
  private final Matrix4 tmpMatrix2 = new Matrix4();
  private final Affine2 tmpAffine = new Affine2();
  private final Area tmpArea = new Area();
  private final TiledDrawable tmpTile = new TiledDrawable();

  public GdxRenderer() {
    new MapToolEventBus().getMainEventBus().register(this);
  }

  public static GdxRenderer getInstance() {
    if (_instance == null) _instance = new GdxRenderer();
    return _instance;
  }

  @Override
  public void create() {
    try {

      // with jogl create is called every time we change the parent frame of the GLJPanel
      // e.g. change from fullcreen to window or the other way around. Reinit everthing in this
      // case.
      if (initialized) {
        initialized = false;
        dispose();

        atlas = null;
        normalFont = null;
        boldFont = null;
      }

      frameBufferPool =
          new ConfigurablePool<>(
              /*
               * Our layer blending requires three buffers (source layer, destination layer, spare
               * buffer. Drawing groups also require extra buffers (should only be one, but the
               * model is recursive). So while we should only require 4, we'll be a bit loose on the
               * maximum to avoid excessive frame buffer allocations.
               */
              3,
              10,
              new ConfigurablePool.PoolSupplier<>() {
                @Override
                public FrameBuffer get() {
                  CodeTimer.get().increment("frame-buffer-get");

                  return new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
                }

                @Override
                public void reset(FrameBuffer object) {
                  CodeTimer.get().increment("frame-buffer-reset");

                  // Nothing to do for resets.
                }

                @Override
                public void discard(FrameBuffer fbo) {
                  CodeTimer.get().increment("frame-buffer-discard");

                  // Need to release the native resources.
                  fbo.dispose();
                }
              });
      maskBufferPool =
          new ConfigurablePool<>(
              // Four clip types, plus a typical three buffers being swapped, plus wiggle room.
              7,
              14,
              new ConfigurablePool.PoolSupplier<>() {
                @Override
                public FrameBuffer get() {
                  CodeTimer.get().increment("mask-buffer-get");

                  return new FrameBuffer(Pixmap.Format.Alpha, width, height, false);
                }

                @Override
                public void reset(FrameBuffer object) {
                  CodeTimer.get().increment("mask-buffer-reset");

                  // Nothing to do for resets.
                }

                @Override
                public void discard(FrameBuffer fbo) {
                  CodeTimer.get().increment("mask-buffer-discard");

                  // Need to release the native resources.
                  fbo.dispose();
                }
              });

      Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      try {
        pixmap.setBlending(Pixmap.Blending.None);

        pixmap.setColor(Color.WHITE);
        pixmap.drawPixel(0, 0);
        whitePixel = new Texture(pixmap);

        pixmap.setColor(Color.CLEAR);
        pixmap.drawPixel(0, 0);
        clearPixel = new Texture(pixmap);
      } finally {
        pixmap.dispose();
      }

      // Make sure the vertex count is way higher than we'll need to triangule, say, a circle.
      batch = new PolygonSpriteBatch(20_000);
      batch.enableBlending();

      layerShader = new LayerShader(batch, whitePixel, clearPixel);

      manager = new com.badlogic.gdx.assets.AssetManager();
      {
        loadAssets();

        var resolver = new InternalFileHandleResolver();
        manager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        manager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));

        manager.finishLoading();

        atlas = manager.get(ATLAS, TextureAtlas.class);

        transferringAsset = atlas.findRegion("unknown");
        brokenAsset = atlas.findRegion("broken");

        transferringAssetTexture = newTextureFromRegion(transferringAsset);
        brokenAssetTexture = newTextureFromRegion(brokenAsset);

        normalFont = manager.get(FONT_NORMAL, BitmapFont.class);
        textRenderer = new TextRenderer(atlas, batch, normalFont);
        hudTextRenderer = new TextRenderer(atlas, batch, normalFont, false);
      }

      width = Gdx.graphics.getWidth();
      height = Gdx.graphics.getHeight();

      cam = new OrthographicCamera();
      cam.setToOrtho(false);

      hudCam = new OrthographicCamera();
      hudCam.setToOrtho(false);

      updateCam();

      drawer = new ShapeDrawer(batch, new TextureRegion(whitePixel));

      areaRenderer = new AreaRenderer(drawer, whitePixel);
      simpleDrawingRenderer = new SimpleDrawingRenderer(areaRenderer);

      initialized = true;
    } catch (Exception e) {
      log.error("Unhandled exception in GdxRenderer::create()", e);
    }
  }

  @Override
  public void dispose() {
    try {
      layerShader.dispose();
      manager.dispose();
      batch.dispose();
      if (zoneCache != null) {
        zoneCache.dispose();
      }
      whitePixel.dispose();
    } catch (Exception e) {
      log.error("Unhandled exception in GdxRenderer::dispose()", e);
    }
  }

  @Override
  public void resize(int width, int height) {
    try {
      /*
       * We have to look up the width and height ourselves because `JoglGraphicsBase` divides by the
       * back buffer scale instead of multiplying by it. This means the `width` and `height`
       * parameters here are neither the back buffer size nor the client area, and thus we can't use
       * them.
       */
      width = Gdx.graphics.getWidth();
      height = Gdx.graphics.getHeight();

      this.width = width;
      this.height = height;

      for (var entry : clipBuffers.entrySet()) {
        entry.getValue().dispose();
      }
      for (var clipType : ClipType.values()) {
        clipBuffers.put(clipType, new FrameBuffer(Pixmap.Format.Alpha, width, height, false));
      }

      // Pooled frame buffers are no longer valid, so discard them all.
      frameBufferPool.clear();
      maskBufferPool.clear();

      updateCam();
    } catch (Exception e) {
      log.error("Unhandled exception in GdxRenderer::resize()", e);
    }
  }

  private TextureRegion fetchImageResource(Images resource) {
    return cachedImageResources.computeIfAbsent(
        resource,
        key -> {
          var name =
              switch (key) {
                case LIGHT_SOURCE -> "lightbulb";
                case ZONE_RENDERER_STACK_IMAGE -> "stack";
                case GRID_BORDER_SQUARE -> "whiteBorder";
                case GRID_BORDER_ISOMETRIC -> "isoBorder";
                case GRID_BORDER_HEX -> "hexBorder";
                case GRID_BORDER_HEX_HORIZONTAL -> "hexBorderHorizontal";
                case ZONE_RENDERER_CELL_WAYPOINT -> "redDot";
                case ZONE_RENDERER_BLOCK_MOVE -> "block_move";
                default -> null;
              };
          return name == null ? null : atlas.findRegion(name);
        });
  }

  private Texture newTextureFromRegion(TextureRegion region) {
    Texture originalTexture = region.getTexture();
    if (!originalTexture.getTextureData().isPrepared()) {
      originalTexture.getTextureData().prepare();
    }
    Pixmap originalPixmap = originalTexture.getTextureData().consumePixmap();
    try {
      Pixmap newPixmap =
          new Pixmap(region.getRegionWidth(), region.getRegionHeight(), originalPixmap.getFormat());
      try {
        newPixmap.drawPixmap(
            originalPixmap,
            0,
            0,
            region.getRegionX(),
            region.getRegionY(),
            region.getRegionWidth(),
            region.getRegionHeight());

        return new Texture(newPixmap);
      } finally {
        newPixmap.dispose();
      }
    } finally {
      originalPixmap.dispose();
    }
  }

  private void updateCam() {
    if (cam == null) return;

    cam.viewportWidth = width;
    cam.viewportHeight = height;
    cam.position.x = zoom * (cam.viewportWidth / 2f + offsetX);
    cam.position.y = zoom * (cam.viewportHeight / 2f * -1 + offsetY);
    cam.zoom = zoom;
    cam.update();

    hudCam.viewportWidth = width;
    hudCam.viewportHeight = height;
    hudCam.position.x = hudCam.viewportWidth / 2f;
    hudCam.position.y = hudCam.viewportHeight / 2f;
    hudCam.update();
  }

  @Override
  public void render() {
    try {
      CodeTimer.using(
          "GdxRenderer.renderZone",
          timer -> {
            timer.setThreshold(10);
            timer.setThreshold(1, TimeUnit.MICROSECONDS);
            timer.setReportingUnit(TimeUnit.MICROSECONDS);

            ScreenUtils.clear(Color.BLACK);

            var instructionSet = renderInstructionSet.get();

            // System.out.println("FPS:   " + Gdx.graphics.getFramesPerSecond());
            var delta = Gdx.graphics.getDeltaTime();
            stateTime += delta;

            ensureTtfFont();
            ScreenUtils.clear(Color.BLACK);

            // Try conservative rasterization. This avoids potential issues of samples being lost
            // between pixels. TODO Should probably enable it solely for Stroke() instructions.
            if (Gdx.graphics.supportsExtension("GL_NV_conservative_raster")) {
              // Values taken from the spec here:
              // https://registry.khronos.org/OpenGL/extensions/NV/NV_conservative_raster.txt
              final var CONSERVATIVE_RASTERIZATION_NV = 0x9346;
              Gdx.gl20.glEnable(CONSERVATIVE_RASTERIZATION_NV);
            }

            doRendering(instructionSet);
          });
    } catch (Exception e) {
      log.error("Unhandled exception in GdxRenderer::render()", e);
    }
  }

  private void ensureTtfFont() {
    // TODO The instruction set should have as its metadata instructions to ensure certain resources
    //  are available, in this case the font needed for path distance text.
    //  This is to avoid the need to depend on the zone.

    if (zoneCache == null) return;

    var fontScale =
        (float) zoneCache.getZone().getGrid().getSize()
            / 50; // Font size of 12 at grid size 50 is default

    if (fontScale == this.boldFontScale && boldFont != null) return;

    var fontParams = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
    //    fontParams.fontFileName = "net/rptools/maptool/client/fonts/OpenSans-Bold.ttf";
    fontParams.fontFileName =
        String.format("net/rptools/maptool/client/fonts/%s/%s-Bold.ttf", font, font);
    fontParams.fontParameters.size = (int) (12 * fontScale);
    fontParams.loadedCallback = GdxRenderer::premultiplyFontOnLoad;
    manager.load(FONT_BOLD, BitmapFont.class, fontParams);
    manager.finishLoading();
    boldFont = manager.get(FONT_BOLD, BitmapFont.class);
    boldFontScale = fontScale;
  }

  private void loadAssets() {
    manager.load(ATLAS, TextureAtlas.class);
    var fontParams = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
    fontParams.fontFileName =
        String.format("net/rptools/maptool/client/fonts/%s/%s-Regular.ttf", font, font);
    fontParams.fontParameters.size = 12;
    fontParams.loadedCallback = GdxRenderer::premultiplyFontOnLoad;

    manager.load(FONT_NORMAL, BitmapFont.class, fontParams);
  }

  private void doRendering(InstructionSet instructionSet) {
    batch.enableBlending();
    // Framebuffer is premultiplied. Assume source textures are as well (can be changed for
    // operations that require something else).
    BlendFunction.PREMULTIPLIED_ALPHA_SRC_OVER.applyToBatch(batch);

    // this happens sometimes when starting with ide (non-debug)
    if (batch.isDrawing()) batch.end();
    batch.begin();

    if (zoneCache == null || !renderZone) {
      return;
    }

    setScale(instructionSet.viewport());

    setProjectionMatrix(cam.combined);

    renderZone(instructionSet);

    setProjectionMatrix(hudCam.combined);

    hudTextRenderer.drawString("FPS:   " + Gdx.graphics.getFramesPerSecond(), width - 30, 30);
    hudTextRenderer.drawString("Draws: " + batch.renderCalls, width - 30, 16);

    batch.end();
  }

  private GdxPaint getPaint(Paint paint) {
    var color = new Color();
    Texture texture;

    switch (paint) {
      case Paint.Color colorPaint -> {
        Color.argb8888ToColor(color, colorPaint.argb8888());
        color.premultiplyAlpha();

        texture = whitePixel;
      }
      case Paint.Texture texturePaint -> {
        color.set(Color.WHITE);
        texture =
            zoneCache.getPaintTexture(
                texturePaint.assetId(), transferringAssetTexture, brokenAssetTexture);
      }
    }

    return new GdxPaint(color, texture);
  }

  private void renderZone(InstructionSet instructionSet) {
    CodeTimer timer = CodeTimer.get();

    // Update the clips.
    batch.setShader(null);
    // When setting up the clips, we only want the alpha channel preserved.
    Gdx.gl.glColorMask(false, false, false, true);
    // Exact color doesn't matter as we're only keeping the alpha channel anyways.
    var maskColor = Color.WHITE;
    for (var clipType : ClipType.values()) {
      var area = instructionSet.clips().get(clipType);
      if (area == null) {
        // No need to allocate a buffer for this one.
        continue;
      }

      var clipBuffer = maskBufferPool.obtain();
      clipBuffers.put(clipType, clipBuffer);

      clipBuffer.begin();
      try {
        setProjectionMatrix(cam.combined);
        ScreenUtils.clear(Color.CLEAR);
        // Only fill the region inside the clip.
        BlendFunction.SRC_ONLY.applyToBatch(batch);
        areaRenderer.setColor(maskColor);
        areaRenderer.fillArea(batch, area);
        batch.flush();
      } finally {
        clipBuffer.end();
      }
    }
    // Re-enable full color writing.
    Gdx.gl.glColorMask(true, true, true, true);

    var rootLayer =
        new LayerState(
            "<root>",
            ClipType.NoClipping,
            BlendMode.AlphaSrcOver,
            1.,
            BlendFunction.PREMULTIPLIED_ALPHA_SRC_OVER,
            frameBufferPool.obtain());
    rootLayer.begin(batch);
    layerShader.setClipBuffer(
        rootLayer.maskBuffer == null ? null : rootLayer.maskBuffer.getColorBufferTexture());
    ScreenUtils.clear(Color.CLEAR);

    processInstructions(rootLayer, instructionSet.instructions());

    rootLayer.buffer.end();

    Gdx.gl.glViewport(0, 0, width, height);
    layerShader.start();
    setProjectionMatrix(hudCam.combined);
    BlendFunction.PREMULTIPLIED_ALPHA_SRC_OVER.applyToBatch(batch);
    batch.draw(rootLayer.buffer.getColorBufferTexture(), 0, 0, width, height, 0, 0, 1, 1);
    batch.flush();

    frameBufferPool.free(rootLayer.buffer);
    if (rootLayer.maskBuffer != null) {
      maskBufferPool.free(rootLayer.maskBuffer);
    }
    setProjectionMatrix(cam.combined);

    for (var buffer : clipBuffers.values()) {
      maskBufferPool.free(buffer);
    }
    clipBuffers.clear();
  }

  private void processInstructions(LayerState rootLayer, List<RenderInstruction> instructions) {
    var timer = CodeTimer.get();
    timer.increment("instructions", instructions.size(), new Object[0]);

    layerStack.clear();
    var currentLayer = rootLayer;

    // Use our custom shader, but with no clipping or extra blending.
    layerShader.start();

    // Most layers will want premultiplied alpha, so just set it here.
    BlendFunction.PREMULTIPLIED_ALPHA_SRC_OVER.applyToBatch(batch);

    for (var instruction : instructions) {
      final var timerLayer = currentLayer.name;
      timer.increment("instructions-%s", 1, instruction);
      timer.start("layer-%s[%s]", timerLayer, instruction);

      setProjectionMatrix(cam.combined);

      switch (instruction) {
        case RenderInstruction.Meta.StartBufferedLayer(
            String layerName,
            ClipType clipType,
            BlendMode blendMode,
            double opacity) -> {
          timer.increment("layer-%s-render", 1, layerName);
          timer.start("layer-%s-render", layerName);

          layerStack.add(currentLayer);
          currentLayer.buffer.end();

          currentLayer =
              new LayerState(
                  layerName,
                  clipType,
                  blendMode,
                  opacity,
                  BlendFunction.readFromBatch(batch),
                  frameBufferPool.obtain());
          currentLayer.begin(batch);
          ScreenUtils.clear(Color.CLEAR, true);

          layerShader.start();
          layerShader.setDestination(null);
          layerShader.setBlendMode(BlendMode.AlphaSrcOver);
          layerShader.setOpacity(1.f);
          layerShader.setClipBuffer(
              currentLayer.maskBuffer == null
                  ? null
                  : currentLayer.maskBuffer.getColorBufferTexture());
        }
        case RenderInstruction.Meta.StartUnbufferedLayer(String layerName, ClipType clipType) -> {
          timer.increment("layer-%s-render", 1, layerName);
          timer.start("layer-%s-render", layerName);

          layerStack.add(currentLayer);
          currentLayer.buffer.end();

          currentLayer =
              new LayerState(
                  layerName,
                  clipType,
                  BlendMode.AlphaSrcOver,
                  1.,
                  BlendFunction.readFromBatch(batch),
                  frameBufferPool.obtain());
          currentLayer.begin(batch);
          ScreenUtils.clear(Color.CLEAR, true);

          layerShader.start();
          layerShader.setDestination(null);
          layerShader.setBlendMode(BlendMode.AlphaSrcOver);
          layerShader.setOpacity(1.f);
          layerShader.setClipBuffer(
              currentLayer.maskBuffer == null
                  ? null
                  : currentLayer.maskBuffer.getColorBufferTexture());
        }
        case RenderInstruction.Meta.FinishLayer(String layerName) -> {
          // TODO We can still blit down if the stack is empty. Just new up a brand new layer state
          //  (while still warning, of course).
          if (layerStack.isEmpty()) {
            log.error("Tried to finish layer {}, but there are no layers right now!", layerName);
            break;
          }

          if (!currentLayer.name.equals(layerName)) {
            log.error(
                "Tried to finish layer {}, but layer {} is still active!",
                layerName,
                currentLayer.name);
            break;
          }
          timer.stop("layer-%s-render", layerName);
          var poppedLayer = currentLayer;
          poppedLayer.end(batch);

          currentLayer = layerStack.removeLast();

          timer.increment("layer-%s-blit", 1, layerName);
          timer.start("layer-%s-blit", layerName);
          // We want to keep exactly whatever results the shader blended.
          BlendFunction.SRC_ONLY.applyToBatch(batch);
          var spareBuffer = frameBufferPool.obtain();
          {
            setProjectionMatrix(hudCam.combined);

            spareBuffer.begin();
            ScreenUtils.clear(Color.RED, true);
            layerShader.start();

            layerShader.setDestination(currentLayer.buffer.getColorBufferTexture());

            /*TODO With dynamic layers, there are two sources of masks:
             * 1. The outgoing layer's configured mask.
             * 2. The parent layer's custom clip.
             */
            FrameBuffer clip = clipBuffers.get(poppedLayer.clipType);
            Texture texture = clip == null ? null : clip.getColorBufferTexture();
            layerShader.setClipBuffer(texture);

            layerShader.setBlendMode(poppedLayer.blendMode);
            layerShader.setOpacity((float) poppedLayer.opacity);

            batch.setColor(Color.WHITE);
            batch.draw(poppedLayer.buffer.getColorBufferTexture(), 0, 0, width, height, 0, 0, 1, 1);
            batch.flush();
            timer.stop("layer-%s-blit", layerName);

            timer.start("layer-%s-complete", layerName);
            // Release old buffers.
            frameBufferPool.free(poppedLayer.buffer);
            if (poppedLayer.maskBuffer != null) {
              maskBufferPool.free(poppedLayer.maskBuffer);
            }
            // Swap buffers spare buffer with current layer's back buffer.
            frameBufferPool.free(currentLayer.buffer);
            currentLayer.buffer = spareBuffer;

            // spareBuffer, being the new buffer for the now-current layer, must remain active.
            currentLayer.blendFunction.applyToBatch(batch);
            layerShader.start();
            layerShader.setDestination(null);
            layerShader.setOpacity(1.f);
            layerShader.setBlendMode(BlendMode.AlphaSrcOver);
            layerShader.setClipBuffer(
                currentLayer.maskBuffer == null
                    ? null
                    : currentLayer.maskBuffer.getColorBufferTexture());
            timer.stop("layer-%s-complete", layerName);
          }
        }
        case RenderInstruction.Meta.SwitchAlphaMode(AlphaMode mode) -> {
          var blendFunction =
              switch (mode) {
                case Clear -> BlendFunction.CLEAR;
                case SrcOnly -> BlendFunction.SRC_ONLY;
                case SrcOver -> BlendFunction.PREMULTIPLIED_ALPHA_SRC_OVER;
                case Screen -> BlendFunction.SCREEN;
              };
          currentLayer.blendFunction = blendFunction;
          blendFunction.applyToBatch(batch);
        }
        case RenderInstruction.Meta.SetClipType(ClipType clipType) -> {
          batch.flush();
          currentLayer.buffer.end();

          var clipBuffer = clipBuffers.get(clipType);
          if (clipBuffer == null) {
            // Remove any clips.
            if (currentLayer.maskBuffer != null) {
              maskBufferPool.free(currentLayer.maskBuffer);
              currentLayer.maskBuffer = null;
            }
            layerShader.setClipBuffer(null);
          } else {
            // Set up the new clip.
            FrameBuffer maskBuffer = currentLayer.maskBuffer;
            if (maskBuffer == null) {
              maskBuffer = currentLayer.maskBuffer = maskBufferPool.obtain();
            } else {
              log.warn(
                  "This layer already has a mask set. Overwriting with clip type {}", clipType);
            }

            // Update the mask texture to include the given clip type.
            layerShader.save();
            maskBuffer.begin();

            // When setting up the clips, we only want the alpha channel preserved.
            Gdx.gl.glColorMask(false, false, false, true);
            BlendFunction.SRC_ONLY.applyToBatch(batch);
            layerShader.setOpacity(1.f);
            layerShader.setBlendMode(BlendMode.SrcOnly);
            layerShader.setDestination(null);
            layerShader.setClipBuffer(null);
            ScreenUtils.clear(Color.CLEAR);
            batch.draw(clipBuffer.getColorBufferTexture(), 0, 0, width, height, 0, 0, 1, 1);
            batch.flush();
            // Re-enable full color writing.
            Gdx.gl.glColorMask(true, true, true, true);

            maskBuffer.end();
            currentLayer.buffer.begin();

            currentLayer.blendFunction.applyToBatch(batch);
            layerShader.restore();
            layerShader.setClipBuffer(maskBuffer.getColorBufferTexture());
          }
        }
        case RenderInstruction.Meta.SetCustomClip(Area clip, boolean inverted) -> {
          batch.flush();
          currentLayer.buffer.end();

          {
            // Set up the new clip.
            FrameBuffer maskBuffer = currentLayer.maskBuffer;
            if (maskBuffer == null) {
              maskBuffer = currentLayer.maskBuffer = maskBufferPool.obtain();
            } else {
              log.warn("This layer already has a mask set. Overwriting with custom clip");
            }

            // Update the mask texture to include the given clip type.
            layerShader.save();
            maskBuffer.begin();

            // When setting up the clips, we only want the alpha channel preserved.
            Gdx.gl.glColorMask(false, false, false, true);
            BlendFunction.SRC_ONLY.applyToBatch(batch);
            // TODO Save all layerShadeer state and reset it.
            layerShader.setOpacity(1.f);
            layerShader.setBlendMode(BlendMode.SrcOnly);
            layerShader.setDestination(null);
            layerShader.setClipBuffer(null);
            ScreenUtils.clear(inverted ? Color.WHITE : Color.CLEAR);
            areaRenderer.setColor(inverted ? Color.CLEAR : Color.WHITE);
            areaRenderer.fillArea(batch, clip);
            batch.flush();
            // Re-enable full color writing.
            Gdx.gl.glColorMask(true, true, true, true);

            maskBuffer.end();
            currentLayer.buffer.begin();

            currentLayer.blendFunction.applyToBatch(batch);
            layerShader.restore();
            layerShader.setClipBuffer(maskBuffer.getColorBufferTexture());
          }
        }
        case RenderInstruction.Meta.ClearCustomClip() -> {
          if (currentLayer.maskBuffer == null) {
            log.error("Unexpected instruction: ClearCustomClip without previous SetCustomClip");
            break;
          }

          maskBufferPool.free(currentLayer.maskBuffer);
          currentLayer.maskBuffer = null;
          layerShader.setClipBuffer(null);
        }
        case RenderInstruction.ClearScreen(java.awt.Color clearColor) -> {
          Color.argb8888ToColor(tmpColor, clearColor.getRGB());
          tmpColor.premultiplyAlpha();
          ScreenUtils.clear(tmpColor);
        }
        case RenderInstruction.FillFrameBuffer(Paint paint, double opacity) -> {
          var gdxPain = getPaint(paint);
          fillViewportWith(tmpColor.set(gdxPain.color()).mul((float) opacity), gdxPain.texture());
        }
        case RenderInstruction.Noise(DrawableNoise noise) -> {
          // TODO How can we implement this? Shouldn't it be basically the same thing as any
          //  paint? Almost, but not quite.
        }
        case RenderInstruction.ImageAsset(
            MD5Key id,
            Rectangle2D preTransformBounds,
            AffineTransform transform,
            double opacity) -> {
          var image = new Sprite(zoneCache.getImageAsset(id, transferringAsset, brokenAsset));
          image.setOrigin(0, 0);

          if (preTransformBounds != null) {
            var bounds = preTransformBounds;
            image.setSize((float) bounds.getWidth(), (float) bounds.getHeight());
            image.setPosition((float) bounds.getMinX(), -(float) bounds.getMaxY());
          } else {
            // Makes sure the image is at (0, 0) before we need to transform it.
            image.setPosition(0, -image.getHeight());
          }

          // Opacity affect alpha, meaning we need to make sure to premultipy the tint properly.
          // We can't use setAlpha() since that won't respect premultiplication.
          image.setColor(tmpColor.set(Color.WHITE).mul((float) opacity));

          // Shears and y-translation have to be negated.
          Affine2 affine = tmpAffine;
          affine.m00 = (float) transform.getScaleX();
          affine.m01 = -(float) transform.getShearX();
          affine.m02 = (float) transform.getTranslateX();
          affine.m10 = -(float) transform.getShearY();
          affine.m11 = (float) transform.getScaleY();
          affine.m12 = -(float) transform.getTranslateY();

          tmpMatrix.idt();
          tmpMatrix.setAsAffine(affine);

          tmpMatrix2.set(batch.getTransformMatrix());
          try {
            batch.setTransformMatrix(tmpMatrix);
            image.draw(batch);
          } finally {
            batch.setTransformMatrix(tmpMatrix2);
          }
        }
        case RenderInstruction.Icon(Images resource, Rectangle2D worldBounds) -> {
          TextureRegion image = fetchImageResource(resource);
          if (image == null) {
            log.warn("Could not find texture for image resource {}", resource);
          } else {
            var x = worldBounds.getCenterX() - worldBounds.getWidth() / 2.;
            var y = -worldBounds.getCenterY() - worldBounds.getHeight() / 2.;
            batch.draw(
                image,
                (float) x,
                (float) y,
                (float) worldBounds.getWidth(),
                (float) worldBounds.getHeight());
          }
        }
        case RenderInstruction.Border(
            Borders resource,
            Rectangle2D worldBounds,
            double rotation,
            Point2D rotateAround) -> {
          // TODO Modify GdxRenderer#fetchBorder() to act directly on a Borders tag.
          var borderResource = RessourceManager.getBorder(resource);

          setProjectionMatrix(hudCam.combined);

          tmpWorldCoord.set((float) worldBounds.getMinX(), (float) -worldBounds.getMaxY(), 0);
          cam.project(tmpWorldCoord);

          var gdxTokenRectangle =
              new Rectangle(
                  tmpWorldCoord.x,
                  tmpWorldCoord.y,
                  (float) worldBounds.getWidth() / zoom,
                  (float) worldBounds.getHeight() / zoom);

          tmpWorldCoord.set((float) rotateAround.getX(), -(float) rotateAround.getY(), 0);
          cam.project(tmpWorldCoord);

          tmpMatrix.idt();
          tmpMatrix.translate(tmpWorldCoord.x, tmpWorldCoord.y, 0);
          tmpMatrix.rotateRad(0, 0, 1, -(float) rotation);
          tmpMatrix.translate(-tmpWorldCoord.x, -tmpWorldCoord.y, 0);
          tmpMatrix2.set(batch.getTransformMatrix());
          try {
            batch.setTransformMatrix(tmpMatrix);
            renderImageBorderAround(borderResource, gdxTokenRectangle);
          } finally {
            batch.setTransformMatrix(tmpMatrix2);
          }
        }
        case RenderInstruction.BoxedString(
            Point2D center,
            String text,
            LabelBackgrounds background,
            java.awt.Color foreground) -> {
          setProjectionMatrix(hudCam.combined);

          Color.argb8888ToColor(tmpColor, foreground.getRGB());
          tmpColor.premultiplyAlpha();

          hudTextRenderer.drawBoxedString(
              text,
              (float) center.getX(),
              (float) (height - center.getY()),
              SwingUtilities.CENTER,
              background,
              tmpColor);
        }
        case RenderInstruction.Fill(Shape shape, Paint paint, double opacity) -> {
          simpleDrawingRenderer.fill(batch, shape, getPaint(paint), (float) opacity);
        }
        case RenderInstruction.Stroke(
            Shape shape,
            Paint paint,
            BasicStroke stroke,
            double opacity) -> {
          simpleDrawingRenderer.stroke(batch, shape, getPaint(paint), (float) opacity, stroke);
        }
        case RenderInstruction.Text(
            String text,
            Font font,
            Rectangle2D screenBounds,
            java.awt.Color foreground,
            RenderInstruction.Text.Decoration decoration) -> {
          setProjectionMatrix(hudCam.combined);

          Color.argb8888ToColor(tmpColor, foreground.getRGB());
          tmpColor.premultiplyAlpha();

          // TODO Need to be able to provie the font size.
          textRenderer.drawString(
              text,
              (float) screenBounds.getCenterX(),
              height - (float) screenBounds.getCenterY(),
              tmpColor);
        }
      }

      timer.stop("layer-%s[%s]", timerLayer, instruction);
    }

    batch.flush();
  }

  private void setProjectionMatrix(Matrix4 matrix) {
    batch.setProjectionMatrix(matrix);
    drawer.update();
  }

  private void createScreenshot(String name) {
    var file = Gdx.files.absolute("C:\\Users\\tkunze\\OneDrive\\Desktop\\" + name + ".png");
    if (!file.exists()) {
      Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
      PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true);
      pixmap.dispose();
    }
  }

  private void fillViewportWith(Color tint, Texture texture) {
    var w = cam.viewportWidth * zoom;
    var h = cam.viewportHeight * zoom;
    var startX = (cam.position.x - cam.viewportWidth * zoom / 2);

    var startY = (cam.position.y - cam.viewportHeight * zoom / 2);
    var vertices =
        new float[] {
          startX, startY, startX, startY + h, startX + w, startY + h, startX + w, startY
        };

    var indices = new short[] {1, 0, 3, 3, 2, 1};

    var polySprite =
        new PolygonSprite(new PolygonRegion(new TextureRegion(texture), vertices, indices));
    polySprite.setColor(tint);
    polySprite.draw(batch);
  }

  private RegionBorder fetchBorder(ImageBorder imageBorder) {
    var imagePath = imageBorder.getImagePath();
    var index = imagePath.indexOf("border/");
    var borderName = imagePath.substring(index);
    return cachedBorders.computeIfAbsent(
        borderName,
        name -> {
          return new RegionBorder(
              atlas.findRegion(name + "/tr"),
              atlas.findRegion(name + "/top"),
              atlas.findRegion(name + "/tl"),
              atlas.findRegion(name + "/left"),
              atlas.findRegion(name + "/bl"),
              atlas.findRegion(name + "/bottom"),
              atlas.findRegion(name + "/br"),
              atlas.findRegion(name + "/right"));
        });
  }

  private void renderImageBorderAround(ImageBorder imageBorder, Rectangle bounds) {
    var border = fetchBorder(imageBorder);

    // x,y is bottom left of the rectangle
    var leftMargin = imageBorder.getLeftMargin();
    var rightMargin = imageBorder.getRightMargin();
    var topMargin = imageBorder.getTopMargin();
    var bottomMargin = imageBorder.getBottomMargin();

    var x = bounds.x - leftMargin;
    var y = bounds.y - bottomMargin;

    var width = bounds.width + leftMargin + rightMargin;
    var height = bounds.height + topMargin + bottomMargin;

    // Draw Corners

    batch.draw(
        border.bottomLeft(),
        x + leftMargin - border.bottomLeft().getRegionWidth(),
        y + topMargin - border.bottomLeft().getRegionHeight());
    batch.draw(
        border.bottomRight(),
        x + width - rightMargin,
        y + topMargin - border.bottomRight().getRegionHeight());
    batch.draw(
        border.topLeft(),
        x + leftMargin - border.topLeft().getRegionWidth(),
        y + height - bottomMargin);
    batch.draw(border.topRight(), x + width - rightMargin, y + height - bottomMargin);

    tmpTile.setRegion(border.top());
    tmpTile.draw(
        batch,
        x + leftMargin,
        y + height - bottomMargin,
        width - leftMargin - rightMargin,
        border.top().getRegionHeight());

    tmpTile.setRegion(border.bottom());
    tmpTile.draw(
        batch,
        x + leftMargin,
        y + topMargin - border.bottom().getRegionHeight(),
        width - leftMargin - rightMargin,
        border.bottom().getRegionHeight());

    tmpTile.setRegion(border.left());
    tmpTile.draw(
        batch,
        x + leftMargin - border.left().getRegionWidth(),
        y + topMargin,
        border.left().getRegionWidth(),
        height - topMargin - bottomMargin);

    tmpTile.setRegion(border.right());
    tmpTile.draw(
        batch,
        x + width - rightMargin,
        y + topMargin,
        border.right().getRegionWidth(),
        height - topMargin - bottomMargin);
  }

  @Subscribe
  void onZoneActivated(ZoneActivated event) {
    Gdx.app.postRunnable(
        () -> {
          renderZone = false;

          var newZone = event.zone();
          var renderer = MapTool.getFrame().getZoneRenderer(newZone.getId());
          if (renderer == null) {
            log.warn("Failed to find Swing renderer for zone {}", newZone.getId());
            return;
          }

          zoneCache = new ZoneCache(renderer);
          renderZone = true;
        });
  }

  private void setScale(ZoneViewport scale) {
    if (!initialized) {
      return;
    }

    zoneViewport = scale;
    offsetX = -scale.zoneScale().getOffsetX();
    offsetY = scale.zoneScale().getOffsetY();
    zoom = (float) (1f / scale.zoneScale().getScale());
    updateCam();
  }

  /**
   * Premultiplies the texture for a font upon loading.
   *
   * <p>This method assumes the font is backed by a single texture.
   *
   * <p>It would be nicer if LibGDX supported premultiplied fonts, but the feature never get added
   * (see <a href="https://github.com/libgdx/libgdx/issues/3642">this issue</a>).
   *
   * @param assetManager
   * @param fileName
   * @param type
   */
  private static void premultiplyFontOnLoad(
      com.badlogic.gdx.assets.AssetManager assetManager, String fileName, Class<BitmapFont> type) {
    var font = assetManager.get(fileName, type);
    var texture = font.getRegion().getTexture();
    try {
      Pixmap pixmap = texture.getTextureData().consumePixmap();
      ByteBuffer pixels = pixmap.getPixels();
      Color color = new Color();
      while (pixels.hasRemaining()) {
        var position = pixels.position();
        color.set(pixels.getInt());
        color.premultiplyAlpha();
        pixels.putInt(position, color.toIntBits());
      }
      pixels.rewind();
      pixmap.setPixels(pixels);
      font.getRegion().setTexture(new Texture(pixmap));
    } catch (Throwable t) {
      log.error("Unexpected error while loading font", t);
    } finally {
      texture.dispose();
    }
  }
}
