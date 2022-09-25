package net.rptools.maptool.client.ui.zone.rendering;

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.ui.zone.BufferedImagePool;
import net.rptools.maptool.client.ui.zone.PlayerView;

import java.awt.*;
import java.awt.geom.Area;


public abstract class AbstractRenderLayer implements RenderLayer {
    protected final CodeTimer timer;
    protected final BufferedImagePool tempBufferPool;

    public AbstractRenderLayer(CodeTimer timer, BufferedImagePool tempBufferPool) {
        this.timer = timer;
        this.tempBufferPool = tempBufferPool;
    }

    @Override
    public String toString() {
        return String.format("RenderLayer[%s]", getName());
    }

    @Override
    public final void render(Graphics2D g, Area visibleScreenArea, PlayerView view) {
        final var name = getName();
        timer.start(name);
        doRender(g, visibleScreenArea, view);
        timer.stop(name);
    }

    protected abstract void doRender(Graphics2D g, Area visibleScreenArea, PlayerView view);
}
