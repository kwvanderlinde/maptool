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
package net.rptools.maptool.model.library.token;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.gamedata.MTScriptDataConversion;
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.data.DataValueFactory;
import net.rptools.maptool.model.library.data.LibraryData;

/** Class that represents the data for a token library. */
public class TokenLibraryData implements LibraryData {

  private final LibraryToken libraryToken;

  /**
   * Creates a the {@code TokenLibraryData} for a token library data object.
   *
   * @param libraryToken The library token to use.
   */
  TokenLibraryData(LibraryToken libraryToken) {
    this.libraryToken = libraryToken;
  }

  @Override
  public CompletableFuture<Set<String>> getAllKeys() {
    return libraryToken.getToken().thenApply(Token::getPropertyNames);
  }

  /**
   * Returns the {@link DataValue} for the given key. This method must be run on thw swing thread.
   *
   * @param token the lib: token.
   * @param key The key to get the value for.
   * @return The value for the given key.
   */
  private DataValue getData(Token token, String key) {
    var val = token.getProperty(key);
    if (val == null) {
      return DataValueFactory.undefined(key);
    } else if (val instanceof Number n) {
      if (n.intValue() == n.doubleValue()) {
        return DataValueFactory.fromLong(key, n.longValue());
      } else {
        return DataValueFactory.fromDouble(key, n.doubleValue());
      }
    } else if (val instanceof Boolean b) {
      return DataValueFactory.fromBoolean(key, b);
    } else if (val instanceof String s) {
      try {
        double dval = Double.parseDouble(s.trim());
        if (dval == (long) dval) {
          return DataValueFactory.fromLong(key, (long) dval);
        } else {
          return DataValueFactory.fromDouble(key, dval);
        }
      } catch (NumberFormatException e) {
        // Do nothing if it can't be parsed as a number
      }
      return DataValueFactory.fromString(key, s);
    } else {
      return DataValueFactory.fromString(key, val.toString());
    }
  }

  @Override
  public CompletableFuture<DataValue> getValue(String key) {
    return libraryToken.getToken().thenApply(token -> getData(token, key));
  }

  @Override
  public CompletableFuture<Void> setData(DataValue value) {
    return libraryToken
        .getToken()
        .thenAccept(
            token -> {
              Object tokenVal = new MTScriptDataConversion().convertToMTScriptType(value);
              MapTool.serverCommand()
                  .updateTokenProperty(
                      token, Token.Update.setProperty, value.getName(), tokenVal.toString());
            });
  }

  @Override
  public DataValue getStaticData(String path) {
    return DataValueFactory.undefined(path);
  }

  @Override
  public DataValue getPublicStaticData(String path) {
    return DataValueFactory.undefined(path);
  }
}
