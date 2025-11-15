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

import java.awt.Shape;
import javax.annotation.Nullable;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZonePoint;

/**
 * Base class for the radius, line, and cone templates.
 *
 * @author jgorrell
 */
public abstract class AbstractTemplate extends AbstractDrawing {
  /*---------------------------------------------------------------------------------------------
   * Instance Variables
   *-------------------------------------------------------------------------------------------*/

  /** The current width of this template in squares. */
  private int radius;

  /** The location of the vertex where painting starts. */
  private ZonePoint vertex = new ZonePoint(0, 0);

  /**
   * @deprecated This used to indicate the zone where this drawable is painted. We no longer track
   *     this in the drawing, but old campaign files may keep a reference to this field. So we have
   *     to keep this around so XStream can find the value if it needs it.
   */
  @Deprecated private GUID zoneId;

  protected AbstractTemplate() {}

  protected AbstractTemplate(GUID id) {
    super(id);
  }

  protected AbstractTemplate(AbstractTemplate other) {
    super(other);
    this.radius = other.radius;
    this.vertex = new ZonePoint(other.vertex);
  }

  public Object readResolve() {
    zoneId = null;
    return this;
  }

  /*---------------------------------------------------------------------------------------------
   * Class Variables
   *-------------------------------------------------------------------------------------------*/

  /** Minimum radius value allowed. */
  public static final int MIN_RADIUS = 1;

  /** Extra padding added to insure the wide lines do not get clipped. */
  public static final int BOUNDS_PADDING = 10;

  /** The alpha forced on all background fills. */
  public static final float DEFAULT_BG_ALPHA = 0.20f;

  /** Indicates what kind of cursor to draw when tools are manipulating the template. */
  public enum CursorType {
    /** The template uses a cross-hair cursor. */
    Cross,
    /** The template cursor covers the entire cell. */
    Cell
  }

  /** The directions that can be drawn. All is for a radius and the other values are for cones. */
  public enum Direction {
    /** Draw a Radius */
    ALL,

    // Draw a cone in the indicated direction. Order is important!
    /** Draw a cone directly to the west (left) of the selection point. */
    WEST,
    /** Draw a cone directly to the north west (upper left quadrant) of the selection point. */
    NORTH_WEST,
    /** Draw a cone directly to the north (up) of the selection point. */
    NORTH,
    /** Draw a cone directly to the north east (upper right quadrant) of the selection point. */
    NORTH_EAST,
    /** Draw a cone directly to the east (right) of the selection point. */
    EAST,
    /** Draw a cone directly to the south east (lower right quadrant) of the selection point. */
    SOUTH_EAST,
    /** Draw a cone directly to the south (down) of the selection point. */
    SOUTH,
    /** Draw a cone directly to the south west (lower left quadrant) of the selection point. */
    SOUTH_WEST;

    /**
     * Find the direction to draw a cone from two points. The first point would be the mouse
     * location and the second would be the vertex of the cone.
     *
     * @param x1 Mouse X coordinate.
     * @param y1 Mouse Y coordinate.
     * @param x2 Vertex X coordinate.
     * @param y2 Vertex Y coordinate.
     * @return The direction from the vertex (point 2) to the mouse (point 1).
     */
    public static Direction findDirection(int x1, int y1, int x2, int y2) {
      double dX = x1 - x2;
      double dY = y1 - y2;
      double angle = Math.atan2(dY, dX);
      int value = (int) Math.floor(((angle / Math.PI + 1.0) / 2.0) * 16.0);
      if (value >= 15) value = 0;
      return values()[((value + 1) / 2) + 1];
    }
  }

  /** The quadrants for drawing. */
  public enum Quadrant {
    /** Draw in the north east (upper right) quadrant. */
    NORTH_EAST,
    /** Draw in the north west (upper left) quadrant. */
    NORTH_WEST,
    /** Draw in the south east (lower right) quadrant. */
    SOUTH_EAST,
    /** Draw in the south west (lower left) quadrant. */
    SOUTH_WEST
  }

  /*---------------------------------------------------------------------------------------------
   * Instance Methods
   *-------------------------------------------------------------------------------------------*/

  public abstract CursorType getCursorType();

  public @Nullable Shape getDecorationsToStroke(Zone zone) {
    // Most templates don't have a visible vertex.
    return null;
  }

  /**
   * Set the radius of the template in squares.
   *
   * @param squares The number of squares in the radius for this template.
   */
  public void setRadius(int squares) {
    radius = squares;
  }

  /**
   * Get the radius for this RadiusTemplate.
   *
   * @return Returns the current value of radius in squares.
   */
  public int getRadius() {
    return radius;
  }

  /**
   * Get the vertex for this RadiusTemplate.
   *
   * @return Returns the current value of vertex.
   */
  public ZonePoint getVertex() {
    return vertex;
  }

  /**
   * Set the value of vertex for this RadiusTemplate.
   *
   * @param vertex The vertex to set.
   */
  public void setVertex(ZonePoint vertex) {
    this.vertex = vertex;
  }

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

  /**
   * Get the distance to a specific coordinate.
   *
   * @param x delta-X of the coordinate.
   * @param y delta-Y of the coordinate.
   * @return Number of cells to the passed coordinate.
   */
  public int getDistance(int x, int y) {
    if (x > y) return x + (y / 2) + 1 + (y & 1);
    return y + (x / 2) + 1 + (x & 1);
  }
}
