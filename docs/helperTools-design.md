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

### 2. Proximity & Target Finding

#### 2.1 `findClosestFood(Unit[] units, Position position) -> Optional<Position>`

**Input**:

- Map<String, Unit> currentState (bot's internal game state with all current units),
- Position position

**Purpose**: Locate the closest food unit to a given position.

**Algorithm**: Linear scan + distance calculation.

- Calculates Manhattan or Euclidean distance to all food units.
- Returns position of nearest food, or `Optional.empty()` if no food exists.

**Complexity**: O(num_food_units).

#### 2.2 `findClosestEnemy(Unit[] units, Position position) -> Optional<Pawn>`

**Purpose**: Find the closest enemy pawn to a given position.

**Algorithm**: Linear scan of enemy pawns + distance calculation.

- Useful for defensive or aggressive strategy (e.g., "flee from closest enemy" or "pursue closest enemy").

**Complexity**: O(num_enemy_pawns).


#### 2.3 `findAllEnemiesWithinDistance(Map<String, Unit> currentState, Position position, int distance) -> List<Pawn>`

**Input**:

- Map<String, Unit> currentState,
- Position position,
- int distance

**Purpose**: Find all enemy pawns within a specified distance.

**Algorithm**: Linear scan with distance filter.

- Useful for threat assessment (e.g., "are there enemies within 3 tiles?").

**Complexity**: O(num_enemy_pawns).

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

### 3. Collision & Threat Assessment

#### 3.1 `predictCollisions(Unit[], List<Action> actions) -> CollisionPrediction`

**Input**:
- Unit[] list of all units,
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

#### 3.2 `isPositionSafe(Unit[] units, Position position) -> boolean`

**Input**:

- Unit[] units,
- Position position

**Purpose**: Determine if a position is safe (no enemy pawns, not a wall, not the opponent's base).

**Algorithm**: Lookup in currentState.

**Complexity**: O(1) to O(num_enemy_pawns) depending on implementation.

---

#### 3.3 `getEnemiesAtPosition(Unit[] units, Position position) -> List<Pawn>`

**Input**:

- Units[] units,
- Position position

**Purpose**: Get all enemy pawns at a specific position.

**Algorithm**: Filter enemy pawns by position.

**Complexity**: O(num_enemy_pawns).

### 4. Strategic Planning & Analysis

#### 4.1 `findAllFoodLocations(Unit[] units) -> List<Position>`

**Input**:

- Unit[] unit

**Purpose**: Get all current food positions, sorted by distance from a reference point.

**Algorithm**: Extract from gameState + optional sort by distance.

**Complexity**: O(num_food) or O(num_food × log(num_food)) with sorting.

---

**4.2 `getFriendlyPawnCount(Unit[] units) -> int`**

**Purpose**: Return the number of friendly pawns alive.

**Algorithm**: Count pawns in gameState.

**Complexity**: O(num_friendly_pawns).

---

**4.3 `getEnemyPawnCount(Unit[] units) -> int`**

**Purpose**: Return the number of enemy pawns alive.

---

**4.4 `getCentroidOfPawns(List<Pawn> pawns) -> Position`**


**Purpose**: Calculate the average (centroid) position of a group of pawns.

**Algorithm**: Average x and y coordinates.

**Complexity**: O(num_pawns).

---

**4.5 `isPositionAdjacentToBase(Unit[] units Position position) -> boolean`**

**Purpose**: Check if a position is adjacent to (within 1 step of) either base.

**Algorithm**: Calculate distance to both bases.

**Complexity**: O(1).

---

**4.6 `isPositionAdjacentToEnemy(Unit[] unit, Position position) -> boolean`**

**Purpose**: Check if a position is adjacent to any enemy pawn.

**Algorithm**: Check distance to all enemy pawns.

**Complexity**: O(num_enemy_pawns).

---

**4.7 `generateRandomAction(Unit[] unit, Pawn pawn) -> Action`**


**Purpose**: Generate a random legal action for a pawn (fallback for timeout or simple agents).

**Algorithm**: Pick a random direction; if blocked by wall or out of bounds, pick another.

**Complexity**: O(num_directions × attempts).


---

**4.8 `getPawnsNearFood(Unit[] unit, Position foodPos, int distance) -> List<Pawn>`**

**Purpose**: Find all (friendly or enemy) pawns within `distance` of a food unit.

**Algorithm**: Filter pawns by distance to food.

**Complexity**: O(num_pawns).

---

**4.9 `identifyStrategicPositions(MapLayout map, Position basePos) -> List<Position>`**

**Purpose**: Identify key choke points or defensive positions near a base.

**Algorithm**: Heuristic analysis (e.g., positions with few adjacent walkable tiles).

- Useful for setting up a defense perimeter.

**Complexity**: O(width × height).

---

**4.10 `getWallsNear(MapLayout map, Position position, int radius) -> Set<Position>`**


**Purpose**: Find all wall positions within `radius` steps of a position.

**Algorithm**: Filter wall positions by distance.

**Complexity**: O(num_walls).

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