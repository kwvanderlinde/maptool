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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import net.rptools.maptool.model.Asset;
import net.rptools.maptool.model.Token;

public class TransferableToken implements Transferable {
  public static final DataFlavor dataFlavor = new DataFlavor(Token.class, "Token");

  private Token token;
  private List<Asset> assets;

  public TransferableToken(Token token, List<Asset> assets) {
    this.token = token;
    // TODO Consume the list of assets somewhere so they can be added to the campaign.
    this.assets = assets;
  }

  public Object getTransferData(DataFlavor flavor) {
    return token;
  }

  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[] {dataFlavor};
  }

  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(dataFlavor);
  }
}
