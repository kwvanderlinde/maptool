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
package net.rptools.maptool.model.topology;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.server.proto.TopologyDirectionModifier;
import net.rptools.maptool.server.proto.TopologyMovementDirectionModifier;
import net.rptools.maptool.server.proto.WallDataDto;
import net.rptools.maptool.server.proto.WallDto;
import net.rptools.maptool.util.CollectionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a single wall.
 *
 * <p>Even though the underlying graph is undirected, walls still have a concept of an orientation
 * or bearing. This is only important when considering directionality, which is why it is not part
 * of the graph structure.
 */
public final class Wall {
  private static final Logger log = LogManager.getLogger(Wall.class);
  private static final DirectionModifier defaultModifier = DirectionModifier.SameDirection;

  private final GUID from;
  private final GUID to;
  private final Data data;

  public Wall() {
    this(new GUID(), new GUID(), new Data());
  }

  public Wall(Wall other) {
    this(other.from, other.to, other.data);
  }

  /**
   * @param from The ID of the source vertex.
   * @param to The ID of the target vertex.
   */
  public Wall(GUID from, GUID to) {
    this(from, to, new Data());
  }

  /**
   * @param from The ID of the source vertex.
   * @param to The ID of the target vertex.
   * @param data The payload of the wall.
   */
  public Wall(GUID from, GUID to, Data data) {
    this.from = from;
    this.to = to;
    this.data = new Data(data);
  }

  public Wall(
      GUID from,
      GUID to,
      Direction direction,
      MovementDirectionModifier movementModifier,
      Map<VisibilityType, DirectionModifier> modifiers) {
    this(from, to, new Data(direction, movementModifier, modifiers));
  }

  public GUID from() {
    return from;
  }

  public GUID to() {
    return to;
  }

  /**
   * Swap the heading of the wall.
   *
   * <p>The source and target vertices in the result are swapped around. To make the direction be
   * equivalent, it is also reversed.
   *
   * @return An equivalent but reversed wall.
   */
  public Wall reversed() {
    return new Wall(to, from, direction().reversed(), data.movementModifier, data.modifiers);
  }

  public Direction direction() {
    return data.direction;
  }

  public void direction(Direction direction) {
    data.direction = direction;
  }

  public MovementDirectionModifier movementModifier() {
    return data.movementModifier;
  }

  public void movementModifier(MovementDirectionModifier modifier) {
    data.movementModifier = modifier;
  }

  public DirectionModifier directionModifier(VisibilityType visibilityType) {
    return data.modifiers.getOrDefault(visibilityType, defaultModifier);
  }

  public void directionModifier(VisibilityType visibilityType, DirectionModifier modifier) {
    data.modifiers.put(visibilityType, modifier);
  }

  public void setData(Data otherData) {
    data.set(otherData);
  }

  public void copyDataFrom(Wall other) {
    data.set(other.data);
  }

  public void mergeDataFrom(Wall other) {
    var newData = data.merge(other.data);
    data.set(newData);
  }

