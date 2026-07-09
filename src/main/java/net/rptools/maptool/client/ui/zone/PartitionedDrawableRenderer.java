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
package net.rptools.maptool.client.ui.zone;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Function;
import net.rptools.lib.CodeTimer;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.client.DeveloperOptions;
import net.rptools.maptool.client.entities.DrawableComponent;
import net.rptools.maptool.client.entities.DrawableSetComponent;
import net.rptools.maptool.client.entities.Entity;
import net.rptools.maptool.client.entities.EraserComponent;
import net.rptools.maptool.client.entities.Paint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** */
public class PartitionedDrawableRenderer implements DrawableRenderer {
  private static final Logger log = LogManager.getLogger(PartitionedDrawableRenderer.class);
  private static boolean messageLogged = false;

  private static final int CHUNK_SIZE = 256;
  private static final List<BufferedImage> unusedChunkList = new LinkedList<>();

  private final Set<String> noImageSet = new HashSet<>();
  private final List<Tuple> chunkList = new LinkedList<>();
  private int maxChunks;

  private double lastScale;
  private Rectangle lastViewport;

  private int horizontalChunkCount;
  private int verticalChunkCount;

  private boolean dirty = false;

  private final Function<Paint, java.awt.Paint> paintResolver;

  public PartitionedDrawableRenderer(Function<Paint, java.awt.Paint> paintResolver) {
    this.paintResolver = paintResolver;
  }

  public void flush() {
    int unusedSize = unusedChunkList.size();
    for (Tuple tuple : chunkList) {
      // Reuse the images
      if (unusedSize < maxChunks && tuple != null) {
        unusedChunkList.add(tuple.image);
        unusedSize++;
      }
    }
    chunkList.clear();
    noImageSet.clear();
    dirty = false;
  }

  public void setDirty() {
    dirty = true;
  }

  public void renderDrawables(
      Graphics g, DrawableSetComponent component, Rectangle viewport, double scale) {
    CodeTimer.using(
        "Renderer",
        timer -> {
          timer.setThreshold(10);
          timer.setEnabled(false);

          // NOTHING TO DO
          if (component.drawables().isEmpty()) {
            // TODO Why not check `dirty` || `lastScale != scale` first?
            if (dirty) {
              flush();
            }
            return;
          }
          // View changed ?
          if (dirty || lastScale != scale) {
            flush();
          }
          if (lastViewport == null
              || viewport.width != lastViewport.width
              || viewport.height != lastViewport.height) {
            horizontalChunkCount = (int) Math.ceil(viewport.width / (double) CHUNK_SIZE) + 1;
            verticalChunkCount = (int) Math.ceil(viewport.height / (double) CHUNK_SIZE) + 1;

            maxChunks = (horizontalChunkCount * verticalChunkCount * 2);
          }
          // Compute grid
          int gridx = (int) Math.floor(-viewport.x / (double) CHUNK_SIZE);
          int gridy = (int) Math.floor(-viewport.y / (double) CHUNK_SIZE);

          // OK, weirdest hack ever. Basically, when the viewport.x is exactly divisible by the
          // chunk size, the gridx decrements too early, creating a visual jump in the drawables. I
          // don't know the exact cause, but this seems to account for it
          // note that it only happens in the negative space. Weird.
          gridx += (viewport.x > CHUNK_SIZE && (viewport.x % CHUNK_SIZE == 0) ? -1 : 0);
          gridy += (viewport.y > CHUNK_SIZE && (viewport.y % CHUNK_SIZE == 0) ? -1 : 0);

          for (int row = 0; row < verticalChunkCount; row++) {
            for (int col = 0; col < horizontalChunkCount; col++) {
              int cellX = gridx + col;
              int cellY = gridy + row;

              String key = getKey(cellX, cellY);
              if (noImageSet.contains(key)) {
                continue;
              }
              Tuple chunk = findChunk(chunkList, key);
              if (chunk == null) {
                chunk = new Tuple(key, createChunk(component, cellX, cellY, scale));

                if (chunk.image == null) {
                  noImageSet.add(key);
                  continue;
                }
              }
              // Most recently used is at the front
              chunkList.add(0, chunk);

              // Trim to the right size
              if (chunkList.size() > maxChunks) {
                int chunkSize = chunkList.size();
                while (chunkSize > maxChunks) {
                  chunkList.remove(--chunkSize);
                }
              }
              int x =
                  col * CHUNK_SIZE
                      - ((CHUNK_SIZE - viewport.x)) % CHUNK_SIZE
                      - (gridx < -1 ? CHUNK_SIZE : 0);
              int y =
                  row * CHUNK_SIZE
                      - ((CHUNK_SIZE - viewport.y)) % CHUNK_SIZE
                      - (gridy < -1 ? CHUNK_SIZE : 0);

              timer.start("render:DrawImage");
              g.drawImage(chunk.image, x, y, null);
              timer.stop("render:DrawImage");

              // DEBUG: Show partition boundaries
              if (DeveloperOptions.Toggle.ShowPartitionDrawableBoundaries.get()) {
                if (!messageLogged) {
                  messageLogged = true;
                  log.debug(
                      "DEBUG logging of "
                          + this.getClass().getSimpleName()
                          + " causes colored rectangles and message strings.");
                }
                if (col % 2 == 0) {
                  if (row % 2 == 0) {
                    g.setColor(Color.white);
                  } else {
                    g.setColor(Color.green);
                  }
                } else {
                  if (row % 2 == 0) {
                    g.setColor(Color.green);
                  } else {
                    g.setColor(Color.white);
                  }
                }
                g.drawRect(x, y, CHUNK_SIZE - 1, CHUNK_SIZE - 1);
                g.drawString(key, x + CHUNK_SIZE / 2, y + CHUNK_SIZE / 2);
              }
            }
          }
          // REMEMBER
          lastViewport = viewport;
          lastScale = scale;
        });
  }

