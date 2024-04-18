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

import com.google.protobuf.ByteString;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import net.rptools.clientserver.decomposed.Connection;
import net.rptools.clientserver.decomposed.ConnectionObserver;
import net.rptools.clientserver.simple.MessageHandler;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.gamedata.DataStoreManager;
import net.rptools.maptool.model.gamedata.proto.DataStoreDto;
import net.rptools.maptool.model.library.LibraryManager;
import net.rptools.maptool.model.library.proto.AddOnLibraryListDto;
import net.rptools.maptool.model.player.*;
import net.rptools.maptool.server.proto.*;
import net.rptools.maptool.util.PasswordGenerator;
import net.rptools.maptool.util.cipher.CipherUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides the server side of a connection handshake.
 *
 * <p>For role-based password authentication, the happy path for a handshake is as follows:
 *
 * <ol>
 *   <li>AwaitingClientInit (expecting CLIENT_INIT_MSG)
 *   <li>AwaitingClientPasswordAuth (expecting CLIENT_AUTH_MESSAGE)
 *   <li>Success
 * </ol>
 *
 * <p>For asymmetric key authentication, the path is slightly different:
 *
 * <ol>
 *   <li>AwaitingClientInit (expecting CLIENT_INIT_MSG)
 *   <li>AwaitingPublicKey (expecting PUBLIC_KEY_UPLOAD_MSG; skipped if not using EasyConnect)
 *   <li>AwaitingClientPublicKeyAuth (expecting CLIENT_AUTH_MESSAGE)
 *   <li>Success
 * </ol>
 *
 * <p>If an unexpected exception is encountered at any time, the state is set to Error. If there is
 * a protocol error (e.g., unexpected messages), the state is set to PlayerBlocked. If the player is
 * blocked in the database, the state is set to Error.
 */
public class ServerHandshake2 implements Handshake2, MessageHandler {
  /** Instance used for log messages. */
  private static final Logger log = LogManager.getLogger(ServerHandshake2.class);

  /** The database used for retrieving players. */
  private final PlayerDatabase playerDatabase;

  /** The connection to the client. */
  private final Connection connection;

  private final ConnectionObserver connectionObserver;

  private final boolean useEasyConnect;

  /** Observers that want to be notified when the status changes. */
  private final List<Observer> observerList = new CopyOnWriteArrayList<>();

  private IState state = new StartState();

  /**
   * Creates a new handshake.
   *
   * @param connection The client connection for the handshake.
   * @param playerDatabase The database of players.
   * @param useEasyConnect If true, the client will use the easy connect method.
   */
  public ServerHandshake2(
      Connection connection, PlayerDatabase playerDatabase, boolean useEasyConnect) {
    this.connection = connection;
    this.connectionObserver =
        new ConnectionObserver() {
          @Override
          public void onMessageReceived(Connection connection, byte[] message) {
            handleMessage(connection.getId(), message);
          }
        };

    this.playerDatabase = playerDatabase;
    this.useEasyConnect = useEasyConnect;
  }

  // region Public API

  @Override
  public void startHandshake() {
    try {
      connection.addObserver(connectionObserver);
      transitionToState(new AwaitingClientInitState());
    } catch (Exception e) {
      // Very important: we do not leak exceptions here, instead simply considering them a failed
      // handshake like any error encountered during message handling.
      setUnexpectedException(e);
    }
  }

  @Override
  public synchronized Connection getConnection() {
    return connection;
  }

  /**
   * Returns the player associated with the handshake.
   *
   * @return the player associated with the handshake.
   */
  public Player getPlayer() {
    return state.getPlayer();
  }

  @Override
  public boolean isSuccessful() {
    return state.getPlayer() != null;
  }

  @Override
  public String getErrorMessage() {
    return state.getErrorMessage();
  }

  @Override
  public Exception getException() {
    return state.getException();
  }

  // region TODO Should we just require one observer to be injected during construction?

  /**
   * Adds an observer to the handshake process.
   *
   * @param observer the observer of the handshake process.
   */
  public synchronized void addObserver(Observer observer) {
    observerList.add(observer);
  }

  /**
   * Removes an observer from the handshake process.
   *
   * @param observer the observer of the handshake process.
   */
  public synchronized void removeObserver(Observer observer) {
    observerList.remove(observer);
  }

