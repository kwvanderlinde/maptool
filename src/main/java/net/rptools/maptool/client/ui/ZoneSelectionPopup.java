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
package net.rptools.maptool.client.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Comparator;
import javax.swing.*;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.AppState;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.theme.Icons;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Zone;

public class ZoneSelectionPopup extends JScrollPopupMenu {

  private JMenuItem selection;

  public ZoneSelectionPopup() {
    selection = createEntries();
  }

  @Override
  public void show(Component invoker, int x, int y) {
    super.show(invoker, x, y);
    scrollComponentToVisible(selection);
  }

  private JMenuItem createEntries() {

    JMenuItem selection = null;
    if (MapTool.getServerPolicy().getMapSelectUIHidden() && !MapTool.getPlayer().isGM()) {
      MapTool.getFrame().getToolbarPanel().getMapselect().setVisible(false);
    } else {
      var zoneList = new ArrayList<>(MapTool.getClient().getCampaign().getZones());
      if (!MapTool.getPlayer().isGM()) {
        zoneList.removeIf(zone -> !zone.isVisible());
      }

      if (AppPreferences.getMapSortType().equals(AppPreferences.MapSortType.GMNAME)) {
        zoneList.sort(Comparator.comparing(Zone::getName, String.CASE_INSENSITIVE_ORDER));
      } else {
        zoneList.sort(Comparator.comparing(Zone::toString, String.CASE_INSENSITIVE_ORDER));
      }

      var currentZone = MapTool.getClient().getCurrentZone();
      for (Zone zone : zoneList) {
        ZoneItem item = new ZoneItem(zone);
        if (currentZone != null && currentZone.getId().equals(zone.getId())) {
          item.setSelected(true);
          selection = item;
        } else if (!zone.isVisible()) {
          item.setIcon(RessourceManager.getSmallIcon(Icons.TOOLBAR_ZONE_NOT_VISIBLE));
        }
        add(item);
      }
    }

    return selection;
  }

  private static class ZoneItem extends JCheckBoxMenuItem implements ActionListener {

    private final Zone zone;

    ZoneItem(Zone zone) {
      this.zone = zone;

      String name = zone.toString();
      if ("".equals(name)) {
        name = I18N.getText("Button.map");
      }
      setText(name);
      addActionListener(this);
    }

    @Override
    public boolean isShowing() {

      // The JScrollPopupMenu does a layout that moves menu items into negative y
      // with the help of the scrollbar. If after the popup is shown the user releases
      // the mouse over the button above the popu menu, then the MenuSelectionManager
      // selects the menu item under the mouse even though that menu item is covered by
      // the button leading to unexpected item selection.
      // Checking the y position here to avoid that kind of release choosing a menu item
      // that shouldn't be selectable. This way the MenuSelectionManager consuming the
      // mouse release event doesn't consider this item wrongly.
      return getY() >= 0 && super.isShowing();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      var client = MapTool.getClient();

      // Set current zone renderer if new
      if (client.getCurrentZone() == null
          || !client.getCurrentZone().getId().equals(zone.getId())) {
        client.setCurrentZoneId(zone.getId());
        MapTool.getFrame().refresh();

        if (AppState.isPlayerViewLinked() && client.getPlayer().isGM()) {
          client.getServerCommand().enforceZone(zone.getId());

          var renderer = MapTool.getFrame().getZoneRenderer(zone.getId());
          renderer.forcePlayersView();
        }
      }
    }
  }
}
