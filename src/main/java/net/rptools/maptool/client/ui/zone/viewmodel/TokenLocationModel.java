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
package net.rptools.maptool.client.ui.zone.viewmodel;

import com.google.common.collect.Lists;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.ui.Scale;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.StringUtil;

/** This class tracks where each token is, separated by layer. */
public class TokenLocationModel {
  private final CodeTimer timer;
  private final Zone zone;
  // TODO Necessary evil for now. In future, depend on a special model that handles view offsets and
  //  scales. Better yet, work entirely in zone space rather than screen space so we don't have to
  //  care about offsets.
  private final Supplier<Scale> scaleSupplier;

  private final Map<Zone.Layer, List<TokenLocation>> tokenLocationMap = new HashMap<>();

  // region Caches. We could work right off tokenLocationMap, but this has its conveniences.
  //  Be sure to flush these on flush.
  private final List<TokenLocation> markerLocations = new ArrayList<>();
  private final Map<Token, TokenLocation> tokenLocationCache = new HashMap<>();
  // endregion

  public TokenLocationModel(CodeTimer timer, Zone zone, Supplier<Scale> scaleSupplier) {
    this.timer = timer;
    this.zone = zone;
    this.scaleSupplier = scaleSupplier;
  }

  public void flush() {
    tokenLocationCache.clear();
  }

  public void flush(Token token) {
    // This method can be called from a non-EDT thread so if that happens, make sure
    // we synchronize with the EDT.
    // TODO We really shouldn't be allowing this!
    synchronized (tokenLocationCache) {
      tokenLocationCache.remove(token);
    }
  }

  public void clear() {
    tokenLocationMap.clear();
    markerLocations.clear();
    tokenLocationCache.clear();
  }

  public @Nonnull TokenLocation getTokenLocation(Token token) {
    timer.start("TokenLocationModel.tokenLocationCache.get");
    var location = tokenLocationCache.get(token);
    timer.stop("TokenLocationModel.tokenLocationCache.get");
    if (location == null) {
      timer.start("TokenLocationModel.tokenLocationCache miss");
      // TODO Consider only using coordinates in zone space so that things don't need to be updated
      //  just because the viewport moves.
      // Create a new one.
      final var zoneScale = scaleSupplier.get();
      final var scale = zoneScale.getScale();
      final var footprintBounds = token.getBounds(zone);
      final var scaledWidth = (footprintBounds.width * scale);
      final var scaledHeight = (footprintBounds.height * scale);
      final var tokenScreenLocation =
          ScreenPoint.fromZonePoint(zoneScale, footprintBounds.x, footprintBounds.y);
      // Tokens are centered on the image center point
      double x = tokenScreenLocation.x;
      double y = tokenScreenLocation.y;
      final var origBounds = new Rectangle2D.Double(x, y, scaledWidth, scaledHeight);
      final var tokenBounds = new Area(origBounds);

      if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
        double sx = scaledWidth / 2 + x - (token.getAnchor().x * scale);
        double sy = scaledHeight / 2 + y - (token.getAnchor().y * scale);
        // facing defaults to down, or -90 degrees
        tokenBounds.transform(
            AffineTransform.getRotateInstance(Math.toRadians(-token.getFacing() - 90), sx, sy));
      }

      location =
          new TokenLocation(
              tokenBounds,
              footprintBounds,
              token,
              x,
              y,
              scaledWidth,
              scaledHeight,
              zoneScale.getOffsetX(),
              zoneScale.getOffsetY());
      tokenLocationCache.put(token, location);

      // A marker either has regular notes (visible to everyone) or GM notes
      //  for just the GM. GM sees all.
      // TODO May also want to show stamp whenever a portrait is available.
      //  https://discord.com/channels/296230822262865920/296657960720007169/1046889695273029702
      // TODO This is the only use of the isMarker() concept. If we lifted the logic for isMarker()
      //  out, could we get a clearer condition here?
      timer.start("TokenLocationModel.tokenLocationCache markers");
      if (token.isMarker()
          && (MapTool.getPlayer().isGM() || !StringUtil.isEmpty(token.getNotes()))) {
        markerLocations.add(location);
      }
      timer.stop("TokenLocationModel.tokenLocationCache markers");

      timer.stop("TokenLocationModel.tokenLocationCache miss");
    } else {
      timer.start("TokenLocationModel.tokenLocationCache hit");
      // Update the existing one.
      final var scale = scaleSupplier.get();
      location.updateOffset(scale.getOffsetX(), scale.getOffsetY());
      timer.start("TokenLocationModel.tokenLocationCache hit");
    }

    // Note: no check for being on screen as we don't care about that kind of stuff.