  // endregion

  // endregion

  // region Message send/receive

  private void sendMessage(HandshakeMsg message) {
    var msgType = message.getMessageTypeCase();
    log.info("Server sent to {}: {}", connection.getId(), msgType);
    connection.sendMessage(null, message.toByteArray());
  }

  @Override
  public void handleMessage(String id, byte[] message) {
    // TODO Should we validate that id matches connection.getId()?
    //  Can we get rid of this parameter (our nature as a MessageHandler is probably not important).
    try {
      var handshakeMsg = HandshakeMsg.parseFrom(message);
      var msgType = handshakeMsg.getMessageTypeCase();

      log.info("from {} got: {}", id, msgType);

      if (msgType == HandshakeMsg.MessageTypeCase.HANDSHAKE_RESPONSE_CODE_MSG) {
        HandshakeResponseCodeMsg code = handshakeMsg.getHandshakeResponseCodeMsg();
        final var errorMessage =
            switch (code) {
              case HandshakeResponseCodeMsg.INVALID_PASSWORD -> I18N.getText(
                  "Handshake.msg.incorrectPassword");
              case HandshakeResponseCodeMsg.INVALID_PUBLIC_KEY -> I18N.getText(
                  "Handshake.msg.incorrectPublicKey");
              default -> I18N.getText("Handshake.msg.invalidHandshake");
            };

        transitionToState(new ErrorState(errorMessage));
        notifyObservers();
        return;
      }

      final var newState = this.state.handle(handshakeMsg);
      transitionToState(newState);
    } catch (ProtocolError e) {
      setProtocolError(e.errorCode, e.getMessage());
    } catch (Exception e) {
      setUnexpectedException(e);
    }
  }

  // endregion

  // region State machine

  /*
   * TODO Because of the UI interaction for AwaitingPublicKeyState, this actually needs to be
   *  thread-safe. Actually I take that back - the entirety of state management needs to be thread-
   *  safe. I see a couple of approach to actually accomplishing that:
   *  1. Take a lock during message handling and state transitions. I.e., we cannot transition
   *     states while handling a message (except in the same thread), no two threads can transition
   *     at the same time, and not two messages can be handled at the same time. GOOD LUCK getting
   *     this correct though.
   *  2. A transactional approach. This is best done by holding mutable state in state objects,
   *     though idempotent state can leak into the application at large. When attempting to
   *     transition states, we have to check that the current state is what it was when we started,
   *     and only then can we transition.
   *  3. Using an event queue. This is actually applicable beyond just handshake, and so I really
   *     like the idea. First, remember the premise that each connection potentially reads messages
   *     in its own thread. Rather than directly invoking observers, connections should instead push
   *     individual messages to a concurrent queue. The server (or related components) can then
   *     action messages on its own dedicated thread by pulling from the queue, shared between all
   *     connections. This could also be used in callbacks, e.g., for AwaitingPublicKeyState.
   */
  private void transitionToState(IState newState) {
    if (newState == state) {
      return;
    }

    state.beforeTransitionFrom();
    newState.beforeTransitionTo();
    state = newState;
    newState.afterTransitionTo();
    state.afterTransitionFrom();
  }

  private void setProtocolError(HandshakeResponseCodeMsg errorCode, String message) {
    sendMessage(HandshakeMsg.newBuilder().setHandshakeResponseCodeMsg(errorCode).build());
    transitionToState(new ErrorState(message));

    // TODO This note comes to us from the original:
    //  > Do not notify users as it will disconnect and client won't get message instead wait
    //  > for client to disconnect after getting this message, if they don't then it will fail
    //  > with invalid handshake.
    //  I can't think of what this is about. Either it's some whacko EasyConnect case, or some of
    //  our observers do stuff they shouldn't. I need to make sure such a case does not actually
    //  exist.
    //      Oh, I think I understand the message now - I was thinking of "client" as "UI" because of
    //  the use of the term "users", but it just means don't invoke the observers because doing so
    //  will close the connection and prevent the above response code message from making it. And I
    //  have to say I vehemently disagree with this as a solution. Disconnection should be queued
    //  up like any other message, so that we can indeed send a final message before closing. This
    //  is something we need to improve connection-side in order to recover sanity. Or even better,
    //  once I have a server event pump, I should be able to guarantee sequencing of all these types
    //  of things.
  }

