package net.rptools.maptool.client.ui.javfx;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;

public class SimpleSwingJavaFXDialog {

  private final String fxmlPath;
  private SwingJavaFXDialogController controller;
  private final String title;
  private SwingJavaFXDialog dialog;


  public SimpleSwingJavaFXDialog(String fxmlPath, String title) {
    this.fxmlPath = fxmlPath;
    this.title = I18N.getText(title);
  }

  /** Shows the dialog and its contents. This method must be called on the Swing Event thread. */
  public void show() {
    if (!SwingUtilities.isEventDispatchThread()) {
      throw new AssertionError(
          "PlayerDatabaseDialog.show() can only be called on the Swing thread.");
    }

    FXMLLoaderUtil loaderUtil = new FXMLLoaderUtil();
    loaderUtil.jfxPanelFromFXML(fxmlPath, this::showEDT);
  }

  /**
   * Displays the contents of the {@link JFXPanel} in a {@link SwingJavaFXDialog}. This method must
   * be called on the Swing EDT thread.
   *
   * @param panel the panel to display in the dialog.
   * @param controller the controller class for the dialog.
   */
  private void showEDT(JFXPanel panel, SwingJavaFXDialogController controller) {
    this.controller = controller;
    if (!SwingUtilities.isEventDispatchThread()) {
      throw new AssertionError(
          "showEDT() can only be called on the Swing thread.");
    }
    dialog =
        new SwingJavaFXDialog(I18N.getText(title), MapTool.getFrame(), panel);
    Platform.runLater(
        () -> {
          controller.registerEventHandler(this::closeDialog);
          controller.init();
        });
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            controller.deregisterEventHandler(SimpleSwingJavaFXDialog.this::closeDialog);
            e.getWindow().dispose();
          }
        });
    dialog.showDialog();
  }

  /**
   * This method closes the dialog. It is safe to call this method on any thread.
   *
   * @param controller the controller for the JavaFX gui.
   */
  private void closeDialog(SwingJavaFXDialogController controller) {
    if (SwingUtilities.isEventDispatchThread()) {
      closeDialogEDT(controller);
    } else {
      SwingUtilities.invokeLater(() -> closeDialogEDT(controller));
    }
  }

  /**
   * This method closes the dialog It must be called only on the Swing EDT thread.
   *
   * @param controller the controller for the JavaFX gui.
   */
  private void closeDialogEDT(SwingJavaFXDialogController controller) {
    if (!SwingUtilities.isEventDispatchThread()) {
      throw new AssertionError(
          "closeDialogEDT() can only be called on the Swing thread.");
    }
    dialog.closeDialog();
  }

  public  SwingJavaFXDialogController getController() {
    return controller;
  }

}
