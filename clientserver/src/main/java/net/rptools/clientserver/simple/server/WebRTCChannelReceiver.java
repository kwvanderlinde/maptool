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

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import java.util.HashMap;
import java.util.Map;
import net.rptools.clientserver.simple.channels.WebRTCChannel;
import net.rptools.clientserver.simple.webrtc.AnswerMessageDto;
import net.rptools.clientserver.simple.webrtc.CandidateMessageDto;
import net.rptools.clientserver.simple.webrtc.LoginMessageDto;
import net.rptools.clientserver.simple.webrtc.OfferMessageDto;
import net.rptools.clientserver.simple.webrtc.PeerConnection;
import net.rptools.clientserver.simple.webrtc.SignalingClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles connection requests through a signaling server.
 *
 * <p>Connections are tracked until a data channel is established. At that point, the connection is
 * yielded as a `Connection` object.
 */
public class WebRTCChannelReceiver extends AbstractChannelReceiver {
  private static final Logger log = LogManager.getLogger(WebRTCChannelReceiver.class);

  private final SignalingClient signalingClient;

  private final Map<String, PeerConnection> pendingConnections = new HashMap<>();

  public WebRTCChannelReceiver(String serverName) {
    this.signalingClient = new SignalingClient(serverName, 5);
    this.signalingClient.addObserver(new SignalingClientObserver());
  }

  @Override
  public void start() {
    this.signalingClient.start();
  }

  @Override
  public void close() {
    // TODO Close the signaling client.
  }

  private PeerConnection initConnection() {
    return new PeerConnection();
  }

  private final class SignalingClientObserver implements SignalingClient.Observer {
    @Override
    public void onLogin(LoginMessageDto message) {
      if (!message.success) {
        // TODO Translated string. "ServerDialog.error.serverAlreadyExists"
        onError(new RuntimeException("Failed to log in to signaling server"));
      }
    }

    @Override
    public void onOffer(OfferMessageDto message) {
      final var offer = message.offer;
      final var clientId = message.source;

      // TODO Reject an offer from a known client?
      var peerConnection = initConnection();
      peerConnection.addObserver(new PeerConnectionObserverImpl(clientId));

      pendingConnections.put(clientId, peerConnection);

      peerConnection
          .offerReceived(offer)
          .thenCompose(session -> peerConnection.answer())
          .thenAccept(session -> signalingClient.sendAnswer(clientId, session));
    }

    @Override
    public void onAnswer(AnswerMessageDto message) {
      // We send the answers, clients shouldn't be sending them to us.
      log.warn(
          "{} Received an unexpected WebRTC answer from {}", message.destination, message.source);
    }

    @Override
    public void onCandidate(CandidateMessageDto message) {
      // TODO Check that the message source actually exists.
      var peerConnection = pendingConnections.get(message.source);
      if (peerConnection != null) {
        peerConnection.addIceCandidate(message.candidate);
      }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
      if (code == 1000) {
        return;
      }

      var message = String.format("Unexpected closure: remote: %s (%d) %s", remote, code, reason);
      onError(new RuntimeException(message));
    }
  }

  private final class PeerConnectionObserverImpl implements PeerConnection.Observer {
    private final String clientId;

    public PeerConnectionObserverImpl(String clientId) {
      this.clientId = clientId;
    }

    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
      log.info("Connection {} produced an ICE candidate", clientId);
      signalingClient.sendCandidate(clientId, candidate);
    }

    @Override
    public void onDataChannel(RTCDataChannel rtcDataChannel) {
      log.info("Connection {} produced a data channel", clientId);

      var peerConnection = pendingConnections.remove(clientId);
      if (peerConnection != null) {
        peerConnection.removeObserver(this);

        // TODO Does the connection somehow need to reference the PeerConnection? Perhaps to recover
        //  a data channel in case of temporary disconnect? Is that even something we can handle?
        var channel = new WebRTCChannel(rtcDataChannel);
        onConnected(clientId, channel);
      }
    }

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {
      switch (state) {
          // TODO Original just had FAILED, but this situation is different. Double check my logic
        case DISCONNECTED, FAILED, CLOSED -> {
          // Peer connection no longer useful
          log.warn("Connection {} closed unexpectedly", clientId);

          // TODO Any more clean up that is needed?
          pendingConnections.remove(clientId);
        }
      }
    }
  }
}
