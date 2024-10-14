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
import net.rptools.maptool.server.proto.drawing.ConeTemplateDto;
import net.rptools.maptool.server.proto.drawing.DrawableDto;

/**
 * The cone template draws a highlight over all the squares effected from a specific spine. There
 * are 8 different directions from each spine.
 *
 * @author jgorrell
 * @version $Revision: 5945 $ $Date: 2013-06-03 04:35:50 +0930 (Mon, 03 Jun 2013) $ $Author:
 *     azhrei_fje $
 */
public class ConeTemplate extends RadiusTemplate {

  /**
   * The dirction to paint. The ne,se,nw,sw paint a quadrant and the n,w,e,w paint along the spine
   * of the selected vertex. Saved as a string as a hack to get around the hessian library's problem
   * w/ serializing enumerations.
   */
  private String direction = Direction.SOUTH_EAST.name();

  public ConeTemplate() {}

  public ConeTemplate(GUID id) {
    super(id);
  }

  public ConeTemplate(ConeTemplate other) {
    super(other);
    this.direction = other.direction;
  }

  /*---------------------------------------------------------------------------------------------
   * Instance Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public Drawable copy() {
    return new ConeTemplate(this);
  }

  /**
   * Get the direction for this ConeTemplate.
   *
   * @return Returns the current value of direction.
   */
  public Direction getDirection() {
    if (direction == null) return null;
    return Direction.valueOf(direction);
  }

  /**
   * Set the value of direction for this ConeTemplate.
   *
   * @param direction The direction to draw the cone from the center vertex.
   */
  public void setDirection(Direction direction) {
    if (direction != null) this.direction = direction.name();
  }

  /*---------------------------------------------------------------------------------------------
   * Overridden AbstractTemplate Methods
   *-------------------------------------------------------------------------------------------*/

  private boolean withinQuadrant(Quadrant q) {
    Direction dir = getDirection();
    switch (q) {
      case SOUTH_EAST:
        return dir == Direction.SOUTH || dir == Direction.EAST || dir == Direction.SOUTH_EAST;
      case NORTH_EAST:
        return dir == Direction.NORTH || dir == Direction.EAST || dir == Direction.NORTH_EAST;
      case SOUTH_WEST:
        return dir == Direction.SOUTH || dir == Direction.WEST || dir == Direction.SOUTH_WEST;
      case NORTH_WEST:
        return dir == Direction.NORTH || dir == Direction.WEST || dir == Direction.NORTH_WEST;
      default:
        throw new RuntimeException(
            String.format(
                "Quadrant must be SOUTH_EAST, NORTH_EAST, SOUTH_WEST, or NORTH_WEST, was %s", q));
    }
  }

  /*---------------------------------------------------------------------------------------------
   * Drawable Interface Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public Rectangle getBounds(Zone zone) {
    int gridSize = zone.getGrid().getSize();
    int quadrantSize = getRadius() * gridSize + BOUNDS_PADDING;

    // Find the x,y loc
    ZonePoint vertex = getVertex();
    int x = vertex.x;
    if (getDirection() == Direction.NORTH_WEST
        || getDirection() == Direction.WEST
        || getDirection() == Direction.SOUTH_WEST
        || getDirection() == Direction.NORTH
        || getDirection() == Direction.SOUTH) {

      x -= quadrantSize;
    }

    int y = vertex.y;
    if (getDirection() == Direction.NORTH_WEST
        || getDirection() == Direction.NORTH
        || getDirection() == Direction.NORTH_EAST
        || getDirection() == Direction.EAST
        || getDirection() == Direction.WEST) {

      y -= quadrantSize;
    }

    // Find the width,height
    int width = quadrantSize + BOUNDS_PADDING;
    if (getDirection() == Direction.NORTH || getDirection() == Direction.SOUTH)
      width += quadrantSize;
    int height = quadrantSize + BOUNDS_PADDING;
    if (getDirection() == Direction.EAST || getDirection() == Direction.WEST)
      height += quadrantSize;
    return new Rectangle(x, y, width, height);
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
        // spine templates
        if ((getDirection() == Direction.EAST || getDirection() == Direction.WEST) && y > x) {
          continue;
        }
        if ((getDirection() == Direction.NORTH || getDirection() == Direction.SOUTH) && x > y) {
          continue;
        }
        if (getDistance(x, y) > r) {
          continue;
        }

        int xOff = x * gridSize;
        int yOff = y * gridSize;
        for (Quadrant q : Quadrant.values()) {
          if (!withinQuadrant(q)) {
            continue;
          }
          int rx = vertex.x + getXMult(q) * xOff + ((getXMult(q) - 1) / 2) * gridSize;
          int ry = vertex.y + getYMult(q) * yOff + ((getYMult(q) - 1) / 2) * gridSize;
          result.add(new Area(new Rectangle(rx, ry, gridSize, gridSize)));
        }
      }
    }
    return result;
  }

  @Override
  public DrawableDto toDto() {
    var dto = ConeTemplateDto.newBuilder();
    dto.setId(getId().toString())
        .setLayer(getLayer().name())
        .setRadius(getRadius())
        .setVertex(getVertex().toDto())
        .setDirection(getDirection().name());

    if (getName() != null) dto.setName(StringValue.of(getName()));

    return DrawableDto.newBuilder().setConeTemplate(dto).build();
  }

  public static ConeTemplate fromDto(ConeTemplateDto dto) {
    var id = GUID.valueOf(dto.getId());
    var drawable = new ConeTemplate(id);
    drawable.setRadius(dto.getRadius());
    var vertex = dto.getVertex();
    drawable.setVertex(new ZonePoint(vertex.getX(), vertex.getY()));
    drawable.setDirection(AbstractTemplate.Direction.valueOf(dto.getDirection()));
    if (dto.hasName()) {
      drawable.setName(dto.getName().getValue());
    }
    drawable.setLayer(Zone.Layer.valueOf(dto.getLayer()));
    return drawable;
  }
}
