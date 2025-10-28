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

import com.google.common.collect.Iterables;
import com.google.protobuf.StringValue;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntBiFunction;
import net.rptools.maptool.model.CellPoint;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Grid;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;
import net.rptools.maptool.server.proto.drawing.DrawableDto;
import net.rptools.maptool.server.proto.drawing.WallTemplateDto;

/**
 * A template that draws consecutive blocks
 *
 * @author Jay
 */
public class WallTemplate extends LineTemplate {
  /**
   * Set the path vertex, it isn't needed by the wall template but the superclass needs it to paint.
   */
  public WallTemplate() {
    setPathVertex(new ZonePoint(0, 0));
  }

  public WallTemplate(GUID id) {
    super(id);
    setPathVertex(new ZonePoint(0, 0));
  }

  public WallTemplate(WallTemplate other) {
    super(other);
  }

  @Override
  public Drawable copy() {
    return new WallTemplate(this);
  }

  @Override
  public CursorType getCursorType() {
    return CursorType.Cell;
  }

  @Override
  public Shape getBorder(Zone zone) {
    return getBorderAsShape(zone.getGrid());
  }

  @Override
  public int getRadius() {
    return getPath() == null ? 0 : getPath().size();
  }

  @Override
  public void setRadius(int squares) {
    // Do nothing, calculated from path length
  }

  @Override
  public void setVertex(ZonePoint vertex) {
    ZonePoint v = getVertex();
    v.x = vertex.x;
    v.y = vertex.y;
  }

  @Override
  public List<CellPoint> calcPath() {
    return getPath(); // Do nothing, path is set by tool.
  }

  @Override
  public DrawableDto toDto() {
    var dto = WallTemplateDto.newBuilder();
    dto.setId(getId().toString())
        .setLayer(getLayer().name())
        .setRadius(getRadius())
        .setVertex(getVertex().toDto())
        .setMouseSlopeGreater(isMouseSlopeGreater())
        .setPathVertex(getPathVertex().toDto())
        .setDoubleWide(isDoubleWide());

    if (getName() != null) dto.setName(StringValue.of(getName()));

    for (var point : getPath()) dto.addPoints(point.toDto());

    return DrawableDto.newBuilder().setWallTemplate(dto).build();
  }

  public static WallTemplate fromDto(WallTemplateDto dto) {
    var id = GUID.valueOf(dto.getId());
    var drawable = new WallTemplate(id);
    drawable.setRadius(dto.getRadius());
    var vertex = dto.getVertex();
    drawable.setVertex(new ZonePoint(vertex.getX(), vertex.getY()));
    drawable.setMouseSlopeGreater(dto.getMouseSlopeGreater());
    var pathVertex = dto.getPathVertex();
    drawable.setPathVertex(new ZonePoint(pathVertex.getX(), pathVertex.getY()));
    drawable.setDoubleWide(dto.getDoubleWide());
    if (dto.hasName()) {
      drawable.setName(dto.getName().getValue());
    }
    drawable.setLayer(Zone.Layer.valueOf(dto.getLayer()));

    var cellpoints = new ArrayList<CellPoint>();
    for (var point : dto.getPointsList()) {
      cellpoints.add(new CellPoint(point.getX(), point.getY()));
    }
    drawable.setPath(cellpoints);

    return drawable;
  }

  private Shape getBorderAsShape(Grid grid) {
    var leftPath = new Path2D.Double();

    var vertex = getVertex();
    var path = getPath();
    if (vertex == null || path == null) {
      return leftPath;
    }

    var gridSize = grid.getSize();

    // All we have to do is extend the border on the left as we go from the start to the end, then
    // turn around and come back.
    // Note: only square grids are properly supported.

    var thereAndBackAgain = Iterables.concat(path, path.reversed().subList(1, path.size()));
    var it = thereAndBackAgain.iterator();

    if (!it.hasNext()) {
      // No points in the path. An empty shape will suffice.
      return leftPath;
    }

    CellPoint first = it.next();
    Point2D.Double previousPoint =
        new Point2D.Double(vertex.x + first.x * gridSize, vertex.y + first.y * gridSize);

    if (!it.hasNext()) {
      // Only a single cell in the path. Can return a basic rectangle instead of a path.
      return new Rectangle2D.Double(previousPoint.getX(), previousPoint.getY(), gridSize, gridSize);
    }
    CellPoint current = it.next();

    // Note the arrangement of directions is increasing clockwise.
    // So adding 1 == right turn, and subtracting 1 == left turn.
    final var EAST = 0b00;
    final var SOUTH = 0b01;
    final var WEST = 0b10;
    final var NORTH = 0b11;

    ToIntBiFunction<CellPoint, CellPoint> determineHeading =
        (from, to) -> {
          // Because the cells are adjacent, the heading can only be horizontal or vertical.
          int x = Integer.compare(to.x, from.x);
          if (x < 0) {
            return WEST;
          }
          if (x > 0) {
            return EAST;
          }

          int y = Integer.compare(to.y, from.y);
          if (y < 0) {
            return NORTH;
          }
          return SOUTH;
        };
    Consumer<Integer> advancePath =
        heading -> {
          var northSouth = (heading & 0b01) != 0;
          var sign = (heading & 0b10) != 0 ? -1 : 1;

          var deltaX = northSouth ? 0 : sign;
          var deltaY = northSouth ? sign : 0;

          previousPoint.setLocation(
              previousPoint.getX() + gridSize * deltaX, previousPoint.getY() + gridSize * deltaY);
          leftPath.lineTo(previousPoint.getX(), previousPoint.getY());
        };

    // To start, we need to know which way we are heading. Also need to adjust the starting point to
    // be at one of the other cell corners depending on that heading.
    int heading = determineHeading.applyAsInt(first, current);
    // Now adjust which corner we're at if needed.
    if (heading == SOUTH || heading == WEST) {
      previousPoint.x += gridSize;
    }
    if (heading == NORTH || heading == WEST) {
      previousPoint.y += gridSize;
    }
    leftPath.moveTo(previousPoint.getX(), previousPoint.getY());

    // Take one step in the current heading to handle the first cell.
    advancePath.accept(heading);
    while (it.hasNext()) {
      CellPoint next = it.next();

      var newHeading = determineHeading.applyAsInt(current, next);

      // Test each heading starting with a left turn and going clockwise until we find `newHeading`.
      // Each heading we rule out is another segment and turn we h ave to make.
      heading = (heading - 1) & 0b11;
      while (heading != newHeading) {
        heading = (heading + 1) & 0b11;
        advancePath.accept(heading);
      }

      current = next;
    }
    // Handle the last cell by taking one final step in the current heading.
    advancePath.accept(heading);

    leftPath.closePath();

    return leftPath;
  }
}
