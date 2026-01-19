# HelperTools — Technical Design Document

## Overview

`HelperTools` is a utility class that provides game-aware algorithms and analysis functions for the `GameManager` and `ActionPlanner`. These functions help agents make strategic decisions such as pathfinding, threat assessment, resource location, and tactical positioning. All functions are **stateless** and operate on immutable game state snapshots.

## Goals

- Provide efficient pathfinding algorithms (BFS, A*) for navigating around walls.
- Identify priority targets (enemy pawns, food, enemy bases).
- Calculate distances and proximity metrics.
- Assist with strategic positioning and collision avoidance.
- Remain computationally efficient to respect the 15s turn timeout.

## Core Functions

### 1. Pathfinding & Navigation

#### 1.1 `findShortestPath(MapLayout map, Position start, Position goal) -> Optional<List<Direction>>`**

**Input**:

- MapLayout map
- Position start
- Position goal

**Purpose**: Find the shortest path from `start` to `goal`, navigating around walls.

**Algorithm**: BFS (Breadth-First Search)

- Returns a sequence of directions (N, NE, E, ..., NW) to follow
- Guarantees the shortest path (fewest steps).
- Handles diagonal movement (8 directions).

**Complexity**: O(width × height) in worst case.

**Example**:

```java
Position start = new Position(1, 1);
Position goal = new Position(10, 10);
List<Direction> path = HelperTools.findShortestPath(mapLayout, start, goal);
// Returns: [E, E, E, NE, NE, N] or similar
```

**Returns**: List of directions to follow; empty list if `start == goal`;

#### 1.2 `findPathAvoiding(MapLayout map, Position start, Position goal, Set<Position> dangerZones) -> List<Direction>`

**Input**:

- MapLayout map,
- Position start,
- Position goal,
- Set<Position> dangerZones

**Purpose**: Find path from `start` to `goal`, avoiding tiles in `dangerZones` (enemy pawn locations, predicted collisions).

**Algorithm**: BFS with cost function (prefer safe paths).

- Weights danger zones with higher cost.
- Still returns shortest path, but favors safer routes.

**Complexity**: O(width × height).

**Example**:

```java
Set<Position> dangerZones = new HashSet<>(enemyPawns.stream()
  .map(Pawn::getPosition)
  .collect(Collectors.toList()));

List<Direction> safePath = HelperTools.findPathAvoiding(
  mapLayout,
  myPawn.getPosition(),
  foodPos,
  dangerZones
);
```

#### 1.3 `getReachablePositions(MapLayout map, Position start, int maxSteps) -> Set<Position>`**

**Input**:

- MapLayout map,
- Position start,
- int maxSteps

**Purpose**: Find all positions reachable from `start` within `maxSteps` moves.

**Algorithm**: BFS with step limit.

- Useful for tactical planning (e.g., "where can my pawn move in 3 turns?").
- Can be used to calculate if a pawn can reach food before an enemy does.

**Complexity**: O(width × height) for unlimited steps; O(maxSteps²) for bounded steps.

**Example**:

```java
Set<Position> reachable = HelperTools.getReachablePositions(mapLayout, myPawnPos, 5);
boolean canReachFood = reachable.contains(foodPos);
```

### 2. Proximity & Target Finding

#### 2.1 `findClosestFood(GameState gameState, Position position) -> Optional<Position>`

**Input**:

- GameState gameState,
- Position position

**Purpose**: Locate the closest food unit to a given position.

**Algorithm**: Linear scan + distance calculation.

- Calculates Manhattan or Euclidean distance to all food units.
- Returns position of nearest food, or `Optional.empty()` if no food exists.

**Complexity**: O(num_food_units).

**Example**:

```java
Optional<Position> closestFood = HelperTools.findClosestFood(gameState, myPawn.getPosition());

if (closestFood.isPresent()) {
  List<Direction> pathToFood = HelperTools.findShortestPath(
    gameState.getMapLayout(),
    myPawn.getPosition(),
    closestFood.get()
  );
}
```

#### 2.2 `findClosestEnemy(GameState gameState, Position position) -> Optional<Pawn>`

```java
public static Optional<Pawn> findClosestEnemy(
  GameState gameState,
  Position position
)
```

