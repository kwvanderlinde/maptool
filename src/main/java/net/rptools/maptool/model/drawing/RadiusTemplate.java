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
import net.rptools.maptool.server.proto.drawing.RadiusTemplateDto;

/**
 * The radius template draws a highlight over all the squares effected from a specific spine.
 *
 * @author jgorrell
 * @version $Revision: 5945 $ $Date: 2013-06-03 04:35:50 +0930 (Mon, 03 Jun 2013) $ $Author:
 *     azhrei_fje $
 */
public class RadiusTemplate extends AbstractTemplate {

  public RadiusTemplate() {}

  public RadiusTemplate(GUID id) {
    super(id);
  }

  public RadiusTemplate(RadiusTemplate other) {
    super(other);
  }

  @Override
  public Drawable copy() {
    return new RadiusTemplate(this);
  }

  /*---------------------------------------------------------------------------------------------
   * Overridden AbstractTemplate Methods
   *-------------------------------------------------------------------------------------------*/

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
    return new Rectangle(
        vertex.x - quadrantSize, vertex.y - quadrantSize, quadrantSize * 2, quadrantSize * 2);
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
        if (getDistance(x, y) <= r) {
          int xOff = x * gridSize;
          int yOff = y * gridSize;
          // Add all four quadrants
          for (Quadrant q : Quadrant.values()) {
            int rx = vertex.x + getXMult(q) * xOff + ((getXMult(q) - 1) / 2) * gridSize;
            int ry = vertex.y + getYMult(q) * yOff + ((getYMult(q) - 1) / 2) * gridSize;
            result.add(new Area(new Rectangle(rx, ry, gridSize, gridSize)));
          }
        }
      }
    }
    return result;
  }

  @Override
  public DrawableDto toDto() {
    var dto = RadiusTemplateDto.newBuilder();
    dto.setId(getId().toString())
        .setLayer(getLayer().name())
        .setRadius(getRadius())
        .setVertex(getVertex().toDto());

    if (getName() != null) dto.setName(StringValue.of(getName()));

    return DrawableDto.newBuilder().setRadiusTemplate(dto).build();
  }

  public static RadiusTemplate fromDto(RadiusTemplateDto dto) {
    var id = GUID.valueOf(dto.getId());
    var drawable = new RadiusTemplate(id);
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
