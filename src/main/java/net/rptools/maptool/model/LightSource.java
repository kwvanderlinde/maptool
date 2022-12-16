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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.rptools.lib.FileUtil;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.drawing.DrawableLightPaint;
import net.rptools.maptool.model.drawing.DrawablePaint;
import net.rptools.maptool.server.proto.LightSourceDto;
import org.apache.commons.lang.math.NumberUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class LightSource implements Comparable<LightSource>, Serializable {
  public enum Type {
    NORMAL,
    AURA
  }

  /**
   * All lights belonging to the light source. These are sorted according to their lumens values.
   */
  private final List<Light> lightList;
  private String name;
  private GUID id;
  private Type type;
  private boolean scaleWithToken;
  private @Nullable MD5Key textureAssetId;  // TODO Support non-asset "textures", specifically "flat" or "attenuated"/"fade".
  private final transient Map<Color, DrawablePaint> tintedPaints = new HashMap<>();

  // Lumens are now in the individual Lights. This field is only here for backwards compatibility
  // and should not otherwise be used.
  @Deprecated
  private int lumens = Integer.MIN_VALUE;

  /**
   * Constructs a personal light source.
   *
   * <p>Since a personal light source is directly attached to a specific sight type, they do not
   * need (or have) names and GUIDs.
   */
  public LightSource() {
    this(null, null, Type.NORMAL, false, Collections.emptyList());
  }

  /**
   * Constructs a non-personal light source.
   *
   * <p>These light sources are referenced both by name and GUID, and thus need both. A new GUID
   * will be created automatically.
   *
   * @param name The name of the light source.
   */
  public LightSource(String name) {
    this(new GUID(), name, Type.NORMAL, false, Collections.emptyList());
  }

  private LightSource(@Nullable GUID id, @Nullable String name, Type type, boolean scaleWithToken, Collection<Light> lights) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.scaleWithToken = scaleWithToken;

    this.lightList = new LinkedList<>();
    this.lightList.addAll(lights);
    this.lightList.sort(Comparator.comparingDouble(Light::getRadius));
  }

  @Serial
  private Object readResolve() {
    // Old LightSources will have a lumens value that needs to be pushed into Light.
    if (lumens != Integer.MIN_VALUE) {
      if (lightList != null) {
        for (int i = 0; i < lightList.size(); ++i) {
          final var light = lightList.get(i);
          lightList.set(i, new Light(
                  light.getShape(),
                  light.getFacingOffset(),
                  light.getRadius(),
                  light.getArcAngle(),
                  light.getColor(),
                  this.lumens == 0 ? 100 : this.lumens,
                  light.isGM(),
                  light.isOwnerOnly()
          ));
        }
      }
      // Make sure we don't try to convert the lumens again in the future.
      this.lumens = Integer.MIN_VALUE;
    }

    // Rather than touching up the current object, we'll create a replacement that is definitely
    // initialized.
    return new LightSource(
            this.id,
            this.name,
            Objects.requireNonNullElse(this.type, Type.NORMAL),
            this.scaleWithToken,
            Objects.requireNonNullElse(this.lightList, Collections.emptyList())
    );
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LightSource)) {
      return false;
    }
    return ((LightSource) obj).id.equals(id);
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
    return id.hashCode();
  }

  public void setId(GUID id) {
    this.id = id;
  }

  public GUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void add(Light source) {
    lightList.add(source);
    lightList.sort(Comparator.comparingDouble(Light::getRadius));
  }

  public void remove(Light source) {
    lightList.remove(source);
  }

  /**
   * @return the lights belonging to this LightSource. The list is sorted from small lights to large
   *     lights
   */
  public List<Light> getLightList() {
    return Collections.unmodifiableList(lightList);
  }

  public Type getType() {
    return type != null ? type : Type.NORMAL;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public @Nullable MD5Key getTextureAssetId() {
    return textureAssetId;
  }

  public void setTextureAssetId(@Nullable MD5Key assetId) {
    if (!Objects.equals(textureAssetId, assetId)) {
      // TODO Make sure it gets loading right away.
      textureAssetId = assetId;
      tintedPaints.clear();
    }
  }

  public DrawablePaint getPaint(Light light) {
    if (light.getColor() == null) {
      return null;
    }

    return tintedPaints.computeIfAbsent(
        light.getColor(), tint -> new DrawableLightPaint(textureAssetId, tint));
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
  // TODO This is only used in ZoneView.getDrawableAuras(). We should update that to function
  //  similarly to the illumination case, where it does a single pass to construct disjoint sets
  //  from overlapping ranges.
  public Area getArea(Token token, Zone zone, Light light) {
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
  public Area getArea(Token token, Zone zone) {
    Area area = new Area();
    for (Light light : lightList) {
      area.add(light.getArea(token, zone, isScaleWithToken()));
    }

    return area;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, List<LightSource>> getDefaultLightSources() throws IOException {
    Object defaultLights =
        FileUtil.objFromResource("net/rptools/maptool/model/defaultLightSourcesMap.xml");
    return (Map<String, List<LightSource>>) defaultLights;
  }

  @Override
  public String toString() {
    return name;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(@NotNull LightSource o) {
    if (o != this) {
      int nameLong = NumberUtils.toInt(name, Integer.MIN_VALUE);
      int onameLong = NumberUtils.toInt(o.name, Integer.MIN_VALUE);
      if (nameLong != Integer.MIN_VALUE && onameLong != Integer.MIN_VALUE)
        return nameLong - onameLong;
      return name.compareTo(o.name);
    }
    return 0;
  }

  public static LightSource fromDto(LightSourceDto dto) {
    return new LightSource(
            dto.hasId() ? GUID.valueOf(dto.getId().getValue()) : null,
            dto.hasName() ? dto.getName().getValue() : null,
            Type.valueOf(dto.getType().name()),
            dto.getScaleWithToken(),
            dto.getLightsList().stream().map(Light::fromDto).toList()
    );
  }

  public LightSourceDto toDto() {
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
    return dto.build();
  }
}
