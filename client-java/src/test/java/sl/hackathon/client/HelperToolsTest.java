package sl.hackathon.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import sl.hackathon.client.dtos.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for HelperTools.
 * Tests pathfinding, proximity detection, threat assessment, and strategic planning functions.
 */
@DisplayName("HelperTools Tests")
class HelperToolsTest {
    private MapLayout emptyMap;
    private MapLayout mapWithWalls;
    private String playerId;

    @BeforeEach
    public void setUp() {
        playerId = "player-1";
        
        // Create empty 8x8 map
        emptyMap = new MapLayout(new Dimension(8, 8), new Position[0]);

        // Create 8x8 map with some walls
        Position[] walls = {
            new Position(3, 3),
            new Position(3, 4),
            new Position(3, 5),
            new Position(4, 3)
        };
        mapWithWalls = new MapLayout(new Dimension(8, 8), walls);
    }

    // ==================== Pathfinding Tests ====================

    @Test
    @DisplayName("Should return empty path when start equals goal")
    public void testFindShortestPathStartEqualsGoal() {
        Position pos = new Position(4, 4);
        Optional<List<Direction>> path = HelperTools.findShortestPath(emptyMap, pos, pos);

        assertTrue(path.isPresent());
        assertTrue(path.get().isEmpty());
    }

    @Test
    @DisplayName("Should find straight path in empty map")
    public void testFindShortestPathStraightLine() {
        Position start = new Position(0, 0);
        Position goal = new Position(3, 0);
        
        Optional<List<Direction>> path = HelperTools.findShortestPath(emptyMap, start, goal);

        assertTrue(path.isPresent());
        assertEquals(3, path.get().size());
        // Should be moving East three times
        assertTrue(path.get().stream().allMatch(d -> d == Direction.E));
    }

    @Test
    @DisplayName("Should find path around walls")
    public void testFindShortestPathWithWalls() {
        Position start = new Position(2, 3);
        Position goal = new Position(4, 4);
        /*  ........
            ........
            ........
            ..aww...
            ...wb...
            ...w....
            ........
            ........
         */
        Optional<List<Direction>> path = HelperTools.findShortestPath(mapWithWalls, start, goal);

        assertTrue(path.isPresent());
        assertFalse(path.get().isEmpty());
    }

    @Test
    @DisplayName("Should return empty optional for unreachable goal")
    public void testFindShortestPathUnreachable() {
        // Create a wall barrier
        Position[] walls = new Position[100];
        for (int i = 0; i < 8; i++) {
            walls[i] = new Position(4, i);
        }
        MapLayout barrierMap = new MapLayout(new Dimension(8, 8), walls);

        Position start = new Position(0, 4);
        Position goal = new Position(7, 4);
        
        Optional<List<Direction>> path = HelperTools.findShortestPath(barrierMap, start, goal);

        assertFalse(path.isPresent());
    }

    @Test
    @DisplayName("Should find path avoiding danger zones")
    public void testFindPathAvoiding() {
        Position start = new Position(0, 4);
        Position goal = new Position(7, 4);
        Set<Position> dangerZones = new HashSet<>(Arrays.asList(
            new Position(4, 4),
            new Position(5, 4)
        ));
        
        Optional<List<Direction>> path = HelperTools.findPathAvoiding(emptyMap, start, goal, dangerZones);

        assertTrue(path.isPresent());
        assertFalse(path.get().isEmpty());
    }

    @Test
    @DisplayName("Should get all reachable positions within max steps")
    public void testGetReachablePositions() {
        Position start = new Position(4, 4);
        Set<Position> reachable = HelperTools.getReachablePositions(emptyMap, start, 2);

        assertTrue(reachable.contains(start));
        assertTrue(reachable.contains(new Position(5, 4)));
        assertTrue(reachable.contains(new Position(3, 4)));
        assertTrue(reachable.contains(new Position(4, 3)));
        assertTrue(reachable.contains(new Position(4, 5)));
    }

    @Test
    @DisplayName("Should respect max steps limit")
    public void testGetReachablePositionsRespectLimit() {
        Position start = new Position(4, 4);
        Set<Position> reachable = HelperTools.getReachablePositions(emptyMap, start, 1);

        // With 1 step, should only reach 8 adjacent positions plus start
        assertTrue(reachable.size() <= 9);
        assertTrue(reachable.contains(start));
    }

    // ==================== Proximity Tests ====================

    @Test
    @DisplayName("Should find closest food")
    public void testFindClosestFood() {
        Unit[] units = {
            new Unit(1, "system", UnitType.FOOD, new Position(2, 2)),
            new Unit(2, "system", UnitType.FOOD, new Position(6, 6)),
            new Unit(3, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);
        Position searcher = new Position(0, 0);

        Optional<Position> closest = HelperTools.findClosestFood(gameState, searcher);

        assertTrue(closest.isPresent());
        assertEquals(new Position(2, 2), closest.get());
    }

    @Test
    @DisplayName("Should return empty when no food exists")
    public void testFindClosestFoodNoFood() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        Optional<Position> closest = HelperTools.findClosestFood(gameState, new Position(0, 0));

        assertFalse(closest.isPresent());
    }

