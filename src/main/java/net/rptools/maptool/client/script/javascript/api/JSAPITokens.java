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
import java.util.List;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.script.javascript.*;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
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
    return MapTool.getClient().getCurrentZone().map(this::getMapTokens).orElse(List.of());
  }

  @HostAccess.Export
  public List<Object> getMapTokens(String zoneName) {
    return MapTool.getCampaign().getZoneByName(zoneName).map(this::getMapTokens).orElse(List.of());
  }

  public List<Object> getMapTokens(Zone zone) {
    final var tokens = new ArrayList<>();
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
    for (Zone z : MapTool.getCampaign().getZones()) {
      if (trusted || z.isVisible()) {
        Token t = z.getTokenByName(tokenName);
        if (t != null && (trusted || t.isOwner(playerId))) {
          return new JSAPIToken(t);
        }
      }
    }
    return null;
  }

  @HostAccess.Export
  public List<JSAPIToken> getSelectedTokens() {
    List<Token> tokens =
        MapTool.getClient()
            .getCurrentZone()
            .map(Zone::getId)
            .map(MapTool.getFrame()::getZoneRenderer)
            .map(ZoneRenderer::getSelectedTokensList)
            .orElse(List.of());
    var out_tokens = new ArrayList<JSAPIToken>();
    for (Token token : tokens) {
      out_tokens.add(new JSAPIToken(token));
    }
    return out_tokens;
  }

  @HostAccess.Export
  public JSAPIToken getSelected() {
    List<Token> tokens =
        MapTool.getClient()
            .getCurrentZone()
            .map(Zone::getId)
            .map(MapTool.getFrame()::getZoneRenderer)
            .map(ZoneRenderer::getSelectedTokensList)
            .orElse(List.of());
    if (!tokens.isEmpty()) {
      return new JSAPIToken(tokens.get(0));
    }
    return null;
  }

  @HostAccess.Export
  public JSAPIToken getTokenByID(String uuid) {
    // Start by checking the current map.
    JSAPIToken token = getMapTokenByID(uuid);
    if (token != null) {
      return token;
    }

    // Fallback to checking all maps.
    for (Zone zone : MapTool.getCampaign().getZones()) {
      var findToken = zone.resolveToken(uuid);
      if (findToken != null) {
        token = new JSAPIToken(findToken);
        token.setMap(zone);
        break;
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
    var currentZone = MapTool.getClient().getCurrentZone().orElse(null);
    if (currentZone == null) {
      return null;
    }

    JSAPIToken token = null;
    Token findToken = currentZone.getToken(new GUID(uuid));
    if (findToken != null
        && (JSScriptEngine.inTrustedContext()
            || findToken.isOwner(MapTool.getPlayer().getName()))) {
      token = new JSAPIToken(findToken);
      token.setMap(currentZone);
    }
    return token;
  }
}
