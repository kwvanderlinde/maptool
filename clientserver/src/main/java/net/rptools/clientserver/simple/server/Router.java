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
package net.rptools.clientserver.simple.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.rptools.clientserver.simple.connection.Connection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Router {
  private static final Logger log = LogManager.getLogger(Router.class);

  // TODO Concurrent map
  private final Map<String, Connection> clients = Collections.synchronizedMap(new HashMap<>());

  public Router() {}

  public Collection<Connection> removeAll() {
    ArrayList<Connection> copy;
    synchronized (clients) {
      copy = new ArrayList<>(clients.values());
      clients.clear();
    }
    return copy;
  }

  public @Nullable Connection getConnection(String id) {
    return this.clients.get(id);
  }

  public void addConnection(Connection connection) {
    var existingConnection = this.clients.putIfAbsent(connection.getId(), connection);
    if (existingConnection != null) {
      log.error(
          "Failed to add connection {} because we already have a connection with that ID",
          connection.getId());
    }
  }

  public void removeConnection(Connection connection) {
    var removed = this.clients.remove(connection.getId(), connection);
    if (!removed) {
      log.error(
          "Failed to remove connection {} because we do not have that connection",
          connection.getId());
    }
  }

  public void broadcastMessage(byte[] message) {
    synchronized (clients) {
      for (Connection conn : clients.values()) {
        conn.sendMessage(message);
      }
    }
  }

  public void broadcastMessage(String[] exclude, byte[] message) {
    // Note: although we except an exclude array, reality is that it only has one element at most.
    // So don't bother setting up a hash set or anything, just loop to check if in the array.
    synchronized (clients) {
      for (Map.Entry<String, Connection> entry : clients.entrySet()) {
        if (!Arrays.asList(exclude).contains(entry.getKey())) {
          entry.getValue().sendMessage(message);
        }
      }
    }
  }

  public void sendMessage(String id, byte[] message) {
    synchronized (clients) {
      var connection = clients.get(id);
      if (connection != null) {
        connection.sendMessage(message);
      }
    }
  }

  /** Looks for any clients that have disconnected, and removes them. */
  public void reapClients() {
    // TODO Part of the point of this method is that we can clear out stale connections before
    //  adding one. I.e., we don't let a stale connection block addition of a new connection. So
    //  perhaps I ought to do this in addConnection() as well, all under one sync block.

    log.debug("About to reap clients");
    synchronized (clients) {
      log.debug("Reaping clients");

      for (Iterator<Map.Entry<String, Connection>> i = clients.entrySet().iterator();
          i.hasNext(); ) {
        Map.Entry<String, Connection> entry = i.next();
        Connection conn = entry.getValue();
        if (!conn.isAlive()) {
          log.debug("\tReaping: {}", conn.getId());
          i.remove();
          try {
            // tODO Original did a fireDisconnect() here. But I think in the new approach,
            //  interested parties will be informed by observing the connection itself... or
            //  something.
            conn.close();
          } catch (Exception e) {
            // Don't want to raise an error if notification of removing a dead connection failed
          }
        }
      }
    }
  }
}