    @Test
    @DisplayName("Should find closest enemy")
    public void testFindClosestEnemy() {
        Unit[] units = {
            new Unit(1, "player-2", UnitType.PAWN, new Position(5, 5)),
            new Unit(2, "player-2", UnitType.PAWN, new Position(1, 1)),
            new Unit(3, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        Optional<Unit> closest = HelperTools.findClosestEnemy(gameState, new Position(0, 0), playerId);

        assertTrue(closest.isPresent());
        assertEquals(2, closest.get().id());
    }

    @Test
    @DisplayName("Should find all enemies within distance")
    public void testFindAllEnemiesWithinDistance() {
        Unit[] units = {
            new Unit(1, "player-2", UnitType.PAWN, new Position(2, 0)),
            new Unit(2, "player-2", UnitType.PAWN, new Position(5, 5)),
            new Unit(3, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        List<Unit> threats = HelperTools.findAllEnemiesWithinDistance(gameState, new Position(0, 0), 3, playerId);

        assertEquals(1, threats.size());
        assertEquals(1, threats.get(0).id());
    }

    @Test
    @DisplayName("Should calculate correct Euclidean distance")
    public void testDistanceTo() {
        assertEquals(0, HelperTools.distanceTo(new Position(0, 0), new Position(0, 0)));
        assertEquals(2, HelperTools.distanceTo(new Position(0, 0), new Position(2, 2)));
        assertEquals(3, HelperTools.distanceTo(new Position(0, 0), new Position(3, 2)));
    }

    // ==================== Threat Assessment Tests ====================

    @Test
    @DisplayName("Should identify safe positions")
    public void testIsPositionSafe() {
        Unit[] units = {
            new Unit(1, "player-2", UnitType.PAWN, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        assertTrue(HelperTools.isPositionSafe(gameState, new Position(0, 0), emptyMap, playerId));
        assertTrue(HelperTools.isPositionSafe(gameState, new Position(3, 3), emptyMap, playerId));
        assertFalse(HelperTools.isPositionSafe(gameState, new Position(5, 5), emptyMap, playerId));
        assertFalse(HelperTools.isPositionSafe(gameState, new Position(-1, 0), emptyMap, playerId));
        assertFalse(HelperTools.isPositionSafe(gameState, new Position(8, 0), emptyMap, playerId));
    }

    @Test
    @DisplayName("Should identify wall positions as unsafe")
    public void testIsPositionSafeWall() {
        Unit[] units = {};
        GameState gameState = new GameState(units, 0L);

        assertFalse(HelperTools.isPositionSafe(gameState, new Position(3, 3), mapWithWalls, playerId));
    }

    @Test
    @DisplayName("Should find enemies at specific position")
    public void testGetEnemiesAtPosition() {
        Unit[] units = {
            new Unit(1, "player-2", UnitType.PAWN, new Position(5, 5)),
            new Unit(2, "player-2", UnitType.PAWN, new Position(5, 5)),
            new Unit(3, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        List<Unit> enemies = HelperTools.getEnemiesAtPosition(gameState, new Position(5, 5), playerId);

        assertEquals(2, enemies.size());
    }

    // ==================== Strategic Planning Tests ====================

    @Test
    @DisplayName("Should find all food locations")
    public void testFindAllFoodLocations() {
        Unit[] units = {
            new Unit(1, "system", UnitType.FOOD, new Position(2, 2)),
            new Unit(2, "system", UnitType.FOOD, new Position(6, 6)),
            new Unit(3, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        List<Position> foods = HelperTools.findAllFoodLocations(gameState);

        assertEquals(2, foods.size());
        assertTrue(foods.contains(new Position(2, 2)));
        assertTrue(foods.contains(new Position(6, 6)));
    }

    @Test
    @DisplayName("Should count friendly pawns")
    public void testGetFriendlyPawnCount() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(3, "player-2", UnitType.PAWN, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        int count = HelperTools.getFriendlyPawnCount(gameState, playerId);

        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should count enemy pawns")
    public void testGetEnemyPawnCount() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, "player-2", UnitType.PAWN, new Position(5, 5)),
            new Unit(3, "player-2", UnitType.PAWN, new Position(6, 6))
        };
        GameState gameState = new GameState(units, 0L);

        int count = HelperTools.getEnemyPawnCount(gameState, playerId);

        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should calculate centroid of units")
    public void testGetCentroidOfUnits() {
        List<Unit> units = Arrays.asList(
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(3, playerId, UnitType.PAWN, new Position(2, 2))
        );

        Position centroid = HelperTools.getCentroidOfUnits(units);

        assertEquals(2, centroid.x()); // (0 + 4 + 2) / 3 = 2
        assertEquals(2, centroid.y()); // (0 + 4 + 2) / 3 = 2
    }

    @Test
    @DisplayName("Should return (0,0) for empty unit list centroid")
    public void testGetCentroidOfUnitsEmpty() {
        List<Unit> units = new ArrayList<>();

        Position centroid = HelperTools.getCentroidOfUnits(units);

        assertEquals(0, centroid.x());
        assertEquals(0, centroid.y());
    }

    @Test
    @DisplayName("Should detect position adjacent to enemy")
    public void testIsPositionAdjacentToEnemy() {
        Unit[] units = {
            new Unit(1, "player-2", UnitType.PAWN, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        assertTrue(HelperTools.isPositionAdjacentToEnemy(gameState, new Position(4, 5), playerId));
        assertTrue(HelperTools.isPositionAdjacentToEnemy(gameState, new Position(5, 4), playerId));
        assertFalse(HelperTools.isPositionAdjacentToEnemy(gameState, new Position(0, 0), playerId));
        assertFalse(HelperTools.isPositionAdjacentToEnemy(gameState, new Position(7, 7), playerId));
    }

    @Test
    @DisplayName("Should generate random action for unit")
    public void testGenerateRandomAction() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4))
        };
        GameState gameState = new GameState(units, 0L);
        Unit pawn = units[0];

        Action action = HelperTools.generateRandomAction(gameState, pawn, emptyMap);

        assertNotNull(action);
        assertEquals(1, action.unitId());
        assertNotNull(action.direction());
    }

    @Test
    @DisplayName("Should generate valid actions for edge positions")
    public void testGenerateRandomActionEdgePosition() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);
        Unit pawn = units[0];

        for (int i = 0; i < 10; i++) {
            Action action = HelperTools.generateRandomAction(gameState, pawn, emptyMap);
            assertNotNull(action);
            assertNotNull(action.direction());
        }
    }

    @Test
    @DisplayName("Should get units near position")
    public void testGetUnitsNearPosition() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, playerId, UnitType.PAWN, new Position(2, 2)),
            new Unit(3, playerId, UnitType.PAWN, new Position(7, 7))
        };
        GameState gameState = new GameState(units, 0L);

        List<Unit> nearby = HelperTools.getUnitsNearPosition(gameState, new Position(0, 0), 3);

        assertEquals(2, nearby.size()); // pawn-1 and pawn-2
    }

    @Test
    @DisplayName("Should identify strategic positions")
    public void testIdentifyStrategicPositions() {
        Position basePos = new Position(0, 0);
        List<Position> strategic = HelperTools.identifyStrategicPositions(emptyMap, basePos, 2);

        assertNotNull(strategic);
        assertTrue(strategic.size() > 0);
    }

    @Test
    @DisplayName("Should find walls near position")
    public void testGetWallsNear() {
        Set<Position> walls = HelperTools.getWallsNear(mapWithWalls, new Position(3, 3), 1);

        assertTrue(walls.contains(new Position(3, 3)));
        assertTrue(walls.contains(new Position(3, 4)));
    }

    @Test
    @DisplayName("Should return empty set for no nearby walls")
    public void testGetWallsNearNoWalls() {
        Set<Position> walls = HelperTools.getWallsNear(emptyMap, new Position(3, 3), 1);

        assertTrue(walls.isEmpty());
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle empty game state")
    public void testEmptyGameState() {
        GameState emptyState = new GameState(new Unit[0], 0L);

        assertFalse(HelperTools.findClosestFood(emptyState, new Position(0, 0)).isPresent());
        assertFalse(HelperTools.findClosestEnemy(emptyState, new Position(0, 0), playerId).isPresent());
        assertEquals(0, HelperTools.getFriendlyPawnCount(emptyState, playerId));
        assertEquals(0, HelperTools.getEnemyPawnCount(emptyState, playerId));
    }

    @Test
    @DisplayName("Should handle large game state")
    public void testLargeGameState() {
        Unit[] units = new Unit[100];
        for (int i = 0; i < 100; i++) {
            units[i] = new Unit(i + 1, playerId, UnitType.PAWN, 
                              new Position(i % 8, i / 8));
        }
        GameState gameState = new GameState(units, 0L);

        assertEquals(100, HelperTools.getFriendlyPawnCount(gameState, playerId));
    }

    @Test
    @DisplayName("Should handle positions at map boundaries")
    public void testBoundaryPositions() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, playerId, UnitType.PAWN, new Position(7, 7))
        };
        GameState gameState = new GameState(units, 0L);

        assertTrue(HelperTools.isPositionSafe(gameState, new Position(1, 0), emptyMap, playerId));
        assertTrue(HelperTools.isPositionSafe(gameState, new Position(6, 7), emptyMap, playerId));
        assertFalse(HelperTools.isPositionSafe(gameState, new Position(-1, 0), emptyMap, playerId));
        assertFalse(HelperTools.isPositionSafe(gameState, new Position(8, 0), emptyMap, playerId));
    }
}
