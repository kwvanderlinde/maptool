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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.tool.editing;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * Represents the behaviour of the tool at a point in time.
 *
 * <p>By encapsulating this state into separate modes, it avoids the possibility of mixing up which
 * actions we are expecting.
 *
 * <p>The various mouse event handler include a {@link Snap} parameter. This is a strategy for
 * snapping arbitrary zone points to the grid based on the keys the user is pressing. If a tool
 * doesn't support snapping, it is free to ignore this parameter and use the raw position. If
 * snapaping is supported, the {@link Snap#snap(Point2D)} method should be used to snap to grid.
 */
public interface ToolMode {
  /** Called when the mode becomes the current mode. */
  void activate();

  /**
   * Called when the mode stops being the current mode.
   *
   * <p>When switching modes, the {@code deactivate()} of the old mode will be called before the
   * {@link #activate()} of the new mode.
   */
  void deactivate();

  /**
   * Cancels the current tool mode.
   *
   * <p>Typoically this requires restoring zone state to what it was prior to the last commit.
   *
   * @return {@code true} if the tool mode has its own cancel behaviour; {@code false} if the
   *     regular behaviour (revert to pointer tool) should apply.
   */
  boolean cancel();

  /**
   * Called when the delete key is pressed.
   *
   * <p>Implementations should use this to remove the current selection.
   */
  void delete();

  /**
   * Handles a mouse move.
   *
   * <p><strong>Note:</strong> if the mouse is dragged, {@link #mouseDragged(Point2D, Snap,
   * MouseEvent)} will be called instead of this method..
   *
   * @param point The location of the mouse event in zone units.
   * @param snapMode The strategy for snapping {@code point} to the grid.
   * @param event The original AWT mouse event.
   */
  void mouseMoved(Point2D point, Snap snapMode, MouseEvent event);

  /**
   * Handles a mouse drag.
   *
   * @param point The location of the mouse event in zone units.
   * @param snapMode The strategy for snapping {@code point} to the grid.
   * @param event The original AWT mouse event.
   * @return {@code true} if the default mouse drag behaviour should forbidden.
   */
  boolean mouseDragged(Point2D point, Snap snapMode, MouseEvent event);

  /**
   * Handles a mouse press.
   *
   * @param point The location of the mouse event in zone units.
   * @param snapMode The strategy for snapping {@code point} to the grid.
   * @param event The original AWT mouse event.
   */
  void mousePressed(Point2D point, Snap snapMode, MouseEvent event);

  /**
   * Handles a mouse click.
   *
   * @param point The location of the mouse event in zone units.
   * @param snapMode The strategy for snapping {@code point} to the grid.
   * @param event The original AWT mouse event.
   */
  void mouseClicked(Point2D point, Snap snapMode, MouseEvent event);

  /**
   * Handles a mouse release.
   *
   * @param point The location of the mouse event in zone units.
   * @param snapMode The strategy for snapping {@code point} to the grid.
   * @param event The original AWT mouse event.
   */
  void mouseReleased(Point2D point, Snap snapMode, MouseEvent event);

  /**
   * Draws any custom visuals required by the tool mode.
   *
   * @param g2 The graphics context for drawing.
   */
  void paint(Graphics2D g2);
}