  private void setUnexpectedException(Exception e) {
    log.warn(e);
    transitionToState(new ErrorState(e.getMessage(), e));
    notifyObservers();
  }

  /** Notifies observers that the handshake has completed or errored out.. */
  private synchronized void notifyObservers() {
    // TODO Make sure this happens for every terminal state.
    connection.removeObserver(connectionObserver);

    for (var observer : observerList) {
      observer.onCompleted(this);
    }
  }

  /** The states that the server side of the server side of the handshake process can be in. */
  private interface IState {
    IState handle(HandshakeMsg message) throws ProtocolError;

    default @Nullable Player getPlayer() {
      // Even if we know the player, it isn't firmly set until we're in a success state.
      return null;
    }

    default String getErrorMessage() {
      return "";
    }

    default @Nullable Exception getException() {
      return null;
    }

    default void beforeTransitionTo() {}

    default void afterTransitionTo() {}

    default void beforeTransitionFrom() {}

    default void afterTransitionFrom() {}
  }

  // TODO Problem with StartState: The _client_ sends the first message, so we actually have to be
  //  listening right away. Any we can't reverse the relationship because then the client has the
  //  same problem. In order for StateState to make sense, we cannot start the connection reader
  //  until the handshake is started.
  private static final class StartState implements IState {
    // Like terminal states, no messages are allowed here.

    @Override
    public IState handle(HandshakeMsg message) throws ProtocolError {
      throw ProtocolError.invalidHandshake();
    }
  }

  private class AwaitingClientInitState implements IState {
    @Override
    public final IState handle(HandshakeMsg handshakeMsg) throws ProtocolError {
      if (handshakeMsg.getMessageTypeCase() != HandshakeMsg.MessageTypeCase.CLIENT_INIT_MSG) {
        throw ProtocolError.invalidHandshake();
      }
      final var clientInitMsg = handshakeMsg.getClientInitMsg();

      var server = MapTool.getServer();
      if (server.isPlayerConnected(clientInitMsg.getPlayerName())) {
        throw new ProtocolError(
            HandshakeResponseCodeMsg.PLAYER_ALREADY_CONNECTED,
            I18N.getText("Handshake.msg.duplicateName"));
      }

      if (!MapTool.isDevelopment() && !MapTool.getVersion().equals(clientInitMsg.getVersion())) {
        throw new ProtocolError(
            HandshakeResponseCodeMsg.WRONG_VERSION, I18N.getText("Handshake.msg.wrongVersion"));
      }

      final Player player;
      try {
        player = playerDatabase.getPlayer(clientInitMsg.getPlayerName());
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
        throw new ProtocolError(
            HandshakeResponseCodeMsg.INVALID_PASSWORD,
            I18N.getText("Handshake.msg.encodeInitFail", clientInitMsg.getPlayerName()));
      }

      if (player == null) {
        if (!useEasyConnect) {
          throw new ProtocolError(
              HandshakeResponseCodeMsg.INVALID_PASSWORD,
              I18N.getText("Handshake.msg.unknownPlayer", clientInitMsg.getPlayerName()));
        }

        return requestPublicKey(clientInitMsg.getPlayerName());
      }

      if (playerDatabase.isBlocked(player)) {
        return new PlayerBlockedState(player);
      }

      if (playerDatabase.getAuthMethod(player) == PlayerDatabase.AuthMethod.ASYMMETRIC_KEY) {
        return sendAsymmetricKeyAuthType(player, new MD5Key(clientInitMsg.getPublicKeyMd5()));
      }

      if (playerDatabase.supportsRolePasswords()) {
        return sendRoleSharedPasswordAuthType(player);
      }

      return sendSharedPasswordAuthType(player);
    }

    private IState requestPublicKey(String playerName) {
      var requestPublicKeyBuilder = RequestPublicKeyMsg.newBuilder();
      var easyConnectPin = String.format("%04d", new SecureRandom().nextInt(9999));
      var easyConnectName = playerName;
      requestPublicKeyBuilder.setPin(easyConnectPin);
      sendMessage(
          HandshakeMsg.newBuilder().setRequestPublicKeyMsg(requestPublicKeyBuilder).build());

      return new AwaitingPublicKeyState(easyConnectPin, easyConnectName);
    }

