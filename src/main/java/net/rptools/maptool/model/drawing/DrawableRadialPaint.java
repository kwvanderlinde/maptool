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
package net.rptools.maptool.model.drawing;

import java.awt.Color;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.image.ImageObserver;
import net.rptools.maptool.server.proto.drawing.DrawablePaintDto;

public class DrawableRadialPaint extends DrawablePaint {

  @Override
  public Paint getPaint(double offsetX, double offsetY, double scale, ImageObserver... observers) {
    return new RadialGradientPaint(
        (float) -offsetX,
        (float) -offsetY,
        (float) scale,
        new float[] {0.f, 1.f},
        new Color[] {Color.white, Color.black});
  }

  @Override
  public Paint getCenteredPaint(
      double centerX, double centerY, double width, double height, ImageObserver... observers) {
    return new RadialGradientPaint(
        (float) -centerX,
        (float) -centerY,
        (float) Math.max(width, height) / 2,
        new float[] {0.f, 1.f},
        new Color[] {Color.white, Color.black});
  }

  @Override
  public DrawablePaintDto toDto() {
    return null;
  }
}
