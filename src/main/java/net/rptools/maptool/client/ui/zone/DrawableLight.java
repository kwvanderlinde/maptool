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

import java.awt.Color;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import javax.annotation.Nonnull;
import net.rptools.maptool.model.LightSource;

public class DrawableLight {
  private @Nonnull Color color;
  private @Nonnull LightSource.BuiltInTexture texture;
  private @Nonnull Area area;
  private @Nonnull Point2D center;
  private double sourceRange;

  public DrawableLight(
      @Nonnull Color color,
      @Nonnull LightSource.BuiltInTexture texture,
      @Nonnull Area area,
      @Nonnull Point2D center,
      double sourceRange) {
    super();
    this.color = color;
    this.texture = texture;
    this.area = area;
    this.center = center;
    this.sourceRange = sourceRange;
  }

  public @Nonnull Paint getPaint(Point2D center, double radius) {
    return switch (texture) {
      case FLAT -> color;
      case FADE -> new RadialGradientPaint(
          (float) center.getX(),
          (float) center.getY(),
          (float) radius,
          new float[] {0, 1},
          new Color[] {color, new Color(0, 0, 0, 0)});
    };
  }

  public @Nonnull Area getArea() {
    return area;
  }

  public Point2D getCenter() {
    return center;
  }

  public double getSourceRange() {
    return sourceRange;
  }

  @Override
  public String toString() {
    return String.format(
        "DrawableLight[%s, %s, %s, %s, %s]",
        area.getBounds(), color, texture.name(), center, sourceRange);
  }
}
