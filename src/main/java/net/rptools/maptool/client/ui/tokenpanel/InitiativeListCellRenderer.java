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
package net.rptools.maptool.client.ui.tokenpanel;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.rptools.lib.AwtUtil;
import net.rptools.lib.StringUtil;
import net.rptools.lib.image.ImageUtil;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.swing.label.FlatImageLabel;
import net.rptools.maptool.client.swing.label.FlatImageLabelFactory;
import net.rptools.maptool.client.ui.theme.Borders;
import net.rptools.maptool.client.ui.theme.Icons;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.client.ui.token.AbstractTokenOverlay;
import net.rptools.maptool.client.ui.token.BarTokenOverlay;
import net.rptools.maptool.model.InitiativeList.TokenInitiative;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.util.ImageManager;

/**
 * This is the renderer that shows a token in the initiative panel.
 *
 * @author Jay
 */
public class InitiativeListCellRenderer extends JPanel
    implements ListCellRenderer<TokenInitiative> {

  /*---------------------------------------------------------------------------------------------
   * Instance Variables
   *-------------------------------------------------------------------------------------------*/

  private final JPanel right;
  private final JPanel textPanel;

  /** The label used to display the current item indicator. */
  private final JLabel currentIndicator;

  private final JLabel iconLabel;

  /** The label used to display the item's name and icon. */
  private final JLabel name;

  private final JLabel initLabel;

  /** This is the panel showing initiative. It contains the state for display. */
  private final InitiativePanel panel;

  /** Used to draw the background of the item. */
  private FlatImageLabel backgroundFlatImageLabel;

  /**
   * The text height for the background image label. Only the text is painted inside, the token
   * remains on the outside,
   */
  private final int textHeight;

  /*---------------------------------------------------------------------------------------------
   * Class Variables
   *-------------------------------------------------------------------------------------------*/

  /** The size of an indicator. */
  public static final Dimension INDICATOR_SIZE = new Dimension(18, 16);

  /** The icon for the current indicator. */
  public static final Icon CURRENT_INDICATOR_ICON =
      RessourceManager.getSmallIcon(Icons.INITIATIVE_CURRENT_INDICATOR);

  /** Border used to show that an item is selected */
  public static final Border SELECTED_BORDER = RessourceManager.getBorder(Borders.RED);

  /** Border used to show that an item is not selected */
  public static final Border UNSELECTED_BORDER =
      BorderFactory.createEmptyBorder(
          RessourceManager.getBorder(Borders.RED).getTopMargin(),
          RessourceManager.getBorder(Borders.RED).getLeftMargin(),
          RessourceManager.getBorder(Borders.RED).getBottomMargin(),
          RessourceManager.getBorder(Borders.RED).getRightMargin());

  /** The size of the ICON shown in the list renderer */
  public static final int ICON_SIZE = 50;

  /*---------------------------------------------------------------------------------------------
   * Constructor
   *-------------------------------------------------------------------------------------------*/

  /**
   * Create a renderer for the initiative panel.
   *
   * @param aPanel The initiative panel containing view state.
   */
  public InitiativeListCellRenderer(InitiativePanel aPanel) {

    // Set up the panel
    panel = aPanel;

    setLayout(new GridLayoutManager(1, 2, new Insets(6, 6, 9, 6), 0, 0, false, false));

    // The current indicator
    currentIndicator = new JLabel();
    currentIndicator.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    currentIndicator.setPreferredSize(INDICATOR_SIZE);
    currentIndicator.setHorizontalAlignment(SwingConstants.CENTER);
    currentIndicator.setVerticalAlignment(SwingConstants.CENTER);
    add(
        currentIndicator,
        new GridConstraints(
            0,
            0,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            INDICATOR_SIZE,
            INDICATOR_SIZE,
            INDICATOR_SIZE,
            0,
            false));

    right = new JPanel();
    right.setOpaque(false);
    right.setLayout(new GridLayoutManager(3, 3, new Insets(2, 7, 0, 0), 4, 0, false, false));
    right.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 4));
    add(
        right,
        new GridConstraints(
            0,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            null,
            null,
            null,
            0,
            false));

    textPanel = new JPanel(new GridLayoutManager(2, 1, new Insets(3, 0, 1, 0), 0, 0, false, true));
    // textPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
    textPanel.setOpaque(false);
    right.add(
        textPanel,
        new GridConstraints(
            1,
            1,
            1,
            1,
            GridConstraints.ANCHOR_EAST,
            GridConstraints.FILL_VERTICAL,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            null,
            null,
            null,
            0,
            false));

    textHeight = getFontMetrics(getFont()).getHeight();

    iconLabel = new JLabel();
    iconLabel.putClientProperty("html.disable", true);
    var iconSize = new Dimension(ICON_SIZE, ICON_SIZE);
    right.add(
        iconLabel,
        new GridConstraints(
            0,
            0,
            3,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            iconSize,
            iconSize,
            iconSize,
            0,
            false));

    // And the name
    name = new JLabel();
    name.setHorizontalTextPosition(SwingConstants.LEADING);
    name.putClientProperty("html.disable", true);
    name.setText("Ty");
    name.setBorder(BorderFactory.createEmptyBorder());
    name.setFont(getFont());
    name.setBackground(Color.blue);
    textPanel.add(
        name,
        new GridConstraints(
            0,
            0,
            1,
            1,
            GridConstraints.ANCHOR_EAST,
            GridConstraints.FILL_VERTICAL,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            new Dimension(0, 0),
            null,
            null,
            0,
            false));

    initLabel = new JLabel();
    initLabel.setHorizontalTextPosition(SwingConstants.LEADING);
    initLabel.putClientProperty("html.disable", true);
    initLabel.setText("Ty");
    initLabel.setBorder(BorderFactory.createEmptyBorder());
    initLabel.setFont(getFont());
    initLabel.setBackground(Color.red);
    textPanel.add(
        initLabel,
        new GridConstraints(
            1,
            0,
            1,
            1,
            GridConstraints.ANCHOR_EAST,
            GridConstraints.FILL_VERTICAL,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
            new Dimension(0, 0),
            null,
            null,
            0,
            false));

    right.add(
        new Spacer(),
        new GridConstraints(
            0,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_VERTICAL,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            null,
            null,
            null,
            0,
            false));
    right.add(
        new Spacer(),
        new GridConstraints(
            2,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_VERTICAL,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            null,
            null,
            null,
            0,
            false));

    validate();
  }

  /*---------------------------------------------------------------------------------------------
   * ListCellRenderer Interface Methods
   *-------------------------------------------------------------------------------------------*/

  /**
   * @see javax.swing.ListCellRenderer#getListCellRendererComponent(javax.swing.JList,
   *     java.lang.Object, int, boolean, boolean)
   */
  @Override
  public Component getListCellRendererComponent(
      JList list, TokenInitiative ti, int index, boolean isSelected, boolean cellHasFocus) {
    setOpaque(false);

    // Set the background by type
    Token token = null;
    if (ti != null) {
      token = ti.getToken();
    }
    if (token == null) { // Can happen when deleting a token before all events have propagated
      currentIndicator.setIcon(null);
      name.setText(null);
      name.setIcon(null);
      setBorder(UNSELECTED_BORDER);
      return this;
    }

    var labelRenderFactory = new FlatImageLabelFactory();
    backgroundFlatImageLabel = labelRenderFactory.getMapImageLabel(token);

    // We still use the UI text so use the map label color preferences
    if (!token.isVisible()) {
      name.setForeground(AppPreferences.nonVisibleTokenMapLabelForeground.get());
    } else if (token.getType() == Token.Type.NPC) {
      name.setForeground(AppPreferences.npcMapLabelForeground.get());
    } else {
      name.setForeground(AppPreferences.pcMapLabelForeground.get());
    }
    name.setFont(name.getFont().deriveFont(token.isVisible() ? Font.PLAIN : Font.ITALIC));

    // Show the indicator?
    int currentIndex = panel.getList().getCurrent();
    if (currentIndex >= 0 && ti == panel.getList().getTokenInitiative(currentIndex)) {
      currentIndicator.setIcon(CURRENT_INDICATOR_ICON);
    } else {
      currentIndicator.setIcon(null);
    }

    // Get the name string, add the state if displayed, then get the icon if needed
    boolean initStateSecondLine = panel.isInitStateSecondLine() && panel.isShowInitState();

    var nameHtml = new StringBuilder(ti.getToken().getName());
    if (MapTool.getFrame().getInitiativePanel().hasGMPermission()
        && token.getGMName() != null
        && !StringUtil.isEmpty(token.getGMName())) {
      nameHtml.append(" (").append(token.getGMName().trim()).append(")");
    }

    initLabel.setText("");
    if (panel.isShowInitState() && ti.getState() != null) {
      if (initStateSecondLine) {
        initLabel.setText(ti.getState());
      } else {
        nameHtml.append(" = ").append(ti.getState());
      }
    }

    name.setText(nameHtml.toString());

    Icon icon = null;
    if (panel.isShowTokens()) {
      icon = ti.getDisplayIcon();
      if (icon == null || ti.wasTokenVisibleWhenIconUpdated() != token.isVisible()) {
        icon = new InitiativeListIcon(ti);
        ti.setDisplayIcon(icon);
        ti.setTokenVisibleWhenIconUpdated(token.isVisible());
      }
    }

    var rightLayout = (GridLayoutManager) right.getLayout();
    var textLayout = (GridLayoutManager) textPanel.getLayout();

    // Align it properly
    var iconConstraints = rightLayout.getConstraintsForComponent(iconLabel);
    var textConstraints = rightLayout.getConstraintsForComponent(textPanel);
    var nameConstraints = textLayout.getConstraintsForComponent(name);
    var initConstraints = textLayout.getConstraintsForComponent(initLabel);
    iconLabel.setIcon(icon);
    iconLabel.setVisible(true);
    if (ti.isHolding()) {
      iconConstraints.setColumn(2);

      textConstraints.setAnchor(GridConstraints.ANCHOR_EAST);
      nameConstraints.setAnchor(GridConstraints.ANCHOR_EAST);
      initConstraints.setAnchor(GridConstraints.ANCHOR_EAST);
    } else {
      iconConstraints.setColumn(0);

      textConstraints.setAnchor(GridConstraints.ANCHOR_WEST);
      nameConstraints.setAnchor(GridConstraints.ANCHOR_WEST);
      initConstraints.setAnchor(GridConstraints.ANCHOR_WEST);
    }

    // Selected?
    if (isSelected) {
      setBorder(SELECTED_BORDER);
    } else {
      setBorder(UNSELECTED_BORDER);
    }

    setSize(list.getWidth(), getPreferredSize().height);
    setMinimumSize(getSize());
    setMaximumSize(getSize());
    setPreferredSize(getSize());

    validate();
    doLayout();

    return this;
  }

  @Override
  protected void paintComponent(Graphics g) {
    var rightBounds = right.getBounds();
    var rightInsets = right.getInsets();
    var iconBounds = iconLabel.getBounds();
    var textBounds = textPanel.getBounds();

    var bounds = new Rectangle();
    bounds.x = Math.min(iconBounds.x, textBounds.x);
    bounds.y = textBounds.y;
    bounds.width =
        Math.max(textBounds.x + textBounds.width, iconBounds.x + iconBounds.width) - bounds.x;
    bounds.height = textBounds.height;

    bounds.x += rightBounds.x;
    bounds.y += rightBounds.y;

    // render an image label with set dimensions and without text
    backgroundFlatImageLabel.render(
        (Graphics2D) g,
        // TODO Add in padding.
        bounds.x - rightInsets.left,
        bounds.y - rightInsets.top,
        bounds.width + (rightInsets.left + rightInsets.right),
        bounds.height + (rightInsets.top + rightInsets.bottom),
        "");

    super.paintComponent(g);
  }

  /**
   * An icon that will show a token image and all of the states as needed.
   *
   * @author Jay
   */
  public class InitiativeListIcon extends ImageIcon {

    /** Bounds sent to the token state */
    private final Rectangle bounds = new Rectangle(0, 0, ICON_SIZE, ICON_SIZE);

    /** The token painted by this icon */
    private final TokenInitiative tokenInitiative;

    /** The image that is displayed when states are not being shown. */
    private Image textTokenImage;

    /** The image that is displayed when states are being shown. */
    private Image stateTokenImage;

    /** Size of the text only token */
    private final int textTokenSize = Math.max(textHeight + 4, 16);

    /**
     * Create the image from the token and then build an icon suitable for displaying state.
     *
     * @param aTokenInitiative The initiative item being rendered
     */
    public InitiativeListIcon(TokenInitiative aTokenInitiative) {
      tokenInitiative = aTokenInitiative;
      if (panel.isShowTokenStates()) {
        stateTokenImage = scaleImage();
      } else {
        textTokenImage = scaleImage();
      }
    }

    /**
     * Scale the token's image to fit in the allotted space for text or state painting.
     *
     * @return The properly scaled image.
     */
    public Image scaleImage() {
      Image image = ImageManager.getImageAndWait(tokenInitiative.getToken().getImageAssetId());
      BufferedImage bi =
          ImageUtil.createCompatibleImage(
              getIconWidth(), getIconHeight(), Transparency.TRANSLUCENT);
      Dimension d = new Dimension(image.getWidth(null), image.getHeight(null));
      AwtUtil.constrainTo(d, getIconWidth(), getIconHeight());
      Graphics2D g = bi.createGraphics();
      g.setComposite(
          AlphaComposite.getInstance(
              AlphaComposite.SRC_OVER, tokenInitiative.getToken().isVisible() ? 1.0F : 0.5F));
      g.drawImage(
          image,
          (getIconWidth() - d.width) / 2,
          (getIconHeight() - d.height) / 2,
          d.width,
          d.height,
          null);
      setImage(bi);
      return bi;
    }

    /**
     * @see javax.swing.ImageIcon#getIconHeight()
     */
    @Override
    public int getIconHeight() {
      return panel.isShowTokenStates() ? ICON_SIZE : textTokenSize;
    }

    /**
     * @see javax.swing.ImageIcon#getIconWidth()
     */
    @Override
    public int getIconWidth() {
      return panel.isShowTokenStates() ? ICON_SIZE : textTokenSize;
    }

    /**
     * Paint the icon and then the image.
     *
     * @see javax.swing.ImageIcon#paintIcon(java.awt.Component, java.awt.Graphics, int, int)
     */
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

      // Paint the halo if needed
      Token token = tokenInitiative.getToken();
      Color haloColor = token.getHaloColor();
      if (panel.isShowTokenStates() && haloColor != null) {
        Graphics2D g2d = (Graphics2D) g;
        Stroke oldStroke = g2d.getStroke();
        Color oldColor = g.getColor();
        g2d.setStroke(new BasicStroke(AppPreferences.haloLineWidth.get()));
        g.setColor(haloColor);
        g2d.draw(new Rectangle2D.Double(x, y, ICON_SIZE, ICON_SIZE));
        g2d.setStroke(oldStroke);
        g.setColor(oldColor);
      }

      // Paint the icon, is that all that's needed?
      if (panel.isShowTokenStates() && getImage() != stateTokenImage) {
        if (stateTokenImage == null) stateTokenImage = scaleImage();
        setImage(stateTokenImage);
      } else if (!panel.isShowTokenStates() && getImage() != textTokenImage) {
        if (textTokenImage == null) {
          textTokenImage = scaleImage();
        }
        setImage(textTokenImage);
      }
      super.paintIcon(c, g, x, y);
      if (!panel.isShowTokenStates()) {
        return;
      }

      // Paint all the states
      g.translate(x, y);
      Shape old = g.getClip();
      g.setClip(bounds.intersection(old.getBounds()));
      for (String state : MapTool.getCampaign().getTokenStatesMap().keySet()) {
        Object stateSet = token.getState(state);
        AbstractTokenOverlay overlay = MapTool.getCampaign().getTokenStatesMap().get(state);
        if (stateSet instanceof AbstractTokenOverlay
            || overlay == null
            || !overlay.showPlayer(token, MapTool.getPlayer())
            || overlay.isMouseover()) {
          continue;
        }
        overlay.paintOverlay((Graphics2D) g, token, bounds, stateSet);
      }
      for (String bar : MapTool.getCampaign().getTokenBarsMap().keySet()) {
        Object barSet = token.getState(bar);
        BarTokenOverlay overlay = MapTool.getCampaign().getTokenBarsMap().get(bar);
        if (overlay == null || !overlay.showPlayer(token, MapTool.getPlayer())) {
          continue;
        }
        overlay.paintOverlay((Graphics2D) g, token, bounds, barSet);
      }
      g.setClip(old);
      g.translate(-x, -y);
    }
  }
}
