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
package net.rptools.maptool.client.ui.zone.renderer.instructions;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppStyle;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Label;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderInstruction.Text;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Token.Type;
import net.rptools.maptool.model.drawing.AbstractTemplate;
import net.rptools.maptool.model.drawing.DrawnElement;

public class LabelFactory {
  private static final Color COLOR_CLEAR = new Color(0, 0, 0, 0);

  /**
   * Creates a coordinate label with at a standard position.
   *
   * <p>The x coordinate of the bounding box will be set to 0, and the y coordinate with be set to
   * the descent of the font.
   *
   * @param text The text of the label.
   * @param foreground The color of the text.
   * @return The render instruction for a coordinate label.
   */
  private Text buildCoordinateLabel(String text, Color foreground) {
    var fontSize = 20f;
    var font = AppStyle.labelFont.deriveFont(fontSize).deriveFont(Font.BOLD);

    var canvas = new Canvas();
    var fm = canvas.getFontMetrics(font);
    int strWidth = SwingUtilities.computeStringWidth(fm, text);
    int strHeight = fm.getHeight();
    var dimensions = new Dimension(strWidth, strHeight);

    // Align the baseline with where the bottom would otherwise be.
    var bounds = new Rectangle2D.Double(0, fm.getDescent(), dimensions.width, dimensions.height);

    return new Text(text, font, bounds, foreground, Text.Decoration.Shadow);
  }

  public Text getXCoordinateLabel(String text, ScreenPoint centerTop) {
    var result = buildCoordinateLabel(text, Color.orange);
    var bounds = result.screenBounds();
    var descent = bounds.getMinY();
    bounds.setRect(
        (int) (centerTop.x - bounds.getWidth() / 2.),
        (int) (centerTop.y + descent),
        bounds.getWidth(),
        bounds.getHeight());
    return result;
  }

  public Text getYCoordinateLabel(String text, ScreenPoint leftCenter) {
    var result = buildCoordinateLabel(text, Color.yellow);
    var bounds = result.screenBounds();
    var descent = bounds.getMinY();
    var ascent = bounds.getHeight() - descent;
    bounds.setRect(
        (int) leftCenter.x, (int) leftCenter.y - ascent / 2, bounds.getWidth(), bounds.getHeight());
    return result;
  }

  private Label buildMapImageLabel(
      String text, ScreenPoint centerTop, Color background, Color foreground, Color borderColor) {
    // TODO Put the padding into the Label itself.
    final var paddingX = 4;
    final var paddingY = 4;

    boolean showBorder = AppPreferences.mapLabelShowBorder.get();
    int borderWidth = showBorder ? AppPreferences.mapLabelBorderWidth.get() : 0;
    int borderArc = AppPreferences.mapLabelBorderArc.get();

    var font =
        AppStyle.labelFont.deriveFont(
            AppStyle.labelFont.getStyle(), AppPreferences.mapLabelFontSize.get());

    // TODO Why round the results?
    centerTop.x = Math.round(centerTop.x);
    centerTop.y = Math.round(centerTop.y);

    var canvas = new Canvas();
    var fm = canvas.getFontMetrics(font);
    int strWidth = SwingUtilities.computeStringWidth(fm, text);
    int strHeight = fm.getHeight();
    var dimensions =
        new Dimension(
            strWidth + paddingX * 2 + borderWidth * 2, strHeight + paddingY * 2 + borderWidth * 2);

    var bounds =
        new Rectangle2D.Double(
            centerTop.x - dimensions.width / 2., centerTop.y, dimensions.width, dimensions.height);

    return new Label(
        text, font, bounds, background, foreground, borderColor, borderWidth, borderArc);
  }

  /**
   * Retrieves the appropriate map image label based on the provided token.
   *
   * @param token The token representing the entity on the map.
   * @return The map image label corresponding to the token type, and/or visibility.
   */
  public Label getMapImageLabel(Token token, String text, ScreenPoint centerTop) {
    if (!token.isVisible()) {
      return buildMapImageLabel(
          text,
          centerTop,
          AppPreferences.nonVisibleTokenMapLabelBackground.get(),
          AppPreferences.nonVisibleTokenMapLabelForeground.get(),
          AppPreferences.nonVisibleTokenMapLabelBorder.get());
    } else if (token.getType() == Type.NPC) {
      return buildMapImageLabel(
          text,
          centerTop,
          AppPreferences.npcMapLabelBackground.get(),
          AppPreferences.npcMapLabelForeground.get(),
          AppPreferences.npcMapLabelBorder.get());
    } else {
      return buildMapImageLabel(
          text,
          centerTop,
          AppPreferences.pcMapLabelBackground.get(),
          AppPreferences.pcMapLabelForeground.get(),
          AppPreferences.pcMapLabelBorder.get());
    }
  }

  /**
   * Retrieves the map image label based on the provided {@link DrawnElement}.
   *
   * @param drawnElement The DrawnElement representing the entity on the map.
   * @return The map image label corresponding to the DrawnElement's Drawable.
   */
  public Label getMapImageLabel(DrawnElement drawnElement, String text, ScreenPoint centerTop) {
    if (drawnElement.getDrawable() instanceof AbstractTemplate) {
      return buildMapImageLabel(
          text,
          centerTop,
          AppPreferences.templateMapLabelBackgroundColor.get(),
          AppPreferences.templateMapLabelForegroundColor.get(),
          AppPreferences.templateMapLabelBorderColor.get());
    } else {
      return buildMapImageLabel(
          text,
          centerTop,
          AppPreferences.drawingMapLabelBackgroundColor.get(),
          AppPreferences.drawingMapLabelForegroundColor.get(),
          AppPreferences.drawingMapLabelBorderColor.get());
    }
  }
}
