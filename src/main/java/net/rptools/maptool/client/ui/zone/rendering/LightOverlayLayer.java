package net.rptools.maptool.client.ui.zone.rendering;

import net.rptools.lib.CodeTimer;
import net.rptools.lib.swing.SwingUtil;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.ui.zone.*;
import net.rptools.maptool.model.LightSource;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class LightOverlayLayer implements RenderLayer {
    public enum ClipStyle {
        CLIP_TO_VISIBLE_AREA,
        CLIP_TO_NOT_VISIBLE_AREA,
    }

    private final String name;
    private final ZoneView zoneView;
    private final ZoneRenderer zoneRenderer; // TODO I want to eliminate this.

    private final CodeTimer timer;
    private final BufferedImagePool tempBufferPool;

    /**
     * Cached set of lights arranged by lumens for some stability. TODO Token draw order would be
     * nice.
     */
    private List<DrawableLight> drawableLights = null;

    public LightOverlayLayer(String name, ZoneView zoneView, ZoneRenderer zoneRenderer, CodeTimer timer, BufferedImagePool tempBufferPool) {
        this.name = name;
        this.zoneView = zoneView;
        this.zoneRenderer = zoneRenderer;
        this.timer = timer;
        this.tempBufferPool = tempBufferPool;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void flush() {
        drawableLights = null;
    }

    public List<DrawableLight> getLights(PlayerView view) {
        // Collect and organize lights
        timer.start("renderLights:getLights");
        if (drawableLights == null) {
            timer.start("renderLights:populateCache");
            drawableLights = new ArrayList<>(zoneView.getDrawableLights(view));
            drawableLights.removeIf(light -> light.getType() != LightSource.Type.NORMAL);
            drawableLights.removeIf(light -> light.getLumens() < 0);
            timer.stop("renderLights:populateCache");
        }
        timer.stop("renderLights:getLights");

        return drawableLights;
    }

    @Override
    public void render(Graphics2D g, Area visibleScreenArea, PlayerView view) {
        final var lights = getLights(view);

        final var name = getName();
        timer.start("renderLights:render:" + name);
        renderLightOverlay(
                g,
                visibleScreenArea,
                AlphaComposite.SrcOver.derive(AppPreferences.getLightOverlayOpacity() / 255.0f),
                view.isGMView() ? null : ClipStyle.CLIP_TO_VISIBLE_AREA,
                lights,
                new Color(255, 255, 255, 255),
                1.0f);
        timer.stop("renderLights:render:" + name);
    }

    /**
     * Combines a set of lights into an image that is then rendered into the zone.
     *
     * @param g The graphics object used to render the zone.
     * @param composite The composite used to blend lights together.
     * @param clipStyle How to clip the overlay relative to the visible area. Set to null for no extra
     *     clipping.
     * @param lights The lights that will be rendered and blended.
     * @param defaultPaint A default paint for lights without a paint.
     * @param overlayOpacity The opacity used when rendering the final overlay on top of the zone.
     */
    private void renderLightOverlay(
            Graphics2D g,
            Area visibleScreenArea,
            Composite composite,
            @Nullable ClipStyle clipStyle,
            List<DrawableLight> lights,
            Paint defaultPaint,
            float overlayOpacity) {
        if (lights.isEmpty()) {
            // No points spending resources accomplishing nothing.
            return;
        }

        // Set up a buffer image for lights to be drawn onto before the map
        timer.start("renderLightOverlay:allocateBuffer");
        try (final var bufferHandle = tempBufferPool.acquire()) {
            BufferedImage lightOverlay = bufferHandle.get();
            timer.stop("renderLightOverlay:allocateBuffer");

            Graphics2D newG = lightOverlay.createGraphics();
            SwingUtil.useAntiAliasing(newG);

            if (clipStyle != null && visibleScreenArea != null) {
                timer.start("renderLightOverlay:setClip");
                Area clip = new Area(g.getClip());
                switch (clipStyle) {
                    case CLIP_TO_VISIBLE_AREA -> clip.intersect(visibleScreenArea);
                    case CLIP_TO_NOT_VISIBLE_AREA -> clip.subtract(visibleScreenArea);
                }
                newG.setClip(clip);
                timer.stop("renderLightOverlay:setClip");
            }

            timer.start("renderLightOverlay:setTransform");
            AffineTransform af = new AffineTransform();
            af.translate(zoneRenderer.getViewOffsetX(), zoneRenderer.getViewOffsetY());
            af.scale(zoneRenderer.getScale(), zoneRenderer.getScale());
            newG.setTransform(af);
            timer.stop("renderLightOverlay:setTransform");

            newG.setComposite(composite);

            // Draw lights onto the buffer image so the map doesn't affect how they blend
            timer.start("renderLightOverlay:drawLights");
            for (var light : lights) {
                var paint = light.getPaint() != null ? light.getPaint().getPaint() : defaultPaint;
                newG.setPaint(paint);
                newG.fill(light.getArea());
            }
            timer.stop("renderLightOverlay:drawLights");
            newG.dispose();

            // Draw the buffer image with all the lights onto the map
            timer.start("renderLightOverlay:drawBuffer");
            Composite previousComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlayOpacity));
            g.drawImage(lightOverlay, null, 0, 0);
            g.setComposite(previousComposite);
            timer.stop("renderLightOverlay:drawBuffer");
        }
    }
}
