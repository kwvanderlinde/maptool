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
package net.rptools.maptool.model.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntityManager {
  // TODO Z-order management.
  private List<Entity> entities = new ArrayList<>();

  public List<Entity> getAllEntities() {
    return Collections.unmodifiableList(entities);
  }

  private void adopt(Entity entity) {
    // TODO Ensure the entity has a local id, and store it in a lookup map.
    this.entities.add(entity);
  }

  public Camera2 spawnCamera() {
    var camera = new Camera2();
    adopt(camera);
    return camera;
  }

  public Token2 spawnToken() {
    var token = new Token2();
    adopt(token);
    return token;
  }

  public Map2 spawnMap() {
    var map = new Map2();
    adopt(map);
    return map;
  }
}
