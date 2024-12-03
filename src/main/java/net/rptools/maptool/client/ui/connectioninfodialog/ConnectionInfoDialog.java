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
package net.rptools.maptool.client.ui.connectioninfodialog;

import java.awt.GridLayout;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.swing.AbeillePanel;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.server.MapToolServer;
import net.rptools.maptool.util.NetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectionInfoDialog extends JDialog {
  private static JTextField externalAddressLabel;

  private static final Logger log = LogManager.getLogger(ConnectionInfoDialog.class);

  /**
   * Get the IP-Address of the active NetworkInterface
   *
   * @param v6 Should the IPv6 Address be returnes (true) or the IPv4 Address (false)
   */
  private static String getIPAddress(boolean v6) throws SocketException {
    Enumeration<NetworkInterface> netInts = NetworkInterface.getNetworkInterfaces();
    for (NetworkInterface netInt : Collections.list(netInts)) {
      if (netInt.isUp() && !netInt.isLoopback()) {
        for (InetAddress inetAddress : Collections.list(netInt.getInetAddresses())) {

          if (inetAddress.isLoopbackAddress()
              || inetAddress.isLinkLocalAddress()
              || inetAddress.isMulticastAddress()) {
            continue;
          }

          try {
            InetAddress rptools = InetAddress.getByName("www.rptools.net");
          } catch (UnknownHostException e) {
            continue;
          }

          if (v6 && inetAddress instanceof Inet6Address) {
            return inetAddress.getHostAddress();
          }

          if (!v6 && inetAddress instanceof Inet4Address) {
            return inetAddress.getHostAddress();
          }
        }
      }
    }
    return null;
  }

  /**
   * This is the default constructor
   *
   * @param server the server instance for the connection dialog
   */
  public ConnectionInfoDialog(MapToolServer server) {
    super(MapTool.getFrame(), I18N.getText("ConnectionInfoDialog.title"), true);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(275, 275);

    AbeillePanel panel = new AbeillePanel(new ConnectionInfoDialogView().getRootComponent());

    JTextField nameLabel = panel.getTextField("name");
    JTextField localv4AddressLabel = panel.getTextField("localv4Address");
    JTextField localv6AddressLabel = panel.getTextField("localv6Address");
    JTextField portLabel = panel.getTextField("port");
    externalAddressLabel = panel.getTextField("externalAddress");

    String name = server.getName();
    if (name == null || name.isEmpty()) {
      name = "---";
    }

    String localv4Address = "Unknown";
    try {
      localv4Address = getIPAddress(false);
    } catch (IOException e) { // UnknownHost | Socket
      log.warn("Can't resolve our own IPv4 address!?", e);
    }

    String localv6Address = "Unknown";
    try {
      localv6Address = getIPAddress(true);
    } catch (IOException e) { // UnknownHost | Socket
      log.warn("Can't resolve our own IPv6 address!?", e);
    }

    int port = server.getPort();
    String portString = port < 0 ? "---" : Integer.toString(port);

    nameLabel.setText(name);
    localv4AddressLabel.setText(localv4Address);
    localv6AddressLabel.setText(localv6Address);
    externalAddressLabel.setText(I18N.getText("ConnectionInfoDialog.discovering"));
    portLabel.setText(portString);

    JButton okButton = (JButton) panel.getButton("okButton");
    bindOKButtonActions(okButton);

    setLayout(new GridLayout());
    ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    add(panel);

    NetUtil.getInstance()
        .getExternalAddress()
        .thenAccept(
            address -> {
              if (address != null) {
                SwingUtilities.invokeLater(
                    () -> externalAddressLabel.setText(NetUtil.formatAddress(address)));
              }
            });
  }

  @Override
  public void setVisible(boolean b) {
    if (b) {
      SwingUtil.centerOver(this, MapTool.getFrame());
    }
    super.setVisible(b);
  }

  /**
   * This method initializes okButton
   *
   * @return javax.swing.JButton
   */
  private void bindOKButtonActions(JButton okButton) {
    okButton.addActionListener(e -> setVisible(false));
  }
}
