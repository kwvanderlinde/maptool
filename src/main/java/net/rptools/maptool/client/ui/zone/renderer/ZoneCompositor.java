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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.*;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.client.DeveloperOptions;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ScreenPoint;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderableImage;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderablePath;
import net.rptools.maptool.client.ui.zone.renderer.instructions.RenderableText;
import net.rptools.maptool.client.walker.ZoneWalker;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.IsometricGrid;
import net.rptools.maptool.model.LookupTable;
import net.rptools.maptool.model.Path;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.TokenFootprint;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.util.ImageManager;
import net.rptools.parser.ParserException;
import org.javatuples.Pair;

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

  public Map<Token, RenderablePath> getOwnedPaths(PlayerView view, boolean ownedByCurrentPlayer) {
    final var result = new HashMap<Token, RenderablePath>();

    Set<SelectionSet> movementSet =
        ownedByCurrentPlayer
            ? renderer.getOwnedMovementSet(view)
            : renderer.getUnOwnedMovementSet(view);
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

      final var tokenFootprint = keyToken.getFootprint(zone.getGrid());

      // Show path only on the key token on token layer that are visible to the owner or gm while
      // fow and vision is on
      if (keyToken.getLayer().supportsWalker()) {
        ZoneWalker walker = set.getWalker();
        if (walker != null) {
          // Gridded path
          var path = walker.getPath();
          result.put(keyToken, griddedPathToRenderable(path, tokenFootprint));
        } else {
          // Gridless path.
          var path = set.getGridlessPath();
          result.put(keyToken, gridlessPathToRenderable(path, tokenFootprint));
        }
      }
    }

    return result;
  }

  public RenderablePath.CellPath griddedPathToRenderable(
      Path<CellPoint> path, TokenFootprint tokenFootprint) {
    final var grid = zone.getGrid();
    double distanceTextFontSize = 12. * grid.getSize() / 50.;
    final var useDistanceWithoutTerrain = DeveloperOptions.Toggle.ShowAiDebugging.isEnabled();
    if (useDistanceWithoutTerrain) {
      distanceTextFontSize *= 0.75;
    }
    final var distanceTextFont = new Font(Font.DIALOG, Font.BOLD, (int) distanceTextFontSize);

    var occupiedCells = new HashMap<CellPoint, RenderablePath.PathCell>();
    var pathPoints = new ArrayList<Point2D>();
    for (var cellPoint : path.getCellPath()) {
      for (var occupiedCellPoint : tokenFootprint.getOccupiedCells(cellPoint)) {
        occupiedCells.computeIfAbsent(
            occupiedCellPoint,
            c -> {
              ZonePoint zonePoint = grid.convert(c);
              return new RenderablePath.PathCell(
                  new Rectangle2D.Double(
                      zonePoint.x + grid.getCellOffset().width,
                      zonePoint.y + grid.getCellOffset().height,
                      grid.getCellWidth(),
                      grid.getCellHeight()),
                  null,
                  false);
            });
      }

      var pathCell = occupiedCells.get(cellPoint);
      if (pathCell == null) {
        // Should not happen since it should have been added as an occupied cell.
        continue;
      }

      // Font size of 12 at grid size 50 is default
      RenderablePath.DistanceText distanceText = null;
      if (AppState.getShowMovementMeasurements()) {
        var distance = cellPoint.getDistanceTraveled(zone);
        var distanceWithoutTerrain = cellPoint.getDistanceTraveledWithoutTerrain();

        var text = NumberFormat.getInstance().format(distance);
        if (useDistanceWithoutTerrain) {
          text += " (" + NumberFormat.getInstance().format(distanceWithoutTerrain) + ")";
        }

        // Padding is 7 pixels at 100% zoom and grid size of 50.
        var textPadding = 0.14 * grid.getSize();
        var textPosition = new Point2D.Double(textPadding, textPadding);
        if (grid.isHex()) {
          textPosition.x += grid.getCellWidth() / 10.;
          textPosition.y += grid.getCellHeight() / 10.;
        }
        distanceText = new RenderablePath.DistanceText(text, textPosition);
      }

      var isFirstOrLast =
          pathPoints.isEmpty() || pathPoints.size() >= path.getCellPath().size() - 1;

      pathCell.distanceText = distanceText;

      pathPoints.add(
          new Point2D.Double(pathCell.bounds.getCenterX(), pathCell.bounds.getCenterY()));
    }

    // Add in waypoint information, but exclude the first and last since those are
    // waypoints according to the user.
    var waypointList = path.getWayPointList();
    if (waypointList.size() > 2) {
      waypointList = waypointList.subList(1, waypointList.size() - 1);
      for (var waypoint : waypointList) {
        var pathCell = occupiedCells.get(waypoint);
        if (pathCell == null) {
          // Should not happen, but just in case.
          continue;
        }

        pathCell.isWaypoint = true;
      }
    }

    return new RenderablePath.CellPath(
        grid.getCellHighlight(),
        RessourceManager.getImage(Images.ZONE_RENDERER_CELL_WAYPOINT),
        0.333,
        distanceTextFont,
        List.copyOf(occupiedCells.values()),
        pathPoints);
  }

  public RenderablePath.PointPath gridlessPathToRenderable(
      Path<ZonePoint> path, TokenFootprint tokenFootprint) {
    var points = new ArrayList<Point2D>();
    var waypoints = new ArrayList<Point2D>();

    var grid = zone.getGrid();
    var footprintBounds = tokenFootprint.getBounds(grid);

    for (var zp : path.getCellPath()) {
      var point =
          new Point2D.Double(
              zp.x + (footprintBounds.width / 2.) * tokenFootprint.getScale(),
              zp.y + (footprintBounds.height / 2.) * tokenFootprint.getScale());
      points.add(point);
    }

    var waypointList = path.getWayPointList();
    // We don't want to render the first and last point since those are not "waypoints"
    // according to the user
    if (waypointList.size() > 2) {
      for (var zp : waypointList.subList(1, waypointList.size() - 1)) {
        var point =
            new Point2D.Double(
                zp.x + (footprintBounds.width / 2.) * tokenFootprint.getScale(),
                zp.y + (footprintBounds.height / 2.) * tokenFootprint.getScale());
        waypoints.add(point);
      }
    }

    return new RenderablePath.PointPath(
        RessourceManager.getImage(Images.ZONE_RENDERER_CELL_WAYPOINT),
        grid.getCellWidth() * 0.333,
        grid.getCellHeight() * 0.333,
        points,
        waypoints);
  }

  public Pair<List<RenderableImage>, List<RenderableText>> getMovingTokens(
      PlayerView view, boolean ownedByCurrentPlayer) {
    final var result = new ArrayList<RenderableImage>();
    final var labels = new ArrayList<RenderableText>();

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
      return new Pair<>(result, labels);
    }

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

        // Other details.
        // If the token is visible on the screen it will be in the location cache
        if (token == keyToken && (isOwner || shouldShowMovementLabels(token, set, clearArea))
        // TODO don't depend on a location cache for this nonsense. We need some manner of checking
        //  if the label would be on-screen, or at worst, if the token is on-screen.
        // && tokenLocationCache.containsKey(token)
        ) {
          var scale = renderer.getScale();
          ScreenPoint labelScreenPoint =
              ScreenPoint.fromZonePoint(renderer, footprintBounds.x, footprintBounds.y);
          // Tokens are centered on the image center point
          var labelY = labelScreenPoint.y + 10 + footprintBounds.height * scale;
          var labelX = labelScreenPoint.x + footprintBounds.width * scale / 2.;

          if (token.getLayer().supportsWalker() && AppState.getShowMovementMeasurements()) {
            double distanceTraveled = calculateTraveledDistance(set);
            if (distanceTraveled >= 0) {
              String distance = NumberFormat.getInstance().format(distanceTraveled);
              labels.add(new RenderableText(distance, (int) labelX, (int) labelY));
              labelY += 20;
            }
          }
          if (set.getPlayerId() != null && !set.getPlayerId().isEmpty()) {
            labels.add(new RenderableText(set.getPlayerId(), (int) labelX, (int) labelY));
          }
        }

        // region Insanity
        double iso_ho = 0;
        Dimension imgSize = new Dimension(image.getWidth(), image.getHeight());
        if (token.getShape() == Token.TokenShape.FIGURE) {
          double th = token.getHeight() * (double) footprintBounds.width / token.getWidth();
          iso_ho = footprintBounds.height - th;
          footprintBounds =
              new Rectangle(
                  footprintBounds.x,
                  footprintBounds.y - (int) iso_ho,
                  footprintBounds.width,
                  (int) th);
        }
        SwingUtil.constrainTo(imgSize, footprintBounds.width, footprintBounds.height);

        int offsetx = 0;
        int offsety = 0;
        if (token.isSnapToScale()) {
          offsetx =
              (int)
                  (imgSize.width < footprintBounds.width
                      ? (footprintBounds.width - imgSize.width) / 2
                      : 0);
          offsety =
              (int)
                  (imgSize.height < footprintBounds.height
                      ? (footprintBounds.height - imgSize.height) / 2
                      : 0);
        }

        // TODO This dependence on scaled with etc would probably not be needed if we operated in
        //  world space.

        int tx = footprintBounds.x + offsetx;
        int ty = footprintBounds.y + offsety + (int) iso_ho;

        var bounds = new Rectangle2D.Double(tx, ty, imgSize.width, imgSize.height);

        // facing defaults to down, or -90 degrees.
        double rotation = 0.;
        Point2D rotationAnchor =
            new Point2D.Double(
                footprintBounds.width / 2. - token.getAnchor().x - offsetx,
                footprintBounds.height / 2. - token.getAnchor().y - offsety);
        if (token.hasFacing() && token.getShape() == Token.TokenShape.TOP_DOWN) {
          rotation = Math.toRadians(-token.getFacing() - 90);
        }
        if (token.isSnapToScale()) {
          bounds.width = imgSize.width;
          bounds.height = imgSize.height;
        } else {
          if (token.getShape() == Token.TokenShape.FIGURE) {
            var ratio = bounds.width / footprintBounds.width;
            bounds.width *= ratio;
            bounds.height *= ratio;
          } else {
            bounds.width = footprintBounds.width;
            bounds.height = footprintBounds.height;
          }
        }
        // endregion

        result.add(
            new RenderableImage(
                image,
                clearArea,
                rotation,
                rotationAnchor,
                token.isFlippedX(),
                token.isFlippedY(),
                bounds));
      }
    }

    return new Pair<>(result, labels);
  }

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
