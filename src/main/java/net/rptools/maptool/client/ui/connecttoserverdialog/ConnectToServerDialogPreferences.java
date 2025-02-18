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
package net.rptools.maptool.client.ui.connecttoserverdialog;

import java.util.prefs.Preferences;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.AppConstants;
import net.rptools.maptool.server.ServerConfig;

public class ConnectToServerDialogPreferences {

  private static Preferences prefs =
      Preferences.userRoot().node(AppConstants.APP_NAME + "/prefs/connect");

  private static final String KEY_USERNAME = "name";
  private static final String KEY_HOST = "host";
  private static final String KEY_PORT = "port";
  private static final String KEY_PASSWORD = "password";
  private static final String KEY_TAB = "tab";
  private static final String KEY_SERVER_NAME = "serverName";
  private static final String USE_PUBLIC_KEY = "usePublicKey";
  private static final String USE_WEB_RTC = "useWebRTC";

  @Nonnull
  public String getUsername() {
    return prefs.get(KEY_USERNAME, "");
  }

  public void setUsername(String name) {
    prefs.put(KEY_USERNAME, name);
  }

  public void setHost(String host) {
    prefs.put(KEY_HOST, host);
  }

  @Nonnull
  public String getHost() {
    return prefs.get(KEY_HOST, "");
  }

  public int getPort() {
    return prefs.getInt(KEY_PORT, ServerConfig.DEFAULT_PORT);
  }

  public void setPort(int port) {
    prefs.putInt(KEY_PORT, port);
  }

  public void setPassword(String password) {
    prefs.put(KEY_PASSWORD, password);
  }

  @Nonnull
  public String getPassword() {
    return prefs.get(KEY_PASSWORD, "");
  }

  public int getTab() {
    return prefs.getInt(KEY_TAB, 0);
  }

  public void setTab(int tab) {
    prefs.putInt(KEY_TAB, tab);
  }

  public void setServerName(String host) {
    prefs.put(KEY_SERVER_NAME, host);
  }

  @Nonnull
  public String getServerName() {
    return prefs.get(KEY_SERVER_NAME, "");
  }

  public boolean getUsePublicKey() {
    return prefs.getBoolean(USE_PUBLIC_KEY, false);
  }

  public void setUsePublicKey(boolean usePublicKey) {
    prefs.putBoolean(USE_PUBLIC_KEY, usePublicKey);
  }

  public boolean getUseWebRTC() {
    return prefs.getBoolean(USE_WEB_RTC, false);
  }

  public void setUseWebRTC(boolean useWebRTC) {
    prefs.putBoolean(USE_WEB_RTC, useWebRTC);
  }
}
