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
package net.rptools.maptool.model.entities;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.rptools.maptool.model.entities.components.Component;
import org.jspecify.annotations.Nullable;

public class Entity {
  public record UpdateEvent<T extends Record & Component>(
      Entity entity, @Nullable T previous, @Nullable T current) {}

  private final com.badlogic.ashley.core.Entity ashleyEntity;
  private final List<Consumer<UpdateEvent<?>>> updateListeners = new CopyOnWriteArrayList<>();

  public Entity(com.badlogic.ashley.core.Entity ashleyEntity) {
    this.ashleyEntity = ashleyEntity;
  }

  private <T extends Record & Component> void emitUpdate(
      @Nullable T previous, @Nullable T current) {
    var event = new UpdateEvent<T>(this, previous, current);
    for (var listener : updateListeners) {
      listener.accept(event);
    }
  }

  public <T extends Record & Component> void add(T component) {
    Class<T> componentClass = (Class<T>) component.getClass();
    var previous = this.ashleyEntity.getComponent(componentClass);
    this.ashleyEntity.add(component);

    emitUpdate(previous, component);
  }

  public <T extends Record & Component> void remove(Class<T> componentClass) {
    var previous = this.ashleyEntity.getComponent(componentClass);
    this.ashleyEntity.remove(componentClass);

    emitUpdate(previous, null);
  }
}
