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

import java.awt.geom.Point2D;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.rptools.lib.GeometryUtil;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.server.proto.VertexDto;
import net.rptools.maptool.server.proto.WallDataDto;
import net.rptools.maptool.server.proto.WallDto;
import net.rptools.maptool.server.proto.WallTopologyDto;
import net.rptools.maptool.server.proto.drawing.DoublePointDto;
import net.rptools.maptool.util.CollectionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineSegment;

/**
 * Topology based on thin connected walls.
 *
 * <p>Wall topology forms a graph where the walls themselves are the edges of the graph.
 */
public final class WallTopology implements Topology {
  private static final Logger log = LogManager.getLogger(WallTopology.class);

  public static final class GraphException extends Exception {
    public GraphException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public enum WallDirection {
    Both,
    Left,
    Right;

    public WallDirection reversed() {
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

    public static WallDirection fromDto(net.rptools.maptool.server.proto.WallDirection direction) {
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

  /**
   * Represents the end of one or more walls.
   *
   * <p>Multiple {@code Vertex} instances may be created for a single vertex. All such instances can
   * be used interchangeably and with compare equal.
   */
  public static final class Vertex {
    private final GUID id;
    private final VertexInternal internal;

    public Vertex() {
      this(new GUID());
    }

    private Vertex(GUID id) {
      this(id, new VertexInternal(id, new Point2D.Double(0, 0)));
    }

    private Vertex(GUID id, VertexInternal internal) {
      this.id = id;
      this.internal = internal;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Vertex vertex && Objects.equals(id, vertex.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }

    public GUID id() {
      return id;
    }

    public int getZIndex() {
      return internal.zIndex();
    }

    public Point2D getPosition() {
      var position = new Point2D.Double();
      position.setLocation(internal.position());
      return position;
    }

    public void setPosition(double x, double y) {
      internal.position().setLocation(x, y);
    }

    public void setPosition(Point2D position) {
      setPosition(position.getX(), position.getY());
    }
  }

  public static final class WallData {
    private WallDirection direction;
    private MovementDirectionModifier movementModifier;
    private final EnumMap<DirectionModifierType, DirectionModifier> modifiers;

    public WallData() {
      this(WallDirection.Both, MovementDirectionModifier.ForceBoth, Map.of());
    }

    public WallData(WallData other) {
      this(other.direction, other.movementModifier, other.modifiers);
    }

    public WallData(
        WallDirection direction,
        MovementDirectionModifier movementModifier,
        Map<DirectionModifierType, DirectionModifier> modifiers) {
      this.direction = direction;
      this.movementModifier = movementModifier;
      this.modifiers =
          CollectionUtil.newFilledEnumMap(
              DirectionModifierType.class, type -> DirectionModifier.SameDirection);
      this.modifiers.putAll(modifiers);
    }

    public WallDirection direction() {
      return direction;
    }

    public void direction(WallDirection direction) {
      this.direction = direction;
    }

    public MovementDirectionModifier movementModifier() {
      return movementModifier;
    }

    public void movementModifier(MovementDirectionModifier modifier) {
      this.movementModifier = modifier;
    }

    public DirectionModifier directionModifier(DirectionModifierType type) {
      return modifiers.getOrDefault(type, DirectionModifier.SameDirection);
    }

    public void directionModifier(DirectionModifierType type, DirectionModifier modifier) {
      modifiers.put(type, modifier);
    }

    public void set(WallData other) {
      if (this == other) {
        return;
      }

      this.direction = other.direction;
      this.movementModifier = other.movementModifier;
      this.modifiers.clear();
      this.modifiers.putAll(other.modifiers);
    }

    /**
     * A convenience method that makes sure the wall is pointing Left or Both.
     *
     * <p>This helps with merges by cutting down the possible casework.
     */
    private void normalizeTo(WallDirection direction) {
      if (direction == WallDirection.Both
          || this.direction == WallDirection.Both
          || direction == this.direction) {
        // Nothing to do.
        return;
      }

      this.direction = this.direction.reversed();
      // Nothing for movement direction right now.
      for (var type : DirectionModifierType.values()) {
        var modifier = this.directionModifier(type);
        var newModifier =
            switch (modifier) {
              case SameDirection -> DirectionModifier.ReverseDirection;
              case ReverseDirection -> DirectionModifier.SameDirection;
              case ForceBoth -> DirectionModifier.ForceBoth;
              case Disabled -> DirectionModifier.Disabled;
            };
        this.directionModifier(type, newModifier);
      }
    }

    public WallData merge(WallData other, boolean reversed) {
      var otherDirection = reversed ? other.direction.reversed() : other.direction;

      var result = new WallData();
      if (this.direction == otherDirection) {
        // Both walls agree on the direction.
        result.direction(this.direction);
      } else if (this.direction == WallDirection.Both || otherDirection == WallDirection.Both) {
        // All directions are blocked by one of the inputs.
        result.direction(WallDirection.Both);
      } else {
        // Inputs point in different directions. They combine to block both directions.
        result.direction(WallDirection.Both);
      }

      // Normalized copy so we know that both walls are pointing in the same direction,
      // (assuming neither are set to `Both`; otherwise it doesn't matter).
      var normalizedOther = new WallData(other);
      normalizedOther.direction(otherDirection);
      normalizedOther.normalizeTo(this.direction);

      if (this.movementModifier == MovementDirectionModifier.ForceBoth
          || normalizedOther.movementModifier == MovementDirectionModifier.ForceBoth) {
        this.movementModifier = MovementDirectionModifier.ForceBoth;
      } else {
        this.movementModifier = MovementDirectionModifier.Disabled;
      }

      for (var type : DirectionModifierType.values()) {
        var thisMod = this.directionModifier(type);
        var otherMod = normalizedOther.directionModifier(type);

        // Block in every direction that the inputs block.
        if (thisMod == otherMod) {
          result.directionModifier(type, thisMod);
        } else if (thisMod == DirectionModifier.ForceBoth
            || otherMod == DirectionModifier.ForceBoth) {
          result.directionModifier(type, DirectionModifier.ForceBoth);
        } else if (thisMod == DirectionModifier.Disabled) {
          result.directionModifier(type, otherMod);
        } else if (otherMod == DirectionModifier.Disabled) {
          result.directionModifier(type, thisMod);
        }
        // One mod is SameDirection and the other is ReverseDirection. If the wall is not
        // directional, we can set to SameDirection. Otherwise, it must ForceBoth.
        else if (result.direction() == WallDirection.Both) {
          result.directionModifier(type, DirectionModifier.SameDirection);
        } else {
          result.directionModifier(type, DirectionModifier.ForceBoth);
        }
      }

      return result;
    }

    public WallDataDto toDto() {
      return WallDataDto.newBuilder()
          .setDirection(direction.toDto())
          .setMovementDirectionModifier(movementModifier.toDto())
          .setSightDirectionModifier(directionModifier(DirectionModifierType.Sight).toDto())
          .setLightDirectionModifier(directionModifier(DirectionModifierType.Light).toDto())
          .setAuraDirectionModifier(directionModifier(DirectionModifierType.Aura).toDto())
          .build();
    }

    public static WallData fromDto(WallDataDto dto) {
      var direction = WallDirection.fromDto(dto.getDirection());
      var movementModifier = MovementDirectionModifier.fromDto(dto.getMovementDirectionModifier());
      var modifiers =
          Map.of(
              DirectionModifierType.Sight,
                  DirectionModifier.fromDto(dto.getSightDirectionModifier()),
              DirectionModifierType.Light,
                  DirectionModifier.fromDto(dto.getLightDirectionModifier()),
              DirectionModifierType.Aura,
                  DirectionModifier.fromDto(dto.getAuraDirectionModifier()));

      return new WallData(direction, movementModifier, modifiers);
    }
  }

  /**
   * Represents a single wall.
   *
   * <p>Multiple {@code Wall} instances may be created for a single wall. All such instances can be
   * used interchangeably and will compare equal.
   */
  public static final class Wall {
    private final Vertex from;
    private final Vertex to;
    private final WallInternal internal;

    public Wall() {
      this(new Vertex(), new Vertex());
    }

    private Wall(Vertex from, Vertex to) {
      this(from, to, new WallInternal(from.id(), to.id(), new WallData()));
    }

    private Wall(Vertex from, Vertex to, WallInternal internal) {
      this.from = from;
      this.to = to;
      this.internal = internal;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Wall wall
          && Objects.equals(from, wall.from)
          && Objects.equals(to, wall.to);
    }

    @Override
    public int hashCode() {
      return Objects.hash(from, to);
    }

    public Vertex from() {
      return from;
    }

    public Vertex to() {
      return to;
    }

    public WallData data() {
      return internal.data();
    }

    public int getZIndex() {
      return Math.max(from.getZIndex(), to.getZIndex());
    }

    /**
     * Represent this wall as a {@link LineSegment}.
     *
     * <p>This is a convenience method for getting the coordinates from {@link #from()} and {@link
     * #to()}. Since {@link LineSegment} cannot carry user data, the result does not include any
     * attached wall data.
     *
     * @return The {@link LineSegment} representation of the wall.
     */
    public LineSegment asSegment() {
      return new LineSegment(
          GeometryUtil.point2DToCoordinate(from.getPosition()),
          GeometryUtil.point2DToCoordinate(to.getPosition()));
    }

    public WallDto toDto() {
      return internal.toDto();
    }
  }

  /**
   * Internal payload for a vertex.
   *
   * <p>Users of {@link WallTopology} will interact with the {@link Vertex} wrapper rather than with
   * {@code VertexInternal} directly.
   *
   * <p>The position is intentionally mutable as vertices are free to move about the plane without
   * affecting the graph structure.
   */
  private static final class VertexInternal {
    private final GUID id;
    private final Point2D position;
    private int zIndex;

    public VertexInternal(GUID id, Point2D position) {
      this.id = id;
      this.position = position;
      this.zIndex = -1;
    }

    public GUID id() {
      return id;
    }

    public Point2D position() {
      return position;
    }

    public int zIndex() {
      return zIndex;
    }

    public void zIndex(int zIndex) {
      this.zIndex = zIndex;
    }
  }

  /**
   * Internal representation of a wall.
   *
   * <p>Even though the underlying graph is undirected, walls still have a concept of a direction
   * This is only important when considering orientation, which is why it is not part of the graph
   * structure.
   *
   * <p>Uses of {@link WallTopology} will consume {@link Wall} instead of {@code WallInternal}. That
   * representation has all IDs already resolved to {@link Vertex}.
   */
  private static final class WallInternal {
    private final GUID from;
    private final GUID to;
    private final WallData data;

    /**
     * @param from The ID of the source vertex.
     * @param to The ID of the target vertex.
     * @param data The payload of the wall.
     */
    public WallInternal(GUID from, GUID to, WallData data) {
      this.from = from;
      this.to = to;
      this.data = new WallData(data);
    }

    public GUID from() {
      return from;
    }

    public GUID to() {
      return to;
    }

    public WallData data() {
      return data;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    public WallDto toDto() {
      return WallDto.newBuilder()
          .setFrom(from.toString())
          .setTo(to.toString())
          .setData(data.toDto())
          .build();
    }

    public static WallInternal fromDto(WallDto dto) {
      return new WallInternal(
          new GUID(dto.getFrom()), new GUID(dto.getTo()), WallData.fromDto(dto.getData()));
    }
  }

  /**
   * Return type for {@link #string(Point2D, Consumer)} that ensures walls are built in a valid
   * fashion.
   */
  public final class StringBuilder {
    private record Piece(Point2D position, WallData data) {}

    // These vertices and walls are not added to the graph until build() is called.
    private final Point2D firstVertexPosition;
    private final ArrayList<Piece> pieces = new ArrayList<>();

    private StringBuilder(Point2D startingPoint) {
      this.firstVertexPosition = new Point2D.Double(startingPoint.getX(), startingPoint.getY());
    }

    public void push(Point2D nextPoint) {
      push(nextPoint, new WallData());
    }

    public void push(Point2D nextPoint, WallData data) {
      this.pieces.add(
          new Piece(new Point2D.Double(nextPoint.getX(), nextPoint.getY()), new WallData(data)));
    }

    /** Finish the string and return the last vertex. */
    public void build() {
      if (pieces.isEmpty()) {
        // Insufficient points to add to the graph, otherwise vertices would dangle.
        return;
      }

      var previousVertex = createVertex();
      previousVertex.setPosition(firstVertexPosition);

      for (var currentPiece : pieces) {
        var currentVertex = createVertex();
        currentVertex.setPosition(currentPiece.position());

        try {
          createWallImpl(previousVertex.id(), currentVertex.id(), currentPiece.data());
        } catch (GraphException e) {
          throw new RuntimeException(
              "Unexpected scenario: wall already exists despite vertex being new", e);
        }

        previousVertex = currentVertex;
      }
    }
  }

  // Vertices are identified by GUID. Attached data is maintained in vertexInternalById
  private final Graph<GUID, WallInternal> graph;
  private final Map<GUID, VertexInternal> vertexInternalById = new LinkedHashMap<>();
  private transient int nextZIndex = 0;

  public WallTopology() {
    this.graph =
        GraphTypeBuilder.<GUID, WallInternal>undirected()
            .allowingMultipleEdges(false)
            .allowingSelfLoops(false)
            .weighted(false)
            .buildGraph();
  }

  public WallTopology(WallTopology other) {
    this();

    other
        .getVertices()
        .sorted(Comparator.comparingInt(Vertex::getZIndex))
        .forEach(
            vertex -> {
              VertexInternal newVertexInternal;
              try {
                newVertexInternal = createVertexImpl(vertex.id());
              } catch (GraphException e) {
                log.error(
                    "Unexpected scenario: graph has duplicate vertex ID {}. Skipping.",
                    vertex.id(),
                    e);
                return;
              }
              newVertexInternal.position.setLocation(vertex.getPosition());
            });

    other
        .getWalls()
        .sorted(Comparator.comparingInt(Wall::getZIndex))
        .forEach(
            wall -> {
              try {
                createWallImpl(wall.from().id(), wall.to().id(), wall.data());
              } catch (GraphException e) {
                log.error(
                    "Unexpected scenario: topology has duplicate wall ({}, {}). Skipping.",
                    wall.from().id(),
                    wall.to().id(),
                    e);
              }
            });
  }

  private VertexInternal createVertexImpl(GUID id) throws GraphException {
    var added = graph.addVertex(id);
    if (!added) {
      throw new GraphException("Vertex with that GUID is already in the graph", null);
    }

    // Because we store the vertices in a LinkedHashMap, they will always be iterated in insertion
    // order. We also include a z-index so that other components can put vertices in other data
    // structures and sort them again by z-order.
    var data = new VertexInternal(id, new Point2D.Double());
    data.zIndex(++nextZIndex);
    var previous = vertexInternalById.put(id, data);
    assert previous == null : "Invariant not held";

    return data;
  }

  private WallInternal createWallImpl(GUID fromId, GUID toId, WallData data) throws GraphException {
    var wall = new WallInternal(fromId, toId, data);

    boolean added;
    try {
      added = graph.addEdge(fromId, toId, wall);
    } catch (IllegalArgumentException e) {
      throw new GraphException("One of the vertices does not exist in the graph", e);
    }

    if (!added) {
      throw new GraphException("Wall with those vertices is already in the graph", null);
    }
    return wall;
  }

  // region These exist to support serialization. They are little loose for general use.

  /**
   * Adds a new vertex to the graph.
   *
   * @param id The ID of the vertex
   * @throws GraphException If a vertex with ID {@code id} already exists.
   */
  public Vertex createVertex(GUID id) throws GraphException {
    var vertexInternal = createVertexImpl(id);
    return new Vertex(id, vertexInternal);
  }

  public void createWall(
      GUID fromId,
      GUID toId,
      WallDirection direction,
      MovementDirectionModifier movementModifier,
      Map<DirectionModifierType, DirectionModifier> directionModifiers)
      throws GraphException {
    createWallImpl(fromId, toId, new WallData(direction, movementModifier, directionModifiers));
  }

  // endregion

  // region Other builder methods

  private Vertex createVertex() {
    var id = new GUID();
    VertexInternal vertexInternal;
    try {
      vertexInternal = createVertexImpl(id);
    } catch (GraphException e) {
      throw new RuntimeException("Impossible case: vertex already exists with new GUID", e);
    }
    return new Vertex(id, vertexInternal);
  }

  public void string(Point2D startingPoint, Consumer<StringBuilder> action) {
    var builder = new StringBuilder(startingPoint);
    action.accept(builder);
    builder.build();
  }

  // endregion

  // region Main interface

  public Stream<Vertex> getVertices() {
    return this.graph.vertexSet().stream()
        .map(
            id -> {
              var vertexInternal = vertexInternalById.get(id);
              return new Vertex(id, vertexInternal);
            });
  }

  public Stream<Wall> getWalls() {
    return this.graph.edgeSet().stream()
        .map(
            wall -> {
              var fromId = wall.from();
              var toId = wall.to();
              var fromInternal = vertexInternalById.get(fromId);
              var toInternal = vertexInternalById.get(toId);
              return new Wall(new Vertex(fromId, fromInternal), new Vertex(toId, toInternal), wall);
            });
  }

  public Optional<Wall> getWall(GUID fromId, GUID toId) {
    var internal = this.graph.getEdge(fromId, toId);
    if (internal == null) {
      return Optional.empty();
    }

    return Optional.of(
        new Wall(
            new Vertex(fromId, vertexInternalById.get(fromId)),
            new Vertex(toId, vertexInternalById.get(toId)),
            internal));
  }

  public void removeVertex(Vertex vertex) {
    var removed = graph.removeVertex(vertex.id());
    if (!removed) {
      throw new RuntimeException("Foreign vertex");
    }

    vertexInternalById.remove(vertex.id());
    removeDanglingVertices();
  }

  public void removeWall(Wall wall) {
    var removedWall = graph.removeEdge(wall.from().id(), wall.to().id());
    if (removedWall != null) {
      // TODO More efficient would be to just check the specific vertices of the edge.
      removeDanglingVertices();
    }
  }

  public Wall brandNewWall() {
    var from = createVertex();
    var to = createVertex();

    WallInternal newWall;
    try {
      newWall = createWallImpl(from.id(), to.id(), new WallData());
    } catch (GraphException e) {
      throw new RuntimeException(
          "Unexpected scenario: wall already exists despite both vertices being new", e);
    }

    return new Wall(from, to, newWall);
  }

  public Wall newWallStartingAt(Vertex from) {
    var isForeign = !this.graph.containsVertex(from.id());
    if (isForeign) {
      throw new RuntimeException("Vertex is not part of this topology");
    }

    var newVertex = createVertex();

    WallInternal newWall;
    try {
      newWall = createWallImpl(from.id(), newVertex.id(), new WallData());
    } catch (GraphException e) {
      throw new RuntimeException(
          "Unexpected scenario: wall already exists despite the one vertex being new", e);
    }

    return new Wall(from, newVertex, newWall);
  }

  public Vertex splitWall(Wall wall) {
    // Remove wall and replace with two new walls connected through a new vertex.

    var removed = graph.removeEdge(wall.from().id(), wall.to().id());
    if (removed == null) {
      throw new RuntimeException("Wall does not exist");
    }
    // Don't trust the contents of `wall`, instead use `removed`.

    var newVertex = createVertex();

    try {
      createWallImpl(removed.from(), newVertex.id(), wall.data());
      createWallImpl(newVertex.id(), removed.to(), wall.data());
    } catch (GraphException e) {
      throw new RuntimeException("Unexpected scenario: wall already exists for new vertex", e);
    }

    return newVertex;
  }

  /**
   * Merge {@code first} with {@code second}.
   *
   * <p>How this merger is done is up to the implementation. Either {@code first} or {@code second}
   * may remaining in the graph while the other is removed, or both may be removed and replaced with
   * a new vertex. Callers should use the return value if the new or surviving vertex is required.
   *
   * @param first The vertex to remove by merging.
   * @param second The vertex to augment by merging {@code remove} into it.
   * @return The merged vertex.
   */
  public Vertex merge(Vertex first, Vertex second) {
    // Current implementation is to remove `first` and keep `second.

    // A copy is essential since graph.edgesOf() returns a live set.
    var incidentWalls = new ArrayList<>(graph.edgesOf(first.id()));

    var secondIsForeign = !graph.containsVertex(second.id());
    if (secondIsForeign) {
      throw new RuntimeException("Unable unable merge vertex that does not exist!");
    }

    var wasRemoved = graph.removeVertex(first.id());
    if (!wasRemoved) {
      throw new RuntimeException("Unable unable merge vertex that does not exist!");
    }

    // Anything that used to point to or from `first` now has to point to or from `second`.
    for (var oldWall : incidentWalls) {
      var firstIsSource = oldWall.from().equals(first.id());
      var neighbour = firstIsSource ? oldWall.to() : oldWall.from();

      // Three cases:
      // 1. `neighbour` is `second`. Drop such contracted walls.
      // 2. A wall already exists from `neighbour` to `second`. Merge into the existing wall.
      // 3. No wall exists from `neighbour` to `second`. Copy the wall over.

      if (neighbour.equals(second.id())) {
        // The wall was contracted. Drop it as redundant.
        continue;
      }

      var existingWall = graph.getEdge(second.id(), neighbour); // Order doesn't matter.
      if (existingWall == null) {
        // Simply duplicate the wall for `second` instead of `first`.
        try {
          var from = firstIsSource ? second.id() : neighbour;
          var to = firstIsSource ? neighbour : second.id();
          createWallImpl(from, to, oldWall.data());
        } catch (GraphException e) {
          throw new RuntimeException(
              "Unexpected scenario: wall already exists despite not existing", e);
        }
      } else {
        // Merge obsolete wall into the kept wall.
        var secondIsSource = existingWall.from().equals(second.id());
        var reversed = firstIsSource != secondIsSource;
        var newData = existingWall.data().merge(oldWall.data(), reversed);
        existingWall.data().set(newData);
      }
    }

    removeDanglingVertices();

    return second;
  }

  public void bringToFront(Vertex vertex) {
    vertex.internal.zIndex(++nextZIndex);
  }

  // endregion

  /**
   * Ensures that any vertices with no walls are removed from the graph.
   *
   * <p>This is usually called automatically when needed, so don't call this method without reason.
   * Deserializing is an exception where improper XML could result in vertices being added without
   * associated walls.
   */
  public void removeDanglingVertices() {
    var verticesToRemove = new ArrayList<GUID>();
    for (var vertex : graph.vertexSet()) {
      if (graph.edgesOf(vertex).isEmpty()) {
        verticesToRemove.add(vertex);
      }
    }
    graph.removeAllVertices(verticesToRemove);

    for (var vertexId : verticesToRemove) {
      vertexInternalById.remove(vertexId);
    }
  }

  @Override
  public VisionResult addSegments(
      DirectionModifierType type, Coordinate origin, Envelope bounds, Consumer<Coordinate[]> sink) {
    getWalls()
        .forEach(
            wall -> {
              var segment = wall.asSegment();
              // Very rough bounds check so we don't include everything.
              if (!bounds.intersects(segment.p0, segment.p1)) {
                return;
              }

              // For directional walls, ensure the origin is on the correct side.
              var direction =
                  switch (wall.data().directionModifier(type)) {
                    case SameDirection -> wall.data().direction();
                    case ReverseDirection -> wall.data().direction.reversed();
                    case ForceBoth -> WallDirection.Both;
                    case Disabled -> null;
                  };
              if (direction == null) {
                // Segment is not active for this type.
                return;
              }

              boolean correctOrientation =
                  switch (direction) {
                    case Both -> true;
                    case Left -> Orientation.RIGHT == segment.orientationIndex(origin);
                    case Right -> Orientation.LEFT == segment.orientationIndex(origin);
                  };
              if (!correctOrientation) {
                return;
              }

              sink.accept(new Coordinate[] {segment.p0, segment.p1});
            });
    return VisionResult.Possible;
  }

  public WallTopologyDto toDto() {
    var builder = WallTopologyDto.newBuilder();
    for (var vertex : this.vertexInternalById.values()) {
      builder.addVertices(
          VertexDto.newBuilder()
              .setId(vertex.id().toString())
              .setPosition(
                  DoublePointDto.newBuilder()
                      .setX(vertex.position().getX())
                      .setY(vertex.position().getY())));
    }
    for (var wall : this.graph.edgeSet()) {
      builder.addWalls(wall.toDto());
    }
    return builder.build();
  }

  public static WallTopology fromDto(WallTopologyDto dto) {
    var topology = new WallTopology();
    for (var vertexDto : dto.getVerticesList()) {
      topology.createVertex();
      Vertex vertex;
      try {
        vertex = topology.createVertex(new GUID(vertexDto.getId()));
      } catch (GraphException e) {
        log.error("Unexpected error while adding vertex to graph", e);
        continue;
      }

      vertex.setPosition(vertexDto.getPosition().getX(), vertexDto.getPosition().getY());
    }
    for (var wallDto : dto.getWallsList()) {
      try {
        var internal = WallInternal.fromDto(wallDto);
        topology.createWallImpl(internal.from(), internal.to(), internal.data());
      } catch (GraphException e) {
        log.error("Unexpected error while adding wall to topology", e);
      }
    }
    return topology;
  }
}
