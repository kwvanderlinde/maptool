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
package net.rptools.clientserver.decomposed.trivial;

import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.clientserver.decomposed.AbstractConnection;
import net.rptools.clientserver.decomposed.Connection;

public class TrivialConnection extends AbstractConnection implements Connection {
  public record Pipe(TrivialConnection clientSide, TrivialConnection serverSide) {}

  public static Pipe createPipe(String id) {
    final var clientSide = new TrivialConnection(id);
    final var serverSide = new TrivialConnection(id);

    clientSide.setMessageSender(serverSide::onMessageReceived);
    serverSide.setMessageSender(clientSide::onMessageReceived);

    return new Pipe(new TrivialConnection(id), new TrivialConnection(id));
  }

  private Consumer<byte[]> messageSender;

  public TrivialConnection(String id) {
    super(id);
    this.messageSender = message -> {};
  }

  private void setMessageSender(Consumer<byte[]> messageSender) {
    this.messageSender = messageSender;
  }

  @Override
  public void sendMessage(@Nullable Object channel, @Nonnull byte[] message) {
    this.messageSender.accept(message);
  }
}
