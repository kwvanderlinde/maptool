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
package net.rptools.maptool.util.threads;

import java.awt.EventQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javax.swing.SwingUtilities;

/**
 * Utility for helping execute tasks on the correct thread.
 *
 * @param <T> the type that this execution helper will be dealing with..
 */
public final class ThreadExecutionHelper {
  private ThreadExecutionHelper() {}

  /**
   * This method ensures tht the callable argument passed to it is executed on the swing thread. If
   * called from the swing thread it will execute immediately and return an already completed {@link
   * CompletableFuture}.
   *
   * @param callable the task to be executed on the swing thread.
   * @return A {@link CompletableFuture} for the task to be executed.
   */
  public static <T> CompletableFuture<T> runOnSwingThread(Callable<T> callable) {
    if (SwingUtilities.isEventDispatchThread()) {
      return CompletableFuture.completedFuture(doCall(callable));
    } else {
      return CompletableFuture.supplyAsync(() -> doCall(callable), EventQueue::invokeLater);
    }
  }

  /**
   * This method ensures tht the callable argument passed to it is executed on the JavaFX thread. If
   * called from the swing thread it will execute immediately and return an already completed {@link
   * CompletableFuture}.
   *
   * @param callable the task to be executed on the swing thread.
   * @return A {@link CompletableFuture} for the task to be executed.
   */
  public static <T> CompletableFuture<T> runOnJFXThread(Callable<T> callable) {
    if (Platform.isFxApplicationThread()) {
      return CompletableFuture.completedFuture(doCall(callable));
    } else {
      return CompletableFuture.supplyAsync(() -> doCall(callable), Platform::runLater);
    }
  }

  /**
   * Helper method to run the task passed to one of the runOnXThread() methods.
   *
   * @param callable the task to be executed.
   * @return the value returned by the task.
   * @throws CompletionException if any exception occurs while running the task.
   */
  private static <T> T doCall(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      if (e instanceof CompletionException ce) {
        throw ce;
      } else {
        throw new CompletionException(e);
      }
    }
  }
}
