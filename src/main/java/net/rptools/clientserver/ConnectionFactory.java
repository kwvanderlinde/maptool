package net.rptools.clientserver;

import net.rptools.clientserver.hessian.client.IMethodClientConnection;
import net.rptools.clientserver.hessian.client.MethodClientConnection;
import net.rptools.clientserver.hessian.server.IMethodServerConnection;
import net.rptools.clientserver.hessian.server.MethodServerConnection;
import net.rptools.clientserver.simple.IConnection;
import net.rptools.clientserver.simple.client.SocketClientConnection;
import net.rptools.clientserver.simple.client.WebRTCClientConnection;
import net.rptools.clientserver.simple.server.IHandshake;
import net.rptools.clientserver.simple.server.SocketServerConnection;
import net.rptools.clientserver.simple.server.WebRTCServerConnection;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.server.ServerConfig;

import java.io.IOException;

public class ConnectionFactory {
  private static ConnectionFactory instance = new ConnectionFactory();

  public static ConnectionFactory getInstance() {
    return instance;
  }

  public IMethodClientConnection createClientConnection(String id, ServerConfig config) throws IOException {
    if(!AppState.useWebRTC())
      return new MethodClientConnection(new SocketClientConnection(id, config.getHostName(), config.getPort()));

    return new MethodClientConnection(new WebRTCClientConnection(id, config));
  }

  public IMethodServerConnection createServerConnection(ServerConfig config, IHandshake handshake) throws IOException {
    if(!AppState.useWebRTC())
      return new MethodServerConnection(new SocketServerConnection(config.getPort(), handshake));

    return new MethodServerConnection(new WebRTCServerConnection(config, handshake));
  }
}
