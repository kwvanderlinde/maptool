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
package net.rptools.lib.events;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

/** Class to handle the MapTool event bus. */
public class MapToolEventBus {
  private final EventBus eventBus = new EventBus();

  /**
   * Registers all the subscriber methods of {@code subscriber}
   *
   * <p>A subscriber method is one marked with {@link Subscribe} with a single parameter that is the
   * event.
   *
   * @param subscriber The object listening for events.
   * @return {@code this}
   */
  public MapToolEventBus register(Object subscriber) {
    eventBus.register(subscriber);
    return this;
  }

  /**
   * Unregisters all the subscriber method on {@code subscriber}.
   *
   * <p>{@code subscriber} should previously have been passed to {@link #register(Object)} for this
   * event bus.
   *
   * @param subscriber The object that should no longer listen for events.
   * @return {@code this}
   */
  public MapToolEventBus unregister(Object subscriber) {
    eventBus.unregister(subscriber);
    return this;
  }

  // TODO For consistency, always emit via EventQueue.invokeLater(). I.e., never synchronous.
  /**
   * Emits an event, notifying all matching subscribers.
   *
   * <p>This method will return after all subscribers have been notified.
   *
   * @param event The event to send to subscribers.
   * @return {@code this}
   */
  public MapToolEventBus post(Object event) {
    eventBus.post(event);
    return this;
  }
}
