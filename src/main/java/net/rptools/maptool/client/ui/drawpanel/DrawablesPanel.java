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
package net.rptools.maptool.client.ui.drawpanel;

import java.awt.*;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.drawing.*;

public class DrawablesPanel extends JComponent {
  private static final long serialVersionUID = 441600187734634440L;
  private static final int MAX_PANEL_SIZE = 250;
  private final List<GUID> selectedIDList = new ArrayList<GUID>();

  public List<Object> getSelectedIds() {
    List<Object> list = new ArrayList<Object>(selectedIDList);
    return list;
  }

  public void setSelectedIds(List<GUID> ids) {
    this.selectedIDList.clear();
    this.selectedIDList.addAll(ids);
    repaint();
  }

  public void addSelectedId(GUID id) {
    this.selectedIDList.add(id);
    repaint();
  }

  public void clearSelectedIds() {
    this.selectedIDList.clear();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (selectedIDList.isEmpty()) {
      return;
    }

    var renderer = MapTool.getFrame().getCurrentZoneRenderer();
    if (renderer == null) {
      return;
    }

    var zone = renderer.getZone();
    if (zone == null) {
      return;
    }

    List<DrawnElement> drawableList = new ArrayList<>();
    boolean onlyCuts = true;
    for (GUID id : selectedIDList) {
      DrawnElement de = zone.getDrawnElement(id);
      if (de != null) {
        drawableList.add(de);
        if (!de.getPen().isEraser()) {
          onlyCuts = false;
        }
      }
    }
    if (!drawableList.isEmpty()) {
      Collections.reverse(drawableList);
      Rectangle bounds = getBounds(zone, drawableList);
      double scale = (double) Math.min(MAX_PANEL_SIZE, getSize().width) / (double) bounds.width;
      if ((bounds.height * scale) > MAX_PANEL_SIZE) {
        scale = (double) Math.min(MAX_PANEL_SIZE, getSize().height) / (double) bounds.height;
      }
      g.drawImage(drawDrawables(zone, drawableList, bounds, scale, onlyCuts), 0, 0, null);
    }
  }

  private BufferedImage drawDrawables(
      Zone zone,
      List<DrawnElement> drawableList,
      Rectangle viewport,
      double scale,
      boolean showEraser) {
    BufferedImage backBuffer =
        new BufferedImage(
            (int) Math.max(1, viewport.width * scale),
            (int) Math.max(1, viewport.height * scale),
            Transparency.TRANSLUCENT);
    Graphics2D g = backBuffer.createGraphics();
    g.setClip(0, 0, backBuffer.getWidth(), backBuffer.getHeight());
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    AffineTransform tf = new AffineTransform();
    tf.translate(-(viewport.x * scale), -(viewport.y * scale));
    tf.scale(scale, scale);
    g.transform(tf);

    for (DrawnElement element : drawableList) {
      Drawable drawable = element.getDrawable();
      Pen pen = element.getPen();
      // If we are only drawing cuts, make the pen visible
      if (showEraser && pen.isEraser()) {
        pen = new Pen(pen);
        pen.setEraser(false);
        pen.setPaint(new DrawableColorPaint(Color.red));
        pen.setBackgroundPaint(new DrawableColorPaint(Color.red));
      }

      switch (drawable) {
        case DrawablesGroup group -> {
          g.setComposite(AlphaComposite.SrcOver);
          g.drawImage(
              drawDrawables(zone, group.getDrawableList(), new Rectangle(viewport), 1, false),
              viewport.x,
              viewport.y,
              null);
        }
        case DrawnLabel drawnLabel -> {
          // TODO Conveniently ignoring this since DrawnLabels are not part of the model anymore.
        }
        default -> {
          var isEraser = pen.isEraser();
          var foregroundPaint = pen.getPaint();
          var backgroundPaint = pen.getBackgroundPaint();

          if (backgroundPaint != null) {
            if (isEraser) {
              g.setComposite(AlphaComposite.Clear);
            } else {
              var opacity =
                  pen.getOpacity()
                      * (drawable instanceof AbstractTemplate
                          ? AbstractTemplate.DEFAULT_BG_ALPHA
                          : 1);
              g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            }

            var area = drawable.getArea(zone);
            if (area != null) {
              g.setPaint(backgroundPaint.getPaint());
              g.fill(area);
            }
          }

          if (foregroundPaint != null) {
            if (isEraser) {
              g.setComposite(AlphaComposite.Clear);
            } else {
              g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pen.getOpacity()));
            }
            g.setStroke(pen.getStroke());

            var border = drawable.getBorder(zone);
            if (border != null) {
              g.setPaint(foregroundPaint.getPaint());
              g.draw(border);
            }

            var decorations =
                drawable instanceof AbstractTemplate template
                    ? template.getDecorationsToStroke(zone)
                    : null;
            if (decorations != null) {
              g.draw(decorations);
            }
          }
        }
      }
    }
    g.dispose();
    return backBuffer;
  }

  private Rectangle getBounds(Zone zone, List<DrawnElement> drawableList) {
    Rectangle bounds = null;
    for (DrawnElement element : drawableList) {
      // Empty drawables are created by right clicking during the draw process
      // and need to be skipped.
      Rectangle drawnBounds = element.getDrawable().getBounds(zone);
      if (drawnBounds == null) {
        continue;
      }
      drawnBounds = new Rectangle(drawnBounds);
      // Handle pen size
      Pen pen = element.getPen();
      int penSize = pen.getPaint() == null ? 0 : (int) pen.getThickness();
      drawnBounds.setRect(
          drawnBounds.getX() - penSize,
          drawnBounds.getY() - penSize,
          drawnBounds.getWidth() + (penSize * 2),
          drawnBounds.getHeight() + (penSize * 2));
      if (bounds == null) {
        bounds = drawnBounds;
      } else {
        bounds.add(drawnBounds);
      }
    }
    // Fix for Sentry MAPTOOL-20
    // TODO No chance this actually fixes anything. The only use for the bounds is to create a
    //  BufferedImage of the same dimension, meaning it will receive a negative width and height.
    if (bounds != null && bounds.getWidth() > 0 && bounds.getHeight() > 0) {
      return bounds;
    }
    return new Rectangle(0, 0, -1, -1);
  }
}
