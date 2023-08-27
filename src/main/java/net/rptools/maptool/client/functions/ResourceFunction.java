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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;
import org.apache.commons.lang.StringUtils;

public class ResourceFunction extends AbstractFunction {

  /** The singleton instance. */
  private static final ResourceFunction instance = new ResourceFunction();

  private ResourceFunction() {
    super(0, -1, "getResourceURI");
  }

  /**
   * Gets the instance of ResourceFunction.
   *
   * @return the instance.
   */
  public static ResourceFunction getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> parameters)
      throws ParserException {
    if (functionName.equalsIgnoreCase("getResourceURI")) {
      FunctionUtil.checkNumberParam(functionName, parameters, 2, 2);

      final String resourceLibraryName = parameters.get(0).toString();
      final String path = StringUtils.stripStart(parameters.get(1).toString(), "/");

      // TOOD Incorporate an ephemeral nonce.

      return String.format(
          "resource://%s/%s", URLEncoder.encode(resourceLibraryName, StandardCharsets.UTF_8), path);
    }
    throw new ParserException(I18N.getText("macro.function.general.unknownFunction", functionName));
  }
}
