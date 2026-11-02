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
package net.rptools.clientserver.simple.lib;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.Nonnull;
import net.rptools.clientserver.ActivityListener;

public class ActivityListeningOutputStream extends FilterOutputStream {
  private final ActivityListener listener;
  private int expectedBytes = 0;
  private int currentMessageBytes = 0;

  public ActivityListeningOutputStream(OutputStream nested, ActivityListener listener) {
    super(nested);
    this.listener = listener;
  }

  private void notifyListeners(ActivityListener.State state) {
    listener.notify(ActivityListener.Direction.Outbound, state, expectedBytes, currentMessageBytes);
  }

  public void startMessage(int length) {
    expectedBytes = length;
    currentMessageBytes = 0;
    notifyListeners(ActivityListener.State.Start);
  }

  public void completeMessage(int length) {
    currentMessageBytes = length;
    notifyListeners(ActivityListener.State.Complete);
  }

  @Override
  public void write(int b) throws IOException {
    ++currentMessageBytes;
    notifyListeners(ActivityListener.State.Progress);
    super.write(b);
  }

  @Override
  public void write(@Nonnull byte[] b, int off, int len) throws IOException {
    super.write(b, off, len);

    currentMessageBytes += len;
    notifyListeners(ActivityListener.State.Progress);
  }
}
