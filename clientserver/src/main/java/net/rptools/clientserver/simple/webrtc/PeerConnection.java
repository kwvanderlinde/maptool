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

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionIceErrorEvent;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCSignalingState;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PeerConnection {
  private static final Logger log = LogManager.getLogger(PeerConnection.class);

  private static RTCConfiguration makeRtcConfiguration() {
    final var rtcConfig = new RTCConfiguration();

    var googleStun = new RTCIceServer();
    googleStun.urls.add("stun:stun.l.google.com:19302");
    googleStun.urls.add("stun:stun1.l.google.com:19302");
    googleStun.urls.add("stun:stun2.l.google.com:19302");
    googleStun.urls.add("stun:stun3.l.google.com:19302");
    googleStun.urls.add("stun:stun4.l.google.com:19302");
    rtcConfig.iceServers.add(googleStun);

    var openRelayStun = new RTCIceServer();
    openRelayStun.urls.add("stun:openrelay.metered.ca:80");
    rtcConfig.iceServers.add(openRelayStun);

    var openRelayTurn = new RTCIceServer();
    openRelayTurn.urls.add("turn:openrelay.metered.ca:80");
    openRelayTurn.username = "openrelayproject";
    openRelayTurn.password = "openrelayproject";
    rtcConfig.iceServers.add(openRelayTurn);

    var openRelayTurn2 = new RTCIceServer();
    openRelayTurn2.urls.add("turn:openrelay.metered.ca:443");
    openRelayTurn2.username = "openrelayproject";
    openRelayTurn2.password = "openrelayproject";
    rtcConfig.iceServers.add(openRelayTurn2);

    var openRelayTurn3 = new RTCIceServer();
    openRelayTurn3.urls.add("turn:openrelay.metered.ca:443?transport=tcp");
    openRelayTurn3.username = "openrelayproject";
    openRelayTurn3.password = "openrelayproject";
    rtcConfig.iceServers.add(openRelayTurn3);

    return rtcConfig;
  }

  public interface Observer {
    void onIceCandidate(RTCIceCandidate candidate);

    void onDataChannel(RTCDataChannel channel);

    void onConnectionChange(RTCPeerConnectionState state);
  }

  private final RTCPeerConnection peerConnection;
  private State state;

  private final List<Observer> observers = new CopyOnWriteArrayList<>();
  private final Observer delegator;

  public PeerConnection() {
    this.peerConnection =
        new PeerConnectionFactory()
            .createPeerConnection(makeRtcConfiguration(), new PeerConnectionObserverImpl());
    this.state = new CreatedState();

    this.delegator =
        new Observer() {
          @Override
          public void onIceCandidate(RTCIceCandidate candidate) {
            observers.forEach(o -> o.onIceCandidate(candidate));
          }

          @Override
          public void onDataChannel(RTCDataChannel channel) {
            observers.forEach(o -> o.onDataChannel(channel));
          }

          @Override
          public void onConnectionChange(RTCPeerConnectionState state) {
            observers.forEach(o -> o.onConnectionChange(state));
          }
        };
  }

  public void addObserver(Observer observer) {
    this.observers.add(observer);
  }

  public void removeObserver(Observer observer) {
    this.observers.removeIf(observer::equals);
  }

  public boolean isAlive() {
    return state.isAlive();
  }

  public void close() {
    transitionTo(new ClosedState());
    peerConnection.close();
  }

  public RTCPeerConnectionState getConnectionState() {
    return peerConnection.getConnectionState();
  }

  private void transitionTo(State newState) {
    this.state = newState;
  }

  public CompletionStage<RTCDataChannel> createDataChannel(
      String label, RTCDataChannelInit initDict) {
    return state.createDataChannel(label, initDict);
  }

  /**
   * Offer a channel to the remote.
   *
   * @return A future providing the local session details.
   */
  public CompletionStage<RTCSessionDescription> offer() {
    return state.offer();
  }

  public CompletionStage<RTCSessionDescription> offerReceived(RTCSessionDescription offer) {
    return state.offerReceived(offer);
  }

  public CompletionStage<RTCSessionDescription> answer() {
    return state.answer();
  }

  public CompletionStage<RTCSessionDescription> answerReceived(RTCSessionDescription answer) {
    return state.answerReceived(answer);
  }

  public void addIceCandidate(RTCIceCandidate candidate) {
    state.addIceCandidate(candidate);
  }

  private abstract class State {
    public boolean isAlive() {
      return switch (peerConnection.getConnectionState()) {
        case CONNECTED, DISCONNECTED -> true;
        default -> false;
      };
    }

    public void addIceCandidate(RTCIceCandidate candidate) {
      peerConnection.addIceCandidate(candidate);
    }

    public CompletionStage<RTCDataChannel> createDataChannel(
        String label, RTCDataChannelInit initDict) {
      return CompletableFuture.failedFuture(new RuntimeException("Invalid state"));
    }

    public CompletionStage<RTCSessionDescription> offer() {
      return CompletableFuture.failedFuture(new RuntimeException("Invalid state"));
    }

    public CompletionStage<RTCSessionDescription> offerReceived(RTCSessionDescription offer) {
      return CompletableFuture.failedFuture(new RuntimeException("Invalid state"));
    }

    public CompletionStage<RTCSessionDescription> answer() {
      return CompletableFuture.failedFuture(new RuntimeException("Invalid state"));
    }

    public CompletionStage<RTCSessionDescription> answerReceived(RTCSessionDescription answer) {
      return CompletableFuture.failedFuture(new RuntimeException("Invalid state"));
    }
  }

  /** State the allows send an offer to a remote or accepting an offer from a remote. */
  private class CreatedState extends State {
    @Override
    public CompletionStage<RTCDataChannel> createDataChannel(
        String label, RTCDataChannelInit initDict) {
      final var channel = peerConnection.createDataChannel(label, initDict);
      final var future = CompletableFuture.completedFuture(channel);

      transitionTo(new PendingOfferState());

      return future;
    }

    @Override
    public CompletionStage<RTCSessionDescription> offerReceived(RTCSessionDescription offer) {
      final var future = new CompletableFuture<RTCSessionDescription>();

      var stage =
          future.whenComplete(
              (session, error) -> {
                if (error != null) {
                  transitionTo(new FailedState());
                } else {
                  transitionTo(new HaveRemoteOffer());
                }
              });

      peerConnection.setRemoteDescription(
          offer,
          new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
              future.complete(offer);
            }

            @Override
            public void onFailure(String error) {
              future.completeExceptionally(
                  new RuntimeException("Failed to set the session description locally: " + error));
            }
          });

      return stage;
    }
  }

  private class PendingOfferState extends State {
    public PendingOfferState() {}

    @Override
    public CompletionStage<RTCSessionDescription> offer() {
      final var future = new CompletableFuture<RTCSessionDescription>();
      var stage =
          future.whenComplete(
              (session, error) -> {
                if (error != null) {
                  transitionTo(new FailedState());
                } else {
                  transitionTo(new HaveLocalOffer());
                }
              });

      final var offerOptions = new RTCOfferOptions();
      peerConnection.createOffer(
          offerOptions,
          new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
              peerConnection.setLocalDescription(
                  description,
                  new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {
                      future.complete(description);
                    }

                    @Override
                    public void onFailure(String error) {
                      future.completeExceptionally(
                          new RuntimeException(
                              "Failed to set the session description locally: " + error));
                    }
                  });
            }

            @Override
            public void onFailure(String error) {
              future.completeExceptionally(
                  new RuntimeException("Failed to create the offer: " + error));
            }
          });

      return stage;
    }
  }

  /** State after offer is sent, waiting for an answer to be received. */
  private class HaveLocalOffer extends State {
    public HaveLocalOffer() {}

    @Override
    public CompletionStage<RTCSessionDescription> answerReceived(RTCSessionDescription answer) {
      final var future = new CompletableFuture<RTCSessionDescription>();

      var stage =
          future.whenComplete(
              (session, error) -> {
                if (error != null) {
                  transitionTo(new FailedState());
                } else {
                  transitionTo(new HaveRemoteAnswer());
                }
              });

      peerConnection.setRemoteDescription(
          answer,
          new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
              future.complete(answer);
            }

            @Override
            public void onFailure(String error) {
              future.completeExceptionally(
                  new RuntimeException("Failed to set the session description locally: " + error));
            }
          });

      return stage;
    }
  }

  /** State that permits answering a remote's offer. */
  private class HaveRemoteOffer extends State {
    @Override
    public CompletionStage<RTCSessionDescription> answer() {
      final var future = new CompletableFuture<RTCSessionDescription>();

      var stage =
          future.whenComplete(
              (session, error) -> {
                if (error != null) {
                  transitionTo(new FailedState());
                } else {
                  log.info("State be {}", peerConnection.getSignalingState());
                  transitionTo(new HaveLocalAnswer());
                }
              });

      var answerOptions = new RTCAnswerOptions();
      peerConnection.createAnswer(
          answerOptions,
          new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
              log.info("description type: {}", description.sdpType);

              peerConnection.setLocalDescription(
                  description,
                  new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {
                      future.complete(description);
                    }

                    @Override
                    public void onFailure(String error) {
                      future.completeExceptionally(
                          new RuntimeException(
                              "Failed to set the session description locally: " + error));
                    }
                  });
            }

            @Override
            public void onFailure(String error) {
              future.completeExceptionally(
                  new RuntimeException("Failed to create the answer: " + error));
            }
          });

      return stage;
    }
  }

  private class HaveLocalAnswer extends State {}

  private class HaveRemoteAnswer extends State {}

  private class FailedState extends State {}

  private class ClosedState extends State {
    @Override
    public boolean isAlive() {
      return false;
    }

    @Override
    public void addIceCandidate(RTCIceCandidate candidate) {
      log.warn("Discovered ICE candidate after connection is closed. Ignoring.");
    }
  }

  private final class PeerConnectionObserverImpl implements PeerConnectionObserver {
    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
      delegator.onIceCandidate(candidate);
    }

    @Override
    public void onSignalingChange(RTCSignalingState state) {
      log.info("State changed to {}; our state is {}", state, PeerConnection.this.state.getClass());
    }

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {
      // TODO Transition to Failure state if FAILED state or similar?
      delegator.onConnectionChange(state);
    }

    @Override
    public void onDataChannel(RTCDataChannel dataChannel) {
      delegator.onDataChannel(dataChannel);
    }

    @Override
    public void onIceCandidateError(RTCPeerConnectionIceErrorEvent event) {
      log.debug(
          "PeerConnection.onIceCandidateError: code:{} url: {} address/port: {}:{} text: {}",
          event.getErrorCode(),
          event.getUrl(),
          event.getAddress(),
          event.getPort(),
          event.getErrorText());
    }
  }
}
