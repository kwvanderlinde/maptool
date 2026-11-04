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
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.swing.*;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.MediaPlayerAdapter;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.client.swing.TopologyModeSelectionPanel;
import net.rptools.maptool.client.tool.*;
import net.rptools.maptool.client.tool.boardtool.BoardTool;
import net.rptools.maptool.client.tool.drawing.*;
import net.rptools.maptool.client.tool.gridtool.GridTool;
import net.rptools.maptool.client.tool.texttool.TextTool;
import net.rptools.maptool.client.ui.theme.Icons;
import net.rptools.maptool.client.ui.theme.RessourceManager;
import net.rptools.maptool.client.ui.zone.renderer.ZoneRenderer;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.Zone.TokenSelection;
import net.rptools.maptool.util.MessageUtil;

public class ToolbarPanel extends JToolBar {
  private final ButtonGroup buttonGroup = new ButtonGroup();
  private final ButtonGroup tokenSelectionbuttonGroup = new ButtonGroup();
  private final JToggleButton pointerGroupButton;
  private final JToggleButton drawButton;
  private final JToggleButton templateButton;
  private final JToggleButton fogButton;
  private final JToggleButton topologyButton;

  /**
   * The last component prior to the option panel. This is used to find the index at which to
   * reinsert the option panel when the fullscreen tools are hidden.
   */
  private final Component optionPanelSeparator;

  private final JPanel optionPanel;
  private final Toolbox toolbox;
  private final JButton mapselect;

  public ToolbarPanel(Toolbox tbox) {
    setRollover(true);
    setFloatable(false);

    add(Box.createHorizontalStrut(5));

    toolbox = tbox;
    optionPanel = new JPanel(new CardLayout());

    pointerGroupButton = createPointerGroupButton();
    add(pointerGroupButton);
    drawButton =
        createButton(
            Icons.TOOLBAR_DRAW_ON,
            Icons.TOOLBAR_DRAW_OFF,
            createDrawPanel(),
            I18N.getText("tools.drawing.tooltip"));
    add(drawButton);
    templateButton =
        createButton(
            Icons.TOOLBAR_TEMPLATE_ON,
            Icons.TOOLBAR_TEMPLATE_OFF,
            createTemplatePanel(),
            I18N.getText("tools.template.tooltip"));
    add(templateButton);
    fogButton =
        createButton(
            Icons.TOOLBAR_FOG_ON,
            Icons.TOOLBAR_FOG_OFF,
            createFogPanel(),
            I18N.getText("tools.fog.tooltip"));
    add(fogButton);
    topologyButton =
        createButton(
            Icons.TOOLBAR_TOPOLOGY_ON,
            Icons.TOOLBAR_TOPOLOGY_OFF,
            createTopologyPanel(),
            I18N.getText("tools.topo.tooltip"));
    add(topologyButton);

    optionPanelSeparator = addSeparator(this, 21);

    add(optionPanel);

    add(Box.createGlue());

    // the Volume icon
    add(
        createMuteButton(
            Icons.TOOLBAR_VOLUME_OFF,
            Icons.TOOLBAR_VOLUME_ON,
            I18N.getText("tools.mute.tooltip"),
            I18N.getText("tools.unmute.tooltip")));

    // the Volume slider
    final int MAX_SLIDER = 10;
    final int INIT_VALUE = (int) Math.round(MediaPlayerAdapter.getGlobalVolume() * MAX_SLIDER);
    JSlider jslider = new JSlider(0, MAX_SLIDER, INIT_VALUE);
    jslider.addChangeListener(
        e -> MediaPlayerAdapter.setGlobalVolume((double) jslider.getValue() / MAX_SLIDER));

    // Create the label table
    Hashtable labelTable = new Hashtable();
    labelTable.put(0, new JLabel("0"));
    labelTable.put(MAX_SLIDER, new JLabel("100"));
    jslider.setLabelTable(labelTable);
    jslider.setPaintLabels(true);

    add(jslider);
    // End slider

    addSeparator(this, 21);

    // Jamz: Adding new Token Selection option buttons
    // Default selected button created with reference to set selection true
    final JToggleButton tokenSelectionButtonAll =
        createTokenSelectionButton(
            Icons.TOOLBAR_TOKENSELECTION_ALL_ON,
            Icons.TOOLBAR_TOKENSELECTION_ALL_OFF,
            I18N.getText("tools.token.fow.all.tooltip"),
            TokenSelection.ALL);

    add(
        createTokenSelectionButton(
            Icons.TOOLBAR_TOKENSELECTION_ME_ON,
            Icons.TOOLBAR_TOKENSELECTION_ME_OFF,
            I18N.getText("tools.token.fow.gm.tooltip"),
            TokenSelection.GM));
    add(tokenSelectionButtonAll);
    add(
        createTokenSelectionButton(
            Icons.TOOLBAR_TOKENSELECTION_PC_ON,
            Icons.TOOLBAR_TOKENSELECTION_PC_OFF,
            I18N.getText("tools.token.fow.pc.tooltip"),
            TokenSelection.PC));
    add(
        createTokenSelectionButton(
            Icons.TOOLBAR_TOKENSELECTION_NPC_ON,
            Icons.TOOLBAR_TOKENSELECTION_NPC_OFF,
            I18N.getText("tools.token.fow.npc.tooltip"),
            TokenSelection.NPC));

    addSeparator(this, 21);

    tokenSelectionButtonAll.setSelected(true);
    // Jamz: End panel

    // the "Select Map" button
    mapselect = createZoneSelectionButton();
    add(mapselect);

    add(Box.createHorizontalStrut(5));

    // Non visible tools
    tbox.createTool(GridTool.class);
    tbox.createTool(BoardTool.class);
    tbox.createTool(FacingTool.class);
    tbox.createTool(StampTool.class);
  }

