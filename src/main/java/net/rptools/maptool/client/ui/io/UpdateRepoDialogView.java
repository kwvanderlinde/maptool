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
package net.rptools.maptool.client.ui.io;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.jidesoft.swing.CheckBoxListWithSelectable;
import java.awt.*;
import javax.swing.*;

public class UpdateRepoDialogView {
  private JPanel mainPanel;
  /* spotless:off */
  {
    // GUI initializer generated by IntelliJ IDEA GUI Designer
    // >>> IMPORTANT!! <<<
    // DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR
   * call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    mainPanel = new JPanel();
    mainPanel.setLayout(new GridLayoutManager(10, 6, new Insets(5, 5, 5, 5), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("Save To:");
    mainPanel.add(
        label1,
        new GridConstraints(
            5,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label2 = new JLabel();
    label2.setText(
        "Repositories are listed in the same order as they appear in the Campaign Properties.");
    mainPanel.add(
        label2,
        new GridConstraints(
            0,
            0,
            1,
            5,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label3 = new JLabel();
    label3.setText("FTP Server:");
    mainPanel.add(
        label3,
        new GridConstraints(
            6,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label4 = new JLabel();
    label4.setText("Username:");
    mainPanel.add(
        label4,
        new GridConstraints(
            8,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label5 = new JLabel();
    label5.setText("Password:");
    mainPanel.add(
        label5,
        new GridConstraints(
            9,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JButton button1 = new JButton();
    button1.setName("@okButton");
    button1.setText("OK");
    button1.setToolTipText("Begin the update process.");
    mainPanel.add(
        button1,
        new GridConstraints(
            9,
            3,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JButton button2 = new JButton();
    button2.setName("@cancelButton");
    button2.setText("Cancel");
    button2.setToolTipText("Cancel the update.");
    mainPanel.add(
        button2,
        new GridConstraints(
            9,
            4,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JTextField textField1 = new JTextField();
    textField1.setName("@hostname");
    textField1.setText("");
    textField1.setToolTipText(
        "The FTP server name may be a hostname or an IP address.  When using an IPv6 address"
            + " surround it with \"[\" and \"]\".");
    mainPanel.add(
        textField1,
        new GridConstraints(
            6,
            1,
            1,
            4,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JTextField textField2 = new JTextField();
    textField2.setName("@username");
    textField2.setText("");
    textField2.setToolTipText("The username to login as on the FTP server.");
    mainPanel.add(
        textField2,
        new GridConstraints(
            8,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JPasswordField passwordField1 = new JPasswordField();
    passwordField1.setName("@password");
    passwordField1.setText("");
    passwordField1.setToolTipText(
        "The password associated with the above username.  It will not display here nor will it be"
            + " saved for future use.");
    mainPanel.add(
        passwordField1,
        new GridConstraints(
            9,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JTextField textField3 = new JTextField();
    textField3.setName("@saveTo");
    textField3.setText("");
    textField3.setToolTipText(
        "Set this to one of the repositories in the list above.  You may double-click an entry from"
            + " the list, use keyboard cut/paste, or type in the text.");
    mainPanel.add(
        textField3,
        new GridConstraints(
            5,
            1,
            1,
            4,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label6 = new JLabel();
    label6.setText(
        "<html>Double-click a repository to copy the repository location into the <b>Save To:</b>"
            + " field.");
    mainPanel.add(
        label6,
        new GridConstraints(
            3,
            0,
            1,
            5,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label7 = new JLabel();
    label7.setText("Directory on Server:");
    mainPanel.add(
        label7,
        new GridConstraints(
            7,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JTextField textField4 = new JTextField();
    textField4.setName("@directory");
    textField4.setSelectionEnd(1);
    textField4.setSelectionStart(1);
    textField4.setText("/");
    textField4.setToolTipText(
        "<html>If your FTP login requires that you change directory to navigate to the location of"
            + " the repository's <b>index.gz</b> directory, enter the directory name here.");
    mainPanel.add(
        textField4,
        new GridConstraints(
            7,
            1,
            1,
            4,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label8 = new JLabel();
    label8.setText(
        "Place a checkmark in each repository that you want to search for existing assets.");
    mainPanel.add(
        label8,
        new GridConstraints(
            1,
            0,
            1,
            5,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JLabel label9 = new JLabel();
    label9.setText(
        "<html>This is the <b>index.gz</b> that will be updated with the new image information.");
    mainPanel.add(
        label9,
        new GridConstraints(
            4,
            0,
            1,
            5,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final JCheckBox checkBox1 = new JCheckBox();
    checkBox1.setName("@subdir");
    checkBox1.setSelected(true);
    checkBox1.setText("Create subdir on server?");
    checkBox1.setToolTipText(
        "Some FTP servers may not allow MapTool to create a directory.  If this is the case for"
            + " your server yet you want new assets in their own directory, uncheck this field and"
            + " create the directory yourself, then specify that directory as the location on the"
            + " server.");
    mainPanel.add(
        checkBox1,
        new GridConstraints(
            8,
            2,
            1,
            3,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    final Spacer spacer1 = new Spacer();
    mainPanel.add(
        spacer1,
        new GridConstraints(
            9,
            2,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            1,
            null,
            null,
            null,
            0,
            false));
    final Spacer spacer2 = new Spacer();
    mainPanel.add(
        spacer2,
        new GridConstraints(
            2,
            5,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_VERTICAL,
            1,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            null,
            null,
            null,
            0,
            false));
    final JScrollPane scrollPane1 = new JScrollPane();
    mainPanel.add(
        scrollPane1,
        new GridConstraints(
            2,
            0,
            1,
            5,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            null,
            null,
            null,
            0,
            false));
    final CheckBoxListWithSelectable checkBoxListWithSelectable1 = new CheckBoxListWithSelectable();
    checkBoxListWithSelectable1.setToolTipText(
        "&lt;html&gt;These repositories are from the &lt;b&gt;Campaign Properties&lt;/b&gt; on the"
            + " &lt;b&gt;Edit&lt;/b&gt; menu.");
    scrollPane1.setViewportView(checkBoxListWithSelectable1);
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return mainPanel;
  }
  /* spotless:on */
}
