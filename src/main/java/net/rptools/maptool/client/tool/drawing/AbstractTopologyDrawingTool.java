package net.rptools.maptool.client.tool.drawing;

import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.Drawable;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.Pen;
import net.rptools.maptool.model.drawing.ShapeDrawable;

import java.awt.*;

public abstract class AbstractTopologyDrawingTool extends AbstractDrawingTool {
    protected void paintTopologyOverlay(Graphics2D g, Shape shape, int penMode) {
        ShapeDrawable drawable = null;

        if (shape != null) {
            drawable = new ShapeDrawable(shape, false);
        }

        paintTopologyOverlay(g, drawable, penMode);
    }

    protected void paintTopologyOverlay(Graphics2D g, Drawable drawable, int penMode) {
        if (MapTool.getPlayer().isGM()) {
            Zone zone = renderer.getZone();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(renderer.getViewOffsetX(), renderer.getViewOffsetY());
            g2.scale(renderer.getScale(), renderer.getScale());

            g2.setColor(AppStyle.tokenMblColor);
            g2.fill(getTokenTopology(Zone.TopologyType.MBL));
            g2.setColor(AppStyle.tokenTopologyColor);
            g2.fill(getTokenTopology(Zone.TopologyType.WALL_VBL));
            g2.setColor(AppStyle.tokenHillVblColor);
            g2.fill(getTokenTopology(Zone.TopologyType.HILL_VBL));
            g2.setColor(AppStyle.tokenPitVblColor);
            g2.fill(getTokenTopology(Zone.TopologyType.PIT_VBL));
            g2.setColor(AppStyle.tokenCoverVblColor);
            g2.fill(getTokenTopology(Zone.TopologyType.COVER_VBL));

            g2.setColor(AppStyle.topologyTerrainColor);
            g2.fill(zone.getTopology(Zone.TopologyType.MBL));

            g2.setColor(AppStyle.topologyColor);
            g2.fill(zone.getTopology(Zone.TopologyType.WALL_VBL));

            g2.setColor(AppStyle.hillVblColor);
            g2.fill(zone.getTopology(Zone.TopologyType.HILL_VBL));

            g2.setColor(AppStyle.pitVblColor);
            g2.fill(zone.getTopology(Zone.TopologyType.PIT_VBL));

            g2.setColor(AppStyle.coverVblColor);
            g2.fill(zone.getTopology(Zone.TopologyType.COVER_VBL));

            g2.dispose();
        }

        if (drawable != null) {
            Pen pen = new Pen();
            pen.setEraser(getPen().isEraser());
            pen.setOpacity(AppStyle.topologyRemoveColor.getAlpha() / 255.0f);
            pen.setBackgroundMode(penMode);

            if (penMode == Pen.MODE_TRANSPARENT) {
                pen.setThickness(3.0f);
            }

            if (pen.isEraser()) {
                pen.setEraser(false);
            }
            if (isEraser()) {
                pen.setBackgroundPaint(new DrawableColorPaint(AppStyle.topologyRemoveColor));
            } else {
                pen.setBackgroundPaint(new DrawableColorPaint(AppStyle.topologyAddColor));
            }
            paintTransformed(g, renderer, drawable, pen);
        }
    }
}