  public JPanel getOptionPanel() {
    return optionPanel;
  }

  public JToggleButton getPointerGroupButton() {
    return pointerGroupButton;
  }

  public JToggleButton getDrawButton() {
    return drawButton;
  }

  public JToggleButton getTemplateButton() {
    return templateButton;
  }

  public JToggleButton getFogButton() {
    return fogButton;
  }

  public JToggleButton getTopologyButton() {
    return topologyButton;
  }

  public int getOptionsPanelIndex() {
    return getComponentIndex(optionPanelSeparator) + 1;
  }

  public JButton getMapselect() {
    return mapselect;
  }

  private JToggleButton createPointerGroupButton() {
    final OptionPanel pointerGroupOptionPanel = createPointerPanel();
    final JToggleButton pointerGroupButton =
        createButton(
            Icons.TOOLBAR_POINTERTOOL_ON,
            Icons.TOOLBAR_POINTERTOOL_OFF,
            pointerGroupOptionPanel,
            I18N.getText("tools.interaction.tooltip"));

    final var aiPanel = new OptionPanel();

    aiPanel.add(AI_Tool.class, Icons.TOOLBAR_POINTERTOOL_AI_OFF, Icons.TOOLBAR_POINTERTOOL_AI_ON);
    aiPanel.add(
        AI_UseVblTool.class,
        Icons.TOOLBAR_POINTERTOOL_VBL_ON_MOVE_OFF,
        Icons.TOOLBAR_POINTERTOOL_VBL_ON_MOVE_ON);

    pointerGroupOptionPanel.add(Box.createHorizontalStrut(5));
    pointerGroupOptionPanel.add(aiPanel);
    pointerGroupButton.setSelected(true);
    pointerGroupOptionPanel.activate();

    return pointerGroupButton;
  }

  public JButton createZoneSelectionButton() {
    String title = I18N.getText("tools.zoneselector.tooltip");

    final JButton button = new JButton(title, RessourceManager.getBigIcon(Icons.TOOLBAR_ZONE));
    button.setToolTipText(title);

    SwingUtil.makePopupMenuButton(button, ZoneSelectionPopup::new, true);
    return button;
  }

  private OptionPanel createPointerPanel() {
    OptionPanel panel = new OptionPanel();
    panel.add(PointerTool.class, Icons.TOOLBAR_POINTERTOOL_POINTER);
    panel.add(MeasureTool.class, Icons.TOOLBAR_POINTERTOOL_MEASURE);
    return panel;
  }

