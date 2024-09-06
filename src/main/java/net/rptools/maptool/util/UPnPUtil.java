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
package net.rptools.maptool.util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;
import net.sbbi.upnp.Discovery;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.ActionResponse;
import net.sbbi.upnp.messages.UPNPResponseException;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

/**
 * @author Phil Wright
 */
public class UPnPUtil {
  private static final Logger log = LogManager.getLogger(UPnPUtil.class);
  private static Map<InternetGatewayDevice, NetworkInterface> igds;

  // TODO It looks like sbbi upnp library can accept a `null` NetworkInterface to indicate that it
  //  should look them all up. What's not clear is whether this is of any performance advantage over
  //  how we're doing it now. I am definitely seeing a blocking socket send, but it's purely a send
  //  over a UDP socket. Therefore I expect there could be an improvement.
  //      Just tested it, and this certainly does do them all at once. Unfortunately,
  //  InternetGatewayDevice is not strictly associated with the original NetworkInterface, and
  //  indeed multiple interfaces can share the same gateway. This is bad news for us, because even
  //  though we can discover the gateways, we also need to know the local address to use in the port
  //  mapping, and the local address is specific to a network interface.
  //      However, we can probably pry open InternetGatewayDevice.getDevices() and instead use
  //  a DiscoveryListener and DiscoveryResultsHandler directly in order to associated IGDs with
  //  their NIs.
  private static List<InternetGatewayDevice> findIgdsForInterface(NetworkInterface ni) {
    var result = new ArrayList<InternetGatewayDevice>();

    try {
      if (ni.isUp() && !ni.isLoopback() && !ni.isVirtual()) {
        log.info("UPnP:  Looking for gateway devices on {}", ni.getDisplayName());
        InternetGatewayDevice[] thisNI =
            InternetGatewayDevice.getDevices(
                AppPreferences.getUpnpDiscoveryTimeout(),
                Discovery.DEFAULT_TTL,
                Discovery.DEFAULT_MX,
                ni);
        if (thisNI != null) {
          result.addAll(Arrays.asList(thisNI));
        }
      }
    } catch (IOException e) {
      log.warn("Error while searching for internet gateway devices", e);
    }

    log.info("Found {} IGDs on interface {}", result.size(), ni.getDisplayName());

    return result;
  }

  private static Map<InternetGatewayDevice, NetworkInterface> findIgdsFast() {
    if (igds != null && !igds.isEmpty()) {
      return igds;
    }

    var result = new HashMap<InternetGatewayDevice, NetworkInterface>();

    List<NetworkInterface> interfaces;
    try {
      interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
    } catch (SocketException e) {
      return result;
    }
    if (interfaces.isEmpty()) {
      return result;
    }

    // Each thread will basically spend its time blocked, so large amounts makes sense here.
    try (var pool = new ForkJoinPool(interfaces.size())) {
      pool.submit(() -> {
        interfaces.stream()
                .parallel()
                .map(ni -> Pair.with(ni, findIgdsForInterface(ni)))
                .toList()
                .forEach(
                        pair -> {
                          NetworkInterface ni = pair.getValue0();
                          List<InternetGatewayDevice> devices = pair.getValue1();
                          for (var igd : devices) {
                            log.info(
                                    "UPnP:  Found IGD '{}' for network interface '{}'",
                                    igd.getIGDRootDevice().getModelName(),
                                    ni.getDisplayName());
                            if (result.put(igd, ni) != null) {
                              // There was a previous mapping for this IGD! It's unlikely to have two NICs on
                              // the same network segment, but it IS possible. For example, both a wired and
                              // wireless connection using the same router as the gateway. For our purposes it
                              // doesn't really matter which one we use, but in the future we should give the
                              // user a choice.
                              // FIXME We SHOULD be using the "networking binding order" (Windows)
                              // or "network service order" on OSX.
                              log.info("UPnP:  This was not the first time this IGD was found!");
                            }
                          }
                        });
      }).get();
    }
    catch (InterruptedException e) {
      // Nothing to do aside from set igds field below.
    }
    catch (ExecutionException e) {
      log.error("Error during UPnP discovery", e);
    }

    igds = result;

    return result;
  }

