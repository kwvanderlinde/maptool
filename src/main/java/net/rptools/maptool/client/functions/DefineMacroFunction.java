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
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

public class DefineMacroFunction extends AbstractFunction {
  private static final DefineMacroFunction instance = new DefineMacroFunction();

  private DefineMacroFunction() {
    super(
        0,
        UNLIMITED_PARAMETERS,
        "defineFunction",
        "isFunctionDefined",
        "oldFunction",
        "getDefinedFunctions");
  }

  public static DefineMacroFunction getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> parameters)
      throws ParserException {
    if (functionName.equalsIgnoreCase("defineFunction")) {
      FunctionUtil.blockUntrustedMacro(functionName);
      FunctionUtil.checkNumberParam(functionName, parameters, 2, 3);

      String newFunctionName = parameters.get(0).toString();

      String macro = parameters.get(1).toString();
      if (macro.toLowerCase().endsWith("@this")) {
        macro =
            macro.substring(0, macro.length() - 4)
                + MapTool.getParser().getMacroSource().getCallableLocation();
      }

      boolean ignoreOutput = false;
      if (parameters.size() > 2) {
        var ignoreOutputParam = parameters.get(2);
        if (!(ignoreOutputParam instanceof BigDecimal bd)) {
          throw new ParserException(
              I18N.getText(
                  "macro.function.general.argumentTypeN",
                  functionName,
                  3,
                  ignoreOutputParam.toString()));
        }
        ignoreOutput = !BigDecimal.ZERO.equals(bd);
      }
      boolean newVariableContext = true;
      if (parameters.size() > 3) {
        var newVariableContextParam = parameters.get(3);
        if (!(newVariableContextParam instanceof BigDecimal bd)) {
          throw new ParserException(
              I18N.getText(
                  "macro.function.general.argumentTypeN",
                  functionName,
                  4,
                  newVariableContextParam));
        }
        newVariableContext = !BigDecimal.ZERO.equals(bd);
      }

      UserDefinedMacroFunctions.getInstance()
          .defineFunction(parser, newFunctionName, macro, ignoreOutput, newVariableContext);
      return I18N.getText("macro.function.defineFunction.functionDefined", newFunctionName);
    } else if (functionName.equalsIgnoreCase("oldFunction")) {
      return UserDefinedMacroFunctions.getInstance()
          .executeOldFunction(parser, resolver, parameters);
    } else if (functionName.equalsIgnoreCase("getDefinedFunctions")) {
      FunctionUtil.checkNumberParam(functionName, parameters, 0, 2);
      String delim = parameters.size() > 0 ? parameters.get(0).toString() : "";
      boolean showFullLocations = false;
      if (parameters.size() > 1) {
        showFullLocations = FunctionUtil.paramAsBoolean(functionName, parameters, 1, false);
      }
      return UserDefinedMacroFunctions.getInstance().getDefinedFunctions(delim, showFullLocations);
    } else if ("isFunctionDefined".equalsIgnoreCase(functionName)) {
      FunctionUtil.checkNumberParam(functionName, parameters, 1, 1);

      String function = parameters.get(0).toString();

      if (UserDefinedMacroFunctions.getInstance().isFunctionDefined(function)) {
        return BigDecimal.ONE;
      }

      if (parser.getFunction(function) != null) {
        return BigDecimal.valueOf(2);
      }

      return BigDecimal.ZERO;
    }
    throw new ParserException(I18N.getText("macro.function.general.unknownFunction", functionName));
  }
}