  private OptionPanel createDrawPanel() {
    OptionPanel panel = new OptionPanel();

    panel.add(DrawingPointerTool.class, Icons.TOOLBAR_DRAW_POINTER);

    panel.add(DeleteDrawingTool.class, Icons.TOOLBAR_DRAW_DELETE);

    panel.addTool(
        new DrawingTool<>(
            "tool.freehand.instructions", "tool.freehand.tooltip", new PolyLineStrategy(true)),
        Icons.TOOLBAR_DRAW_FREEHAND);
    panel.addTool(
        new DrawingTool<>(
            "tool.line.instructions", "tool.line.tooltip", new PolyLineStrategy(false)),
        Icons.TOOLBAR_DRAW_LINE);
    panel.addTool(
        new DrawingTool<>("tool.rect.instructions", "tool.rect.tooltip", new RectangleStrategy()),
        Icons.TOOLBAR_DRAW_BOX);
    panel.addTool(
        new DrawingTool<>("tool.oval.instructions", "tool.oval.tooltip", new OvalStrategy()),
        Icons.TOOLBAR_DRAW_OVAL);
    panel.addTool(
        new DrawingTool<>(
            "tool.rect.instructions", "tool.isorectangle.tooltip", new IsoRectangleStrategy()),
        Icons.TOOLBAR_DRAW_DIAMOND);

    addSeparator(panel, 11);

    panel.add(TextTool.class, Icons.TOOLBAR_DRAW_TEXT);

    return panel;
  }

  private OptionPanel createTemplatePanel() {
    OptionPanel panel = new OptionPanel();
    panel.add(TemplatePointerTool.class, Icons.TOOLBAR_TEMPLATE_POINTER);
    panel.add(RadiusTemplateTool.class, Icons.TOOLBAR_TEMPLATE_RADIUS);
    panel.add(RadiusCellTemplateTool.class, Icons.TOOLBAR_TEMPLATE_RADIUS_CELL);
    panel.add(ConeTemplateTool.class, Icons.TOOLBAR_TEMPLATE_CONE);
    panel.add(LineTemplateTool.class, Icons.TOOLBAR_TEMPLATE_LINE);
    panel.add(LineCellTemplateTool.class, Icons.TOOLBAR_TEMPLATE_LINE_CELL);
    panel.add(BurstTemplateTool.class, Icons.TOOLBAR_TEMPLATE_BURST);
    panel.add(BlastTemplateTool.class, Icons.TOOLBAR_TEMPLATE_BLAST);
    panel.add(WallTemplateTool.class, Icons.TOOLBAR_TEMPLATE_WALL);
    return panel;
  }

  private OptionPanel createFogPanel() {
    OptionPanel panel =
        new OptionPanel() {
          @Override
          protected void activate() {
            super.activate();
            Campaign c = MapTool.getCampaign();
            ZoneRenderer zr = MapTool.getFrame().getCurrentZoneRenderer();
            // Check if there is a map. Fix #1605
            if (zr != null) {
              boolean tokensSelected = !zr.getSelectedTokenSet().isEmpty();
              if (tokensSelected && !c.hasUsedFogToolbar() && !MapTool.isHostingServer()) {
                MapTool.addLocalMessage(
                    MessageUtil.getFormattedSystemMsg(
                        I18N.getText("ToolbarPanel.manualFogActivated")));
                MapTool.showWarning("ToolbarPanel.manualFogActivated");
              }
            }
          }
        };

    panel.addTool(
        new ExposeTool<>(
            "tool.rectexpose.instructions", "tool.rectexpose.tooltip", new RectangleStrategy()),
        Icons.TOOLBAR_FOG_EXPOSE_BOX);
    panel.addTool(
        new ExposeTool<>(
            "tool.ovalexpose.instructions", "tool.ovalexpose.tooltip", new OvalStrategy()),
        Icons.TOOLBAR_FOG_EXPOSE_OVAL);
    panel.addTool(
        new ExposeTool<>(
            "tool.polyexpose.instructions", "tool.polyexpose.tooltip", new PolyLineStrategy(false)),
        Icons.TOOLBAR_FOG_EXPOSE_POLYGON);
    panel.addTool(
        new ExposeTool<>(
            "tool.freehand.instructions", "tool.freehand.tooltip", new PolyLineStrategy(true)),
        Icons.TOOLBAR_FOG_EXPOSE_FREEHAND);
    panel.addTool(
        new ExposeTool<>(
            "tool.rectexpose.instructions",
            "tool.isorectangleexpose.tooltip",
            new IsoRectangleStrategy()),
        Icons.TOOLBAR_FOG_EXPOSE_DIAMOND);

    return panel;
  }

