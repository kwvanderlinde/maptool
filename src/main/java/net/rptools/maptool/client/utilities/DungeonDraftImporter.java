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
package net.rptools.maptool.client.utilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jidesoft.utils.Base64;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.mappropertiesdialog.MapPropertiesDialog;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.GridFactory;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.Zone.Layer;
import net.rptools.maptool.model.ZoneFactory;
import net.rptools.maptool.model.topology.WallTopology;
import org.apache.commons.io.FilenameUtils;

/** Class for importing Dungeondraft Universal VTT export format. */
public class DungeonDraftImporter {

  /** The format / version of the dungeondraft VTT format. */
  public static final String VTT_FIELD_FORMAT = "format";

  /** The resolution section of the dungeondraft vtt map. */
  public static final String VTT_FIELD_RESOLUTION = "resolution";

  /** The map origin section of the dungeondraft vtt map. */
  public static final String VTT_FIELD_MAP_ORIGIN = "map_origin";

  /** The number of pixels per grid cell on the vtt map. */
  public static final String VTT_FIELD_PIXELS_PER_GRID = "pixels_per_grid";

  /** The image of the map in the vtt file. */
  public static final String VTT_FIELD_IMAGE = "image";

  /** The file containing the dungeondraft VTT export. */
  private final File dungeonDraftFile;

  /** Width of the Light source icon. */
  private static final int LIGHT_WIDTH = 20;

  /** Height of the Light source icon. */
  private static final int LIGHT_HEIGHT = 20;

  private static final double POINT_TOLERANCE = 1e-9;

  /** Asset to use to represent Light sources. */
  private static final Asset lightSourceAsset =
      Asset.createImageAsset("LightSource", RessourceManager.getImage(Images.LIGHT_SOURCE));

  static {
    AssetManager.putAsset(lightSourceAsset);
  }

  /**
   * Creates a new {@code DungeonDraftImporter} object.
   *
   * @param ddFile the file to import.
   */
  public DungeonDraftImporter(File ddFile) {
    dungeonDraftFile = ddFile;
  }