  public static boolean openPort(int port) {
      try {
        var start = System.nanoTime();
          var igds0 = InternetGatewayDevice.getDevices(
                  AppPreferences.getUpnpDiscoveryTimeout(),
                  Discovery.DEFAULT_TTL,
                  Discovery.DEFAULT_MX,
                  null
          );
          var end = System.nanoTime();
          log.info("New approach takes {} seconds", TimeUnit.NANOSECONDS.toSeconds(end - start));
          final var i = 0;

          for (final var gd : igds0) {
            boolean mapped = gd.addPortMapping("MapTool", null, port, port, localHostIP, 0, "TCP");
          }

      } catch (IOException e) {
        log.error(e);
      }

      var igds = findIgdsFast();
    var mappings = new ArrayList<InternetGatewayDevice>(igds.size());

    if (igds.isEmpty()) {
      MapTool.showError("msg.error.server.upnp.noigd");
      return false;
    }
    for (var entry : igds.entrySet()) {
      InternetGatewayDevice gd = entry.getKey();
      NetworkInterface ni = entry.getValue();
      String localHostIP = "(NULL)";
      try {
        switch (ni.getInterfaceAddresses().size()) {
          case 0:
            log.error("IGD shows up in list of IGDs, but no NICs stored therein?!");
            break;
          case 1:
            localHostIP = ni.getInterfaceAddresses().get(0).getAddress().getHostAddress();
            break;
          default:
            for (InterfaceAddress ifAddr : ni.getInterfaceAddresses()) {
              if (ifAddr.getAddress() instanceof Inet4Address) {
                localHostIP = ifAddr.getAddress().getHostAddress();
                log.info("IP address {} on interface {}", localHostIP, ni.getDisplayName());
              }
            }
            break;
        }
        boolean mapped = gd.addPortMapping("MapTool", null, port, port, localHostIP, 0, "TCP");
        if (mapped) {
          mappings.add(gd);
          log.info(
              "UPnP: Port {} mapped on {} at address {}", port, ni.getDisplayName(), localHostIP);
        }
      } catch (UPNPResponseException respEx) {
        // oops the IGD did not like something !!
        log.error(
            "UPnP Error 1: Could not add port mapping on device "
                + ni.getDisplayName()
                + ", IP address "
                + localHostIP,
            respEx);
      } catch (IOException ioe) {
        log.error(
            "UPnP Error 2: Could not add port mapping on device "
                + ni.getDisplayName()
                + ", IP address "
                + localHostIP,
            ioe);
      }
    }
    if (mappings.isEmpty()) {
      MapTool.showError("UPnP: found " + igds.size() + " IGDs but no port mapping succeeded!?");
    }
    return !mappings.isEmpty();
  }

  public static boolean closePort(int port) {
    if (igds == null || igds.isEmpty()) {
      return true;
    }

    int count = 0;
    for (var iter = igds.entrySet().iterator(); iter.hasNext(); ) {
      var entry = iter.next();
      InternetGatewayDevice gd = entry.getKey();
      try {
        ActionResponse actResp = gd.getSpecificPortMappingEntry(null, port, "TCP");
        if (actResp != null
            && "MapTool".equals(actResp.getOutActionArgumentValue("NewPortMappingDescription"))) {
          // NewInternalPort=51234
          // NewEnabled=1
          // NewInternalClient=192.168.0.30
          // NewLeaseDuration=0
          // NewPortMappingDescription=MapTool
          boolean unmapped = gd.deletePortMapping(null, port, "TCP");
          if (unmapped) {
            count++;
            log.info("UPnP: Port unmapped from {}", entry.getValue().getDisplayName());
            iter.remove();
          } else {
            log.info("UPnP: Failed to unmap port from {}", entry.getValue().getDisplayName());
          }
        }
      } catch (IOException e) {
        log.info("UPnP: IOException while talking to IGD", e);
      } catch (UPNPResponseException e) {
        log.info("UPnP: UPNPResponseException while talking to IGD", e);
      }
    }
    return count > 0;
  }
}