  private OptionPanel createTopologyPanel() {
    OptionPanel panel = new OptionPanel();
    final var topologyModeSelectionPanel = new TopologyModeSelectionPanel();
    topologyModeSelectionPanel.setEnabled(false);

    panel.addTool(new WallTopologyTool(), Icons.TOOLBAR_TOPOLOGY_WALL);

    panel.addTool(
        new TopologyTool<>(
            "tool.recttopology.instructions",
            "tool.recttopology.tooltip",
            true,
            new RectangleStrategy(),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_BOX);
    panel.addTool(
        new TopologyTool<>(
            "tool.recttopology.instructions",
            "tool.recttopologyhollow.tooltip",
            false,
            new RectangleStrategy(),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_BOX_HOLLOW);
    panel.addTool(
        new TopologyTool<>(
            "tool.ovaltopology.instructions",
            "tool.ovaltopology.tooltip",
            true,
            // 10 steps to keep number of topology vertices reasonable.
            new OvalStrategy(10),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_OVAL);
    panel.addTool(
        new TopologyTool<>(
            "tool.ovaltopology.instructions",
            "tool.ovaltopologyhollow.tooltip",
            false,
            // 10 steps to keep number of topology vertices reasonable.
            new OvalStrategy(10),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_OVAL_HOLLOW);
    panel.addTool(
        new TopologyTool<>(
            "tool.poly.instructions",
            "tool.polytopo.tooltip",
            true,
            new PolyLineStrategy(false),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_POLYGON);
    panel.addTool(
        new TopologyTool<>(
            "tool.poly.instructions",
            "tool.polylinetopo.tooltip",
            false,
            new PolyLineStrategy(false),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_POLYLINE);
    panel.addTool(
        new TopologyTool<>(
            "tool.crosstopology.instructions",
            "tool.crosstopology.tooltip",
            false,
            new CrossStrategy(),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_CROSS);
    panel.addTool(
        new TopologyTool<>(
            "tool.isorectangletopology.instructions",
            "tool.isorectangletopology.tooltip",
            true,
            new IsoRectangleStrategy(),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_DIAMOND);
    panel.addTool(
        new TopologyTool<>(
            "tool.isorectangletopology.instructions",
            "tool.isorectangletopologyhollow.tooltip",
            false,
            new IsoRectangleStrategy(),
            topologyModeSelectionPanel),
        Icons.TOOLBAR_TOPOLOGY_DIAMOND_HOLLOW);

    panel.addTool(new VBLPenTool(topologyModeSelectionPanel), Icons.TOOLBAR_DRAW_FREEHAND);

    // Add with separator to separate mode button group from shape button group.
    addSeparator(panel, 11);

    panel.add(topologyModeSelectionPanel);

    return panel;
  }

  private static Component addSeparator(JToolBar toolBar, final int size) {
    final var sep =
        new JToolBar.Separator() {
          @Override
          public Dimension getPreferredSize() {
            return new Dimension(size, super.getPreferredSize().height);
          }
        };
    toolBar.add(sep);
    return sep;
  }

  private JToggleButton createButton(
      final Icons icon, final Icons offIcon, final OptionPanel panel, String tooltip) {
    final JToggleButton button = new JToggleButton();
    button.setToolTipText(tooltip);

    button.addActionListener(
        e -> {
          if (button.isSelected()) {
            panel.activate();
            ((CardLayout) optionPanel.getLayout()).show(optionPanel, tooltip);
            // This is has only an effect when the panel is used in fullscreen mode.
            optionPanel.setSize(panel.getPreferredSize());
          }
        });

    button.setIcon(RessourceManager.getBigIcon(offIcon));
    button.setSelectedIcon(RessourceManager.getBigIcon(icon));

    optionPanel.add(panel, tooltip);
    buttonGroup.add(button);

    return button;
  }

  private JToggleButton createMuteButton(
      final Icons icon, final Icons offIcon, String mutetooltip, String unmutetooltip) {
    final JToggleButton button = new JToggleButton();
    button.setToolTipText(mutetooltip);
    button.addActionListener(
        e -> {
          MediaPlayerAdapter.setGlobalMute(button.isSelected());
          if (button.isSelected()) {
            button.setToolTipText(unmutetooltip);
          } else {
            button.setToolTipText(mutetooltip);
          }
        });

    button.setIcon(RessourceManager.getBigIcon(offIcon));
    button.setSelectedIcon(RessourceManager.getBigIcon(icon));

    if (MediaPlayerAdapter.getGlobalMute()) {
      button.doClick();
    }

    return button;
  }

  private JToggleButton createTokenSelectionButton(
      final Icons icon, final Icons offIcon, String tooltip, TokenSelection tokenSelection) {
    final JToggleButton button = new JToggleButton();
    button.setToolTipText(tooltip);
    button.addActionListener(
        e -> {
          if (button.isSelected()) {
            ZoneRenderer zr = MapTool.getFrame().getCurrentZoneRenderer();
            // Check if there is a map. Fix #1605
            if (zr != null) {
              zr.getZone().setTokenSelection(tokenSelection);
              MapTool.getFrame().refresh();
            }
          }
        });
    button.setIcon(RessourceManager.getBigIcon(offIcon));
    button.setSelectedIcon(RessourceManager.getBigIcon(icon));
    tokenSelectionbuttonGroup.add(button);
    return button;
  }

  public void setTokenSelectionGroupEnabled(boolean enabled) {
    Enumeration<AbstractButton> enumeration = tokenSelectionbuttonGroup.getElements();
    while (enumeration.hasMoreElements()) {
      AbstractButton button = enumeration.nextElement();
      button.setEnabled(enabled);
    }
  }

  private class OptionPanel extends JToolBar {
    private DefaultTool firstTool;
    private DefaultTool currentTool;
    private final ButtonGroup buttonGroup = new ButtonGroup();

    public OptionPanel() {
      setFloatable(false);
      setRollover(true);
      setBorder(null);
      setBorderPainted(false);
    }

    public void add(Class<? extends Tool> toolClass, Icons icon) {
      add(toolClass, icon, icon);
    }

    public void add(Class<? extends Tool> toolClass, Icons icon, Icons selectedIcon) {
      var tool = toolbox.createTool(toolClass);
      setupTool(tool, icon, selectedIcon);
    }

    public void addTool(Tool tool, Icons icon) {
      addTool(tool, icon, icon);
    }

    public void addTool(Tool tool, Icons icon, Icons selectedIcon) {
      toolbox.addTool(tool);
      setupTool(tool, icon, selectedIcon);
    }

    private void setupTool(Tool tool, Icons icon, Icons selectedIcon) {
      var button = new JToggleButton();
      button.setIcon(RessourceManager.getBigIcon(icon));
      button.setSelectedIcon(RessourceManager.getBigIcon(selectedIcon));
      button.setFocusable(false);
      button.setFocusPainted(false);
      button.setToolTipText(I18N.getText(tool.getTooltip()));

      if (tool instanceof DefaultTool defaultTool) {
        if (firstTool == null) {
          firstTool = defaultTool;
        }
        buttonGroup.add(button);
        button.addActionListener(
            e -> {
              if (button.isSelected()) {
                currentTool = defaultTool;
                toolbox.setSelectedTool(defaultTool);
              }
            });
      }

      button.addActionListener(
          new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              tool.setSelected(button.isSelected());
            }
          });

      tool.addPropertyChangeListener(
          new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
              switch (evt.getPropertyName()) {
                case Tool.ENABLED_PROPERTY -> {
                  var enabled = (boolean) evt.getNewValue();
                  button.setEnabled(enabled);
                }
                case Tool.SELECTED_PROPERTY -> {
                  var selected = (boolean) evt.getNewValue();
                  if (selected && tool instanceof DefaultTool defaultTool) {
                    // Remember for later in case the user switches away.
                    currentTool = defaultTool;
                    toolbox.setSelectedTool(defaultTool);
                  }
                  button.setSelected(selected);
                }
              }
              toolbox.updateTools();
            }
          });

      tool.updateState();
      button.setSelected(tool.isSelected());
      button.setEnabled(tool.isEnabled());

      add(button);
    }

    protected void activate() {
      if (currentTool == null) {
        currentTool = firstTool;
      }
      if (currentTool != null) {
        toolbox.setSelectedTool(currentTool);
      }
    }
  }
}
