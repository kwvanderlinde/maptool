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
package net.rptools.maptool.model;

import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;
import net.rptools.maptool.model.TokenFootprint.OffsetTranslator;
import net.rptools.maptool.server.proto.GridTypeDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum GridType {
  Square(
      OffsetTranslator.IDENTITY,
      new TokenFootprints(
          new GUID(0x7F000101CF65152AL, 0x050000002A000101L),
          List.of(
              Map.entry(
                  new GUID(0x7F000101CD65152AL, 0x010000002A000101L),
                  new TokenFootprint("Fine", "TokenFootprint.name.fine", 0.5, List.of())),
              Map.entry(
                  new GUID(0x7F000101CD65152AL, 0x020000002A000101L),
                  new TokenFootprint(
                      "Diminutive", "TokenFootprint.name.diminutive", 0.5, List.of())),
              Map.entry(
                  new GUID(0x7F000101CE65152AL, 0x030000002A000100L),
                  new TokenFootprint("Tiny", "TokenFootprint.name.tiny", 0.5, List.of())),
              Map.entry(
                  new GUID(0x7F000101CE65152AL, 0x040000002A000100L),
                  new TokenFootprint("Small", "TokenFootprint.name.small", 0.75, List.of())),
              Map.entry(
                  new GUID(0x7F000101CF65152AL, 0x050000002A000101L),
                  new TokenFootprint("Medium", "TokenFootprint.name.medium", 1.0, List.of())),
              Map.entry(
                  new GUID(0x7F000101D065152AL, 0x060000002A000100L),
                  new TokenFootprint(
                      "Large",
                      "TokenFootprint.name.large",
                      1.0,
                      List.of(new Point(1, 0), new Point(0, 1), new Point(1, 1)))),
              Map.entry(
                  new GUID(0x7F000101D065152AL, 0x070000002A000100L),
                  new TokenFootprint(
                      "Huge",
                      "TokenFootprint.name.huge",
                      1.0,
                      List.of(
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(2, 1),
                          new Point(0, 2),
                          new Point(1, 2),
                          new Point(2, 2)))),
              Map.entry(
                  new GUID(0x7F000101D165152AL, 0x080000002A000101L),
                  new TokenFootprint(
                      "Gargantuan",
                      "TokenFootprint.name.gargantuan",
                      1.0,
                      List.of(
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(3, 0),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(2, 1),
                          new Point(3, 1),
                          new Point(0, 2),
                          new Point(1, 2),
                          new Point(2, 2),
                          new Point(3, 2),
                          new Point(0, 3),
                          new Point(1, 3),
                          new Point(2, 3),
                          new Point(3, 3)))),
              Map.entry(
                  new GUID(0x7F000101E165152AL, 0x090000002A000101L),
                  new TokenFootprint(
                      "Colossal",
                      "TokenFootprint.name.colossal",
                      1.0,
                      List.of(
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(3, 0),
                          new Point(4, 0),
                          new Point(5, 0),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(2, 1),
                          new Point(3, 1),
                          new Point(4, 1),
                          new Point(5, 1),
                          new Point(0, 2),
                          new Point(1, 2),
                          new Point(2, 2),
                          new Point(3, 2),
                          new Point(4, 2),
                          new Point(5, 2),
                          new Point(0, 3),
                          new Point(1, 3),
                          new Point(2, 3),
                          new Point(3, 3),
                          new Point(4, 3),
                          new Point(5, 3),
                          new Point(0, 4),
                          new Point(1, 4),
                          new Point(2, 4),
                          new Point(3, 4),
                          new Point(4, 4),
                          new Point(5, 4),
                          new Point(0, 5),
                          new Point(1, 5),
                          new Point(2, 5),
                          new Point(3, 5),
                          new Point(4, 5),
                          new Point(5, 5))))))),
  Isometric(
      OffsetTranslator.IDENTITY,
      new TokenFootprints(
          new GUID(0x7F000101CF65152AL, 0x050000002A000101L),
          List.of(
              Map.entry(
                  new GUID(0x7F000101CD65152AL, 0x010000002A000101L),
                  new TokenFootprint("Fine", "TokenFootprint.name.fine", 0.5, List.of())),
              Map.entry(
                  new GUID(0x7F000101CD65152AL, 0x020000002A000101L),
                  new TokenFootprint(
                      "Diminutive", "TokenFootprint.name.diminutive", 0.5, List.of())),
              Map.entry(
                  new GUID(0x7F000101CE65152AL, 0x030000002A000100L),
                  new TokenFootprint("Tiny", "TokenFootprint.name.tiny", 0.5, List.of())),
              Map.entry(
                  new GUID(0x7F000101CE65152AL, 0x040000002A000100L),
                  new TokenFootprint("Small", "TokenFootprint.name.small", 0.75, List.of())),
              Map.entry(
                  new GUID(0x7F000101CF65152AL, 0x050000002A000101L),
                  new TokenFootprint("Medium", "TokenFootprint.name.medium", 1.0, List.of())),
              Map.entry(
                  new GUID(0x7F000101D065152AL, 0x060000002A000100L),
                  new TokenFootprint(
                      "Large",
                      "TokenFootprint.name.large",
                      1.0,
                      List.of(new Point(1, 0), new Point(0, 1), new Point(1, 1)))),
              Map.entry(
                  new GUID(0x7F000101D065152AL, 0x070000002A000100L),
                  new TokenFootprint(
                      "Huge",
                      "TokenFootprint.name.huge",
                      1.0,
                      List.of(
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(2, 1),
                          new Point(0, 2),
                          new Point(1, 2),
                          new Point(2, 2)))),
              Map.entry(
                  new GUID(0x7F000101D165152AL, 0x080000002A000101L),
                  new TokenFootprint(
                      "Gargantuan",
                      "TokenFootprint.name.gargantuan",
                      1.0,
                      List.of(
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(3, 0),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(2, 1),
                          new Point(3, 1),
                          new Point(0, 2),
                          new Point(1, 2),
                          new Point(2, 2),
                          new Point(3, 2),
                          new Point(0, 3),
                          new Point(1, 3),
                          new Point(2, 3),
                          new Point(3, 3)))),
              Map.entry(
                  new GUID(0x7F000101E165152AL, 0x090000002A000101L),
                  new TokenFootprint(
                      "Colossal",
                      "TokenFootprint.name.colossal",
                      1.0,
                      List.of(
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(3, 0),
                          new Point(4, 0),
                          new Point(5, 0),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(2, 1),
                          new Point(3, 1),
                          new Point(4, 1),
                          new Point(5, 1),
                          new Point(0, 2),
                          new Point(1, 2),
                          new Point(2, 2),
                          new Point(3, 2),
                          new Point(4, 2),
                          new Point(5, 2),
                          new Point(0, 3),
                          new Point(1, 3),
                          new Point(2, 3),
                          new Point(3, 3),
                          new Point(4, 3),
                          new Point(5, 3),
                          new Point(0, 4),
                          new Point(1, 4),
                          new Point(2, 4),
                          new Point(3, 4),
                          new Point(4, 4),
                          new Point(5, 4),
                          new Point(0, 5),
                          new Point(1, 5),
                          new Point(2, 5),
                          new Point(3, 5),
                          new Point(4, 5),
                          new Point(5, 5))))))),
  HexVertical(
      OffsetTranslator.HEX_VERTICAL,
      new TokenFootprints(
          new GUID(0x7F00010109655C38L, 0x0100000038000101L),
          List.of(
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0100000040A8090CL),
                  new TokenFootprint("1/6", "TokenFootprint.name.1/6", .408, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0200000040A8090CL),
                  new TokenFootprint("1/4", "TokenFootprint.name.1/4", .500, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0300000040A8090CL),
                  new TokenFootprint("1/3", "TokenFootprint.name.1/3", .577, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0400000040A8090CL),
                  new TokenFootprint("1/2", "TokenFootprint.name.1/2", .707, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0500000040A8090CL),
                  new TokenFootprint("2/3", "TokenFootprint.name.2/3", .816, List.of())),
              Map.entry(
                  new GUID(0x7F00010109655C38L, 0x0100000038000101L),
                  new TokenFootprint("Medium", "TokenFootprint.name.medium", 1.0, List.of())),
              Map.entry(
                  new GUID(0x7F00010109655C38L, 0x0200000038000101L),
                  new TokenFootprint(
                      "Large",
                      "TokenFootprint.name.large",
                      1.0,
                      List.of(new Point(0, 1), new Point(1, 0)))),
              Map.entry(
                  new GUID(0x7F00010109655C38L, 0x0300000038000101L),
                  new TokenFootprint(
                      "Huge",
                      "TokenFootprint.name.huge",
                      1.0,
                      List.of(
                          new Point(-1, -1),
                          new Point(-1, 0),
                          new Point(0, -1),
                          new Point(0, 1),
                          new Point(1, -1),
                          new Point(1, 0)))),
              Map.entry(
                  new GUID(0xC0A80F464BAAC1B1L, 0x0900000080800A42L),
                  new TokenFootprint(
                      "Humongous",
                      "TokenFootprint.name.humongous",
                      1.0,
                      List.of(
                          new Point(-2, -1),
                          new Point(-2, 0),
                          new Point(-2, 1),
                          new Point(-1, -2),
                          new Point(-1, -1),
                          new Point(-1, 0),
                          new Point(-1, 1),
                          new Point(0, -2),
                          new Point(0, -1),
                          new Point(0, 1),
                          new Point(0, 2),
                          new Point(1, -2),
                          new Point(1, -1),
                          new Point(1, 0),
                          new Point(1, 1),
                          new Point(2, -1),
                          new Point(2, 0),
                          new Point(2, 1))))))),
  HexHorizontal(
      OffsetTranslator.HEX_HORIZONTAL,
      new TokenFootprints(
          new GUID(0x7F00010109655C38L, 0x0100000038000101L),
          List.of(
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0100000040A8090CL),
                  new TokenFootprint("1/6", "TokenFootprint.name.1/6", .408, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0200000040A8090CL),
                  new TokenFootprint("1/4", "TokenFootprint.name.1/4", .500, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0300000040A8090CL),
                  new TokenFootprint("1/3", "TokenFootprint.name.1/3", .577, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0400000040A8090CL),
                  new TokenFootprint("1/2", "TokenFootprint.name.1/2", .707, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0E0CB9FB56L, 0x0500000040A8090CL),
                  new TokenFootprint("2/3", "TokenFootprint.name.2/3", .816, List.of())),
              Map.entry(
                  new GUID(0x7F00010109655C38L, 0x0100000038000101L),
                  new TokenFootprint("Medium", "TokenFootprint.name.medium", 1.0, List.of())),
              Map.entry(
                  new GUID(0x7F00010109655C38L, 0x0200000038000101L),
                  new TokenFootprint(
                      "Large",
                      "TokenFootprint.name.large",
                      1.0,
                      List.of(new Point(1, 0), new Point(0, 1)))),
              Map.entry(
                  new GUID(0x7F00010109655C38L, 0x0300000038000101L),
                  new TokenFootprint(
                      "Huge",
                      "TokenFootprint.name.huge",
                      1.0,
                      List.of(
                          new Point(0, 1),
                          new Point(1, 0),
                          new Point(-1, 0),
                          new Point(-1, -1),
                          new Point(0, -1),
                          new Point(-1, 1)))),
              Map.entry(
                  new GUID(0xC0A80F464BAAC1B1L, 0x0900000080800A42L),
                  new TokenFootprint(
                      "Humongous",
                      "TokenFootprint.name.humongous",
                      1.0,
                      List.of(
                          new Point(-1, -2),
                          new Point(0, -2),
                          new Point(1, -2),
                          new Point(-2, -1),
                          new Point(-1, -1),
                          new Point(0, -1),
                          new Point(1, -1),
                          new Point(-2, 0),
                          new Point(-1, 0),
                          new Point(1, 0),
                          new Point(2, 0),
                          new Point(-2, 1),
                          new Point(-1, 1),
                          new Point(0, 1),
                          new Point(1, 1),
                          new Point(-1, 2),
                          new Point(0, 2),
                          new Point(1, 2))))))),
  None(
      OffsetTranslator.IDENTITY,
      new TokenFootprints(
          new GUID(0xC0A80F0EBEFED0A2L, 0x0C00000080800E0EL),
          List.of(
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0100000080800E0EL),
                  new TokenFootprint("-11", "TokenFootprint.name.-11", 0.086, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0200000080800E0EL),
                  new TokenFootprint("-10", "TokenFootprint.name.-10", 0.107, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0300000080800E0EL),
                  new TokenFootprint("-9", "TokenFootprint.name.-9", 0.134, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0400000080800E0EL),
                  new TokenFootprint("-8", "TokenFootprint.name.-8", 0.168, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0500000080800E0EL),
                  new TokenFootprint("-7", "TokenFootprint.name.-7", 0.210, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0600000080800E0EL),
                  new TokenFootprint("-6", "TokenFootprint.name.-6", 0.262, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0700000080800E0EL),
                  new TokenFootprint("-5", "TokenFootprint.name.-5", 0.328, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0800000080800E0EL),
                  new TokenFootprint("-4", "TokenFootprint.name.-4", 0.410, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0900000080800E0EL),
                  new TokenFootprint("-3", "TokenFootprint.name.-3", 0.512, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0A00000080800E0EL),
                  new TokenFootprint("-2", "TokenFootprint.name.-2", 0.640, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0B00000080800E0EL),
                  new TokenFootprint("-1", "TokenFootprint.name.-1", 0.800, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0C00000080800E0EL),
                  new TokenFootprint("0", "TokenFootprint.name.0", 1.000, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0D00000080800E0EL),
                  new TokenFootprint("1", "TokenFootprint.name.1", 1.200, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0E00000080800E0EL),
                  new TokenFootprint("2", "TokenFootprint.name.2", 1.440, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x0F00000080800E0EL),
                  new TokenFootprint("3", "TokenFootprint.name.3", 1.728, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1000000080800E0EL),
                  new TokenFootprint("4", "TokenFootprint.name.4", 2.074, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1100000080800E0EL),
                  new TokenFootprint("5", "TokenFootprint.name.5", 2.488, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1200000080800E0EL),
                  new TokenFootprint("6", "TokenFootprint.name.6", 2.986, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1300000080800E0EL),
                  new TokenFootprint("7", "TokenFootprint.name.7", 3.583, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1400000080800E0EL),
                  new TokenFootprint("8", "TokenFootprint.name.8", 4.300, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1500000080800E0EL),
                  new TokenFootprint("9", "TokenFootprint.name.9", 5.160, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1600000080800E0EL),
                  new TokenFootprint("10", "TokenFootprint.name.10", 6.192, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1700000080800E0EL),
                  new TokenFootprint("11", "TokenFootprint.name.11", 7.430, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1800000080800E0EL),
                  new TokenFootprint("12", "TokenFootprint.name.12", 8.916, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1900000080800E0EL),
                  new TokenFootprint("13", "TokenFootprint.name.13", 10.699, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1A00000080800E0EL),
                  new TokenFootprint("14", "TokenFootprint.name.14", 12.839, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1B00000080800E0EL),
                  new TokenFootprint("15", "TokenFootprint.name.15", 15.407, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1C00000080800E0EL),
                  new TokenFootprint("16", "TokenFootprint.name.16", 18.488, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1D00000080800E0EL),
                  new TokenFootprint("17", "TokenFootprint.name.17", 22.186, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1E00000080800E0EL),
                  new TokenFootprint("18", "TokenFootprint.name.18", 26.623, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x1F00000080800E0EL),
                  new TokenFootprint("19", "TokenFootprint.name.19", 31.948, List.of())),
              Map.entry(
                  new GUID(0xC0A80F0EBEFED0A2L, 0x2000000080800E0EL),
                  new TokenFootprint("20", "TokenFootprint.name.20", 38.338, List.of())))));

  private final OffsetTranslator offsetTranslator;
  private final TokenFootprints footprints;

  GridType(OffsetTranslator offsetTranslator, TokenFootprints footprints) {
    this.offsetTranslator = offsetTranslator;
    this.footprints = footprints;
  }

  public OffsetTranslator getOffsetTranslator() {
    return offsetTranslator;
  }

  public TokenFootprints getFootprints() {
    return footprints;
  }

  private static final Logger log = LogManager.getLogger(GridType.class);

  public boolean isHex() {
    return this == HexVertical || this == HexHorizontal;
  }

  public boolean isIsometric() {
    return this == Isometric;
  }

  public boolean isNone() {
    return this == None;
  }

  public Shape createGridShape(double size) {
    return switch (this) {
      case Square -> {
        var path = new Path2D.Double();
        path.moveTo(size / 2, size / 2);
        path.lineTo(-size / 2, size / 2);
        path.lineTo(-size / 2, -size / 2);
        path.lineTo(size / 2, -size / 2);
        path.closePath();
        yield path;
      }
      case Isometric -> {
        var path = new Path2D.Double();
        path.moveTo(size, 0.);
        path.lineTo(0., size / 2);
        path.lineTo(-size, 0.);
        path.lineTo(0, -size / 2);
        path.closePath();
        yield path;
      }
      case HexHorizontal -> {
        final double l = size / Math.sqrt(3.);
        var path = new Path2D.Double();
        path.moveTo(size / 2, l / 2);
        path.lineTo(0, l);
        path.lineTo(-size / 2, l / 2);
        path.lineTo(-size / 2, -l / 2);
        path.lineTo(0, -l);
        path.lineTo(size / 2, -l / 2);
        path.closePath();
        yield path;
      }
      case HexVertical -> {
        final double l = size / Math.sqrt(3.);
        var path = new Path2D.Double();
        path.moveTo(l, 0);
        path.lineTo(l / 2, size / 2);
        path.lineTo(-l / 2, size / 2);
        path.lineTo(-l, 0);
        path.lineTo(-l / 2, -size / 2);
        path.lineTo(l / 2, -size / 2);
        path.closePath();
        yield path;
      }
      case None -> new Ellipse2D.Double(-size / 2, -size / 2, size, size);
    };
  }

  public String toString() {
    return switch (this) {
      case Square -> "Square";
      case Isometric -> "Isometric";
      case HexVertical -> "Vertical Hex";
      case HexHorizontal -> "Horizontal Hex";
      case None -> "None";
    };
  }

  public static GridType fromString(String string) {
    return switch (string) {
      case "Square" -> Square;
      case "Isometric" -> Isometric;
      case "Vertical Hex" -> HexVertical;
      case "Horizontal Hex" -> HexHorizontal;
      case "None" -> None;
      default ->
          throw new IllegalArgumentException(
              String.format("\"%s\" is not a valid grid type", string));
    };
  }

  public GridTypeDto toDto() {
    return switch (this) {
      case Square -> GridTypeDto.Square;
      case Isometric -> GridTypeDto.Isometric;
      case HexVertical -> GridTypeDto.HexVertical;
      case HexHorizontal -> GridTypeDto.HexHorizontal;
      case None -> GridTypeDto.Gridless;
    };
  }

  public static GridType fromDto(GridTypeDto dto) {
    return switch (dto) {
      case Square -> Square;
      case Gridless -> None;
      case HexVertical -> HexVertical;
      case HexHorizontal -> HexHorizontal;
      case Isometric -> Isometric;
      case UNRECOGNIZED -> {
        log.error("Unrecognized grid. Defaulting to {}", Square);
        yield Square;
      }
    };
  }
}
