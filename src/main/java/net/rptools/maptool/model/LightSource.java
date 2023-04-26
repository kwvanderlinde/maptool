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

import com.google.protobuf.StringValue;
import java.awt.Color;
import java.awt.geom.Area;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.lib.FileUtil;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.drawing.DrawableColorPaint;
import net.rptools.maptool.model.drawing.DrawablePaint;
import net.rptools.maptool.model.drawing.DrawableRadialPaint;
import net.rptools.maptool.model.drawing.DrawableTexturePaint;
import net.rptools.maptool.model.drawing.DrawableTintedPaint;
import net.rptools.maptool.server.proto.LightSourceDto;
import org.apache.commons.lang.math.NumberUtils;

public class LightSource implements Comparable<LightSource>, Serializable {
  public enum Type {
    NORMAL,
    AURA
  }

  public sealed interface Texture {
    @Nonnull
    String asString();

    /**
     * @return null to indicate a flat color that does not require expensive tinting.
     */
    @Nullable
    DrawablePaint getUntintedPaint();
  }

  // I wish the following could be records, but XStream doesn't like deserializing them.

  public static final class FlatTexture implements Texture {
    @Nonnull
    @Override
    public String asString() {
      return "flat";
    }

    @Nullable
    @Override
    public DrawablePaint getUntintedPaint() {
      return null;
    }
  }
  // Expectation is that we could add parameters to control the fade curve.
  public static final class FadeTexture implements Texture {
    @Nonnull
    @Override
    public String asString() {
      return "fade";
    }

    @Nullable
    @Override
    public DrawablePaint getUntintedPaint() {
      // TODO This works for circle, but there may be better options for other shapes.

      return new DrawableRadialPaint(
          // This is a two-piece linear interpolation of a circular arc.
          new float[] {0.f, 0.968245837f, 1.f},
          new Color[] {Color.white, new Color(0xFF_40_40_40, true), Color.black});
    }
  }

  public static final class AssetTexture implements Texture {
    private final MD5Key assetKey;

    public AssetTexture(MD5Key assetKey) {
      this.assetKey = assetKey;
    }

    public MD5Key assetKey() {
      return assetKey;
    }

    @Nonnull
    @Override
    public String asString() {
      return "asset://" + assetKey.toString();
    }

    @Nullable
    @Override
    public DrawablePaint getUntintedPaint() {
      return new DrawableTexturePaint(assetKey);
    }
  }

  private @Nullable String name;
  private @Nullable GUID id;
  private @Nonnull Type type;
  private boolean scaleWithToken;
  private Texture texture;
  private final @Nonnull List<Light> lightList;

  // Lumens are now in the individual Lights. This field is only here for backwards compatibility
  // and should not otherwise be used.
  @Deprecated private int lumens = Integer.MIN_VALUE;

  private transient Map<Color, DrawablePaint> paintsByColor = new HashMap<>();

  /**
   * Constructs a personal light source.
   *
   * <p>Since a personal light source is directly attached to a specific sight type, they do not
   * need (or have) names and GUIDs.
   */
  public LightSource() {
    this(null, null, Type.NORMAL, false, new FlatTexture(), Collections.emptyList());
  }

  /**
   * Constructs a non-personal light source.
   *
   * <p>These light sources are referenced both by name and GUID, and thus need both. A new GUID
   * will be created automatically.
   *
   * @param name The name of the light source.
   */
  public LightSource(@Nonnull String name) {
    this(name, new GUID(), Type.NORMAL, false, new FlatTexture(), Collections.emptyList());
  }

  private LightSource(
      @Nullable String name,
      @Nullable GUID id,
      @Nonnull Type type,
      boolean scaleWithToken,
      @Nonnull Texture texture,
      @Nonnull Collection<Light> lights) {
    this.name = name;
    this.id = id;
    this.type = type;
    this.scaleWithToken = scaleWithToken;
    this.texture = texture;

    this.lightList = new LinkedList<>();
    this.lightList.addAll(lights);
  }