    return location;
  }

  // TODO Get rid of this method. Marker bounds _are_ token bounds, with all known tokens having
  //  marker bounds whenever they are marked as isMarker(). So callers could instead call
  //  `getTokenLocation()` after calling `isMarker()` to achieve the same thing.
  public @Nullable TokenLocation getMarkerLocation(Token token) {
    for (TokenLocation location : markerLocations) {
      if (location.token == token) {
        return location;
      }
    }
    return null;
  }

  public @Nullable Token getMarkerAt(int x, int y) {
    // TODO Avoid collection copying. Together with the lookup iterations, this could make things
    //  choppy with lots of notes.
    List<TokenLocation> locationList = new ArrayList<>(markerLocations);
    Collections.reverse(locationList);
    for (TokenLocation location : locationList) {
      if (location.bounds.contains(x, y)) {
        return location.token;
      }
    }
    return null;
  }

  /**
   * Convenience method to populate {@link #tokenLocationMap} on demand.
   *
   * @param layer The layer to get token locations for.
   * @return The token locations associated with the layer.
   */
  private List<TokenLocation> getTokenLocations(Zone.Layer layer) {
    return tokenLocationMap.computeIfAbsent(layer, key -> new ArrayList<>());
  }

  public Stream<Token> getTokensOnLayer(Zone.Layer layer) {
    return getTokenLocations(layer).stream().map(location -> location.token);
  }

  /**
   * Returns the token at screen location x, y.
   *
   * <p>TODO: Add a check so that tokens owned by the current player are given priority.
   *
   * @param layer The layer to look at for tokens.
   * @param point The position to look at, relative to the viewport.
   * @return The token on layer at point, or null if there isn't one.
   */
  public @Nullable Token getTokenAt(Zone.Layer layer, ScreenPoint point) {
    final var locationList = getTokenLocations(layer);
    for (TokenLocation location : Lists.reverse(locationList)) {
      if (location.bounds.contains(point)) {
        return location.token;
      }
    }
    return null;
  }

  public Stream<Token> getTokensContainedIn(Zone.Layer layer, Rectangle bounds) {
    return getTokenLocations(layer).stream()
        .filter(location -> bounds.contains(location.boundsCache))
        .map(location -> location.token);
  }

  public Stream<Token> getTokensIntersecting(Zone.Layer layer, Rectangle rect) {
    return getTokenLocations(layer).stream()
        .filter(location -> rect.intersects(location.bounds.getBounds()))
        .map(location -> location.token);
  }

  /**
   * Store a token location in the model.
   *
   * <p>These constraints must be respected:
   *
   * <ol>
   *   <li>The location must have previously been returned by {@link #getTokenLocation}
   *   <li>The location's token must be on the given layer
   * </ol>
   *
   * @param layer The layer to add the location to.
   * @param location The token location to store.
   */
  // TODO Add tokens automatically in getTokenLocation so there's no need for the constraints. This
  //  is almost possible, but currently we only add visible tokens, and it would change how/when
  //  token stacks are calculated.
  public void addTokenLocation(Zone.Layer layer, TokenLocation location) {
    assert (layer.equals(location.token.getLayer()));
    assert (tokenLocationCache.containsKey(location.token));
    assert (location == tokenLocationCache.get(location.token));

    getTokenLocations(layer).add(location);
  }

  public static class TokenLocation {

    public Area bounds;
    public Rectangle footprintBounds;
    public Token token;
    public Rectangle boundsCache;
    public double scaledHeight;
    public double scaledWidth;
    public double x;
    public double y;
    public int offsetX;
    public int offsetY;

    /**
     * Construct a TokenLocation object that caches where images are stored and what their size is
     * so that the next rendering pass can use that information to optimize the drawing.
     *
     * @param bounds
     * @param footprintBounds
     * @param token
     * @param x
     * @param y
     * @param scaledWidth
     * @param scaledHeight
     * @param initialViewOffsetX
     * @param initialViewOffsetY
     */
    public TokenLocation(
        Area bounds,
        Rectangle footprintBounds,
        Token token,
        double x,
        double y,
        double scaledWidth,
        double scaledHeight,
        int initialViewOffsetX,
        int initialViewOffsetY) {
      this.bounds = bounds;
      this.footprintBounds = footprintBounds;
      this.token = token;
      this.scaledWidth = scaledWidth;
      this.scaledHeight = scaledHeight;
      this.x = x;
      this.y = y;

      offsetX = initialViewOffsetX;
      offsetY = initialViewOffsetY;

      boundsCache = bounds.getBounds();
    }

    public void updateOffset(int viewOffsetX, int viewOffsetY) {
      int deltaX = viewOffsetX - offsetX;
      int deltaY = viewOffsetY - offsetY;

      boundsCache.x += deltaX;
      boundsCache.y += deltaY;

      offsetX = viewOffsetX;
      offsetY = viewOffsetY;
    }
  }
}
