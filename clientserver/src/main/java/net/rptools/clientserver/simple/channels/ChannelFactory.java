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
package net.rptools.clientserver.simple.channels;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.rptools.clientserver.simple.webrtc.AnswerMessageDto;
import net.rptools.clientserver.simple.webrtc.CandidateMessageDto;
import net.rptools.clientserver.simple.webrtc.LoginMessageDto;
import net.rptools.clientserver.simple.webrtc.OfferMessageDto;
import net.rptools.clientserver.simple.webrtc.PeerConnection;
import net.rptools.clientserver.simple.webrtc.SignalingClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.framing.CloseFrame;

public class ChannelFactory {
  private static final Logger log = LogManager.getLogger(ChannelFactory.class);

  public CompletionStage<SocketChannel> createSocketChannel(String hoseName, int port) {
    return CompletableFuture.completedFuture(new SocketChannel(hoseName, port));
  }

  public CompletionStage<WebRTCChannel> createWebRTCChannel(String clientid, String serverName) {
    final var future = new CompletableFuture<WebRTCChannel>();

    CompletionStage<WebRTCChannel> stage =
        future.whenComplete(
            (channel, error) -> {
              if (error != null) {
                log.error("Error while creating data channel", error);
              }
            });

    var signalingClient = new SignalingClient(clientid, 5);
    stage = stage.whenComplete((channel, error) -> signalingClient.close());

    signalingClient.addObserver(new SignalingClientObserver(serverName, signalingClient, future));
    signalingClient.start();

    return stage;
  }

  private static final class SignalingClientObserver implements SignalingClient.Observer {
    private final String serverName;
    private final SignalingClient signalingClient;
    private final CompletableFuture<? super WebRTCChannel> future;
    private PeerConnection peerConnection = null;

    public SignalingClientObserver(
        String serverName,
        SignalingClient signalingClient,
        CompletableFuture<? super WebRTCChannel> future) {
      this.serverName = serverName;
      this.signalingClient = signalingClient;
      this.future = future;
    }

    @Override
    public void onLogin(LoginMessageDto message) {
      if (!message.success) {
        future.completeExceptionally(new IOException("Failed login to signaling server"));
      }

      peerConnection = new PeerConnection();

      var initDict = new RTCDataChannelInit();
      peerConnection
          .createDataChannel("myDataChannel", initDict)
          .thenCompose(
              channel -> {
                channel.registerObserver(new RTCDataChannelObserverImpl(channel, future));
                return peerConnection.offer();
              })
          .thenAccept(description -> signalingClient.sendOffer(serverName, description));
    }

    @Override
    public void onOffer(OfferMessageDto message) {
      log.warn(
          "{} Received an unexpected WebRTC offer from {}", message.destination, message.source);
    }

    @Override
    public void onAnswer(AnswerMessageDto message) {
      // peer connection will have been set after login. If not, we have bigger problems.
      peerConnection.answerReceived(message.answer).thenAccept(description -> {});
    }

    @Override
    public void onCandidate(CandidateMessageDto message) {
      peerConnection.addIceCandidate(message.candidate);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
      if (code != CloseFrame.NORMAL) {
        log.error(
            "Unexpected closure of socket server. Code: {}; Reason: {}; Remote: {}",
            code,
            reason,
            remote);
        future.completeExceptionally(
            new IOException(
                String.format("Unexpected closure of socket server. Reason: %s", reason)));
      }
    }
  }

  private static final class RTCDataChannelObserverImpl implements RTCDataChannelObserver {
    private final RTCDataChannel dataChannel;
    private final CompletableFuture<? super WebRTCChannel> future;

    public RTCDataChannelObserverImpl(
        RTCDataChannel dataChannel, CompletableFuture<? super WebRTCChannel> future) {
      this.dataChannel = dataChannel;
      this.future = future;
    }

    @Override
    public void onBufferedAmountChange(long amount) {
      log.info("Data channel buffered amount changed to {}", amount);
    }

    @Override
    public void onStateChange() {
      var state = dataChannel.getState();
      log.info("Data channel state changed to {}", state);
      switch (state) {
        case OPEN -> {
          // It had better be this listener. The library gives us no way to be specific. Honestly,
          // its lack of proper observers is the one things that makes me groan with this library.
          dataChannel.unregisterObserver();
          future.complete(new WebRTCChannel(dataChannel));
        }
        case CLOSED -> {
          log.error("Data channel unexpectedly closed");
          future.completeExceptionally(new IOException("Data channel unexpectedly closed"));
        }
      }
    }

    @Override
    public void onMessage(RTCDataChannelBuffer buffer) {
      log.warn("Received message prior to connection being finalized.");
    }
  }
}
