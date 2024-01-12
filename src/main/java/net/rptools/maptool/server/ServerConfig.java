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

import net.rptools.maptool.util.PasswordGenerator;

public class ServerConfig {
  public static final int DEFAULT_PORT = 51234;

  private static final String personalServerGMPassword;

  private static final String personalServerPlayerPassword;

  static {
    PasswordGenerator passwordGenerator = new PasswordGenerator();
    // Generate a random password for personal server
    personalServerGMPassword = passwordGenerator.getPassword();
    String playerPass = passwordGenerator.getPassword();
    if (playerPass.equals(personalServerGMPassword)) { // super unlikely but just to play safe
      personalServerPlayerPassword = playerPass + "!";
    } else {
      personalServerPlayerPassword = playerPass;
    }
  }

  private int port;
  private String hostPlayerId;
  private final String gmPassword;
  private final String playerPassword;
  private String serverName;
  private String hostName;
  private final boolean useEasyConnect;
  private final boolean useWebRTC;

  public static String getPersonalServerGMPassword() {
    return personalServerGMPassword;
  }

  public static String getPersonalServerPlayerPassword() {
    return personalServerPlayerPassword;
  }

  public ServerConfig() {
    playerPassword = getPersonalServerPlayerPassword();
    gmPassword = getPersonalServerGMPassword();
    useEasyConnect = false;
    useWebRTC = false;
  }

  public ServerConfig(
      String hostPlayerId,
      String gmPassword,
      String playerPassword,
      int port,
      String serverName,
      String hostName,
      boolean useWebRTC) {
    this(hostPlayerId, gmPassword, playerPassword, port, serverName, hostName, false, useWebRTC);
  }

  public ServerConfig(
      String hostPlayerId,
      String gmPassword,
      String playerPassword,
      int port,
      String serverName,
      String hostName,
      boolean useEasyConnect,
      boolean useWebRTC) {
    this.hostPlayerId = hostPlayerId;
    this.gmPassword = gmPassword;
    this.playerPassword = playerPassword;
    this.port = port;
    this.serverName = serverName;
    this.hostName = hostName;
    this.useEasyConnect = useEasyConnect;
    this.useWebRTC = useWebRTC;
  }

  public String getHostPlayerId() {
    return hostPlayerId;
  }

  public boolean isServerRegistered() {
    return serverName != null && !serverName.isEmpty();
  }

  public String getServerName() {
    return serverName;
  }

  public int getPort() {
    return port;
  }

  public String getGmPassword() {
    return gmPassword;
  }

  public String getPlayerPassword() {
    return playerPassword;
  }

  public String getHostName() {
    return hostName;
  }

  public boolean getUseEasyConnect() {
    return useEasyConnect;
  }

  public boolean getUseWebRTC() {
    return useWebRTC;
  }
}
