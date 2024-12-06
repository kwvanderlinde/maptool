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
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
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

  record LocalAddresses(@Nonnull List<Inet4Address> ipv4, @Nonnull List<Inet6Address> ipv6) {}

  record AddressInfo<T extends InetAddress>(
      @Nonnull T address, boolean isRoutable, boolean isLinkLocal) {}

  /**
   * Get properties of the address necessary to sort in reachability order.
   *
   * <p>This adds to the list directly if this requires no network requests or adds a task to do so
   * to the tasks list.
   *
   * <p>This checks routability by connecting from this address to the rptools one using SSL's port
   * 443 rather than IGMP (ping) or TCP port 7 so strict firewalls won't cause false negatives.
   *
   * @param address The address to get info for
   * @param rptools The rptools address to check routability to
   * @param infos Output list to add info to
   * @param tasks Output list of tasks that should be awaited for results
   */
  private static <T extends InetAddress> void getInfo(
      @Nonnull T address,
      @Nonnull T rptools,
      @Nonnull List<AddressInfo<T>> infos,
      @Nonnull List<Callable<Void>> tasks) {
    if (rptools == null) {
      infos.add(new AddressInfo(address, true /*isRoutable*/, address.isLinkLocalAddress()));
      return;
    }

    tasks.add(
        () -> {
          boolean isRoutable = true;
          try (var s = new Socket(rptools, 443, address, 0)) {
          } catch (IOException | SecurityException | IllegalArgumentException e) {
            isRoutable = false;
          }
          infos.add(new AddressInfo(address, isRoutable, address.isLinkLocalAddress()));
          return null;
        });
  }

  /**
   * Sort the address information into order of most reachable first.
   *
   * <p>Routable addresses are more reachable than non-routable then non-link-local addresses are
   * more reachable than link-local ones.
   */
  private static <T extends InetAddress> void sortInfos(@Nonnull List<AddressInfo<T>> infos) {
    infos.sort(
        (o1, o2) -> {
          if (o1.isRoutable() != o2.isRoutable()) {
            return o1.isRoutable() ? -1 : 1;
          }
          return (o1.isLinkLocal() ? 1 : 0) - (o2.isLinkLocal() ? 1 : 0);
        });
  }

  /**
   * Asynchronously get all local addresses in order of most reachable first.
   *
   * <p>This checks routability to www.rptools.net using the SSL port 443 from each address in
   * parallel and uses this and if it's link-local to sort the addresses in order of most reachable
   * first.
   */
  @Nonnull
  private CompletableFuture<LocalAddresses> getLocalAddresses() {
    return CompletableFuture.supplyAsync(
        () -> {
          // Get the appropriate IPv4/6 address for future reachability checks
          Inet4Address rptools4 = null;
          Inet6Address rptools6 = null;
          try {
            for (InetAddress addr : InetAddress.getAllByName("www.rptools.net")) {
              switch (addr) {
                case Inet4Address a -> {
                  if (rptools4 != null) {
                    continue;
                  }
                  rptools4 = a;
                }
                case Inet6Address a -> {
                  if (rptools6 != null) {
                    continue;
                  }
                  rptools6 = a;
                }
                default -> {
                  continue;
                }
              }

              if (rptools4 != null && rptools6 != null) {
                break;
              }
            }
          } catch (UnknownHostException ignore) {
          }
          // We might find we didn't resolve an address for our address family
          // but we can fall back to skipping the reachability check
          // since rptools.net not having an address doesn't imply we're unreachable.

          // Get all addresses, concurrently checking routability to rptools
          // and partition them into v4 and v6.
          List<AddressInfo<Inet4Address>> v4Infos = new ArrayList();
          List<AddressInfo<Inet6Address>> v6Infos = new ArrayList();
          List<Callable<Void>> tasks = new ArrayList();
          List<NetworkInterface> netInts;
          try {
            netInts = Collections.list(NetworkInterface.getNetworkInterfaces());
          } catch (SocketException e) {
            netInts = List.of();
          }
          for (NetworkInterface netInt : netInts) {
            try {
              if (!netInt.isUp() || netInt.isLoopback()) {
                continue;
              }
            } catch (SocketException e) {
              continue;
            }

            for (InetAddress inetAddress : Collections.list(netInt.getInetAddresses())) {
              if (inetAddress.isLoopbackAddress() || inetAddress.isMulticastAddress()) {
                continue;
              }

              switch (inetAddress) {
                case Inet4Address a -> getInfo(a, rptools4, v4Infos, tasks);
                case Inet6Address a -> getInfo(a, rptools6, v6Infos, tasks);
                default -> {}
              }
            }
          }
          ForkJoinPool.commonPool().invokeAll(tasks);

          // Return addresses most reachable first
          sortInfos(v4Infos);
          sortInfos(v6Infos);
          return new LocalAddresses(
              v4Infos.stream().map(x -> x.address()).collect(Collectors.toList()),
              v6Infos.stream().map(x -> x.address()).collect(Collectors.toList()));
        });
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

    int port = server.getPort();
    String portString = port < 0 ? "---" : Integer.toString(port);

    nameLabel.setText(name);
    localv4AddressLabel.setText("Unknown");
    localv6AddressLabel.setText("Unknown");
    externalAddressLabel.setText(I18N.getText("ConnectionInfoDialog.discovering"));
    portLabel.setText(portString);

    getLocalAddresses()
        .thenAccept(
            localAddresses -> {
              if (!localAddresses.ipv4().isEmpty()) {
                localv4AddressLabel.setText(NetUtil.formatAddress(localAddresses.ipv4().get(0)));
              }
              if (!localAddresses.ipv6().isEmpty()) {
                localv6AddressLabel.setText(NetUtil.formatAddress(localAddresses.ipv6().get(0)));
              }
            });

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
