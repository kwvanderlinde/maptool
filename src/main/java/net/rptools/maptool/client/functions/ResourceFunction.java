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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResourceFunction extends AbstractFunction {
  private static final Logger log = LogManager.getLogger(ResourceFunction.class);

  /** The singleton instance. */
  private static final ResourceFunction instance = new ResourceFunction();

  private ResourceFunction() {
    super(0, -1, "getResourceURI", "getGlobbedResourceURIs");
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
    } else if (functionName.equalsIgnoreCase("getGlobbedResourceURIs")) {
      FunctionUtil.checkNumberParam(functionName, parameters, 2, 2);

      final String resourceLibraryName = parameters.get(0).toString();
      final String glob = StringUtils.stripStart(parameters.get(1).toString(), "/");

      // TODO Look this up in the asset panel somehow.
      //  Need to create a model to capture asset roots. Currently it's directly exposed in
      //  AppPreferences, but we would want a proper way to model and control it. Each added library
      //  (whether newly installed or loaded from preferences) is associated with an internal ID
      //  that is unique to the current MapTool instance. Resource URIs will not include the library
      //  name in the hostname component, but will instead use the internal ID.
      //  Also note that resource library names can be repeated. So we potentially have multiple
      //  root directories we should look at when globbing.
      // TODO When we no longer hardcode this, make sure it is still absolute.
      final var resourceLibraryRoot =
          Paths.get(
              "/home/kenneth/Nextcloud/RPGs/tools/MapTool/Resource Packs/Forgotten Adventures");

      var topPath = resourceLibraryRoot;
      var globPath = Paths.get(glob);

      final var matchedPaths = new ArrayList<Path>();

      int i;
      final var n = globPath.getNameCount();
      final var pattern = Pattern.compile(".*?[*?\\[\\]{}].*");
      // Only to n-1 because we don't want the top path to take the last component in case it is
      // actually a direct references to a single file instead of a glob.
      for (i = 0; i < n - 1; ++i) {
        final var name = globPath.getName(i);
        if (pattern.matcher(name.toString()).matches()) {
          break;
        }
      }

      if (i > 0) {
        // Glob not present in the first element, only in some later element.
        topPath = resourceLibraryRoot.resolve(globPath.subpath(0, i)).normalize();
        if (!topPath.startsWith(resourceLibraryRoot)) {
          // Trying to escape. That's no good.
          log.error("Glob {} escapes the library root {}", glob, resourceLibraryRoot);
          throw new ParserException("Invalid path");
        }

        globPath = globPath.subpath(i, n);
      }

      final var rootDir = topPath;

      try {
        final var newGlob = globPath.toString();
        final var pathMatcher = rootDir.getFileSystem().getPathMatcher("glob:" + newGlob);
        Files.walkFileTree(
            rootDir,
            new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                file = rootDir.relativize(file);
                if (pathMatcher.matches(file)) {
                  matchedPaths.add(file);
                }
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (Exception e) {
        log.error(e);
        throw new ParserException(e);
      }

      final var result = new ArrayList<String>();
      for (final var path : matchedPaths) {
        // TOOD Incorporate an ephemeral nonce.
        final var uri =
            String.format(
                "resource://%s/%s",
                URLEncoder.encode(resourceLibraryName, StandardCharsets.UTF_8), path);
        result.add(uri);
      }

      return FunctionUtil.delimitedResult("json", result);

      // TOOD Incorporate an ephemeral nonce.
    }
    throw new ParserException(I18N.getText("macro.function.general.unknownFunction", functionName));
  }
}