  /**
   * Gets an copy of the wall's data.
   *
   * <p>This is useful for stashing the data somewhere. If the data needs to be manipulated, use
   * methods on {@link Wall} itself.
   *
   * <p>This method returns a copy of the data, so any changes to the wall will not be reflected in
   * the returned data.
   *
   * @return An opaque handle to the wall's data.
   */
  public Data data() {
    return new Data(data);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Wall wall
        && Objects.equals(this.from, wall.from)
        && Objects.equals(this.to, wall.to);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to);
  }

  public WallDto toDto() {
    return WallDto.newBuilder()
        .setFrom(from.toString())
        .setTo(to.toString())
        .setData(data.toDto())
        .build();
  }

  public static Wall fromDto(WallDto dto) {
    return new Wall(new GUID(dto.getFrom()), new GUID(dto.getTo()), Data.fromDto(dto.getData()));
  }

  public enum Direction {
    Both,
    Left,
    Right;

    public Direction reversed() {
      return switch (this) {
        case Both -> Both;
        case Left -> Right;
        case Right -> Left;
      };
    }

    @Override
    public String toString() {
      var key =
          switch (this) {
            case Both -> "WallDirection.Both";
            case Left -> "WallDirection.Left";
            case Right -> "WallDirection.Right";
          };
      return I18N.getText(key);
    }

    public net.rptools.maptool.server.proto.WallDirection toDto() {
      return switch (this) {
        case Both -> net.rptools.maptool.server.proto.WallDirection.Both;
        case Left -> net.rptools.maptool.server.proto.WallDirection.Left;
        case Right -> net.rptools.maptool.server.proto.WallDirection.Right;
      };
    }

    public static Direction fromDto(net.rptools.maptool.server.proto.WallDirection direction) {
      return switch (direction) {
        case Both -> Both;
        case Left -> Left;
        case Right -> Right;
        case UNRECOGNIZED -> {
          log.error("Unrecognized wall direction. Setting to default");
          yield Both;
        }
      };
    }
  }

  public enum DirectionModifier {
    SameDirection,
    ReverseDirection,
    ForceBoth,
    Disabled;

    @Override
    public String toString() {
      var key =
          switch (this) {
            case SameDirection -> "DirectionModifier.SameDirection";
            case ReverseDirection -> "DirectionModifier.ReverseDirection";
            case ForceBoth -> "DirectionModifier.ForceBoth";
            case Disabled -> "DirectionModifier.Disabled";
          };
      return I18N.getText(key);
    }

    public TopologyDirectionModifier toDto() {
      return switch (this) {
        case SameDirection -> TopologyDirectionModifier.SameDirection;
        case ReverseDirection -> TopologyDirectionModifier.ReverseDirection;
        case ForceBoth -> TopologyDirectionModifier.ForceBoth;
        case Disabled -> TopologyDirectionModifier.Disabled;
      };
    }

    public static DirectionModifier fromDto(TopologyDirectionModifier modifier) {
      return switch (modifier) {
        case SameDirection -> SameDirection;
        case ReverseDirection -> ReverseDirection;
        case ForceBoth -> ForceBoth;
        case Disabled -> Disabled;
        case UNRECOGNIZED -> {
          log.error("Unrecognized wall direction modifier. Setting to default");
          yield SameDirection;
        }
      };
    }
  }

  /**
   * Like {@link DirectionModifier} but only allowing forcing both direction or being disabled.
   *
   * <p>This is since movement blocking does not yet support directional walls. This enum allows
   * movement blocking to be disabled just as for sights, lights, and auras while accepting the more
   * limited functionality.
   *
   * <p>If movement blocking does at some point support the same options, this type should be merged
   * with {@link DirectionModifier}.
   */
  public enum MovementDirectionModifier {
    ForceBoth,
    Disabled;

    private static final Logger log = LogManager.getLogger(MovementDirectionModifier.class);

    @Override
    public String toString() {
      var key =
          switch (this) {
            case ForceBoth -> "DirectionModifier.ForceBoth";
            case Disabled -> "DirectionModifier.Disabled";
          };
      return I18N.getText(key);
    }

    public TopologyMovementDirectionModifier toDto() {
      return switch (this) {
        case ForceBoth -> TopologyMovementDirectionModifier.MovementForceBoth;
        case Disabled -> TopologyMovementDirectionModifier.MovementDisabled;
      };
    }

    public static MovementDirectionModifier fromDto(TopologyMovementDirectionModifier modifier) {
      return switch (modifier) {
        case MovementForceBoth -> ForceBoth;
        case MovementDisabled -> Disabled;
        case UNRECOGNIZED -> {
          log.error("Unrecognized wall direction modifier. Setting to default");
          yield ForceBoth;
        }
      };
    }
  }

  /**
   * An opaque handle to a wall's configuration.
   *
   * <p>If the data needs to be changed, use methods on {@link Wall} to achieve it.
   */
  public static final class Data {
    private Direction direction;
    private MovementDirectionModifier movementModifier;
    private final EnumMap<VisibilityType, DirectionModifier> modifiers;

    private Data() {
      this(Direction.Both, MovementDirectionModifier.ForceBoth, Map.of());
    }

    private Data(Data other) {
      this(other.direction, other.movementModifier, other.modifiers);
    }

    private Data(
        Direction direction,
        MovementDirectionModifier movementModifier,
        Map<VisibilityType, DirectionModifier> modifiers) {
      this.direction = direction;
      this.movementModifier = movementModifier;
      this.modifiers =
          CollectionUtil.newFilledEnumMap(
              VisibilityType.class, type -> DirectionModifier.SameDirection);
      this.modifiers.putAll(modifiers);
    }

    private void set(Data other) {
      if (this == other) {
        return;
      }

      this.direction = other.direction;
      this.movementModifier = other.movementModifier;
      this.modifiers.clear();
      this.modifiers.putAll(other.modifiers);
    }

    /**
     * Force the wall to point the given direction.
     *
     * <p>The various modifiers are updated so that the wall is functionally the same. Normalizing
     * the direction of the wall makes some casework easier when merging walls.
     */
    private void normalizeTo(Direction direction) {
      if (direction == Direction.Both
          || this.direction == Direction.Both
          || direction == this.direction) {
        // Nothing to do.
        return;
      }

      this.direction = this.direction.reversed();
      // Nothing for movement direction right now.
      for (var visibilityType : VisibilityType.values()) {
        var modifier = this.modifiers.getOrDefault(visibilityType, defaultModifier);
        var newModifier =
            switch (modifier) {
              case SameDirection -> DirectionModifier.ReverseDirection;
              case ReverseDirection -> DirectionModifier.SameDirection;
              case ForceBoth -> DirectionModifier.ForceBoth;
              case Disabled -> DirectionModifier.Disabled;
            };
        this.modifiers.put(visibilityType, newModifier);
      }
    }

    private Data merge(Data other) {
      var result = new Data();
      if (this.direction == other.direction) {
        // Both walls agree on the direction.
        result.direction = this.direction;
      } else if (this.direction == Direction.Both || other.direction == Direction.Both) {
        // All directions are blocked by one of the inputs.
        result.direction = Direction.Both;
      } else {
        // Inputs point in different directions. They combine to block both directions.
        result.direction = Direction.Both;
      }

      // Normalized copy so we know that both walls are pointing in the same direction,
      // (assuming neither are set to `Both`; otherwise it doesn't matter).
      var normalizedOther = new Data(other);
      normalizedOther.normalizeTo(this.direction);

      if (this.movementModifier == MovementDirectionModifier.ForceBoth
          || normalizedOther.movementModifier == MovementDirectionModifier.ForceBoth) {
        this.movementModifier = MovementDirectionModifier.ForceBoth;
      } else {
        this.movementModifier = MovementDirectionModifier.Disabled;
      }

      for (var visibilityType : VisibilityType.values()) {
        var thisMod = this.modifiers.getOrDefault(visibilityType, defaultModifier);
        var otherMod = normalizedOther.modifiers.getOrDefault(visibilityType, defaultModifier);

        // Block in every direction that the inputs block.
        if (thisMod == otherMod) {
          result.modifiers.put(visibilityType, thisMod);
        } else if (thisMod == DirectionModifier.ForceBoth
            || otherMod == DirectionModifier.ForceBoth) {
          result.modifiers.put(visibilityType, DirectionModifier.ForceBoth);
        } else if (thisMod == DirectionModifier.Disabled) {
          result.modifiers.put(visibilityType, otherMod);
        } else if (otherMod == DirectionModifier.Disabled) {
          result.modifiers.put(visibilityType, thisMod);
        }
        // One mod is SameDirection and the other is ReverseDirection. If the wall is not
        // directional, we can set to SameDirection. Otherwise, it must ForceBoth.
        else if (result.direction == Direction.Both) {
          result.modifiers.put(visibilityType, DirectionModifier.SameDirection);
        } else {
          result.modifiers.put(visibilityType, DirectionModifier.ForceBoth);
        }
      }

      return result;
    }

    private WallDataDto toDto() {
      return WallDataDto.newBuilder()
          .setDirection(direction.toDto())
          .setMovementDirectionModifier(movementModifier.toDto())
          .setSightDirectionModifier(
              modifiers.getOrDefault(VisibilityType.Sight, defaultModifier).toDto())
          .setLightDirectionModifier(
              modifiers.getOrDefault(VisibilityType.Light, defaultModifier).toDto())
          .setAuraDirectionModifier(
              modifiers.getOrDefault(VisibilityType.Aura, defaultModifier).toDto())
          .build();
    }

    private static Data fromDto(WallDataDto dto) {
      var direction = Direction.fromDto(dto.getDirection());
      var movementModifier = MovementDirectionModifier.fromDto(dto.getMovementDirectionModifier());
      var modifiers =
          Map.of(
              VisibilityType.Sight,
              DirectionModifier.fromDto(dto.getSightDirectionModifier()),
              VisibilityType.Light,
              DirectionModifier.fromDto(dto.getLightDirectionModifier()),
              VisibilityType.Aura,
              DirectionModifier.fromDto(dto.getAuraDirectionModifier()));

      return new Data(direction, movementModifier, modifiers);
    }
  }
}