    /** Send the authentication type message when using per player shared passwords. */
    private IState sendSharedPasswordAuthType(Player player) {
      byte[] playerPasswordSalt = playerDatabase.getPlayerPasswordSalt(player.getName());

      SecureRandom rnd = new SecureRandom();
      byte[] iv = new byte[CipherUtil.CIPHER_BLOCK_SIZE];
      rnd.nextBytes(iv);
      String password = new PasswordGenerator().getPassword();
      CipherUtil.Key key = playerDatabase.getPlayerPassword(player.getName()).get();

      final HandshakeChallenge challenge;
      try {
        challenge =
            HandshakeChallenge.createSymmetricChallenge(player.getName(), password, key, iv);
      } catch (NoSuchPaddingException
          | IllegalBlockSizeException
          | NoSuchAlgorithmException
          | BadPaddingException
          | InvalidKeyException
          | InvalidAlgorithmParameterException e) {
        throw new RuntimeException("Unable to create challenge", e);
      }

      var authTypeMsg =
          UseAuthTypeMsg.newBuilder()
              .setAuthType(AuthTypeEnum.SHARED_PASSWORD)
              .setSalt(ByteString.copyFrom(playerPasswordSalt))
              .setIv(ByteString.copyFrom(iv))
              .addChallenge(ByteString.copyFrom(challenge.getChallenge()));
      var handshakeMsg = HandshakeMsg.newBuilder().setUseAuthTypeMsg(authTypeMsg).build();
      sendMessage(handshakeMsg);

      return new AwaitingClientSharedPasswordAuthState(player, challenge);
    }

    /** Send the authentication type message when using role based shared passwords. */
    private IState sendRoleSharedPasswordAuthType(Player player) {
      byte[] playerPasswordSalt = playerDatabase.getPlayerPasswordSalt(player.getName());

      SecureRandom rnd = new SecureRandom();
      byte[] iv = new byte[CipherUtil.CIPHER_BLOCK_SIZE];
      rnd.nextBytes(iv);

      final var gmPassword = new PasswordGenerator().getPassword();
      final var playerPassword = new PasswordGenerator().getPassword();

      final HandshakeChallenge gmChallenge, playerChallenge;
      try {
        gmChallenge =
            HandshakeChallenge.createSymmetricChallenge(
                player.getName(),
                gmPassword,
                playerDatabase.getRolePassword(Player.Role.GM).get(),
                iv);
        playerChallenge =
            HandshakeChallenge.createSymmetricChallenge(
                player.getName(),
                playerPassword,
                playerDatabase.getRolePassword(Player.Role.PLAYER).get(),
                iv);
      } catch (NoSuchPaddingException
          | IllegalBlockSizeException
          | NoSuchAlgorithmException
          | BadPaddingException
          | InvalidKeyException
          | InvalidAlgorithmParameterException e) {
        throw new RuntimeException("Unable to create challenge", e);
      }

      var authTypeMsg =
          UseAuthTypeMsg.newBuilder()
              .setAuthType(AuthTypeEnum.SHARED_PASSWORD)
              .setSalt(ByteString.copyFrom(playerPasswordSalt))
              .setIv(ByteString.copyFrom(iv))
              .addChallenge(ByteString.copyFrom(gmChallenge.getChallenge()))
              .addChallenge(ByteString.copyFrom(playerChallenge.getChallenge()));
      var handshakeMsg = HandshakeMsg.newBuilder().setUseAuthTypeMsg(authTypeMsg).build();
      sendMessage(handshakeMsg);

      return new AwaitingClientRolePasswordAuthState(player, gmChallenge, playerChallenge);
    }

