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
package net.rptools.maptool.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.client.swing.ImageLabel;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;

/** */
public class GraphicsUtil {
  public static final int BOX_PADDINGX = 10;
  public static final int BOX_PADDINGY = 2;

  // TODO: Make this configurable
  public static final ImageLabel GREY_LABEL =
      new ImageLabel(RessourceManager.getImage(Images.BOX_GRAY), 4, 4);
  public static final ImageLabel BLUE_LABEL =
      new ImageLabel(RessourceManager.getImage(Images.BOX_BLUE), 4, 4);
  public static final ImageLabel DARK_GREY_LABEL =
      new ImageLabel(RessourceManager.getImage(Images.BOX_DARK_GRAY), 4, 4);

  public static Rectangle drawBoxedString(Graphics2D g, String string, int centerX, int centerY) {
    return drawBoxedString(g, string, centerX, centerY, SwingUtilities.CENTER);
  }

  public static Rectangle drawBoxedString(
      Graphics2D g, String string, int x, int y, int justification) {
    return drawBoxedString(g, string, x, y, justification, GREY_LABEL, Color.black);
  }

  public static Rectangle drawBoxedString(
      Graphics2D g,
      String string,
      int x,
      int y,
      int justification,
      ImageLabel background,
      Color foreground) {
    if (string == null) {
      string = "";
    }
    FontMetrics fm = g.getFontMetrics();
    int strWidth = SwingUtilities.computeStringWidth(fm, string);

    int width = strWidth + BOX_PADDINGX * 2;
    int height = fm.getHeight() + BOX_PADDINGY * 2;

    y = y - fm.getHeight() / 2 - BOX_PADDINGY;
    switch (justification) {
      case SwingUtilities.CENTER:
        x = x - strWidth / 2 - BOX_PADDINGX;
        break;
      case SwingUtilities.RIGHT:
        x = x - strWidth - BOX_PADDINGX;
        break;
      case SwingUtilities.LEFT:
        break;
    }
    // Box
    Rectangle boxBounds = new Rectangle(x, y, width, height);
    background.renderLabel(g, x, y, width, height);

    // Renderer message
    g.setColor(foreground);
    int textX = x + BOX_PADDINGX;
    int textY = y + BOX_PADDINGY + fm.getAscent();

    g.drawString(string, textX, textY);
    return boxBounds;
  }

  public static boolean intersects(Area lhs, Area rhs) {
    if (lhs == null || lhs.isEmpty() || rhs == null || rhs.isEmpty()) {
      return false;
    }
    if (!lhs.getBounds().intersects(rhs.getBounds())) {
      return false;
    }
    Area newArea = new Area(lhs);
    newArea.intersect(rhs);
    return !newArea.isEmpty();
  }

  /**
   * @param lhs the left hand side area
   * @param rhs the right hand side area
   * @return True if the lhs area totally contains the rhs area
   */
  public static boolean contains(Area lhs, Area rhs) {
    if (lhs == null || lhs.isEmpty() || rhs == null || rhs.isEmpty()) {
      return false;
    }
    if (!lhs.getBounds().intersects(rhs.getBounds())) {
      return false;
    }
    Area newArea = new Area(rhs);
    newArea.subtract(lhs);
    return newArea.isEmpty();
  }

  public static Area createLineSegmentEllipse(int x1, int y1, int x2, int y2, int steps) {
    return createLineSegmentEllipse(x1, y1, x2, (double) y2, steps);
  }

  public static Area createLineSegmentEllipse(
      double x1, double y1, double x2, double y2, int steps) {
    double x = Math.min(x1, x2);
    double y = Math.min(y1, y2);

    double w = Math.abs(x1 - x2);
    double h = Math.abs(y1 - y2);

    // Operate from the center of the ellipse
    x += w / 2;
    y += h / 2;

    // The Ellipse class uses curves, which doesn't work with the topology, so we have to create a
    // geometric ellipse
    // out of line segments
    GeneralPath path = new GeneralPath();

    double a = w / 2;
    double b = h / 2;

    boolean firstMove = true;
    for (double t = -Math.PI; t <= Math.PI; t += (2 * Math.PI / steps)) {
      int px = (int) Math.round(x + a * Math.cos(t));
      int py = (int) Math.round(y + b * Math.sin(t));

      if (firstMove) {
        path.moveTo(px, py);
        firstMove = false;
      } else {
        path.lineTo(px, py);
      }
    }

    path.closePath();
    return new Area(path);
  }

  public static Area createLine(int width, Point2D... points) {
    if (points.length < 2) {
      throw new IllegalArgumentException("Must supply at least two points");
    }
    List<Point2D> bottomList = new ArrayList<Point2D>(points.length);
    List<Point2D> topList = new ArrayList<Point2D>(points.length);

    for (int i = 0; i < points.length; i++) {
      double angle =
          i < points.length - 1
              ? GeometryUtil.getAngle(points[i], points[i + 1])
              : GeometryUtil.getAngle(points[i - 1], points[i]);
      double lastAngle =
          i > 0
              ? GeometryUtil.getAngle(points[i], points[i - 1])
              : GeometryUtil.getAngle(points[i], points[i + 1]);

      double delta =
          i > 0 && i < points.length - 1
              ? Math.abs(GeometryUtil.getAngleDelta(angle, lastAngle))
              : 180; // creates a 90-deg angle

      double bottomAngle = (angle + delta / 2) % 360;
      double topAngle = bottomAngle + 180;
      // System.out.println(angle + " - " + delta + " - " + bottomAngle + " - " + topAngle);

      bottomList.add(getPointAtVector(points[i], bottomAngle, width));
      topList.add(getPointAtVector(points[i], topAngle, width));
    }
    Collections.reverse(topList);

    GeneralPath path = new GeneralPath();
    Point2D initialPoint = bottomList.remove(0);
    path.moveTo((float) initialPoint.getX(), (float) initialPoint.getY());

    for (Point2D point : bottomList) {
      path.lineTo((float) point.getX(), (float) point.getY());
    }
    for (Point2D point : topList) {
      path.lineTo((float) point.getX(), (float) point.getY());
    }
    path.closePath();
    return new Area(path);
  }

  private static Point2D getPointAtVector(Point2D point, double angle, double length) {
    double x = point.getX() + length * Math.cos(Math.toRadians(angle));
    double y = point.getY() - length * Math.sin(Math.toRadians(angle));

    return new Point2D.Double(x, y);
  }

  public static void main(String[] args) {
    final Point2D[] points =
        new Point2D[] {
          new Point(20, 20), new Point(50, 50), new Point(80, 20), new Point(100, 100)
        };
    final Area line = createLine(10, points);

    JFrame f = new JFrame();
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setBounds(10, 10, 200, 200);

    JPanel p =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            Dimension size = getSize();
            g.setColor(Color.white);
            g.fillRect(0, 0, size.width, size.height);

            g.setColor(Color.gray);
            ((Graphics2D) g).fill(line);

            g.setColor(Color.red);
            for (Point2D p : points) {
              g.fillRect((int) (p.getX() - 1), (int) (p.getY() - 1), 2, 2);
            }
          }
        };
    f.add(p);
    f.setVisible(true);
    // System.out.println(area.equals(area2));
  }
}
