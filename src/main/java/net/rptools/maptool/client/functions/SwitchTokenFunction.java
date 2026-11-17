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
import net.rptools.maptool.client.MapToolVariableResolver;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

public class SwitchTokenFunction extends AbstractFunction {
  private static final SwitchTokenFunction instance = new SwitchTokenFunction();

  private SwitchTokenFunction() {
    super(1, 1, "switchToken");
  }

  public static SwitchTokenFunction getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> parameters)
      throws ParserException {
    FunctionUtil.blockUntrustedMacro(functionName);
    FunctionUtil.checkNumberParam(functionName, parameters, 1, 1);

    Token token = FunctionUtil.getTokenFromParam(resolver, functionName, parameters, 0, -1);

    ((MapToolVariableResolver) resolver).setTokenIncontext(token);
    return "";
  }
}