    /**
     * Send the authentication type message when using asymmetric keys
     *
     * @return the new state for the state machine.
     */
    private IState sendAsymmetricKeyAuthType(Player player, MD5Key playerPublicKeyMD5)
        throws ProtocolError {
      if (!playerDatabase.hasPublicKey(player, playerPublicKeyMD5).join()) {
        if (useEasyConnect) {
          return requestPublicKey(player.getName());
        }

        throw ProtocolError.incorrectPublicKey();
      }

      CipherUtil.Key publicKey;
      try {
        publicKey = playerDatabase.getPublicKey(player, playerPublicKeyMD5).get();
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException("Unable to create public key", e);
      }
      String password = new PasswordGenerator().getPassword();
      HandshakeChallenge gmChallenge;
      try {
        gmChallenge =
            HandshakeChallenge.createAsymmetricChallenge(player.getName(), password, publicKey);
      } catch (NoSuchPaddingException
          | IllegalBlockSizeException
          | NoSuchAlgorithmException
          | BadPaddingException
          | InvalidKeyException
          | InvalidAlgorithmParameterException e) {
        throw new RuntimeException("Unable to create challenge", e);
      }

      var authTypeMsg =
          UseAuthTypeMsg.newBuilder()
              .setAuthType(AuthTypeEnum.ASYMMETRIC_KEY)
              .addChallenge(ByteString.copyFrom(gmChallenge.getChallenge()));
      var handshakeMsg = HandshakeMsg.newBuilder().setUseAuthTypeMsg(authTypeMsg).build();
      sendMessage(handshakeMsg);

      return new AwaitingClientPublicKeyAuthState(player, gmChallenge);
    }
  }

  private class AwaitingClientRolePasswordAuthState implements IState {
    private final Player player;
    private final HandshakeChallenge gmChallenge;
    private final HandshakeChallenge playerChallenge;

    public AwaitingClientRolePasswordAuthState(
        Player player, HandshakeChallenge gmChallenge, HandshakeChallenge playerChallenge) {
      this.player = player;
      this.gmChallenge = gmChallenge;
      this.playerChallenge = playerChallenge;
    }

    @Override
    public final IState handle(HandshakeMsg handshakeMsg) throws ProtocolError {
      if (handshakeMsg.getMessageTypeCase() != HandshakeMsg.MessageTypeCase.CLIENT_AUTH_MESSAGE) {
        throw ProtocolError.invalidHandshake();
      }
      final var clientAuthMessage = handshakeMsg.getClientAuthMessage();

      byte[] response = clientAuthMessage.getChallengeResponse().toByteArray();
      var iv = clientAuthMessage.getIv().toByteArray();
      try {
        final Player connectedPlayer;
        if (Arrays.compare(response, gmChallenge.getExpectedResponse(iv)) == 0) {
          connectedPlayer = playerDatabase.getPlayerWithRole(player.getName(), Player.Role.GM);
        } else if (Arrays.compare(response, playerChallenge.getExpectedResponse(iv)) == 0) {
          connectedPlayer = playerDatabase.getPlayerWithRole(player.getName(), Player.Role.PLAYER);
        } else {
          throw ProtocolError.incorrectPassword();
        }
        return new SuccessState(connectedPlayer);
      } catch (InvalidAlgorithmParameterException
          | NoSuchPaddingException
          | IllegalBlockSizeException
          | NoSuchAlgorithmException
          | BadPaddingException
          | InvalidKeySpecException
          | InvalidKeyException e) {
        throw ProtocolError.incorrectPassword();
      }
    }
  }

  private class AwaitingClientSharedPasswordAuthState implements IState {
    private final Player player;
    private final HandshakeChallenge gmChallenge;

    public AwaitingClientSharedPasswordAuthState(Player player, HandshakeChallenge gmChallenge) {
      this.player = player;
      this.gmChallenge = gmChallenge;
    }

    @Override
    public IState handle(HandshakeMsg handshakeMsg) throws ProtocolError {
      if (handshakeMsg.getMessageTypeCase() != HandshakeMsg.MessageTypeCase.CLIENT_AUTH_MESSAGE) {
        throw ProtocolError.invalidHandshake();
      }
      final var clientAuthMessage = handshakeMsg.getClientAuthMessage();

      byte[] response = clientAuthMessage.getChallengeResponse().toByteArray();
      var iv = clientAuthMessage.getIv().toByteArray();
      try {
        if (Arrays.compare(response, gmChallenge.getExpectedResponse(iv)) != 0) {
          throw ProtocolError.incorrectPassword();
        }
      } catch (NoSuchPaddingException
          | IllegalBlockSizeException
          | NoSuchAlgorithmException
          | BadPaddingException
          | InvalidKeyException
          | InvalidAlgorithmParameterException e) {
        throw ProtocolError.incorrectPassword();
      }

      return new SuccessState(player);
    }
  }

