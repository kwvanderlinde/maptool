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
package net.rptools.maptool.model.drawing;

import com.google.protobuf.StringValue;
import java.awt.Rectangle;
import java.awt.geom.Area;
import javax.annotation.Nonnull;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.server.proto.drawing.DrawableDto;
import net.rptools.maptool.server.proto.drawing.RadiusCellTemplateDto;

/**
 * The radius template draws a highlight over all the squares effected from a specific spine.
 *
 * @author naciron
 */
public class RadiusCellTemplate extends AbstractTemplate {
  public RadiusCellTemplate() {}

  public RadiusCellTemplate(GUID id) {
    super(id);
  }

  public RadiusCellTemplate(RadiusCellTemplate other) {
    super(other);
  }

  @Override
  public Drawable copy() {
    return new RadiusCellTemplate(this);
  }

  /*---------------------------------------------------------------------------------------------
   * Overridden AbstractTemplate Methods
   *-------------------------------------------------------------------------------------------*/

  /**
   * Get the multiplier in the X direction.
   *
   * @param q Quadrant being accessed
   * @return -1 for west and +1 for east
   */
  protected int getXMult(Quadrant q) {
    return ((q == Quadrant.NORTH_WEST || q == Quadrant.SOUTH_WEST) ? -1 : +1);
  }

  /**
   * Get the multiplier in the X direction.
   *
   * @param q Quadrant being accessed
   * @return -1 for north and +1 for south
   */
  protected int getYMult(Quadrant q) {
    return ((q == Quadrant.NORTH_WEST || q == Quadrant.NORTH_EAST) ? -1 : +1);
  }

  /*---------------------------------------------------------------------------------------------
   * Drawable Interface Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public Rectangle getBounds(Zone zone) {
    if (zone == null) {
      return new Rectangle();
    }
    int gridSize = zone.getGrid().getSize();
    int quadrantSize = getRadius() * gridSize + BOUNDS_PADDING;
    ZonePoint vertex = getVertex();

    var x = vertex.x - quadrantSize + gridSize;
    var y = vertex.y - quadrantSize + gridSize;
    var w = quadrantSize * 2 - gridSize;
    var h = quadrantSize * 2 - gridSize;

    return new Rectangle(x, y, w, h);
  }

  /**
   * Get the distance to a specific coordinate.
   *
   * @param x delta-X of the coordinate.
   * @param y delta-Y of the coordinate.
   * @return Number of cells to the passed coordinate.
   */
  @Override
  public int getDistance(int x, int y) {
    int distance;
    if (x > y) distance = x + (y / 2) + 1 + (y & 1);
    else distance = y + (x / 2) + 1 + (x & 1);
    return distance;
  }

  @Override
  public @Nonnull Area getArea(Zone zone) {
    if (zone == null) {
      return new Area();
    }
    int gridSize = zone.getGrid().getSize();
    int r = getRadius();
    ZonePoint vertex = getVertex();
    Area result = new Area();
    for (int x = 0; x < r; x++) {
      for (int y = 0; y < r; y++) {
        for (Quadrant q : Quadrant.values()) {
          int xShift = (getXMult(q) - 1) / 2;
          int yShift = (getYMult(q) - 1) / 2;
          int distance = getDistance(x - xShift, y - yShift);
          if (distance > r) {
            continue;
          }
          int xOff = x * gridSize;
          int yOff = y * gridSize;
          // Add all four quadrants

          int rx = vertex.x + getXMult(q) * xOff + xShift * gridSize;
          int ry = vertex.y + getYMult(q) * yOff + yShift * gridSize;
          result.add(new Area(new Rectangle(rx, ry, gridSize, gridSize)));
        }
      }
    }
    return result;
  }

  @Override
  public DrawableDto toDto() {
    var dto = RadiusCellTemplateDto.newBuilder();
    dto.setId(getId().toString())
        .setLayer(getLayer().name())
        .setRadius(getRadius())
        .setVertex(getVertex().toDto());

    if (getName() != null) dto.setName(StringValue.of(getName()));

    return DrawableDto.newBuilder().setRadiusCellTemplate(dto).build();
  }

  public static RadiusCellTemplate fromDto(RadiusCellTemplateDto dto) {
    var id = GUID.valueOf(dto.getId());
    var drawable = new RadiusCellTemplate(id);
    drawable.setRadius(dto.getRadius());
    var vertex = dto.getVertex();
    drawable.setVertex(new ZonePoint(vertex.getX(), vertex.getY()));
    if (dto.hasName()) {
      drawable.setName(dto.getName().getValue());
    }
    drawable.setLayer(Zone.Layer.valueOf(dto.getLayer()));
    return drawable;
  }
}
