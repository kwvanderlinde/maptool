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

import java.math.BigDecimal;
import java.util.List;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolVariableResolver;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.InitiativeList;
import net.rptools.maptool.model.Zone;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

/**
 * Accessor for the combatant with the current initiative
 *
 * @author Jay
 */
public class CurrentInitiativeFunction extends AbstractFunction {

  /** Handle adding one, all, all PCs or all NPC tokens. */
  private CurrentInitiativeFunction() {
    super(0, 1, "getCurrentInitiative", "setCurrentInitiative", "getInitiativeToken");
  }

  /** singleton instance of this function */
  private static final CurrentInitiativeFunction instance = new CurrentInitiativeFunction();

  /**
   * @return singleton instance
   */
  public static CurrentInitiativeFunction getInstance() {
    return instance;
  }

  /**
   * @see AbstractFunction#childEvaluate(Parser, VariableResolver, String, List)
   */
  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> args)
      throws ParserException {
    if (!MapTool.getParser().isMacroTrusted()) {
      if (!MapTool.getFrame().getInitiativePanel().hasGMPermission())
        throw new ParserException(I18N.getText("macro.function.initiative.mustBeGM", functionName));
    }
    var zone =
        (resolver instanceof MapToolVariableResolver mtResolver)
            ? mtResolver.getZoneInContext().orElse(null)
            : null;
    if (zone == null) {
      throw new ParserException(I18N.getText("macro.function.map.none", functionName));
    }

    if (functionName.equalsIgnoreCase("getCurrentInitiative")) {
      return getCurrentInitiative(zone);
    } else if (functionName.equalsIgnoreCase("setCurrentInitiative")) {
      if (args.size() != 1) {
        throw new ParserException(I18N.getText("macro.function.initiative.oneParam", functionName));
      }
      setCurrentInitiative(zone, args.get(0));
      return args.get(0);
    } else if (functionName.equalsIgnoreCase("getInitiativeToken")) {
      return getInitiativeToken(zone);
    }
    throw new ParserException(I18N.getText("macro.function.general.unknownFunction", functionName));
  }

  /**
   * Get the token that has the current initiative;
   *
   * @return The current initiative
   */
  public static String getInitiativeToken(Zone zone) {
    InitiativeList list = zone.getInitiativeList();
    int index = list.getCurrent();
    return index != -1 ? list.getToken(index).getId().toString() : "";
  }

  /**
   * Get the current initiative;
   *
   * @return The current initiative
   */
  public static BigDecimal getCurrentInitiative(Zone zone) {
    InitiativeList list = zone.getInitiativeList();
    return new BigDecimal(list.getCurrent());
  }

  /**
   * Set the current initiative.
   *
   * @param value New value for the round.
   */
  public static void setCurrentInitiative(Zone zone, Object value) {
    InitiativeList list = zone.getInitiativeList();
    list.setCurrent(InitiativeRoundFunction.getInt(value));
  }
}
