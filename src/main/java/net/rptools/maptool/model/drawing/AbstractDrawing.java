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

import com.google.common.annotations.VisibleForTesting;
import java.awt.*;
import java.awt.image.ImageObserver;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;

/**
 * Abstract drawing. This class takes care of setting up the Pen since that will be the same for all
 * implementing classes.
 */
// TODO Remove ImageObserver
// TODO Remove the draw() methods and related in subclasses.
public abstract class AbstractDrawing implements Drawable, ImageObserver {
  /**
   * The unique identifier for this drawable.
   *
   * <p>It should not typically be changed except to give copies a new ID.
   */
  private GUID id;

  private String layer;
  private String name;

  protected AbstractDrawing() {
    this.id = new GUID();
  }

  protected AbstractDrawing(GUID id) {
    this.id = id;
  }

  protected AbstractDrawing(AbstractDrawing other) {
    // The only thing we don't preserve is the ID.
    this.id = other.id;
    this.layer = other.layer;
    this.name = other.name;
  }

  @VisibleForTesting
  protected Campaign getCampaign() {
    return MapTool.getCampaign();
  }

  /**
   * Get the id for this AbstractDrawing.
   *
   * @return Returns the current value of id.
   */
  public GUID getId() {
    return id;
  }

  @Override
  public void setId(GUID guid) {
    this.id = guid;
  }

  public void setLayer(Zone.Layer layer) {
    this.layer = layer != null ? layer.name() : null;
  }

  public Zone.Layer getLayer() {
    return layer != null ? Zone.Layer.valueOf(layer) : Zone.Layer.getDefaultDrawingLayer();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Use the id for equals.
   *
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AbstractDrawing)) return false;
    return id.equals(obj);
  }

  /**
   * Use the id for hash code.
   *
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("name=").append(getName()).append(";");
    sb.append("layer=").append(getLayer()).append(";");
    sb.append("id=").append(getId()).append(";");
    return sb.toString();
  }

  ////
  // IMAGE OBSERVER
  public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
    MapTool.getFrame().refresh();
    return true;
  }
}
