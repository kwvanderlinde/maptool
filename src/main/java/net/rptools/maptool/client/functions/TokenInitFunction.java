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

import java.math.BigDecimal;
import java.util.List;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.InitiativeList;
import net.rptools.maptool.model.InitiativeList.TokenInitiative;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZoneEntity;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

/**
 * Set the token initiative
 *
 * @author Jay
 */
public class TokenInitFunction extends AbstractFunction {

  /** Getter has 0 to 2, setter has 1 to 3 */
  private TokenInitFunction() {
    super(0, 4, "setInitiative", "getInitiative", "addToInitiative");
  }

  /** singleton instance of this function */
  private static final TokenInitFunction singletonInstance = new TokenInitFunction();

  /**
   * @return singleton instance
   */
  public static TokenInitFunction getInstance() {
    return singletonInstance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> args)
      throws ParserException {
    if (functionName.equalsIgnoreCase("getInitiative")) {
      FunctionUtil.checkNumberParam(functionName, args, 0, 2);
      ZoneEntity<Token> token =
          FunctionUtil.getTokenAndZoneFromParam(resolver, functionName, args, 0, 1);
      return getInitiative(token.zone(), token.entity());
    } else if (functionName.equalsIgnoreCase("addToInitiative")) {
      FunctionUtil.checkNumberParam(functionName, args, 0, 4);
      boolean allowDuplicates =
          args.size() > 0 ? FunctionUtil.paramAsBoolean(functionName, args, 0, true) : false;
      String state = args.size() > 1 && !"".equals(args.get(1)) ? args.get(1).toString() : null;
      ZoneEntity<Token> result =
          FunctionUtil.getTokenAndZoneFromParam(resolver, functionName, args, 2, 3);
      return addToInitiative(allowDuplicates, state, result.zone(), result.entity());
    } else if ("setInitiative".equalsIgnoreCase(functionName)) { // setInitiative
      FunctionUtil.checkNumberParam(functionName, args, 1, 3);
      String value = args.get(0).toString();
      ZoneEntity<Token> token =
          FunctionUtil.getTokenAndZoneFromParam(resolver, functionName, args, 1, 2);
      return setInitiative(token.zone(), token.entity(), value);
    }
    throw new ParserException(I18N.getText("macro.function.general.unknownFunction", functionName));
  }

  /**
   * Return a string containing the initiatives of the token
   *
   * @param token the token
   * @return a String list of the initiatives
   */
  public static String getInitiative(Zone zone, Token token) {
    String ret = "";
    List<TokenInitiative> tis = token.getInitiatives(zone);
    if (tis.isEmpty()) return I18N.getText("macro.function.TokenInit.notOnList");
    for (TokenInitiative ti : tis) {
      if (ret.length() > 0) ret += ", ";
      ret += ti.getState();
    } // endif
    return ret;
  }

  /**
   * Add a token to the initiative. Can also assign the token an initiative state.
   *
   * @param allowDuplicates are duplicates allowed
   * @param state the initiative to assign to the token
   * @param zone the zone containing the token and whose initiaitve is to be modified.
   * @param token the token to add to the initiative
   * @return 1 if the token was added, 0 otherwise
   * @throws ParserException when an error occurs.
   */
  private static BigDecimal addToInitiative(
      boolean allowDuplicates, String state, Zone zone, Token token) throws ParserException {
    boolean hasPermission = MapTool.getFrame().getInitiativePanel().hasOwnerPermission(token);
    if (!MapTool.getParser().isMacroTrusted() && !hasPermission) {
      String message;
      if (MapTool.getFrame().getInitiativePanel().isOwnerPermissions()) {
        message = I18N.getText("macro.function.initiative.gmOrOwner", "addToInitiative");
      } else {
        message = I18N.getText("macro.function.initiative.gmOnly", "addToInitiative");
      }
      throw new ParserException(message);
    } // endif

    InitiativeList list = zone.getInitiativeList();
    // insert the token if needed
    TokenInitiative ti = null;
    if (allowDuplicates || list.indexOf(token).isEmpty()) {
      ti = list.insertToken(-1, token);
      if (state != null) ti.setState(state);
    } else {
      setInitiative(zone, token, state);
    } // endif
    return ti != null ? BigDecimal.ONE : BigDecimal.ZERO;
  }

  /**
   * Set an initiative value to all initiative entries of a token.
   *
   * @param token the token to set the initiative of
   * @param state the initiative to assign to the token
   * @return the value assigned to the initiative
   */
  public static Object setInitiative(Zone zone, Token token, String state) {
    List<InitiativeList.TokenInitiative> tis = token.getInitiatives(zone);
    for (InitiativeList.TokenInitiative ti : tis) ti.setState(state);
    if (tis.isEmpty()) {
      return I18N.getText("macro.function.TokenInit.notOnListSet");
    } else {
      return state;
    }
  }
}