  private class AwaitingClientPublicKeyAuthState implements IState {
    private final Player player;
    private final HandshakeChallenge challenge;

    public AwaitingClientPublicKeyAuthState(Player player, HandshakeChallenge challenge) {
      this.player = player;
      this.challenge = challenge;
    }

    @Override
    public IState handle(HandshakeMsg handshakeMsg) throws ProtocolError {
      if (handshakeMsg.getMessageTypeCase() != HandshakeMsg.MessageTypeCase.CLIENT_AUTH_MESSAGE) {
        throw ProtocolError.invalidHandshake();
      }
      final var clientAuthMessage = handshakeMsg.getClientAuthMessage();

      byte[] response = clientAuthMessage.getChallengeResponse().toByteArray();
      if (Arrays.compare(response, challenge.getExpectedResponse()) != 0) {
        throw ProtocolError.incorrectPublicKey();
      }

      return new SuccessState(player);
    }
  }

  private class AwaitingPublicKeyState implements IState {
    /** The pin for the new public key easy connect request. */
    private final String easyConnectPin;

    /** The username for the new public key easy connect request. */
    private final String easyConnectName;

    private boolean approvalResponseReceived = false;

    public AwaitingPublicKeyState(String easyConnectPin, String easyConnectName) {
      this.easyConnectPin = easyConnectPin;
      this.easyConnectName = easyConnectName;
    }

    @Override
    public void beforeTransitionFrom() {
      SwingUtilities.invokeLater(
          () -> MapTool.getFrame().getConnectionPanel().removeAwaitingApproval(easyConnectName));
    }

    @Override
    public IState handle(HandshakeMsg handshakeMsg) throws ProtocolError {
      if (handshakeMsg.getMessageTypeCase() != HandshakeMsg.MessageTypeCase.PUBLIC_KEY_UPLOAD_MSG) {
        throw ProtocolError.invalidHandshake();
      }
      final var publicKeyUploadMsg = handshakeMsg.getPublicKeyUploadMsg();

      // This state is unusual. It actually cannot directly change states, but rather requires user
      // interaction to approve the state.
      // It's also the only case I know of that inherently requires cross-thread communication. If
      // we can rein that in it would be muchos grande.

      var pendingPlayer =
          new PlayerAwaitingApproval(
              easyConnectName,
              easyConnectPin,
              Player.Role.PLAYER,
              publicKeyUploadMsg.getPublicKey(),
              this::acceptNewPublicKey,
              this::denyNewPublicKey);
      SwingUtilities.invokeLater(
          () -> MapTool.getFrame().getConnectionPanel().addAwaitingApproval(pendingPlayer));

      return this;
    }

    private void denyNewPublicKey(PlayerAwaitingApproval p) {
      approvalResponseReceived = true;
      setProtocolError(HandshakeResponseCodeMsg.SERVER_DENIED, "Handshake.msg.deniedEasyConnect");
    }

    private void acceptNewPublicKey(PlayerAwaitingApproval p) {
      if (approvalResponseReceived) {
        // Protect from event being fired more than once
        return;
      }
      approvalResponseReceived = true;

      try {
        var playerDb = (PersistedPlayerDatabase) playerDatabase;
        var pl = playerDatabase.getPlayer(p.name());
        if (pl == null) {
          playerDb.addPlayerAsymmetricKey(p.name(), p.role(), Set.of(p.publicKey()));
        } else {
          playerDb.addAsymmetricKeys(pl.getName(), Set.of(p.publicKey()));
          if (pl.getRole() != p.role()) {
            playerDb.setRole(pl.getName(), p.role());
          }
          playerDb.commitChanges();
        }
        var publicKeyAddedMsgBuilder = PublicKeyAddedMsg.newBuilder();
        publicKeyAddedMsgBuilder.setPublicKey(p.publicKey());
        sendMessage(
            HandshakeMsg.newBuilder().setPublicKeyAddedMsg(publicKeyAddedMsgBuilder).build());
        transitionToState(new AwaitingClientInitState());
      } catch (NoSuchPaddingException
          | NoSuchAlgorithmException
          | InvalidAlgorithmParameterException
          | InvalidKeySpecException
          | InvalidKeyException
          | PasswordDatabaseException e) {
        log.error("Error adding public key", e);
        setProtocolError(
            HandshakeResponseCodeMsg.INVALID_PUBLIC_KEY,
            I18N.getText("Handshake.msg.incorrectPublicKey"));
      } catch (Exception e) {
        setUnexpectedException(e);
      }
    }
  }

