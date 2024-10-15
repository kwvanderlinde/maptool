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
import net.rptools.maptool.server.proto.drawing.BurstTemplateDto;
import net.rptools.maptool.server.proto.drawing.DrawableDto;

/**
 * Create and paint a donut burst
 *
 * @author Jay
 */
public class BurstTemplate extends RadiusTemplate {
  /*---------------------------------------------------------------------------------------------
   * Instance Variables
   *-------------------------------------------------------------------------------------------*/

  public BurstTemplate() {}

  public BurstTemplate(GUID id) {
    super(id);
  }

  public BurstTemplate(BurstTemplate other) {
    super(other);
  }

  /*---------------------------------------------------------------------------------------------
   * Instance Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public Drawable copy() {
    return new BurstTemplate(this);
  }

  private Rectangle makeVertexShape(Zone zone) {
    int gridSize = zone.getGrid().getSize();
    return new Rectangle(getVertex().x, getVertex().y, gridSize, gridSize);
  }

  private Rectangle makeShape(Zone zone) {
    int gridSize = zone.getGrid().getSize();
    return new Rectangle(
        getVertex().x - getRadius() * gridSize,
        getVertex().y - getRadius() * gridSize,
        (getRadius() * 2 + 1) * gridSize,
        (getRadius() * 2 + 1) * gridSize);
  }

  /*---------------------------------------------------------------------------------------------
   * Overridden *Template Methods
   *-------------------------------------------------------------------------------------------*/

  /**
   * @see net.rptools.maptool.model.drawing.AbstractTemplate#getDistance(int, int)
   */
  @Override
  public int getDistance(int x, int y) {
    return Math.max(x, y);
  }

  @Override
  public Rectangle getBounds(Zone zone) {
    Rectangle r = makeShape(zone);
    // We don't know pen width, so add some padding to account for it
    r.x -= 5;
    r.y -= 5;
    r.width += 10;
    r.height += 10;

    return r;
  }

  /*---------------------------------------------------------------------------------------------
   * Overridden AbstractDrawing Methods
   *-------------------------------------------------------------------------------------------*/

  @Override
  public @Nonnull Area getArea(Zone zone) {
    return new Area(makeShape(zone));
  }

  @Override
  public DrawableDto toDto() {
    var dto = BurstTemplateDto.newBuilder();
    dto.setId(getId().toString())
        .setLayer(getLayer().name())
        .setRadius(getRadius())
        .setVertex(getVertex().toDto());

    if (getName() != null) dto.setName(StringValue.of(getName()));

    return DrawableDto.newBuilder().setBurstTemplate(dto).build();
  }

  public static BurstTemplate fromDto(BurstTemplateDto dto) {
    var id = GUID.valueOf(dto.getId());
    var drawable = new BurstTemplate(id);
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
