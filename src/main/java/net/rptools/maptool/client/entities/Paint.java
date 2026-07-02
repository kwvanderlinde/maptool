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
package net.rptools.maptool.client.entities;

import java.awt.Color;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.DrawablePaint;
import net.rptools.maptool.model.drawing.DrawableTexturePaint;

public sealed interface Paint {
  record Color(int argb8888) implements Paint {}

  record Texture(MD5Key assetId, double imageScale) implements Paint {}

  static Paint of(DrawablePaint paint) {
    return switch (paint) {
      case DrawableColorPaint drawableColorPaint -> new Color(drawableColorPaint.getColor());
      case DrawableTexturePaint drawableTexturePaint ->
          new Texture(drawableTexturePaint.getAssetId(), drawableTexturePaint.getScale());
    };
  }
}
