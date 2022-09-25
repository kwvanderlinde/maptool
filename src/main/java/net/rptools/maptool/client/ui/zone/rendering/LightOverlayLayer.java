package net.rptools.maptool.client.ui.zone.rendering;

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.ui.zone.*;
import net.rptools.maptool.model.LightSource;

import java.awt.*;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

public class LightOverlayLayer extends AbstractLightOverlayLayer {
    public LightOverlayLayer(ZoneView zoneView, ZoneRenderer zoneRenderer, CodeTimer timer, BufferedImagePool tempBufferPool) {
        super(zoneView, zoneRenderer, timer, tempBufferPool);
    }

    @Override
    public String getName() {
        return "lightOverlay";
    }

    protected List<DrawableLight> supplyLights(ZoneView zoneView, PlayerView view) {
        // Collect and organize lights
        final var drawableLights = new ArrayList<>(zoneView.getDrawableLights(view));
        drawableLights.removeIf(light -> light.getType() != LightSource.Type.NORMAL);
        drawableLights.removeIf(light -> light.getLumens() < 0);
        return drawableLights;
    }

    @Override
    protected void doRender(Graphics2D g, Area visibleScreenArea, PlayerView view) {
        final var lights = getLights(view);

        final var name = getName();
        timer.start("renderLights:renderLightOverlay");
        renderLightOverlay(
                g,
                visibleScreenArea,
                AlphaComposite.SrcOver.derive(AppPreferences.getLightOverlayOpacity() / 255.0f),
                view.isGMView() ? null : ClipStyle.CLIP_TO_VISIBLE_AREA,
                lights,
                new Color(255, 255, 255, 255),
                1.0f);
        timer.stop("renderLights:renderLightOverlay");
    }
}
