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
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

public class TokenNameFunction extends AbstractFunction {
  /** Singleton instance. */
  private static final TokenNameFunction instance = new TokenNameFunction();

  private TokenNameFunction() {
    super(0, 3, "getName", "setName");
  }

  /**
   * Gets the instance of Name.
   *
   * @return the instance of name.
   */
  public static TokenNameFunction getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> args)
      throws ParserException {
    if (functionName.equalsIgnoreCase("getName")) {
      FunctionUtil.checkNumberParam(functionName, args, 0, 2);
      var token = FunctionUtil.getTokenFromParam(resolver, functionName, args, 0, 1);
      return token.getName();
    } else if ("setName".equalsIgnoreCase(functionName)) {
      FunctionUtil.checkNumberParam(functionName, args, 1, 3);
      String name = args.get(0).toString();
      var token = FunctionUtil.getTokenAndZoneFromParam(resolver, functionName, args, 1, 2);

      if (name.isEmpty()) {
        throw new ParserException(
            I18N.getText("macro.function.tokenName.emptyTokenNameForbidden", "setName"));
      }
      setName(token.zone(), token.entity(), name);
      return token.entity().getName();
    } else {
      throw new ParserException(
          I18N.getText("macro.function.general.unknownFunction", functionName));
    }
  }

  /**
   * Gets the name of the token.
   *
   * @param token the token to get the name of.
   * @return the name of the token.
   */
  public static String getName(Token token) {
    return token.getName();
  }

  /**
   * Validates the name, sets the name of the token, and updates the server.
   *
   * @param token The token to set the name of.
   * @param name the name of the token.
   * @throws ParserException if an error occurs.
   */
  public static void setName(Zone zone, Token token, String name) throws ParserException {
    validateName(zone, token, name);
    MapTool.serverCommand().updateTokenProperty(token, Token.Update.setName, name);
  }

  /**
   * Validate a token name by testing for duplicates. There is a potential exposure of information
   * to the player in this method: through repeated attempts to name a token they own to another
   * name, they could determine which token names the GM is already using. Fortunately, the
   * showError() call makes this extremely unlikely due to the interactive nature of a failure.
   *
   * @param name the new name of the token
   * @throws ParserException thrown if a token has the same name
   */
  private static void validateName(Zone zone, Token tokenToCheck, String name)
      throws ParserException {
    if (MapTool.getPlayer().isGM() || MapTool.getParser().isMacroTrusted()) {
      // Always allowed.
      return;
    }

    List<Token> tokensList = zone.getAllTokens();

    for (Token token : tokensList) {
      String curTokenName = token.getName();
      if (curTokenName.equalsIgnoreCase(name) && !(token.equals(tokenToCheck))) {
        MapTool.showError(I18N.getText("Token.error.unableToRename", name));
        throw new ParserException(I18N.getText("Token.error.unableToRename", name));
      }
    }
  }
}