  /**
   * Given a List and a String key, find the element in the list that matches the key.
   *
   * @param list
   * @param key
   * @return
   */
  private Tuple findChunk(List<Tuple> list, String key) {
    ListIterator<Tuple> iter = list.listIterator();
    while (iter.hasNext()) {
      Tuple tuple = iter.next();
      if (tuple.key.equals(key)) {
        iter.remove();
        return tuple;
      }
    }
    return null;
  }

  private BufferedImage createChunk(
      DrawableSetComponent component, int gridx, int gridy, double scale) {
    final var timer = CodeTimer.get();

    int x = gridx * CHUNK_SIZE;
    int y = gridy * CHUNK_SIZE;

    final BufferedImage image;
    final Graphics2D g;
    timer.start("createChunk:CreateChunk");
    try {
      image = getNewChunk();
      g = image.createGraphics();
      g.setClip(0, 0, CHUNK_SIZE, CHUNK_SIZE);

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      AffineTransform af = new AffineTransform();
      af.translate(-x, -y);
      af.scale(scale, scale);
      g.setTransform(af);
    } finally {
      timer.stop("createChunk:CreateChunk");
    }

    for (Entity entity : component.drawables()) {

      timer.start("createChunk:calculate");

      Rectangle2D drawnBounds = entity.getBounds();
      Rectangle2D chunkBounds =
          new Rectangle(
              (int) (gridx * (CHUNK_SIZE / scale)),
              (int) (gridy * (CHUNK_SIZE / scale)),
              (int) (CHUNK_SIZE / scale),
              (int) (CHUNK_SIZE / scale));

      // Handle pen size
      timer.stop("createChunk:calculate");

      timer.start("createChunk:BoundsCheck");
      if (!drawnBounds.intersects(chunkBounds)) {
        timer.stop("createChunk:BoundsCheck");
        continue;
      }
      timer.stop("createChunk:BoundsCheck");

      entity
          .getComponent(DrawableSetComponent.class)
          .ifPresent(
              group -> {
                BufferedImage groupImage = createChunk(group, gridx, gridy, scale);
                Graphics2D g2 = image.createGraphics();
                g2.drawImage(groupImage, 0, 0, CHUNK_SIZE, CHUNK_SIZE, null);
                g2.dispose();
              });

      timer.start("createChunk:Draw");

      entity
          .getComponent(DrawableComponent.class)
          .ifPresent(
              drawable -> {
                var g2 = (Graphics2D) g.create();
                try {
                  var fill = drawable.fill();
                  if (fill != null) {
                    g2.setComposite(AlphaComposite.SrcOver.derive((float) fill.opacity()));
                    g2.setPaint(paintResolver.apply(fill.paint()));
                    g2.fill(fill.shape());
                  }

                  var border = drawable.border();
                  if (border != null) {
                    g2.setComposite(AlphaComposite.SrcOver.derive((float) border.opacity()));
                    g2.setPaint(paintResolver.apply(border.paint()));
                    g2.setStroke(border.stroke());
                    g2.draw(border.shape());
                  }

                  var decoration = drawable.decoration();
                  if (decoration != null) {
                    g2.setComposite(AlphaComposite.SrcOver.derive((float) decoration.opacity()));
                    g2.setPaint(paintResolver.apply(decoration.paint()));
                    g2.setStroke(decoration.stroke());
                    g2.draw(decoration.shape());
                  }

                } finally {
                  g2.dispose();
                }
              });

      entity
          .getComponent(EraserComponent.class)
          .ifPresent(
              eraser -> {
                var g2 = (Graphics2D) g.create();
                try {
                  g2.setComposite(AlphaComposite.Clear);
                  g2.fill(eraser.area());
                } finally {
                  g2.dispose();
                }
              });

      timer.stop("createChunk:Draw");
    }
    g.dispose();
    return image;
  }

  private BufferedImage getNewChunk() {
    BufferedImage image;
    if (unusedChunkList.size() > 0) {
      image = unusedChunkList.remove(0);
      ImageUtil.clearImage(image);
    } else {
      image = new BufferedImage(CHUNK_SIZE, CHUNK_SIZE, Transparency.BITMASK);
    }
    image.setAccelerationPriority(1);
    return image;
  }

  private String getKey(int col, int row) {
    return col + "." + row;
  }

  private static class Tuple {
    String key;
    BufferedImage image;

    public Tuple(String key, BufferedImage image) {
      this.key = key;
      this.image = image;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Tuple tuple = (Tuple) o;
      return Objects.equals(key, tuple.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key);
    }
  }
}
