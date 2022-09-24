package net.rptools.maptool.client.ui.zone.rendering;

import net.rptools.maptool.client.ui.zone.PlayerView;

import java.awt.*;
import java.awt.geom.Area;

public interface RenderLayer {
    String getName();

    void flush();

    void render(Graphics2D g, Area visibleScreenArea, PlayerView view);
}
