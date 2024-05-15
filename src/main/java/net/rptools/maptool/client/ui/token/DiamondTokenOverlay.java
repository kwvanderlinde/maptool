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
package net.rptools.maptool.client.ui.token;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.image.ImageObserver;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.server.proto.BooleanTokenOverlayDto;

/**
 * Place a diamond over a token.
 *
 * @author pwright
 * @version $Revision$ $Date$ $Author$
 */
public class DiamondTokenOverlay extends XTokenOverlay {

  /** Default constructor needed for XML encoding/decoding */
  public DiamondTokenOverlay() {
    this(BooleanTokenOverlay.DEFAULT_STATE_NAME, Color.RED, 5);
  }

  /**
   * Create a Diamond token overlay with the given name.
   *
   * @param aName Name of this token overlay.
   * @param aColor The color of this token overlay.
   * @param aWidth The width of the lines in this token overlay.
   */
  public DiamondTokenOverlay(String aName, Color aColor, int aWidth) {
    super(aName, aColor, aWidth);
  }

  /**
   * @see BooleanTokenOverlay#clone()
   */
  @Override
  public Object clone() {
    BooleanTokenOverlay overlay = new DiamondTokenOverlay(getName(), getColor(), getWidth());
    overlay.setOrder(getOrder());
    overlay.setGroup(getGroup());
    overlay.setMouseover(isMouseover());
    overlay.setOpacity(getOpacity());
    overlay.setShowGM(isShowGM());
    overlay.setShowOwner(isShowOwner());
    overlay.setShowOthers(isShowOthers());
    return overlay;
  }

  /**
   * @see BooleanTokenOverlay#paintOverlay(java.awt.Graphics2D, net.rptools.maptool.model.Token,
   *     java.awt.Rectangle, java.awt.image.ImageObserver...)
   */
  @Override
  public void paintOverlay(
      Graphics2D g, Token aToken, Rectangle bounds, ImageObserver... observers) {
    double hc = (double) bounds.width / 2;
    double vc = (double) bounds.height / 2;
    Color tempColor = g.getColor();
    g.setColor(getColor());
    Stroke tempStroke = g.getStroke();
    g.setStroke(getStroke());
    Composite tempComposite = g.getComposite();
    if (getOpacity() != 100)
      g.setComposite(
          AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) getOpacity() / 100));
    g.draw(new Line2D.Double(0, vc, hc, 0));
    g.draw(new Line2D.Double(hc, 0, bounds.width, vc));
    g.draw(new Line2D.Double(bounds.width, vc, hc, bounds.height));
    g.draw(new Line2D.Double(hc, bounds.height, 0, vc));
    g.setColor(tempColor);
    g.setStroke(tempStroke);
    g.setComposite(tempComposite);
  }

  public static DiamondTokenOverlay fromDto(BooleanTokenOverlayDto dto) {
    var overlay = new DiamondTokenOverlay();
    overlay.fillFrom(dto);
    return overlay;
  }

  public BooleanTokenOverlayDto toDto() {
    return getDto().setType(BooleanTokenOverlayDto.BooleanTokenOverlayTypeDto.DIAMOND).build();
  }
}
