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
package net.rptools.maptool.client.functions;

import annotatedmacros.annotations.ContextGivenBy;
import annotatedmacros.annotations.FunctionName;
import annotatedmacros.annotations.MacroFunction;
import java.awt.Rectangle;
import java.math.BigDecimal;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;

public class TokenLocationFunctions_New {
  /** Holds a token's x, y and z coordinates. */
  private static class TokenLocation {
    int x;
    int y;
    int z;
  }

  /** Ignore grid for movement metric in distance methods. */
  private static final String NO_GRID = "NO_GRID";

  @MacroFunction
  public Object getTokenX(
      VariableResolver resolver, Parser parser, @FunctionName String functionName)
      throws ParserException {
    return getTokenX(resolver, true);
  }

  @MacroFunction
  public Object getTokenX(VariableResolver resolver, boolean inPixelsElseCells)
      throws ParserException {
    var token = FunctionUtil.getImpersonated("getTokenX", resolver);
    return getTokenX(inPixelsElseCells, token);
  }

  @MacroFunction
  public Object getTokenX(boolean inPixelsElseCells, Token token) {
    return getTokenX(inPixelsElseCells, token, token.getZoneRenderer().getZone());
  }

  @MacroFunction
  public Object getTokenX(boolean inPixelsElseCells, @ContextGivenBy("zone") Token token, Zone zone) {
    return BigDecimal.valueOf(getTokenLocation(inPixelsElseCells, token, zone).x);
  }

  @MacroFunction
  public Object getTokenY(VariableResolver resolver) throws ParserException {
    return getTokenY(resolver, true);
  }

  @MacroFunction
  public Object getTokenY(VariableResolver resolver, boolean inPixelsElseCells)
      throws ParserException {
    var token = FunctionUtil.getImpersonated("getTokenX", resolver);
    return getTokenY(inPixelsElseCells, token);
  }

  @MacroFunction
  public Object getTokenY(boolean inPixelsElseCells, Token token) {
    return getTokenY(inPixelsElseCells, token, token.getZoneRenderer().getZone());
  }

  @MacroFunction
  public Object getTokenY(boolean inPixelsElseCells, @ContextGivenBy("zone") Token token, Zone zone) {
    return BigDecimal.valueOf(getTokenLocation(inPixelsElseCells, token, zone).y);
  }

  @MacroFunction
  public Object getTokenDrawOrder(@ContextGivenBy("zone") Token token, Zone zone) {
    return null;
  }

  // TODO This one does not support specifying a map since that's what we're looking up.
  @MacroFunction
  public Object getTokenMap(Token token, String delim) {
    return null;
  }

  @MacroFunction
  public Object getDistance(
      Token target, boolean resultIsInDistancePerCellElseCells, Token source, String metric) {
    return null;
  }

  @MacroFunction
  public Object moveToken(
      BigDecimal x, BigDecimal y, boolean inDistancePerCellElseCells, Token token) {
    return null;
  }

  @MacroFunction(name = "goto")
  public Object goto_() {
    return null;
  }

  @MacroFunction
  public Object getDistanceToXY(
      BigDecimal x,
      BigDecimal y,
      boolean resultIsInDistancePerCellElseCells,
      Token source,
      String metric,
      boolean xyIsInPixelsElseCells) {
    return null;
  }

  @MacroFunction
  public Object setTokenDrawOrder() {
    return null;
  }

  @MacroFunction
  public Object moveTokenFromMap(
      @ContextGivenBy("zone") Token token, Zone zone, BigDecimal x, BigDecimal y, BigDecimal z) {
    return null;
  }

  // Note: zone is the map being moved to, it does not provide context for {@code token}.
  @MacroFunction
  public Object MoveTokenToMap(Token token, Zone zone, BigDecimal x, BigDecimal y, BigDecimal z) {
    return null;
  }

  /**
   * Gets the location of the token on the map.
   *
   * @param useDistancePerCell should the cell coordinates per returned?
   * @param token the token to return the location of.
   * @return the location of the token.
   */
  private TokenLocation getTokenLocation(boolean useDistancePerCell, Token token, Zone zone) {
    var loc = new TokenLocation();
    if (useDistancePerCell) {
      Rectangle tokenBounds = token.getBounds(token.getZoneRenderer().getZone());
      loc.x = tokenBounds.x;
      loc.y = tokenBounds.y;
    } else {
      CellPoint cellPoint = getTokenCell(token, zone);
      int x = cellPoint.x;
      int y = cellPoint.y;

      loc.x = x;
      loc.y = y;
    }
    loc.z = token.getZOrder();

    return loc;
  }

  /**
   * Gets the cell point that the token is at.
   *
   * @param token the token.
   * @return the CellPoint where the token is.
   */
  public CellPoint getTokenCell(Token token, Zone zone) {
    return zone.getGrid().convert(new ZonePoint(token.getX(), token.getY()));
  }
}