**Purpose**: Find the closest enemy pawn to a given position.

**Algorithm**: Linear scan of enemy pawns + distance calculation.

- Useful for defensive or aggressive strategy (e.g., "flee from closest enemy" or "pursue closest enemy").

**Complexity**: O(num_enemy_pawns).

**Example**:

```java
Optional<Pawn> threat = HelperTools.findClosestEnemy(gameState, myPawn.getPosition());
if (threat.isPresent()) {
  // Plan to avoid or chase threat.pawn
}
```

#### 2.3 `findAllEnemiesWithinDistance(GameState gameState, Position position, int distance) -> List<Pawn>`

**Input**:

- GameState gameState,
- Position position,
- int distance

**Purpose**: Find all enemy pawns within a specified distance.

**Algorithm**: Linear scan with distance filter.

- Useful for threat assessment (e.g., "are there enemies within 3 tiles?").

**Complexity**: O(num_enemy_pawns).

**Example**:

```java
List<Pawn> threats = HelperTools.findAllEnemiesWithinDistance(gameState, myPawn.getPosition(), 3);
if (!threats.isEmpty()) {
  // Multiple nearby threats; consider defensive strategy.
}
```

#### 2.4 `distanceTo(Position from, Position to, MapLayout map) -> int`

**Input**:

- Position from,
- Position to,
- MapLayout map

**Purpose**: Calculate shortest distance (in steps) between two positions.

**Algorithm**: BFS (or cached result from pathfinding).

- Returns the number of steps required to move from `from` to `to`.
- Returns -1 if unreachable (surrounded by walls).

**Complexity**: O(width × height).

**Example**:

```java
int stepsToFood = HelperTools.distanceTo(myPawn.getPosition(), foodPos, mapLayout);
int stepsToEnemy = HelperTools.distanceTo(myPawn.getPosition(), enemy.getPosition(), mapLayout);
```

### 3. Collision & Threat Assessment

#### 3.1 `predictCollisions(GameState gameState, List<Action> actions) -> CollisionPrediction`

**Input**:
- GameState gameState,
- List<Action> actions

**Output** (CollisionPrediction record):

- Map<String, Boolean> pawnWillDie;      // pawnId -> true if pawn will die
- Set<Position> foodWillBeConsumed;      // food positions consumed
- boolean baseWillBeLost;                // true if own base will be destroyed

**Purpose**: Simulate the turn and predict which pawns will die, food will be consumed, or if the base will be lost.

**Algorithm**: Simulate pawn movement and collisions without actually modifying game state.

- Applies the same collision logic as `GameEngine.applyActions()` on a copy of the state.
- Helpful for avoiding suicidal moves.

**Complexity**: O(num_pawns + num_food) for collision detection.

**Example**:

```java
List<Action> proposedActions = planner.generateCandidateActions(gameState);
CollisionPrediction prediction = HelperTools.predictCollisions(gameState, proposedActions);

// Filter out suicidal actions
List<Action> safeActions = proposedActions.stream()
  .filter(action -> !prediction.pawnWillDie.getOrDefault(action.getPawnId(), false))
  .collect(Collectors.toList());
```

#### 3.2 `isPositionSafe(GameState gameState, Position position) -> boolean`

**Input**:

- GameState gameState,
- Position position