  /**
   * Import the dungeondraft file and create a new {@link Zone} which is added to the campaign.
   *
   * @throws IOException if an error occurs during the import.
   */
  public void importVTT() throws IOException {
    JsonObject ddvtt;
    double dd2vtt_format;
    final AffineTransform at = new AffineTransform();

    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(dungeonDraftFile))) {
      ddvtt = JsonParser.parseReader(reader).getAsJsonObject();
    }

    // Make sure this is a file format we understand
    if (!ddvtt.has(VTT_FIELD_FORMAT)) {
      MapTool.showError("dungeondraft.import.missingFormatField");
      return;
    }

    // Will work if format value is a double or a string
    dd2vtt_format = ddvtt.get(VTT_FIELD_FORMAT).getAsDouble();
    if (dd2vtt_format != 0.2 && dd2vtt_format != 0.3) {
      MapTool.showError(I18N.getText("dungeondraft.import.unknownFormat", dd2vtt_format));
      return;
    }

    // The resolution object has map_origin, map_size in grid cells and pixels_per_grid information.
    if (!ddvtt.has(VTT_FIELD_RESOLUTION)) {
      MapTool.showError("dungeondraft.import.missingResolution");
      return;
    }
    JsonObject resolution = ddvtt.get(VTT_FIELD_RESOLUTION).getAsJsonObject();

    if (!resolution.has(VTT_FIELD_PIXELS_PER_GRID)) {
      MapTool.showError("dungeondraft.import.missingPixelsPerGrid");
      return;
    }
    int pixelsPerCell = resolution.get(VTT_FIELD_PIXELS_PER_GRID).getAsInt();

    if (!ddvtt.has(VTT_FIELD_IMAGE)) {
      MapTool.showError("dungeondraft.import.image");
      return;
    }
    String imageString = ddvtt.get(VTT_FIELD_IMAGE).getAsString();

    byte[] imageBytes = Base64.decode(imageString);
    String mapName = FilenameUtils.removeExtension(dungeonDraftFile.getName());
    Asset asset = Asset.createImageAsset(mapName, imageBytes);
    AssetManager.putAsset(asset);

    Zone zone = ZoneFactory.createZone();
    zone.setPlayerAlias(mapName);

    MapPropertiesDialog dialog =
        MapPropertiesDialog.createMapPropertiesImportDialog(MapTool.getFrame());
    dialog.setZone(zone);
    dialog.forcePixelsPerCell(pixelsPerCell);
    dialog.forceGridType(GridFactory.SQUARE);
    dialog.forceMap(asset);
    dialog.setVisible(true);
    if (dialog.getStatus() != MapPropertiesDialog.Status.OK) {
      return;
    }

    /*
     * If the top or left sides of the map get cropped off, all the LOS points will need to be
     * adjusted.
     */
    if (resolution.has(VTT_FIELD_MAP_ORIGIN)) {
      JsonObject origin = resolution.get(VTT_FIELD_MAP_ORIGIN).getAsJsonObject();
      double origin_x = origin.get("x").getAsDouble() * -1 * pixelsPerCell;
      double origin_y = origin.get("y").getAsDouble() * -1 * pixelsPerCell;
      if (origin_x != 0.0 || origin_y != 0.0) {
        at.translate(origin_x, origin_y);
        // if the map was not cropped on the grid fix the grid offset.
        zone.getGrid()
            .setOffset((int) (origin_x % pixelsPerCell), (int) (origin_y % pixelsPerCell));
      }
    }

    // Handle Walls
    var walls = new WallTopology();
    JsonArray vbl = ddvtt.getAsJsonArray("line_of_sight");
    if (vbl != null) {
      vbl.forEach(v -> addWalls(v.getAsJsonArray(), pixelsPerCell, at, walls));
    }

    // Handle Objects - added with Dungeondraft 1.0.2.1
    JsonArray objVBL = ddvtt.getAsJsonArray("objects_line_of_sight");
    if (objVBL != null) {
      objVBL.forEach(
          v -> {
            Area vblArea = new Area(getVBLPath(v.getAsJsonArray(), pixelsPerCell));
            if (!at.isIdentity()) {
              vblArea.transform(at);
            }
            zone.updateLegacyTopology(vblArea, false, Zone.TopologyType.HILL_VBL);
            zone.updateLegacyTopology(vblArea, false, Zone.TopologyType.PIT_VBL);
          });
    }

    // Handle Doors
    JsonArray doors = ddvtt.getAsJsonArray("portals");
    if (doors != null) {
      doors.forEach(
          d -> {
            JsonObject jobj = d.getAsJsonObject();
            boolean isClosed;
            if (jobj.has("closed")) {
              isClosed = jobj.get("closed").getAsBoolean();
            } else {
              isClosed = true;
            }

            if (isClosed) {
              JsonArray bounds = jobj.get("bounds").getAsJsonArray();
              addWalls(bounds, pixelsPerCell, at, walls);
            }
          });
    }

    zone.replaceWalls(walls);

    JsonArray lights = ddvtt.getAsJsonArray("lights");
    if (lights != null && lights.size() > 0) {
      placeLights(zone, lights, pixelsPerCell);
    }

    // If everything has been successful, we can add the zone to the campaign.
    MapTool.addZone(zone);
  }

  /**
   * Place the tokens for the light sources on the map.
   *
   * @param zone The new {@link Zone} that was created.
   * @param lights The {@link JsonArray} containing the lights.
   * @param pixelsPerCell The number of pixels per grid cell on the map.
   */
  private void placeLights(Zone zone, JsonArray lights, double pixelsPerCell) {
    int lightNo = 1;
    boolean ignoredLights = false;
    for (JsonElement ele : lights) {
      JsonObject position = ele.getAsJsonObject().getAsJsonObject("position");
      if (position.has("x") && position.has("y")) {
        Token lightToken = new Token("light-" + lightNo, lightSourceAsset.getMD5Key());
        lightToken.setLayer(Layer.OBJECT);
        lightToken.setVisible(false);
        lightToken.setSnapToGrid(false);
        lightToken.setSnapToScale(false);
        lightToken.setWidth(LIGHT_WIDTH);
        lightToken.setHeight(LIGHT_HEIGHT);

        lightToken.setX((int) (position.get("x").getAsDouble() * pixelsPerCell) - LIGHT_WIDTH / 2);
        lightToken.setY((int) (position.get("y").getAsDouble() * pixelsPerCell) - LIGHT_HEIGHT / 2);

        JsonObject lightValues = new JsonObject();
        lightValues.addProperty(
            "range", ele.getAsJsonObject().getAsJsonPrimitive("range").getAsBigDecimal());
        lightValues.addProperty(
            "intensity", ele.getAsJsonObject().getAsJsonPrimitive("intensity").getAsBigDecimal());
        lightValues.addProperty(
            "color", ele.getAsJsonObject().getAsJsonPrimitive("color").getAsString());
        lightValues.addProperty(
            "shadows",
            ele.getAsJsonObject().getAsJsonPrimitive("shadows").getAsBoolean()
                ? BigDecimal.ONE
                : BigDecimal.ZERO);
        lightToken.setGMNotes(lightValues.toString());

        zone.putToken(lightToken);
        lightNo++;
      } else {
        ignoredLights = true;
      }
    }

    if (ignoredLights) {
      MapTool.showInformation("dungeondraft.import.lightsIgnored");
    }
  }

  /**
   * Returns a {@link Path2D} for the line of sight / portal array in the dungeondraft VTT file.
   *
   * @param vblArray the array to create the VBL for.
   * @param pixelsPerCell the number of pixels per grid cell.
   * @return a {@link Path2D} for the VBL.
   */
  private Path2D getVBLPath(JsonArray vblArray, double pixelsPerCell) {
    boolean first = true;
    Path2D path = new GeneralPath();
    for (JsonElement element : vblArray) {
      Point2D point = vblPoint(element.getAsJsonObject(), pixelsPerCell);
      if (first) {
        path.moveTo(point.getX(), point.getY());
        first = false;
      } else {
        path.lineTo(point.getX(), point.getY());
      }
    }

    return path;
  }

  private Point2D vblPoint(JsonObject pointJson, double pixelsPerCell) {
    return new Point2D.Double(
        pointJson.get("x").getAsDouble() * pixelsPerCell,
        pointJson.get("y").getAsDouble() * pixelsPerCell);
  }

  /**
   * Returns a {@link Path2D} for the line of sight / portal array in the dungeondraft VTT file.
   *
   * @param vblArray the array to create the VBL for.
   * @param pixelsPerCell the number of pixels per grid cell.
   */
  private void addWalls(
      JsonArray vblArray, double pixelsPerCell, AffineTransform transform, WallTopology walls) {
    if (vblArray.size() < 2) {
      // We don't support lone points.
      return;
    }

    var startPoint =
        transform.transform(vblPoint(vblArray.get(0).getAsJsonObject(), pixelsPerCell), null);
    walls.string(
        startPoint,
        builder -> {
          var previousPoint = startPoint;

          for (var point : vblArray.asList().subList(1, vblArray.size())) {
            var currentPoint =
                transform.transform(vblPoint(point.getAsJsonObject(), pixelsPerCell), null);

            // Dungeondraft has a bad habit of introducing redundant points in generated maps. Let's
            // filter those out.
            if (currentPoint.distance(previousPoint) < POINT_TOLERANCE) {
              continue;
            }

            builder.push(currentPoint);
            previousPoint = currentPoint;
          }
        });
  }
}
