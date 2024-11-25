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
package net.rptools.maptool.client;

public class RemoteServerConfig {
  private final int port;
  private final String serverName;
  private final String hostName;
  private final boolean useWebRTC;

  public RemoteServerConfig(int port, String serverName, String hostName, boolean useWebRTC) {
    this.port = port;
    this.serverName = serverName;
    this.hostName = hostName;
    this.useWebRTC = useWebRTC;
  }

  public String getServerName() {
    return serverName;
  }

  public int getPort() {
    return port;
  }

  public String getHostName() {
    return hostName;
  }

  public boolean getUseWebRTC() {
    return useWebRTC;
  }
}
