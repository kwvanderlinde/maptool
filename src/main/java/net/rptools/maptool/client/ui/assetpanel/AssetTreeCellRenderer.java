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
package net.rptools.maptool.client.ui.assetpanel;

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import net.rptools.maptool.client.ui.theme.Icons;
import net.rptools.maptool.client.ui.theme.RessourceManager;

/** */
public class AssetTreeCellRenderer extends DefaultTreeCellRenderer {
  // Jamz: Add PDF's as a "Leaf" and show the extracted images in the asset window...
  private static final Icon PDF_FOLDER = RessourceManager.getSmallIcon(Icons.ASSETPANEL_PDF_FOLDER);
  // Jamz: Add Hero Lab Portfolio's as a "Leaf" and show the extracted characters in the asset
  // window...
  private static final Icon HERO_LAB_FOLDER =
      RessourceManager.getSmallIcon(Icons.ASSETPANEL_HEROLABS_FOLDER);

  public Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean sel,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus) {

    setBorder(null);
    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

    if (value instanceof Directory) {
      setText(((Directory) value).getPath().getName());
      if (((Directory) value).isPDF()) {
        setIcon(PDF_FOLDER);
      }
      if (((Directory) value).isHeroLabPortfolio()) {
        setIcon(HERO_LAB_FOLDER);
      }
    }

    // Root node...
    if (row == 0) {
      setIcon(null);
    }

    return this;
  }
}
