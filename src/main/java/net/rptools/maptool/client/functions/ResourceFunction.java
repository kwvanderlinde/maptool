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
import net.rptools.maptool.client.MapTool;
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
      // TODO Library names are not unique. I don't accept that this is a good interface.
      //  Perhaps a better alternative (for this and getGlobbedResourceURIs) is to have a separate
      //  function for getting resource library IDs, and then use the ID when building resource
      //  URIs.
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
      final var result = new ArrayList<String>();

      final var globPath = Paths.get(glob);
      int i;
      final var n = globPath.getNameCount();
      final var pattern = Pattern.compile(".*?[*?\\[\\]{}].*");
      // Only to n-1 because we don't want the top path to take the last component in case it is
      // actually a direct reference to a single file instead of a glob. Saves some case work later.
      for (i = 0; i < n - 1; ++i) {
        final var name = globPath.getName(i);
        if (pattern.matcher(name.toString()).matches()) {
          break;
        }
      }
      final var globTopPath = (i == 0) ? null : globPath.subpath(0, i);
      // TODO I don't actually need to modify the glob... might not be worth it.
      final var newGlob = globTopPath == null ? glob : globPath.subpath(i, n).toString();

      for (final var library :
          MapTool.getResourceLibraryManager().getLibrariesByName(resourceLibraryName)) {
        final var rootDir =
            ((globTopPath == null) ? library.path() : library.path().resolve(globTopPath))
                .normalize();
        if (!rootDir.startsWith(library.path())) {
          // Trying to escape. That's no good.
          log.error("Glob {} escapes the library root {}", glob, library.path());
          continue;
        }

        final var matchedPaths = new ArrayList<Path>();
        try {
          final var pathMatcher = rootDir.getFileSystem().getPathMatcher("glob:" + newGlob);
          Files.walkFileTree(
              rootDir,
              new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                  // Glob doesn't include the top directories anymore, so relativize relative to the
                  // new glob root.
                  if (pathMatcher.matches(rootDir.relativize(file))) {
                    // Results should be relative to the library root, not the new glob root.
                    matchedPaths.add(library.path().relativize(file));
                  }
                  return FileVisitResult.CONTINUE;
                }
              });
        } catch (Exception e) {
          log.error(e);
          throw new ParserException(e);
        }

        for (final var path : matchedPaths) {
          final var uri =
              String.format(
                  "resource://%s-%d/%s",
                  URLEncoder.encode(library.name(), StandardCharsets.UTF_8), library.id(), path);
          result.add(uri);
        }
      }

      return FunctionUtil.delimitedResult("json", result);
    }
    throw new ParserException(I18N.getText("macro.function.general.unknownFunction", functionName));
  }
}
