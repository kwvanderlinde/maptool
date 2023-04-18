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
package net.rptools.maptool.model;

import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.awt.Color;
import java.awt.geom.Area;
import java.io.Serial;
import java.io.Serializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.server.proto.LightDto;
import net.rptools.maptool.server.proto.ShapeTypeDto;

public class Light implements Serializable {
  private final @Nonnull ShapeType shape;
  private final double facingOffset;
  private final double radius;
  private final double arcAngle;
  // The name is "paint" for backwards compatibility, would rather call it "color".
  @XStreamConverter(DrawableColorPaintUnwrapper.class)
  private final @Nullable Color paint;

  private final int lumens;
  private final boolean isGM;
  private final boolean ownerOnly;

  public Light(
      @Nonnull ShapeType shape,
      double facingOffset,
      double radius,
      double arcAngle,
      @Nullable Color color,
      int lumens,
      boolean isGM,
      boolean owner) {
    this.shape = shape;
    this.facingOffset = facingOffset;
    this.radius = radius;
    this.arcAngle = (arcAngle == 0) ? 90 : arcAngle;
    this.paint = color;
    this.lumens = lumens;
    this.isGM = isGM;
    this.ownerOnly = owner;
  }

  @SuppressWarnings("ConstantConditions")
  @Serial
  private @Nonnull Object readResolve() {
    // Rather than modifying the current object, we'll create a replacement that is definitely
    // initialized properly.
    return new Light(
        shape == null ? ShapeType.CIRCLE : shape,
        facingOffset,
        radius,
        arcAngle,
        paint,
        lumens == 0 ? 100 : lumens,
        isGM,
        ownerOnly);
  }

  public @Nullable Color getColor() {
    return paint;
  }

  public int getLumens() {
    return lumens;
  }

  public double getFacingOffset() {
    return facingOffset;
  }

  public double getRadius() {
    return radius;
  }

  public double getArcAngle() {
    return arcAngle;
  }

  public @Nonnull ShapeType getShape() {
    return shape;
  }

  public @Nonnull Area getArea(@Nonnull Token token, @Nonnull Zone zone, boolean scaleWithToken) {
    return zone.getGrid()
        .getShapedArea(
            getShape(), token, getRadius(), getArcAngle(), (int) getFacingOffset(), scaleWithToken);
  }

  public boolean isGM() {
    return isGM;
  }

  public boolean isOwnerOnly() {
    return ownerOnly;
  }

  public static @Nonnull Light fromDto(@Nonnull LightDto dto) {
    return new Light(
        ShapeType.valueOf(dto.getShape().name()),
        dto.getFacingOffset(),
        dto.getRadius(),
        dto.getArcAngle(),
        dto.hasColor() ? new Color(dto.getColor(), true) : null,
        dto.getLumens(),
        dto.getIsGm(),
        dto.getOwnerOnly());
  }

  public @Nonnull LightDto toDto() {
    var dto = LightDto.newBuilder();
    if (paint != null) {
      dto.setColor(paint.getRGB());
    }
    dto.setFacingOffset(facingOffset);
    dto.setRadius(radius);
    dto.setArcAngle(arcAngle);
    dto.setShape(ShapeTypeDto.valueOf(shape.name()));
    dto.setIsGm(isGM);
    dto.setOwnerOnly(ownerOnly);
    dto.setLumens(lumens);
    return dto.build();
  }

  public static final class DrawableColorPaintUnwrapper implements Converter {
    private final Class<?> fieldType;

    public DrawableColorPaintUnwrapper(Class<?> fieldType) {
      this.fieldType = fieldType;
    }

    @Override
    public boolean canConvert(Class type) {
      // When reading, the serialized field type could be Color (new) or DrawableColorPaint (old).
      // Either way, this converter expects to fill in a Color field.
      return Color.class.equals(fieldType)
          && (Color.class.equals(type) || DrawableColorPaint.class.equals(type));
    }

    @Override
    public void marshal(
        Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
      // Just write the color back out.
      context.convertAnother(source);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
      // Lights have only ever supported DrawableColorPaint, and now also Color. So unwrap the
      // DrawableColorPaint into a Color, or use the existing Color, otherwise set to null.
      final @Nullable var deserialized = context.convertAnother(null, context.getRequiredType());
      final @Nullable Color result;
      if (deserialized instanceof Color color) {
        result = color;
      } else if (deserialized instanceof DrawableColorPaint paint) {
        result = new Color(paint.getColor(), true);
      } else {
        result = null;
      }
      return result;
    }
  }
}
