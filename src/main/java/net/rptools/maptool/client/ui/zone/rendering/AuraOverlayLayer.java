package net.rptools.maptool.client.ui.zone.rendering;

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.ui.zone.*;
import net.rptools.maptool.model.LightSource;

import java.awt.*;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

public class AuraOverlayLayer extends AbstractLightOverlayLayer {
    public AuraOverlayLayer(ZoneView zoneView, ZoneRenderer zoneRenderer, CodeTimer timer, BufferedImagePool tempBufferPool) {
        super(zoneView, zoneRenderer, timer, tempBufferPool);
    }

    @Override
    public String getName() {
        return "auraOverlay";
    }

    protected List<DrawableLight> supplyLights(ZoneView zoneView, PlayerView view) {
        return new ArrayList<>(zoneView.getLights(LightSource.Type.AURA));
    }

    @Override
    protected void doRender(Graphics2D g, Area visibleScreenArea, PlayerView view) {
        final var lights = getLights(view);

        final var name = getName();
        timer.start("renderLights:renderAuraOverlay");
        renderLightOverlay(
                g,
                visibleScreenArea,
                AlphaComposite.SrcOver.derive(AppPreferences.getAuraOverlayOpacity() / 255.0f),
                view.isGMView() ? null : ClipStyle.CLIP_TO_VISIBLE_AREA,
                lights,
                new Color(255, 255, 255, 150),
                1.0f);
        timer.stop("renderLights:renderAuraOverlay");
    }
}
