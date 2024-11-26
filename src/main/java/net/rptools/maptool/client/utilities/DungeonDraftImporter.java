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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.mappropertiesdialog.MapPropertiesDialog;
import net.rptools.maptool.client.ui.theme.Images;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.GridFactory;
import net.rptools.maptool.model.Light;
import net.rptools.maptool.model.LightSource;
import net.rptools.maptool.model.ShapeType;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.Zone.Layer;
import net.rptools.maptool.model.ZoneFactory;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
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

  /** The width to used for VBL for walls. */
  private static final int WALL_VBL_WIDTH = 3;

  /** The width to used for VBL for doors. */
  private static final int DOOR_VBL_WIDTH = 1;

  /** The width to used for VBL for objects. */
  private static final int OBJECT_VBL_WIDTH = 1;

  /** Stroke to use to create VBL path for walls. */
  private static final BasicStroke WALL_VBL_STROKE =
      new BasicStroke(WALL_VBL_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

  /** Stroke to use to create VBL path for doors. */
  private static final BasicStroke DOOR_VBL_STROKE =
      new BasicStroke(DOOR_VBL_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

  /** Stroke to use to create VBL path for doors. */
  private static final BasicStroke OBJECT_VBL_STROKE =
      new BasicStroke(OBJECT_VBL_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

  /** Width of the Light source icon. */
  private static final int LIGHT_WIDTH = 20;

  /** Height of the Light source icon. */
  private static final int LIGHT_HEIGHT = 20;

  /** Contains environmental details (ambient lighting, baked-in lighting) */
  public static final String VTT_FIELD_ENVIRONMENT = "environment";

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
    boolean do_transform = false;
    AffineTransform at = new AffineTransform();

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

    /**
     * If the top or left sides of the map get cropped off, all the LOS points will need to be
     * adjusted.
     */
    if (resolution.has(VTT_FIELD_MAP_ORIGIN)) {
      JsonObject origin = resolution.get(VTT_FIELD_MAP_ORIGIN).getAsJsonObject();
      double origin_x = origin.get("x").getAsDouble() * -1 * pixelsPerCell;
      double origin_y = origin.get("y").getAsDouble() * -1 * pixelsPerCell;
      if (origin_x != 0.0 || origin_y != 0.0) {
        at.translate(origin_x, origin_y);
        do_transform = true;
        // if the map was not cropped on the grid fix the grid offset.
        zone.getGrid()
            .setOffset((int) (origin_x % pixelsPerCell), (int) (origin_y % pixelsPerCell));
      }
    }

    final boolean finalDo_transform = do_transform;

    // Handle Walls
    JsonArray vbl = ddvtt.getAsJsonArray("line_of_sight");
    if (vbl != null) {
      vbl.forEach(
          v -> {
            Area vblArea =
                new Area(
                    WALL_VBL_STROKE.createStrokedShape(
                        getVBLPath(v.getAsJsonArray(), pixelsPerCell)));
            if (finalDo_transform) vblArea.transform(at);
            zone.updateTopology(vblArea, false, Zone.TopologyType.WALL_VBL);
            zone.updateTopology(vblArea, false, Zone.TopologyType.MBL);
          });
    }

    // Handle Objects - added with Dungeondraft 1.0.2.1
    JsonArray objVBL = ddvtt.getAsJsonArray("objects_line_of_sight");
    if (objVBL != null) {
      objVBL.forEach(
          v -> {
            Area vblArea = new Area(getVBLPath(v.getAsJsonArray(), pixelsPerCell));
            if (finalDo_transform) vblArea.transform(at);
            zone.updateTopology(vblArea, false, Zone.TopologyType.HILL_VBL);
            zone.updateTopology(vblArea, false, Zone.TopologyType.PIT_VBL);
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

              Area vblArea =
                  new Area(DOOR_VBL_STROKE.createStrokedShape(getVBLPath(bounds, pixelsPerCell)));
              if (finalDo_transform) vblArea.transform(at);
              zone.updateTopology(vblArea, false, Zone.TopologyType.WALL_VBL);
              zone.updateTopology(vblArea, false, Zone.TopologyType.MBL);
            }
          });
    }

    boolean bakedLighting = false;
    if (ddvtt.has(VTT_FIELD_ENVIRONMENT)) {
      var environment = ddvtt.getAsJsonObject(VTT_FIELD_ENVIRONMENT);
      var bakedLightingMember = environment.get("baked_lighting");
      if (bakedLightingMember != null) {
        bakedLighting = bakedLightingMember.getAsBoolean();
      }
    }

    JsonArray lights = ddvtt.getAsJsonArray("lights");
    if (lights != null && lights.size() > 0) {
      placeLights(zone, lights, pixelsPerCell, bakedLighting);
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
   * @param bakedLighting If {@code true}, define and attach unique lights to each light token.
   */
  private void placeLights(
      Zone zone, JsonArray lights, double pixelsPerCell, boolean bakedLighting) {
    int lightNo = 1;
    boolean ignoredLights = false;
    for (JsonElement ele : lights) {
      var lightJson = ele.getAsJsonObject();

      JsonObject position = lightJson.getAsJsonObject("position");
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

        // If lighting is baked in, produce a clear light.
        Color color;
        if (bakedLighting) {
          color = null;
        } else {
          color =
              new Color(
                  Integer.parseUnsignedInt(
                      lightJson.getAsJsonPrimitive("color").getAsString(), 16));
        }

        var light =
            new Light(
                ShapeType.CIRCLE,
                0.,
                // Range is measured in cells.
                lightJson.getAsJsonPrimitive("range").getAsDouble() * zone.getUnitsPerCell(),
                0.,
                0.,
                color == null ? null : new DrawableColorPaint(color),
                100,
                false,
                false);
        var lightSource =
            LightSource.createRegular(
                "uvtt-imported",
                new GUID(),
                LightSource.Type.NORMAL,
                false,
                // "shadows" means whether the light respects light blocking.
                !lightJson.getAsJsonPrimitive("shadows").getAsBoolean(),
                List.of(light));
        // Install the light source...
        lightToken.addUniqueLightSource(lightSource);
        // ... and activate it immediately.
        lightToken.addLightSource(lightSource.getId());

        lightToken.setGMNotes(ele.toString());

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
      JsonObject point = element.getAsJsonObject();
      double x = point.get("x").getAsDouble() * pixelsPerCell;
      double y = point.get("y").getAsDouble() * pixelsPerCell;
      if (first) {
        path.moveTo(x, y);
        first = false;
      } else {
        path.lineTo(x, y);
      }
    }

    return path;
  }
}
