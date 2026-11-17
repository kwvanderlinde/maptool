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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.functions;

import java.util.List;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

public class TokenVisibleFunction extends AbstractFunction {

  /** The singleton instance. */
  private static final TokenVisibleFunction instance = new TokenVisibleFunction();

  private TokenVisibleFunction() {
    super(
        0,
        2,
        "setVisible",
        "getVisible",
        "setOwnerOnlyVisible",
        "getOwnerOnlyVisible",
        "setAlwaysVisible",
        "getAlwaysVisible");
  }

  /**
   * Gets the instance of Visible.
   *
   * @return the instance.
   */
  public static TokenVisibleFunction getInstance() {
    return instance;
  }

  /**
   * Gets if the token is visible.
   *
   * @param token The token to check.
   * @return if the token is visible.
   * @throws ParserException if the player does not have permissions to check.
   */
  public static Object getVisible(Token token) throws ParserException {
    FunctionUtil.blockUntrustedMacro("getVisible");

    boolean ownerOnly = token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token);
    return FunctionUtil.getDecimalForBoolean(!ownerOnly && token.isVisible());
  }

  /**
   * Sets if the token is visible or not.
   *
   * @param token the token to set.
   * @param val the value to set the visible flag to.
   * @throws ParserException if no permission
   */
  public static void setVisible(Token token, Object val) throws ParserException {
    FunctionUtil.blockUntrustedMacro("setVisible");

    boolean set = getBooleanValue(val);
    MapTool.serverCommand().updateTokenProperty(token, Token.Update.setVisible, set);
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> param)
      throws ParserException {
    if (functionName.equalsIgnoreCase("getVisible")) {
      FunctionUtil.checkNumberParam(functionName, param, 0, 1);
      Token token = FunctionUtil.getTokenFromParam(resolver, functionName, param, 0, -1);
      return getVisible(token);
    } else if (functionName.equalsIgnoreCase("setOwnerOnlyVisible")) {
      FunctionUtil.blockUntrustedMacro(functionName);
      FunctionUtil.checkNumberParam(functionName, param, 1, 2);
      Object val = param.get(0);
      Token token = FunctionUtil.getTokenFromParam(resolver, functionName, param, 1, -1);
      MapTool.serverCommand()
          .updateTokenProperty(token, Token.Update.setVisibleOnlyToOwner, getBooleanValue(val));
      return val;
    } else if (functionName.equalsIgnoreCase("getOwnerOnlyVisible")) {
      FunctionUtil.blockUntrustedMacro(functionName);
      FunctionUtil.checkNumberParam(functionName, param, 0, 1);
      Token token = FunctionUtil.getTokenFromParam(resolver, functionName, param, 0, -1);
      return FunctionUtil.getDecimalForBoolean(token.isVisibleOnlyToOwner());
    } else if (functionName.equalsIgnoreCase("setVisible")) {
      FunctionUtil.checkNumberParam(functionName, param, 1, 2);
      Object val = param.get(0);
      Token token = FunctionUtil.getTokenFromParam(resolver, functionName, param, 1, -1);
      setVisible(token, val);
      return val;
    } else if (functionName.equalsIgnoreCase("setAlwaysVisible")) {
      FunctionUtil.blockUntrustedMacro(functionName);
      FunctionUtil.checkNumberParam(functionName, param, 1, 3);
      Object val = param.get(0);
      Token token = FunctionUtil.getTokenFromParam(resolver, functionName, param, 1, 2);
      MapTool.serverCommand()
          .updateTokenProperty(
              token, Token.Update.setIsAlwaysVisible, FunctionUtil.getBooleanValue(val));
      return val;
    } else if (functionName.equalsIgnoreCase("getAlwaysVisible")) {
      FunctionUtil.blockUntrustedMacro(functionName);
      FunctionUtil.checkNumberParam(functionName, param, 0, 1);
      Token token = FunctionUtil.getTokenFromParam(resolver, functionName, param, 0, -1);
      boolean ownerOnly = token.isVisibleOnlyToOwner() && !AppUtil.playerOwns(token);
      return FunctionUtil.getDecimalForBoolean(!ownerOnly && token.isAlwaysVisible());
    } else {
      throw new ParserException(I18N.getText("macro.function.general.unknownFunction"));
    }
  }

  /**
   * Converts an arbitrary object to a boolean value.
   *
   * <p>The functions in this class do not support the general set of possibilities as would be
   * supported by {@link FunctionUtil#getBooleanValue(Object)}, which is why we have our own support
   * function here.
   *
   * @param val The value to convert to a boolean.
   * @return The boolean equivalent of {@code val}.
   */
  private static boolean getBooleanValue(Object val) {
    return switch (val) {
      case Boolean b -> b;
      case Integer i -> i != 0;
      default -> {
        try {
          yield Integer.parseInt(val.toString()) != 0;
        } catch (NumberFormatException e) {
          yield Boolean.parseBoolean(val.toString());
        }
      }
    };
  }
}
