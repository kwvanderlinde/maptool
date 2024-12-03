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

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.MapToolRegistry;

public class NetUtil {
  private static final NetUtil instance = new NetUtil();

  @Nonnull
  public static String formatAddress(@Nonnull InetAddress addr) {
    return switch (addr) {
      case Inet4Address a -> a.getHostAddress();
      case Inet6Address a -> {
        var s = a.getHostAddress();
        int scopeCharIdx = s.indexOf("%");
        if (scopeCharIdx != -1) {
          s = s.substring(0, scopeCharIdx);
        }
        yield "[" + s + "]";
      }
      default ->
          throw new AssertionError(
              "Unable to predict how to format future Internet Protocol Addresses");
    };
  }

  @Nonnull
  public static NetUtil getInstance() {
    return instance;
  }

  @Nonnull private CompletableFuture<InetAddress> externalAddressFuture;

  public NetUtil() {
    externalAddressFuture = MapToolRegistry.getInstance().getAddressAsync();
  }

  /**
   * Get a cached result of requesting the external address from the registry.
   *
   * @return A future that resolves to the external address or null if indeterminate.
   */
  @Nonnull
  public CompletableFuture<InetAddress> getExternalAddress() {
    // Reuse the future if the last one didn't fail
    switch (externalAddressFuture.state()) {
      case Future.State.CANCELLED, Future.State.FAILED -> {
        externalAddressFuture = MapToolRegistry.getInstance().getAddressAsync();
      }
      default -> {}
    }

    return externalAddressFuture;
  }
}
