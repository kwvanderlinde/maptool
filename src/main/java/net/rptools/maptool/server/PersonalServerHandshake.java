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
package net.rptools.maptool.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import net.rptools.clientserver.simple.Handshake;
import net.rptools.clientserver.simple.MessageHandler;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.gamedata.DataStoreManager;
import net.rptools.maptool.model.library.LibraryManager;
import net.rptools.maptool.model.player.*;
import net.rptools.maptool.server.proto.*;
import net.rptools.maptool.server.proto.HandshakeMsg.MessageTypeCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Class used to handle the server side part of the connection handshake. */
public class PersonalServerHandshake implements Handshake<Player>, MessageHandler {
  /** Instance used for log messages. */
  private static final Logger log = LogManager.getLogger(PersonalServerHandshake.class);

  private final CompletableFuture<Player> future = new CompletableFuture<>();
  private CompletionStage<Player> stage = future;

  private final IMapToolServer server;

  /** The connection to the client. */
  private final Connection connection;

  /** The player that this connection is for. */
  private final Player player;

  /** The current state of the handshake process. */
  private State currentState = State.AwaitingClientInit;

  /**
   * Creates a new {@code ServerHandshake} instance.
   *
   * @param server
   * @param connection The client connection for the handshake.
   * @param player
   */
  public PersonalServerHandshake(IMapToolServer server, Connection connection, Player player) {
    this.server = server;
    this.connection = connection;
    this.player = player;

    whenComplete(
        (result, error) -> {
          connection.removeMessageHandler(this);
        });
  }

  @Override
  public void whenComplete(BiConsumer<? super Player, ? super Throwable> callback) {
    stage = stage.whenComplete(callback);
  }

  private synchronized void setCurrentState(State state) {
    log.debug("Transitioning from {} to {}", currentState, state);
    currentState = state;
  }

  /**
   * Sends an error response to the client and notifies any observers of the handshake that the
   * status has changed.
   *
   * @param errorCode The error code that should be sent to the client.
   */
  private void sendErrorResponseAndNotify(HandshakeResponseCodeMsg errorCode) {
    var msg = HandshakeMsg.newBuilder().setHandshakeResponseCodeMsg(errorCode).build();
    sendMessage(State.Error, msg);
    // Do not notify users as it will disconnect and client won't get message instead wait
    // for client to disconnect after getting this message, if they don't then it will fail
    // with invalid handshake.
  }

  private void sendMessage(State newState, HandshakeMsg message) {
    setCurrentState(newState);

    var msgType = message.getMessageTypeCase();
    log.debug("Sevver sent to {}: {}", connection.getId(), msgType);
    connection.sendMessage(message.toByteArray());
  }

  @Override
  public void handleMessage(String id, byte[] message) {
    try {
      var handshakeMsg = HandshakeMsg.parseFrom(message);
      var msgType = handshakeMsg.getMessageTypeCase();

      log.debug("from {} got: {}", id, msgType);

      if (msgType == MessageTypeCase.HANDSHAKE_RESPONSE_CODE_MSG) {
        HandshakeResponseCodeMsg code = handshakeMsg.getHandshakeResponseCodeMsg();
        String errorMessage;
        if (code.equals(HandshakeResponseCodeMsg.INVALID_PASSWORD)) {
          errorMessage = I18N.getText("Handshake.msg.incorrectPassword");
        } else if (code.equals(HandshakeResponseCodeMsg.INVALID_PUBLIC_KEY)) {
          errorMessage = I18N.getText("Handshake.msg.incorrectPublicKey");
        } else {
          errorMessage = I18N.getText("Handshake.msg.invalidHandshake");
        }
        future.completeExceptionally(new Failure(errorMessage));
        return;
      }

      switch (currentState) {
        case AwaitingClientInit:
          if (msgType == MessageTypeCase.CLIENT_INIT_MSG) {
            sendConnectionSuccessful();
          } else {
            sendErrorResponseAndNotify(HandshakeResponseCodeMsg.INVALID_HANDSHAKE);
          }
          break;
      }
    } catch (Exception e) {
      log.warn(e.toString());
      setCurrentState(State.Error);
      future.completeExceptionally(new Failure(I18N.getText("Handshake.msg.unexpectedError"), e));
    }
  }

  private void sendConnectionSuccessful() throws ExecutionException, InterruptedException {
    var connectionSuccessfulMsg =
        ConnectionSuccessfulMsg.newBuilder()
            .setRoleDto(player.isGM() ? RoleDto.GM : RoleDto.PLAYER)
            .setServerPolicyDto(server.getPolicy().toDto())
            .setGameDataDto(new DataStoreManager().toDto().get())
            .setAddOnLibraryListDto(new LibraryManager().addOnLibrariesToDto().get());
    var handshakeMsg =
        HandshakeMsg.newBuilder().setConnectionSuccessfulMsg(connectionSuccessfulMsg).build();
    sendMessage(State.Success, handshakeMsg);
    future.complete(player);
  }

  @Override
  public void startHandshake() {
    connection.addMessageHandler(this);
    setCurrentState(State.AwaitingClientInit);
  }

  /** The states that the server side of the server side of the handshake process can be in. */
  private enum State {
    Error,
    Success,
    AwaitingClientInit,
  }
}
