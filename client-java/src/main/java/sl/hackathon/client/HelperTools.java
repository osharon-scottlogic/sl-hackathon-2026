package sl.hackathon.client;

import sl.hackathon.client.dtos.*;

import java.util.*;

/**
 * HelperTools provides game-aware algorithms and analysis functions for strategic decision making.
 * All functions are stateless and operate on immutable game state snapshots.
 */
public class HelperTools {

    private static final int[][] DIRECTION_OFFSETS = {
        {0, -1},   // N
        {1, -1},   // NE
        {1, 0},    // E
        {1, 1},    // SE
        {0, 1},    // S
        {-1, 1},   // SW
        {-1, 0},   // W
        {-1, -1}   // NW
    };

    // ==================== Pathfinding & Navigation ====================

    /**
     * Find the shortest path from start to goal using BFS.
     * Returns a list of directions to follow, or empty list if start equals goal.
     */
    public static Optional<List<Direction>> findShortestPath(
            MapLayout map,
            Position start,
            Position goal) {
        
        if (start.equals(goal)) {
            return Optional.of(new ArrayList<>());
        }

        Set<Position> walls = new HashSet<>(Arrays.asList(map.walls()));
        Queue<Position> queue = new LinkedList<>();
        Map<Position, Position> parent = new HashMap<>();
        Set<Position> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Position current = queue.poll();

            if (current.equals(goal)) {
                return Optional.of(reconstructPath(start, goal, parent));
            }

            for (int i = 0; i < 8; i++) {
                Position next = getNextPosition(current, i, map, walls);
                if (next != null && !visited.contains(next)) {
                    visited.add(next);
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Find path from start to goal while avoiding danger zones using weighted BFS.
     */
    public static Optional<List<Direction>> findPathAvoiding(
            MapLayout map,
            Position start,
            Position goal,
            Set<Position> dangerZones) {
        
        if (start.equals(goal)) {
            return Optional.of(new ArrayList<>());
        }

        Set<Position> walls = new HashSet<>(Arrays.asList(map.walls()));
        Queue<PathNode> queue = new LinkedList<>();
        Map<Position, Position> parent = new HashMap<>();
        Map<Position, Integer> distance = new HashMap<>();
        Set<Position> visited = new HashSet<>();

        queue.add(new PathNode(start, 0));
        distance.put(start, 0);
        visited.add(start);

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();

            if (current.position.equals(goal)) {
                return Optional.of(reconstructPath(start, goal, parent));
            }

            for (int i = 0; i < 8; i++) {
                Position next = getNextPosition(current.position, i, map, walls);
                if (next != null && !visited.contains(next)) {
                    int cost = 1;
                    if (dangerZones.contains(next)) {
                        cost = 10; // Higher cost for danger zones
                    }

                    int newDistance = current.distance + cost;
                    if (!distance.containsKey(next) || distance.get(next) > newDistance) {
                        distance.put(next, newDistance);
                        parent.put(next, current.position);
                        queue.add(new PathNode(next, newDistance));
                        if (cost == 1) {
                            visited.add(next);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Find all positions reachable from start within maxSteps moves.
     */
    public static Set<Position> getReachablePositions(
            MapLayout map,
            Position start,
            int maxSteps) {
        
        Set<Position> walls = new HashSet<>(Arrays.asList(map.walls()));
        Set<Position> reachable = new HashSet<>();
        Queue<StepNode> queue = new LinkedList<>();
        Map<Position, Integer> stepCount = new HashMap<>();

        queue.add(new StepNode(start, 0));
        stepCount.put(start, 0);
        reachable.add(start);

        while (!queue.isEmpty()) {
            StepNode current = queue.poll();

            if (current.steps >= maxSteps) {
                continue;
            }

            for (int i = 0; i < 8; i++) {
                Position next = getNextPosition(current.position, i, map, walls);
                if (next != null && !stepCount.containsKey(next)) {
                    stepCount.put(next, current.steps + 1);
                    reachable.add(next);
                    queue.add(new StepNode(next, current.steps + 1));
                }
            }
        }

        return reachable;
    }

    // ==================== Proximity & Target Finding ====================

    /**
     * Find the closest food unit to a given position.
     */
    public static Optional<Position> findClosestFood(GameState gameState, Position position) {
        Unit[] units = gameState.units();
        Optional<Unit> closestFood = Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.FOOD)
                .min(Comparator.comparingInt(unit -> euclideanDistance(position, unit.position())));

        return closestFood.map(Unit::position);
    }

    /**
     * Find the closest enemy pawn to a given position.
     */
    public static Optional<Unit> findClosestEnemy(GameState gameState, Position position, String playerOwnerId) {
        Unit[] units = gameState.units();

        return Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.PAWN && !unit.owner().equals(playerOwnerId))
                .min(Comparator.comparingInt(unit -> euclideanDistance(position, unit.position())));
    }

    /**
     * Find all enemy pawns within a specified distance.
     */
    public static List<Unit> findAllEnemiesWithinDistance(
            GameState gameState,
            Position position,
            int distance,
            String playerOwnerId) {
        
        Unit[] units = gameState.units();
        return Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.PAWN && !unit.owner().equals(playerOwnerId))
                .filter(unit -> euclideanDistance(position, unit.position()) <= distance)
                .toList();
    }

    /**
     * Calculate shortest distance between two positions using Manhattan distance as approximation.
     */
    public static int distanceTo(Position from, Position to) {
        return euclideanDistance(from, to);
    }

    // ==================== Collision & Threat Assessment ====================

    /**
     * Check if a position is safe (no enemy pawns, not a wall, within bounds).
     */
    public static boolean isPositionSafe(
            GameState gameState,
            Position position,
            MapLayout mapLayout,
            String playerOwnerId) {
        
        // Check if within bounds
        if (position.x() < 0 || position.x() >= mapLayout.dimension().width() ||
            position.y() < 0 || position.y() >= mapLayout.dimension().height()) {
            return false;
        }

        // Check if wall
        for (Position wall : mapLayout.walls()) {
            if (wall.equals(position)) {
                return false;
            }
        }

        // Check if enemy pawn at position
        for (Unit unit : gameState.units()) {
            if (unit.type() == UnitType.PAWN && 
                !unit.owner().equals(playerOwnerId) && 
                unit.position().equals(position)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get all enemy pawns at a specific position.
     */
    public static List<Unit> getEnemiesAtPosition(
            GameState gameState,
            Position position,
            String playerOwnerId) {
        
        Unit[] units = gameState.units();
        return Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.PAWN &&
                               !unit.owner().equals(playerOwnerId) &&
                               unit.position().equals(position))
                .toList();
    }

    // ==================== Strategic Planning & Analysis ====================

    /**
     * Find all food locations in the current game state.
     */
    public static List<Position> findAllFoodLocations(GameState gameState) {
        Unit[] units = gameState.units();
        return Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.FOOD)
                .map(Unit::position)
                .toList();
    }

    /**
     * Get count of friendly pawns alive.
     */
    public static int getFriendlyPawnCount(GameState gameState, String playerOwnerId) {
        Unit[] units = gameState.units();
        return (int) Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.PAWN && unit.owner().equals(playerOwnerId))
                .count();
    }

    /**
     * Get count of enemy pawns alive.
     */
    public static int getEnemyPawnCount(GameState gameState, String playerOwnerId) {
        Unit[] units = gameState.units();
        return (int) Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.PAWN && !unit.owner().equals(playerOwnerId))
                .count();
    }

    /**
     * Calculate the centroid (average position) of a group of units.
     */
    public static Position getCentroidOfUnits(List<Unit> units) {
        if (units.isEmpty()) {
            return new Position(0, 0);
        }

        int sumX = units.stream().mapToInt(u -> u.position().x()).sum();
        int sumY = units.stream().mapToInt(u -> u.position().y()).sum();

        return new Position(sumX / units.size(), sumY / units.size());
    }

    /**
     * Check if a position is adjacent to any enemy pawn.
     */
    public static boolean isPositionAdjacentToEnemy(
            GameState gameState,
            Position position,
            String playerOwnerId) {
        
        Unit[] units = gameState.units();
        return Arrays.stream(units)
                .filter(unit -> unit.type() == UnitType.PAWN && !unit.owner().equals(playerOwnerId))
                .anyMatch(unit -> isAdjacent(position, unit.position()));
    }

    /**
     * Generate a random legal action for a unit.
     */
    public static Action generateRandomAction(
            GameState gameState,
            Unit unit,
            MapLayout mapLayout) {
        
        Set<Position> walls = new HashSet<>(Arrays.asList(mapLayout.walls()));
        List<Direction> validDirections = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            Position next = getNextPosition(unit.position(), i, mapLayout, walls);
            if (next != null) {
                validDirections.add(Direction.values()[i]);
            }
        }

        if (validDirections.isEmpty()) {
            return new Action(unit.id(), Direction.N);
        }

        return new Action(unit.id(), validDirections.get(new Random().nextInt(validDirections.size())));
    }

    /**
     * Find all units (friendly or enemy) near a specific position.
     */
    public static List<Unit> getUnitsNearPosition(
            GameState gameState,
            Position position,
            int distance) {
        
        Unit[] units = gameState.units();
        return Arrays.stream(units)
                .filter(unit -> euclideanDistance(position, unit.position()) <= distance)
                .toList();
    }

    /**
     * Identify strategic positions near a reference position (positions with limited adjacent walkable tiles).
     */
    public static List<Position> identifyStrategicPositions(
            MapLayout map,
            Position referencePos,
            int radius) {
        
        Set<Position> walls = new HashSet<>(Arrays.asList(map.walls()));
        Set<Position> reachable = getReachablePositions(map, referencePos, radius);
        List<Position> strategic = new ArrayList<>();

        for (Position pos : reachable) {
            int adjacentWalkable = 0;
            for (int i = 0; i < 8; i++) {
                Position adjacent = getNextPosition(pos, i, map, walls);
                if (adjacent != null) {
                    adjacentWalkable++;
                }
            }
            // Positions with fewer adjacent tiles are more strategic (choke points)
            if (adjacentWalkable <= 3) {
                strategic.add(pos);
            }
        }

        return strategic;
    }

    /**
     * Get all wall positions within a specified radius of a position.
     */
    public static Set<Position> getWallsNear(MapLayout map, Position position, int radius) {
        Set<Position> nearbyWalls = new HashSet<>();

        for (Position wall : map.walls()) {
            if (manhattanDistance(position, wall) <= radius) {
                nearbyWalls.add(wall);
            }
        }

        return nearbyWalls;
    }

    // ==================== Helper Methods ====================

    private static Position getNextPosition(
            Position current,
            int directionIndex,
            MapLayout map,
            Set<Position> walls) {
        
        int[] offset = DIRECTION_OFFSETS[directionIndex];
        int newX = current.x() + offset[0];
        int newY = current.y() + offset[1];

        // Check bounds
        if (newX < 0 || newX >= map.dimension().width() ||
            newY < 0 || newY >= map.dimension().height()) {
            return null;
        }

        Position newPos = new Position(newX, newY);

        // Check for walls
        if (walls.contains(newPos)) {
            return null;
        }

        return newPos;
    }

    private static List<Direction> reconstructPath(Position start, Position goal, Map<Position, Position> parent) {
        List<Direction> path = new ArrayList<>();
        Position current = goal;

        while (!current.equals(start)) {
            Position prev = parent.get(current);
            Direction dir = getDirectionBetween(prev, current);
            path.addFirst(dir);
            current = prev;
        }

        return path;
    }

    private static Direction getDirectionBetween(Position from, Position to) {
        int dx = Integer.compare(to.x(), from.x());
        int dy = Integer.compare(to.y(), from.y());

        for (int i = 0; i < 8; i++) {
            if (DIRECTION_OFFSETS[i][0] == dx && DIRECTION_OFFSETS[i][1] == dy) {
                return Direction.values()[i];
            }
        }

        return Direction.N; // Fallback
    }

    private static int manhattanDistance(Position from, Position to) {
        return Math.abs(from.x() - to.x()) + Math.abs(from.y() - to.y());
    }

    private static int euclideanDistance(Position from, Position to) {
        return (int) Math.sqrt(Math.pow(from.x() - to.x(), 2) + Math.pow(from.y() - to.y(), 2));
    }

    private static boolean isAdjacent(Position pos1, Position pos2) {
        return euclideanDistance(pos1, pos2) == 1;
    }

    // ==================== Collision Prediction ====================

    /**
     * Predict collision outcomes for a set of actions without modifying game state.
     * Simulates pawn movements and detects:
     * - Pawns that will die from enemy collisions
     * - Food positions that will be consumed
     * - Whether a base will be destroyed
     * Collision rules:
     * - Two enemy pawns on same tile → both die
     * - Two friendly pawns on same tile → both survive
     * - Pawn + food on same tile → pawn survives, food is consumed
     * - Pawn + enemy base on same tile → pawn dies, base is destroyed
     */
    public static CollisionPrediction predictCollisions(
            GameState gameState,
            List<Action> actions,
            MapLayout mapLayout,
            String playerOwnerId,
            Position[] basePositions) {
        
        // Create a map of new positions after all actions
        Map<Position, List<Unit>> newPositions = new HashMap<>();
        Set<Position> foodConsumed = new HashSet<>();
        boolean baseDestroyed = false;

        // First, apply all actions to get new positions
        for (Unit unit : gameState.units()) {
            if (unit.type() == UnitType.PAWN) {
                Position newPos = unit.position();

                // Find action for this pawn
                for (Action action : actions) {
                    if (action.unitId() == unit.id()) {
                        newPos = moveUnitInDirection(unit.position(), action.direction(), mapLayout);
                        break;
                    }
                }

                newPositions.computeIfAbsent(newPos, k -> new ArrayList<>()).add(unit);
            }
        }

        // Track pawns that will die
        Map<Integer, Boolean> pawnWillDie = new HashMap<>();
        for (Unit unit : gameState.units()) {
            if (unit.type() == UnitType.PAWN) {
                pawnWillDie.put(unit.id(), false);
            }
        }

        // Detect enemy collisions (both pawns die)
        for (Position pos : newPositions.keySet()) {
            List<Unit> unitsAtPos = newPositions.get(pos);
            List<Unit> enemyPawns = unitsAtPos.stream()
                    .filter(u -> u.type() == UnitType.PAWN && !u.owner().equals(playerOwnerId))
                    .toList();
            
            List<Unit> friendlyPawns = unitsAtPos.stream()
                    .filter(u -> u.type() == UnitType.PAWN && u.owner().equals(playerOwnerId))
                    .toList();

            // If friendly and enemy pawns collide at same position, enemy pawns die
            if (!friendlyPawns.isEmpty() && !enemyPawns.isEmpty()) {
                for (Unit units : unitsAtPos) {
                    pawnWillDie.put(units.id(), true);
                }
            }

            // Check for enemy collisions (two enemy pawns at same position)
            Map<String, List<Unit>> pawnsByOwner = new HashMap<>();
            for (Unit u : unitsAtPos) {
                if (u.type() == UnitType.PAWN) {
                    pawnsByOwner.computeIfAbsent(u.owner(), k -> new ArrayList<>()).add(u);
                }
            }

            for (List<Unit> ownerPawns : pawnsByOwner.values()) {
                if (ownerPawns.size() > 1 && !ownerPawns.getFirst().owner().equals(playerOwnerId)) {
                    // Two enemy pawns at same position → both die
                    for (Unit pawn : ownerPawns) {
                        pawnWillDie.put(pawn.id(), true);
                    }
                }
            }
        }

        // Detect food consumption
        for (Unit foodUnit : gameState.units()) {
            if (foodUnit.type() == UnitType.FOOD && newPositions.containsKey(foodUnit.position())) {
                List<Unit> unitsAtFood = newPositions.get(foodUnit.position());
                if (unitsAtFood.stream().anyMatch(u -> u.type() == UnitType.PAWN)) {
                    foodConsumed.add(foodUnit.position());
                }
            }
        }

        // Detect base destruction (friendly pawn moves to enemy base)
        for (Position basePos : basePositions) {
            if (newPositions.containsKey(basePos)) {
                List<Unit> unitsAtBase = newPositions.get(basePos);
                for (Unit unit : unitsAtBase) {
                    if (unit.type() == UnitType.PAWN && !unit.owner().equals(playerOwnerId)) {
                        baseDestroyed = true;
                        pawnWillDie.put(unit.id(), true);
                        break;
                    }
                }
            }
        }

        return new CollisionPrediction(pawnWillDie, foodConsumed, baseDestroyed);
    }

    /**
     * Simpler version of predictCollisions that only requires essential parameters.
     * Uses empty base positions array.
     */
    public static CollisionPrediction predictCollisions(
            GameState gameState,
            List<Action> actions,
            MapLayout mapLayout,
            String playerOwnerId) {
        
        return predictCollisions(gameState, actions, mapLayout, playerOwnerId, new Position[0]);
    }

    /**
     * Check if a proposed action would result in pawn death.
     */
    public static boolean wouldResultInDeath(
            GameState gameState,
            Action action,
            MapLayout mapLayout,
            String playerOwnerId) {
        
        CollisionPrediction prediction = predictCollisions(
                gameState,
                List.of(action),
                mapLayout,
                playerOwnerId
        );

        return prediction.pawnWillDie().getOrDefault(action.unitId(), false);
    }

    /**
     * Get all safe actions (actions that won't result in pawn death).
     */
    public static List<Action> filterOutDeadlyActions(
            GameState gameState,
            List<Action> proposedActions,
            MapLayout mapLayout,
            String playerOwnerId) {
        
        CollisionPrediction prediction = predictCollisions(
                gameState,
                proposedActions,
                mapLayout,
                playerOwnerId
        );

        return proposedActions.stream()
                .filter(action -> !prediction.pawnWillDie().getOrDefault(action.unitId(), false))
                .toList();
    }

    // ==================== Collision Helper Methods ====================

    /**
     * Move a unit one step in the specified direction.
     */
    private static Position moveUnitInDirection(Position current, Direction direction, MapLayout mapLayout) {
        int dirIndex = direction.ordinal();
        int[] offset = DIRECTION_OFFSETS[dirIndex];
        int newX = current.x() + offset[0];
        int newY = current.y() + offset[1];

        // Check bounds
        if (newX < 0 || newX >= mapLayout.dimension().width() ||
            newY < 0 || newY >= mapLayout.dimension().height()) {
            return current; // Stay in place if move would go out of bounds
        }

        // Check for walls
        for (Position wall : mapLayout.walls()) {
            if (wall.x() == newX && wall.y() == newY) {
                return current; // Stay in place if move would hit wall
            }
        }

        return new Position(newX, newY);
    }

    // Helper classes for queue operations
    private static class PathNode {
        Position position;
        int distance;

        PathNode(Position position, int distance) {
            this.position = position;
            this.distance = distance;
        }
    }

    private static class StepNode {
        Position position;
        int steps;

        StepNode(Position position, int steps) {
            this.position = position;
            this.steps = steps;
        }
    }
}
