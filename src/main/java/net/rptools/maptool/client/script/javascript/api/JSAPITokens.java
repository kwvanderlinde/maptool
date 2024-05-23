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
package net.rptools.maptool.client.script.javascript.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.script.javascript.*;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import org.graalvm.polyglot.HostAccess;

public class JSAPITokens implements MapToolJSAPIInterface {
  @Override
  public String serializeToString() {
    return "MapTool.tokens";
  }

  @HostAccess.Export
  public List<Object> getMapTokens() {
    var currentZone = MapTool.getClient().getCurrentZone();
    return currentZone == null ? Collections.emptyList() : getMapTokens(currentZone);
  }

  @HostAccess.Export
  public List<Object> getMapTokens(String zoneName) {
    var zone = MapTool.getClient().getCampaign().getZoneByName(zoneName);
    return zone == null ? Collections.emptyList() : getMapTokens(zone);
  }

  private List<Object> getMapTokens(@Nonnull Zone zone) {
    final List<Object> tokens = new ArrayList<>();
    boolean trusted = JSScriptEngine.inTrustedContext();
    String playerId = MapTool.getPlayer().getName();
    zone.getAllTokens()
        .forEach(
            (t -> {
              if (trusted || t.isOwner(playerId)) {
                tokens.add(new JSAPIToken(t));
              }
            }));

    return tokens;
  }

  @HostAccess.Export
  public JSAPIToken getTokenByName(String tokenName) {
    boolean trusted = JSScriptEngine.inTrustedContext();
    String playerId = MapTool.getPlayer().getName();
    for (Zone zone : MapTool.getClient().getCampaign().getZones()) {
      if (trusted || zone.isVisible()) {
        Token t = zone.getTokenByName(tokenName);
        if (t != null && (trusted || t.isOwner(playerId))) {
          return new JSAPIToken(t);
        }
      }
    }
    return null;
  }

  @HostAccess.Export
  public List<JSAPIToken> getSelectedTokens() {
    List<Token> tokens = MapTool.getFrame().getCurrentZoneRenderer().getSelectedTokensList();
    List<JSAPIToken> out_tokens = new ArrayList<JSAPIToken>();
    for (Token token : tokens) {
      out_tokens.add(new JSAPIToken(token));
    }
    return out_tokens;
  }

  @HostAccess.Export
  public JSAPIToken getSelected() {
    List<Token> tokens = MapTool.getFrame().getCurrentZoneRenderer().getSelectedTokensList();
    if (tokens.size() > 0) {
      return new JSAPIToken(tokens.get(0));
    }
    return null;
  }

  @HostAccess.Export
  public JSAPIToken getTokenByID(String uuid) {
    JSAPIToken token = null;
    Token findToken =
        MapTool.getFrame().getCurrentZoneRenderer().getZone().getToken(new GUID(uuid));
    if (findToken != null) {
      token = new JSAPIToken(findToken);
      token.setMap(MapTool.getFrame().getCurrentZoneRenderer().getZone());
    } else {
      for (Zone zone : MapTool.getClient().getCampaign().getZones()) {
        findToken = zone.resolveToken(uuid);
        if (findToken != null) {
          token = new JSAPIToken(findToken);
          token.setMap(zone);
          break;
        }
      }
    }
    if (token != null
        && (JSScriptEngine.inTrustedContext() || token.isOwner(MapTool.getPlayer().getName()))) {
      return token;
    }
    return null;
  }

  @HostAccess.Export
  public JSAPIToken getMapTokenByID(String uuid) {
    JSAPIToken token = null;
    Token findToken =
        MapTool.getFrame().getCurrentZoneRenderer().getZone().getToken(new GUID(uuid));
    if (findToken != null
        && (JSScriptEngine.inTrustedContext() || token.isOwner(MapTool.getPlayer().getName()))) {
      token = new JSAPIToken(findToken);
      token.setMap(MapTool.getFrame().getCurrentZoneRenderer().getZone());
    }
    return token;
  }
}
