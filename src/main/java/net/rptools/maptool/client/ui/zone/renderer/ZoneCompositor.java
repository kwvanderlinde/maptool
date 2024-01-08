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
package net.rptools.maptool.client.ui.zone.renderer;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.*;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.AbstractPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.IsometricGrid;
import net.rptools.maptool.model.LookupTable;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.util.ImageManager;
import net.rptools.parser.ParserException;

/**
 * The Zone Compositor is responsible for providing the Zone Renderer with what needs to be
 * rendered. Within a given map region what objects exist that need to be drawn. Basically "What's
 * on screen?"
 */
public class ZoneCompositor {
  Zone zone;
  ZoneRenderer renderer;
  private Map<Token, Set<Token>> objectCache; // placeholder
  private boolean initialised;

  ZoneCompositor() {
    initialised = false;
  }

  public boolean isInitialised() {
    return initialised;
  }

  public void setRenderer(ZoneRenderer zoneRenderer) {
    renderer = zoneRenderer;
    zone = renderer.getZone();
    initialised = true;
  }

  protected Map<Token, Set<Token>> drawWhat(Rectangle2D bounds) {
    // Some logic goes here
    return objectCache;
  }

  public Map<Token, Path<? extends AbstractPoint>> getOwnedPaths(
      PlayerView view, boolean ownedByCurrentPlayer) {
    final var result = new HashMap<Token, Path<? extends AbstractPoint>>();

    Set<SelectionSet> movementSet =
        ownedByCurrentPlayer
            ? renderer.getOwnedMovementSet(view)
            : renderer.getUnOwnedMovementSet(view);
    if (movementSet.isEmpty()) {
      return Collections.emptyMap();
    }

    for (SelectionSet set : movementSet) {
      Token keyToken = zone.getToken(set.getKeyToken());
      if (keyToken == null) {
        // It was removed ?
        // selectionSetMap.remove(set.getKeyToken());
        continue;
      }
      // Hide the hidden layer
      if (!keyToken.getLayer().isVisibleToPlayers() && !view.isGMView()) {
        continue;
      }

      ZoneWalker walker = set.getWalker();

      // Show path only on the key token on token layer that are visible to the owner or gm while
      // fow and vision is on
      if (keyToken.getLayer().supportsWalker()) {
        final Path<? extends AbstractPoint> path =
            walker != null ? walker.getPath() : set.getGridlessPath();
        result.put(keyToken, path);
      }
    }

    return result;
  }

  public List<MovementResult> getMovingTokens(PlayerView view, boolean ownedByCurrentPlayer) {
    final var result = new ArrayList<MovementResult>();

    // Regardless of vision settings, no need to render beyond the fog.
    final var zoneView = renderer.getZoneView();
    Area clearArea = null;
    if (!view.isGMView()) {
      if (zone.hasFog() && zoneView.isUsingVision()) {
        clearArea = new Area(zoneView.getExposedArea(view));
        clearArea.intersect(zoneView.getVisibleArea(view));
      } else if (zone.hasFog()) {
        clearArea = zoneView.getExposedArea(view);
      } else if (zoneView.isUsingVision()) {
        clearArea = zoneView.getVisibleArea(view);
      }
    }

    Set<SelectionSet> movementSet =
        ownedByCurrentPlayer
            ? renderer.getOwnedMovementSet(view)
            : renderer.getUnOwnedMovementSet(view);
    if (movementSet.isEmpty()) {
      return Collections.emptyList();
    }

    double scale = renderer.getScale();
    for (SelectionSet set : movementSet) {
      Token keyToken = zone.getToken(set.getKeyToken());
      if (keyToken == null) {
        // It was removed ?
        continue;
      }
      // Hide the hidden layer
      if (!keyToken.getLayer().isVisibleToPlayers() && !view.isGMView()) {
        continue;
      }
      ZoneWalker walker = set.getWalker();

      // TODO A different method for ShowAiDebugging.

      for (GUID tokenGUID : set.getTokens()) {
        Token token = zone.getToken(tokenGUID);

        // Perhaps deleted?
        if (token == null) {
          continue;
        }

        // Don't bother if it's not visible
        if (!token.isVisible() && !view.isGMView()) {
          continue;
        }

        // ... or if it's visible only to the owner and that's not us!
        final boolean isOwner = view.isGMView() || AppUtil.playerOwns(token);
        if (token.isVisibleOnlyToOwner() && !isOwner) {
          continue;
        }

        Rectangle footprintBounds = token.getBounds(zone);

        // get token image, using image table if present
        var image = getTokenImage(token);

        // on the iso plane
        if (token.isFlippedIso()) {
          // TODO Image caching, or come up with an alternative.
          //  I am also confused, where does the flipping happen?
          //  I am even more confused - why would we modify the token as part of rendering?
          //      if (flipIsoImageMap.get(token) == null) {
          image = IsometricGrid.isoImage(image);
          //      } else {
          //        workImage = flipIsoImageMap.get(token);
          //      }
          token.setHeight(image.getHeight());
          token.setWidth(image.getWidth());
          footprintBounds = token.getBounds(zone);
        }

        if (token.getShape() == Token.TokenShape.FIGURE) {
          double th = token.getHeight() * (double) footprintBounds.width / token.getWidth();
          final double iso_ho = footprintBounds.height - th;
          footprintBounds =
              new Rectangle(
                  footprintBounds.x,
                  footprintBounds.y - (int) iso_ho,
                  footprintBounds.width,
                  (int) th);
        }

        footprintBounds.x += set.offsetX;
        footprintBounds.y += set.offsetY;

        // TODO This dependence on scaled with etc would probably not be needed if we operated in
        //  world space.

        final var labels = new ArrayList<Label>();
        ScreenPoint newScreenPoint =
            ScreenPoint.fromZonePoint(renderer, footprintBounds.x, footprintBounds.y);
        // Tokens are centered on the image center point
        int x = (int) (newScreenPoint.x);
        int y = (int) (newScreenPoint.y);
        int scaledWidth = (int) (footprintBounds.width * scale);
        int scaledHeight = (int) (footprintBounds.height * scale);

        // Other details.
        // If the token is visible on the screen it will be in the location cache
        if (token == keyToken && (isOwner || shouldShowMovementLabels(token, set, clearArea))
        // TODO don't depend on a location cache for this nonsense.
        // && tokenLocationCache.containsKey(token)
        ) {
          var labelY = y + 10 + scaledHeight;
          var labelX = x + scaledWidth / 2;

          if (token.getLayer().supportsWalker() && AppState.getShowMovementMeasurements()) {
            double distanceTraveled = calculateTraveledDistance(set);
            if (distanceTraveled >= 0) {
              String distance = NumberFormat.getInstance().format(distanceTraveled);
              labels.add(new Label(distance, labelX, labelY));
              labelY += 20;
            }
          }
          if (set.getPlayerId() != null && set.getPlayerId().length() >= 1) {
            labels.add(new Label(set.getPlayerId(), labelX, labelY));
          }
        }

        result.add(new MovementResult(clearArea, token, footprintBounds, image, labels));
      }
    }

    return result;
  }