  @SuppressWarnings("ConstantConditions")
  @Serial
  private @Nonnull Object readResolve() {
    final List<Light> originalLights =
        Objects.requireNonNullElse(lightList, Collections.emptyList());
    final List<Light> lights;
    if (lumens == Integer.MIN_VALUE) {
      // This is an up-to-date Lightsource with lumens already stored in the Lights.
      lights = originalLights;
    } else {
      // This is an old light source with a lumens value that needs to be pushed into the individual
      // Lights.
      lights = new ArrayList<>();
      for (final var light : originalLights) {
        lights.add(
            new Light(
                light.getShape(),
                light.getFacingOffset(),
                light.getRadius(),
                light.getArcAngle(),
                light.getColor(),
                lumens == 0 ? 100 : lumens,
                light.isGM(),
                light.isOwnerOnly()));
      }
    }

    // Rather than modifying the current object, we'll create a replacement that is definitely
    // initialized properly.
    return new LightSource(
        this.name,
        this.id,
        Objects.requireNonNullElse(this.type, Type.NORMAL),
        this.scaleWithToken,
        Objects.requireNonNullElse(this.texture, new FlatTexture()),
        lights);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LightSource)) {
      return false;
    }
    return Objects.equals(((LightSource) obj).id, id);
  }

  public @Nullable DrawablePaint getPaint(Light light) {
    assert lightList.contains(light);

    return paintsByColor.computeIfAbsent(
        light.getColor(),
        color -> {
          final var untintedPaint = texture.getUntintedPaint();
          final @Nullable DrawablePaint paint;
          if (untintedPaint == null) {
            // Solid color
            paint = color == null ? null : new DrawableColorPaint(color);
          } else if (color == null) {
            paint = untintedPaint;
          } else {
            paint = new DrawableTintedPaint(untintedPaint, color);
          }
          return paint;
        });
  }

  public double getMaxRange() {
    double range = 0;
    for (Light light : lightList) {
      range = Math.max(range, light.getRadius());
    }
    return range;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  public void setId(@Nonnull GUID id) {
    this.id = id;
  }

  public @Nullable GUID getId() {
    return id;
  }

  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nonnull String name) {
    this.name = name;
  }

  public void add(@Nonnull Light source) {
    lightList.add(source);
  }

  public void remove(@Nonnull Light source) {
    lightList.remove(source);
  }

  /**
   * @return the lights belonging to this LightSource.
   */
  public @Nonnull List<Light> getLightList() {
    return Collections.unmodifiableList(lightList);
  }

  public @Nonnull Type getType() {
    return type;
  }

  public void setType(@Nonnull Type type) {
    this.type = type;
  }

  public Texture getTexture() {
    return this.texture;
  }

  public void setTexture(Texture texture) {
    this.texture = texture;
    paintsByColor.clear();
  }

  public void setScaleWithToken(boolean scaleWithToken) {
    this.scaleWithToken = scaleWithToken;
  }

  public boolean isScaleWithToken() {
    return scaleWithToken;
  }

  /*
   * Area for a single light, subtracting any previous lights
   */
  public @Nonnull Area getArea(@Nonnull Token token, @Nonnull Zone zone, @Nonnull Light light) {
    Area area = light.getArea(token, zone, scaleWithToken);
    // TODO: This seems horribly inefficient
    // Subtract out the lights that are previously defined
    for (int i = lightList.indexOf(light) - 1; i >= 0; i--) {
      Light lessLight = lightList.get(i);
      area.subtract(lessLight.getArea(token, zone, scaleWithToken));
    }
    return area;
  }

  /* Area for all lights combined */
  public @Nonnull Area getArea(@Nonnull Token token, @Nonnull Zone zone) {
    Area area = new Area();
    for (Light light : lightList) {
      area.add(light.getArea(token, zone, isScaleWithToken()));
    }

    return area;
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull Map<String, List<LightSource>> getDefaultLightSources()
      throws IOException {
    Object defaultLights =
        FileUtil.objFromResource("net/rptools/maptool/model/defaultLightSourcesMap.xml");
    return (Map<String, List<LightSource>>) defaultLights;
  }

  @Override
  public String toString() {
    return name;
  }

  /*
   * Compares this light source with another.
   *
   * Light sources are compared by name. If both names are numeric strings, they will be compared as
   * integers. Otherwise they will be compared lexicographically.
   *
   * This must only be called on light source that have a name, i.e., not on personal lights.
   *
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(@Nonnull LightSource o) {
    if (o != this) {
      int nameLong = NumberUtils.toInt(name, Integer.MIN_VALUE);
      int onameLong = NumberUtils.toInt(o.name, Integer.MIN_VALUE);
      if (nameLong != Integer.MIN_VALUE && onameLong != Integer.MIN_VALUE)
        return nameLong - onameLong;
      return name.compareTo(o.name);
    }
    return 0;
  }

  public static @Nonnull LightSource fromDto(@Nonnull LightSourceDto dto) {
    final var texture =
        switch (dto.getTextureCase()) {
          case FLAT_TEXTURE, TEXTURE_NOT_SET -> new FlatTexture();
          case FADE_TEXTURE -> new FadeTexture();
          case ASSET_TEXTURE -> new AssetTexture(new MD5Key(dto.getAssetTexture().getMd5Key()));
        };

    return new LightSource(
        dto.hasName() ? dto.getName().getValue() : null,
        dto.hasId() ? GUID.valueOf(dto.getId().getValue()) : null,
        Type.valueOf(dto.getType().name()),
        dto.getScaleWithToken(),
        texture,
        dto.getLightsList().stream().map(Light::fromDto).toList());
  }

  public @Nonnull LightSourceDto toDto() {
    var dto = LightSourceDto.newBuilder();
    dto.addAllLights(lightList.stream().map(Light::toDto).collect(Collectors.toList()));
    if (name != null) {
      dto.setName(StringValue.of(name));
    }
    if (id != null) {
      dto.setId(StringValue.of(id.toString()));
    }
    dto.setType(LightSourceDto.LightTypeDto.valueOf(type.name()));
    dto.setScaleWithToken(scaleWithToken);

    if (texture instanceof FlatTexture) {
      dto.setFlatTexture(LightSourceDto.FlatTexture.newBuilder().build());
    } else if (texture instanceof FadeTexture) {
      dto.setFadeTexture(LightSourceDto.FadeTexture.newBuilder().build());
    } else if (texture instanceof AssetTexture assetTexture) {
      dto.setAssetTexture(
          LightSourceDto.AssetTexture.newBuilder()
              .setMd5Key(assetTexture.assetKey().toString())
              .build());
    }

    return dto.build();
  }
}
