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
package net.rptools.maptool.model;

public class CampaignFactory {
  /**
   * @return A new campaign with no zones or properties.
   */
  public static Campaign createEmptyCampaign() {
    return new Campaign();
  }

  public static Campaign createBasicCampaign() {
    Campaign campaign = new Campaign();
    campaign.initDefault();
    campaign.putZone(ZoneFactory.createZone());
    return campaign;
  }
}
