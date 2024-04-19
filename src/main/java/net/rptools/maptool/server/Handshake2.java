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
package net.rptools.maptool.server;

import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import net.rptools.clientserver.decomposed.Connection;

public interface Handshake2 {

  /**
   * Returns if the handshake has been successful or not.
   *
   * @return {@code true} if the handshake has been successful, {code false} if it has failed or is
   *     still in progress.
   */
  boolean isSuccessful();

  /**
   * Returns the message for the error -- if any -- that occurred during the handshake.
   *
   * @return the message for the error that occurred during handshake.
   */
  String getErrorMessage();

  /**
   * Returns the connection for this {@code ServerHandshake}.
   *
   * @return the connection for this {@code ServerHandshake}.
   */
  Connection getConnection();

  /**
   * Returns the exception -- if any -- that occurred during processing of the handshake.
   *
   * @return the exception that occurred during the processing of the handshake.
   */
  @Nullable
  Exception getException();

  /**
   * Starts the handshake process.
   *
   * @throws ExecutionException when there is an exception in the background task.
   * @throws InterruptedException when the background task is interrupted.
   */
  // TODO Server-side we don't expect these kinds of failures. Can we eliminate them somehow for
  //  that case?
  //  Or... can we just dictate that they don't bubble up here, but instead need to be communicated
  //  as a handshake failure? Yeah, I prefer that. It should be as simple as catching the errors in
  //  ClientHandshake and immediately notifyObservers() in that case.
  void startHandshake() throws ExecutionException, InterruptedException;

  interface Observer {
    // TODO Separate callbacks for error and success cases.

    void onCompleted(Handshake2 handshake);
  }
}
