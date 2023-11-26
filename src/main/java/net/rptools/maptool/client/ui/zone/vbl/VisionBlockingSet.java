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
package net.rptools.maptool.client.ui.zone.vbl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineSegment;

public class VisionBlockingSet {
  private final ArrayList<LineSegment> segments;
  private final Envelope envelope = new Envelope();

  public VisionBlockingSet() {
    segments = new ArrayList<>();
  }

  public boolean isEmpty() {
    return this.segments.isEmpty();
  }

  public void union(VisionBlockingSet other) {
    this.segments.addAll(other.segments);
    this.envelope.expandToInclude(other.envelope);
  }

  public void add(LineSegment segment) {
    this.segments.add(segment);
    this.envelope.expandToInclude(segment.p0);
    this.envelope.expandToInclude(segment.p1);
  }

  public Envelope getEnvelope() {
    return new Envelope(envelope);
  }

  public Collection<LineSegment> getSegments() {
    return Collections.unmodifiableCollection(segments);
  }
}
