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
package net.rptools.clientserver.simple.webrtc;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCSessionDescription;
import java.net.URI;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Sends message to the signaling server and receives messages from the signaling server.
 *
 * <p>Also defines the protocol for setting up peer connections.
 */
public class SignalingClient {
  public interface Observer {
    void onLogin(LoginMessageDto message);

    void onOffer(OfferMessageDto message);

    void onAnswer(AnswerMessageDto message);

    void onCandidate(CandidateMessageDto message);

    void onClose(int code, String reason, boolean remote);
  }

  private static final Logger log = LogManager.getLogger(SignalingClient.class);
  private static final String webSocketUrl = "ws://webrtc1.rptools.net:8080";

  private final Gson gson = new Gson();
  private final ScheduledExecutorService reconnector =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setNameFormat("SignalingClient.reconnectThread-%d")
              .build());
  private final List<Observer> observers = new CopyOnWriteArrayList<>();

  private final String sourceId;
  private final Client client;

  public SignalingClient(String sourceId, int maxReconnectAttempts) {
    this.sourceId = sourceId;

    URI webSocketUri = null;
    try {
      webSocketUri = new URI(webSocketUrl);
    } catch (Exception e) {
      assert false : "The hardcoded WebSocket URL must be a valid a URI";
    }

    this.client = new Client(webSocketUri, maxReconnectAttempts);
  }

  public void addObserver(Observer observer) {
    this.observers.add(observer);
  }

  public void removeObserver(Observer observer) {
    this.observers.removeIf(observer::equals);
  }

  public void start() {
    this.client.connect();
  }

  public void close() {
    this.client.close();
  }

  public boolean isOpen() {
    return this.client.isOpen();
  }

  public void sendOffer(String destination, RTCSessionDescription description) {
    log.info("SignalingClient.sendOffer()");

    var msg = new OfferMessageDto();
    msg.offer = description;
    msg.source = sourceId;
    msg.destination = destination;
    this.client.sendMessage(gson.toJson(msg));
  }

  public void sendAnswer(String destination, RTCSessionDescription description) {
    log.info("SignalingClient.sendAnswer()");
    var msg = new AnswerMessageDto();
    msg.source = sourceId;
    msg.destination = destination;
    msg.answer = description;
    this.client.sendMessage(gson.toJson(msg));
  }

  public void sendCandidate(String destination, RTCIceCandidate candidate) {
    log.info("SignalingClient.sendCandidate()");

    var msg = new CandidateMessageDto();
    msg.destination = destination;
    msg.source = sourceId;
    msg.candidate = candidate;
    this.client.sendMessage(gson.toJson(msg));
  }

  private void handleSignalingMessage(String message) {
    var msg = gson.fromJson(message, MessageDto.class);
    switch (msg.type) {
      case "login" -> {
        log.info("SignalingClient.onLogin()");
        final var loginMsg = gson.fromJson(message, LoginMessageDto.class);
        fireEvent(o -> o.onLogin(loginMsg));
      }
      case "answer" -> {
        log.info("SignalingClient.onAnswer()");
        final var answerMsg = gson.fromJson(message, AnswerMessageDto.class);
        fireEvent(o -> o.onAnswer(answerMsg));
      }
      case "offer" -> {
        log.info("SignalingClient.onOffer()");
        final var offerMsg = gson.fromJson(message, OfferMessageDto.class);
        fireEvent(o -> o.onOffer(offerMsg));
      }
      case "candidate" -> {
        log.info("SignalingClient.onCandidate()");
        final var candidateMsg = gson.fromJson(message, CandidateMessageDto.class);
        fireEvent(o -> o.onCandidate(candidateMsg));
      }
      default -> {
        log.warn(
            "{} received unknown signaling message type: {}; source: {}",
            sourceId,
            msg.type,
            msg.source);
      }
    }
  }

  private void fireEvent(Consumer<Observer> consumer) {
    observers.forEach(consumer);
  }

  private final class Client extends WebSocketClient {
    private final Random random;
    private final FibonacciBackoff backoff;
    private final int maxReconnectAttempts;
    private int remainingReconnects;

    public Client(URI serverUri, int maxReconnectAttempts) {
      super(serverUri);
      this.random = new Random();
      this.backoff = new FibonacciBackoff(10, 5000, TimeUnit.MILLISECONDS);
      this.maxReconnectAttempts = maxReconnectAttempts;
      this.remainingReconnects = this.maxReconnectAttempts;
    }

    private void sendMessage(String message) {
      log.debug("{} sent signaling message: {}", sourceId, message);
      this.send(message);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
      remainingReconnects = this.maxReconnectAttempts;
      backoff.reset();

      log.info("{} WebSocket connected", sourceId);

      var msg = new LoginMessageDto();
      msg.source = sourceId;

      sendMessage(gson.toJson(msg));
    }

    @Override
    public void onMessage(String message) {
      log.debug("{} got signaling message: {}", sourceId, message);

      handleSignalingMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
      log.info("{} closed. code: {}; reason: {}; remote: {}", sourceId, code, reason, remote);

      if (code != 1000 && remainingReconnects > 0) {
        // TODO Should we only reconnect on remote closure?
        // TODO We could be more precise about which codes result in a reconnect attempt.

        // If the connection gets closed unexpectedly, try to reconnect.
        --remainingReconnects;
        log.warn(
            "{} lost connection to the signaling server; reconnecting; {} attempt left",
            sourceId,
            remainingReconnects);

        final var delay = random.nextLong(backoff.next());
        reconnector.schedule(this::reconnect, delay, backoff.getTimeUnit());

        return;
      }

      fireEvent(o -> o.onClose(code, reason, remote));
    }

    @Override
    public void onError(Exception ex) {
      log.error("{} encountered an unexpected WebSocket exception", sourceId, ex);
      // onClose will be called after this
    }
  }

  private static final class FibonacciBackoff {
    private final long initialBackoff;
    private final long maxDelay;
    private final TimeUnit unit;

    /** Invariant: nextBackoff <= maxDelay */
    private long currentBackoff;

    /** Invariant: nextBackoff <= maxDelay */
    private long nextBackoff;

    public FibonacciBackoff(long initialBackoff, long maxDelay, TimeUnit unit) {
      if (maxDelay < 1) {
        throw new IllegalArgumentException("Max delay cannot be negative.");
      }

      this.initialBackoff = Math.clamp(initialBackoff, 0, maxDelay);
      this.maxDelay = maxDelay;
      this.unit = unit;

      this.currentBackoff = this.initialBackoff;
      this.nextBackoff = this.initialBackoff;
    }

    public void reset() {
      currentBackoff = this.initialBackoff;
      nextBackoff = initialBackoff;
    }

    public TimeUnit getTimeUnit() {
      return unit;
    }

    public long next() {
      var result = nextBackoff;
      nextBackoff = Math.min(maxDelay, nextBackoff + currentBackoff);
      currentBackoff = result;

      return result;
    }
  }
}