  // The following must reject messages generally, so not suitable for AState.
  private class SuccessState implements IState {
    /** The player that this connection is for. */
    private final Player player;

    public SuccessState(Player player) {
      this.player = player;
    }

    @Override
    public @Nonnull Player getPlayer() {
      return player;
    }

    @Override
    public final IState handle(HandshakeMsg message) throws ProtocolError {
      throw ProtocolError.invalidHandshake();
    }

    @Override
    public void beforeTransitionTo() {
      var server = MapTool.getServer();
      final DataStoreDto dataStoreDto;
      final AddOnLibraryListDto addOnLibraryListDto;
      try {
        dataStoreDto = new DataStoreManager().toDto().get();
        addOnLibraryListDto = new LibraryManager().addOnLibrariesToDto().get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException("Unable to get data store or library data", e);
      }
      var connectionSuccessfulMsg =
          ConnectionSuccessfulMsg.newBuilder()
              .setRoleDto(getPlayer().isGM() ? RoleDto.GM : RoleDto.PLAYER)
              .setServerPolicyDto(server.getPolicy().toDto())
              .setGameDataDto(dataStoreDto)
              .setAddOnLibraryListDto(addOnLibraryListDto);
      var handshakeMsg =
          HandshakeMsg.newBuilder().setConnectionSuccessfulMsg(connectionSuccessfulMsg).build();
      sendMessage(handshakeMsg);
    }

    @Override
    public void afterTransitionTo() {
      notifyObservers();
    }
  }

  private class PlayerBlockedState implements IState {
    private final Player player;

    public PlayerBlockedState(Player player) {
      this.player = player;
    }

    @Override
    public final IState handle(HandshakeMsg message) throws ProtocolError {
      throw ProtocolError.invalidHandshake();
    }

    @Override
    public void afterTransitionTo() {
      var blockedMsg =
          PlayerBlockedMsg.newBuilder().setReason(playerDatabase.getBlockedReason(player)).build();
      var msg = HandshakeMsg.newBuilder().setPlayerBlockedMsg(blockedMsg).build();
      sendMessage(msg);
    }
  }

  private static class ErrorState implements IState {
    /** Message for any error that has occurred, {@code null} if no error has occurred. */
    private final @Nonnull String message;

    /**
     * Any exception that occurred that causes an error, {@code null} if no exception which causes
     * an error has occurred.
     */
    private final @Nullable Exception exception;

    public ErrorState(@Nonnull String message) {
      this(message, null);
    }

    public ErrorState(@Nonnull String message, @Nullable Exception exception) {
      this.message = message;
      this.exception = exception;
    }

    @Override
    public @Nonnull String getErrorMessage() {
      return this.message;
    }

    @Override
    public @Nullable Exception getException() {
      return exception;
    }

    @Override
    public final IState handle(HandshakeMsg message) throws ProtocolError {
      throw ProtocolError.invalidHandshake();
    }
  }

  private static final class ProtocolError extends Exception {
    public final HandshakeResponseCodeMsg errorCode;

    public ProtocolError(HandshakeResponseCodeMsg response, String message) {
      super(message);
      this.errorCode = response;
    }

    public static ProtocolError invalidHandshake() {
      return new ProtocolError(
          HandshakeResponseCodeMsg.INVALID_HANDSHAKE,
          I18N.getText("Handshake.msg.invalidHandshake"));
    }

    public static ProtocolError incorrectPassword() {
      return new ProtocolError(
          HandshakeResponseCodeMsg.INVALID_PASSWORD,
          I18N.getText("Handshake.msg.incorrectPassword"));
    }

    public static ProtocolError incorrectPublicKey() {
      return new ProtocolError(
          HandshakeResponseCodeMsg.INVALID_PUBLIC_KEY,
          I18N.getText("Handshake.msg.incorrectPublicKey"));
    }
  }

  // endregion
}