**Purpose**: Determine if a position is safe (no enemy pawns, not a wall, not the opponent's base).

**Algorithm**: Lookup in gameState.

**Complexity**: O(1) to O(num_enemy_pawns) depending on implementation.

**Example**:

```java
// Move pawn only to safe positions
List<Direction> safeDirections = Direction.ALL
  .stream()
  .filter(dir -> {
    Position newPos = myPawn.getPosition().move(dir);
    return isPositionSafe(gameState, newPos);
  })
  .collect(Collectors.toList());
```

---

#### 3.3 `getEnemiesAtPosition(GameState gameState, Position position) -> List<Pawn>`

**Input**:

- GameState gameState,
- Position position

**Purpose**: Get all enemy pawns at a specific position.

**Algorithm**: Filter enemy pawns by position.

**Complexity**: O(num_enemy_pawns).

**Example**:

```java
List<Pawn> colliders = HelperTools.getEnemiesAtPosition(gameState, position);
if (colliders.size() > 0) {
  // This position has enemies; risky to move there.
}
```

### 4. Strategic Planning & Analysis

#### 4.1 `findAllFoodLocations(GameState gameState) -> List<Position>`

**Input**:

- GameState gameState

**Purpose**: Get all current food positions, sorted by distance from a reference point.

**Algorithm**: Extract from gameState + optional sort by distance.

**Complexity**: O(num_food) or O(num_food × log(num_food)) with sorting.

**Example**:

```java
List<Position> foods = HelperTools.findAllFoodLocations(gameState);
List<Position> foodByProximity = foods.stream()
  .sorted(Comparator.comparingInt(
    pos -> HelperTools.distanceTo(myPawn.getPosition(), pos, mapLayout)
  ))
  .collect(Collectors.toList());
```

---

**4.2 `getFriendlyPawnCount(GameState gameState) -> int`**

```java
public static int getFriendlyPawnCount(GameState gameState)
```

**Purpose**: Return the number of friendly pawns alive.

**Algorithm**: Count pawns in gameState.

**Complexity**: O(num_friendly_pawns).

**Example**:

```java
int myPawnCount = HelperTools.getFriendlyPawnCount(gameState);
int enemyPawnCount = HelperTools.getEnemyPawnCount(gameState);

if (myPawnCount < enemyPawnCount) {
  // We're outnumbered; prioritize food collection.
} else {
  // We have numerical advantage; consider aggressive strategy.
}
```

---

**4.3 `getEnemyPawnCount(GameState gameState) -> int`**

```java
public static int getEnemyPawnCount(GameState gameState)
```

**Purpose**: Return the number of enemy pawns alive.

---

**4.4 `getCentroidOfPawns(List<Pawn> pawns) -> Position`**

```java
public static Position getCentroidOfPawns(List<Pawn> pawns)
```

**Purpose**: Calculate the average (centroid) position of a group of pawns.

**Algorithm**: Average x and y coordinates.

**Complexity**: O(num_pawns).

**Example**:

```java
// Group pawns together for coordinated defense
Position myPawnCentroid = HelperTools.getCentroidOfPawns(gameState.getFriendlyPawns());
Position enemyCentroid = HelperTools.getCentroidOfPawns(gameState.getEnemyPawns());

// Move pawns toward centroid for mutual support
```

---

**4.5 `isPositionAdjacentToBase(GameState gameState, Position position) -> boolean`**

```java
public static boolean isPositionAdjacentToBase(
  GameState gameState,
  Position position
)
```

**Purpose**: Check if a position is adjacent to (within 1 step of) either base.

**Algorithm**: Calculate distance to both bases.

**Complexity**: O(1).

**Example**:

```java
if (HelperTools.isPositionAdjacentToBase(gameState, position)) {
  // Danger zone; enemy could storm the base next turn.
}
```

---

**4.6 `isPositionAdjacentToEnemy(GameState gameState, Position position) -> boolean`**

```java
public static boolean isPositionAdjacentToEnemy(
  GameState gameState,
  Position position
)
```

**Purpose**: Check if a position is adjacent to any enemy pawn.

**Algorithm**: Check distance to all enemy pawns.

**Complexity**: O(num_enemy_pawns).

**Example**:

```java
if (HelperTools.isPositionAdjacentToEnemy(gameState, position)) {
  // Risk of immediate collision; avoid or prepare for combat.
}
```

---

**4.7 `generateRandomAction(GameState gameState, Pawn pawn) -> Action`**

```java
public static Action generateRandomAction(GameState gameState, Pawn pawn)
```

**Purpose**: Generate a random legal action for a pawn (fallback for timeout or simple agents).

**Algorithm**: Pick a random direction; if blocked by wall or out of bounds, pick another.

**Complexity**: O(num_directions × attempts).

**Example**:

```java
// Timeout occurred; use random fallback action.
Action fallbackAction = HelperTools.generateRandomAction(gameState, myPawn);
```

---

**4.8 `getPawnsNearFood(GameState gameState, Position foodPos, int distance) -> List<Pawn>`**

```java
public static List<Pawn> getPawnsNearFood(
  GameState gameState,
  Position foodPos,
  int distance
)
```

**Purpose**: Find all (friendly or enemy) pawns within `distance` of a food unit.

**Algorithm**: Filter pawns by distance to food.

**Complexity**: O(num_pawns).

**Example**:

```java
// A food unit just appeared; see who can reach it first.
List<Pawn> friendlyNearFood = gameState.getFriendlyPawns().stream()
  .filter(p -> HelperTools.distanceTo(p.getPosition(), foodPos, mapLayout) <= 3)
  .collect(Collectors.toList());

List<Pawn> enemyNearFood = gameState.getEnemyPawns().stream()
  .filter(p -> HelperTools.distanceTo(p.getPosition(), foodPos, mapLayout) <= 3)
  .collect(Collectors.toList());

if (friendlyNearFood.size() > enemyNearFood.size()) {
  // We have a chance to claim the food; send nearest pawn.
}
```

---

**4.9 `identifyStrategicPositions(MapLayout map, Position basePos) -> List<Position>`**

```java
public static List<Position> identifyStrategicPositions(
  MapLayout map,
  Position basePos
)
```

**Purpose**: Identify key choke points or defensive positions near a base.

**Algorithm**: Heuristic analysis (e.g., positions with few adjacent walkable tiles).

- Useful for setting up a defense perimeter.

**Complexity**: O(width × height).

**Example**:
```java
List<Position> defensivePositions = HelperTools.identifyStrategicPositions(
  mapLayout,
  gameState.getMapLayout().getPlayerABase()
);

// Direct some pawns to defensive positions.
```

---

**4.10 `getWallsNear(MapLayout map, Position position, int radius) -> Set<Position>`**

```java
public static Set<Position> getWallsNear(
  MapLayout map,
  Position position,
  int radius
)
```

**Purpose**: Find all wall positions within `radius` steps of a position.

**Algorithm**: Filter wall positions by distance.

**Complexity**: O(num_walls).

**Example**:

```java
// Check terrain around pawn for cover
Set<Position> nearbyWalls = HelperTools.getWallsNear(mapLayout, myPawn.getPosition(), 2);
if (nearbyWalls.size() > 0) {
  // Consider moving toward wall for cover.
}
```

## Performance Considerations

- **Caching**: Pathfinding results can be cached per turn if the same queries repeat (e.g., multiple pawns going to the same food).
- **Lazy Evaluation**: Some functions (e.g., `getReachablePositions`) can be optimized with early termination.
- **Distance Metrics**: Use Manhattan distance for fast approximation; use exact BFS pathfinding only when needed.
- **Timeout Safeguard**: All functions should complete within milliseconds; total ActionPlanner execution must finish within 14 seconds (1s buffer from 15s limit).

## Suggested Unit Tests for HelperTools

### PathfindingTest

- Test `findShortestPath` with various map layouts and wall configurations.
- Test `findPathAvoiding` correctly avoids danger zones.
- Test `getReachablePositions` with bounded steps.
- Test unreachable goal throws `PathNotFoundException`.

### ProximityTest

- Test `findClosestFood` returns nearest food.
- Test `findClosestEnemy` returns nearest enemy pawn.
- Test `distanceTo` calculates correct distances.
- Test behavior with empty food or pawn lists.

### ThreatAssessmentTest

- Test `predictCollisions` correctly simulates turn outcomes.
- Test `isPositionSafe` identifies dangerous positions.
- Test `findAllEnemiesWithinDistance` with various radii.

### StrategicTest

- Test `getCentroidOfPawns` calculates centroid correctly.
- Test `isPositionAdjacentToBase` and `isPositionAdjacentToEnemy`.
- Test `identifyStrategicPositions` identifies key locations.

### UtilityTest

- Test `generateRandomAction` produces valid directions.
- Test `getFriendlyPawnCount` and `getEnemyPawnCount`.

## Implementation Notes

- Use a **queue-based BFS** for pathfinding (or A* with Manhattan heuristic for optimization).
- **Cache MapLayout walkability** to avoid repeated boundary checks.
- **Immutability**: All functions operate on `GameState` snapshots; no state is modified.
- **Thread Safety**: HelperTools is stateless; multiple ActionPlanners can call functions concurrently without synchronization.