  // TODO clearArea is not properly part of the result (the renderer should decide its own clipping)
  //  but in this case it determines whether labels _should_ be rendered, not just whether they are
  //  clipped.
  public record MovementResult(
      Area clearArea, Token token, Rectangle footprint, BufferedImage image, List<Label> labels) {}

  public record Label(String text, int x, int y) {}

  /**
   * Checks to see if token has an image table and references that if the token has a facing
   * otherwise uses basic image
   *
   * @param token the token to get the image from.
   * @return BufferedImage
   */
  private BufferedImage getTokenImage(Token token) {
    BufferedImage image = null;
    // Get the basic image
    if (token.getHasImageTable() && token.hasFacing() && token.getImageTableName() != null) {
      LookupTable lookupTable =
          MapTool.getCampaign().getLookupTableMap().get(token.getImageTableName());
      if (lookupTable != null) {
        try {
          LookupTable.LookupEntry result = lookupTable.getLookup(token.getFacing().toString());
          if (result != null) {
            image = ImageManager.getImage(result.getImageId(), renderer);
          }
        } catch (ParserException p) {
          // do nothing
        }
      }
    }

    if (image == null) {
      // Adds this as observer so we can repaint once the image is ready. Fixes #1700.
      image = ImageManager.getImage(token.getImageAssetId(), renderer);
    }
    return image;
  }

  private boolean shouldShowMovementLabels(Token token, SelectionSet set, Area clearArea) {
    Rectangle tokenRectangle;
    if (set.getWalker() != null) {
      final var path = set.getWalker().getPath();
      if (path.getCellPath().isEmpty()) {
        return false;
      }
      final var lastPoint = path.getCellPath().getLast();
      final var grid = zone.getGrid();
      tokenRectangle = token.getFootprint(grid).getBounds(grid, lastPoint);
    } else {
      final var path = set.getGridlessPath();
      if (path.getCellPath().isEmpty()) {
        return false;
      }
      final var lastPoint = path.getCellPath().getLast();
      Rectangle tokBounds = token.getBounds(zone);
      tokenRectangle = new Rectangle();
      tokenRectangle.setBounds(
          lastPoint.x, lastPoint.y, (int) tokBounds.getWidth(), (int) tokBounds.getHeight());
    }

    return clearArea == null || clearArea.intersects(tokenRectangle);
  }

  private double calculateTraveledDistance(SelectionSet set) {
    ZoneWalker walker = set.getWalker();
    if (walker != null) {
      // This wouldn't be true unless token.isSnapToGrid() && grid.isPathingSupported()
      return walker.getDistance();
    }

    double distanceTraveled = 0;
    ZonePoint lastPoint = null;
    for (ZonePoint zp : set.getGridlessPath().getCellPath()) {
      if (lastPoint == null) {
        lastPoint = zp;
        continue;
      }
      int a = lastPoint.x - zp.x;
      int b = lastPoint.y - zp.y;
      distanceTraveled += Math.hypot(a, b);
      lastPoint = zp;
    }
    distanceTraveled /= zone.getGrid().getSize(); // Number of "cells"
    distanceTraveled *= zone.getUnitsPerCell(); // "actual" distance traveled
    return distanceTraveled;
  }
}
