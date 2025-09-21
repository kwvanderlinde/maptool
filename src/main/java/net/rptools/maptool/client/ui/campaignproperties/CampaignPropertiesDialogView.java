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
package net.rptools.maptool.client.ui.campaignproperties;

import java.awt.*;
import javax.swing.*;
import net.rptools.maptool.client.walker.WalkerMetric;

public class CampaignPropertiesDialogView {

  private JPanel mainPanel;
  private JCheckBox gameplayStrictTokenOwnership;
  private JCheckBox gameplayPlayersCanRevealVision;
  private JCheckBox gameplayGmRevealsVisionForUnownedTokens;
  private JCheckBox gameplayUseIndividualViews;
  private JCheckBox gameplayUnrestrictedImpersonation;
  private JCheckBox gameplayPlayersReceiveCampaignMacros;
  private JCheckBox gameplayUseToolTipsForDefaultRollFormat;
  private JCheckBox gameplayMapSelectUIHidden;
  private JCheckBox gameplayTokenEditorLocked;
  private JCheckBox gameplayMovementLocked;
  private JCheckBox gameplayDisablePlayerAssetPanel;
  private JCheckBox gameplayUseIndividualFow;
  private JCheckBox gameplayAutoRevealOnMovement;
  private JComboBox<WalkerMetric> gameplayMovementMetric;
  private JCheckBox gameplayNavigateAroundVbl;
  private JCheckBox gameplayUseAiPathfinding;

  public JComponent getRootComponent() {
    return mainPanel;
  }

  public JCheckBox getGameplayStrictTokenOwnership() {
    return gameplayStrictTokenOwnership;
  }

  public JCheckBox getGameplayPlayersCanRevealVision() {
    return gameplayPlayersCanRevealVision;
  }

  public JCheckBox getGameplayGmRevealsVisionForUnownedTokens() {
    return gameplayGmRevealsVisionForUnownedTokens;
  }

  public JCheckBox getGameplayUseIndividualViews() {
    return gameplayUseIndividualViews;
  }

  public JCheckBox getGameplayUseIndividualFow() {
    return gameplayUseIndividualFow;
  }

  public JCheckBox getGameplayUnrestrictedImpersonation() {
    return gameplayUnrestrictedImpersonation;
  }

  public JCheckBox getGameplayPlayersReceiveCampaignMacros() {
    return gameplayPlayersReceiveCampaignMacros;
  }

  public JCheckBox getGameplayUseToolTipsForDefaultRollFormat() {
    return gameplayUseToolTipsForDefaultRollFormat;
  }

  public JCheckBox getGameplayMapSelectUIHidden() {
    return gameplayMapSelectUIHidden;
  }

  public JCheckBox getGameplayTokenEditorLocked() {
    return gameplayTokenEditorLocked;
  }

  public JCheckBox getGameplayMovementLocked() {
    return gameplayMovementLocked;
  }

  public JCheckBox getGameplayDisablePlayerAssetPanel() {
    return gameplayDisablePlayerAssetPanel;
  }

  public JCheckBox getGameplayAutoRevealOnMovement() {
    return gameplayAutoRevealOnMovement;
  }

  public JComboBox<WalkerMetric> getGameplayMovementMetric() {
    return gameplayMovementMetric;
  }

  public JCheckBox getGameplayUseAiPathfinding() {
    return gameplayUseAiPathfinding;
  }

  public JCheckBox getGameplayNavigateAroundVbl() {
    return gameplayNavigateAroundVbl;
  }
}
