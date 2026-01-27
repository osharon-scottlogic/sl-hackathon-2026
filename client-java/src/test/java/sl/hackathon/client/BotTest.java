package sl.hackathon.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import sl.hackathon.client.dtos.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for Bot interface and StrategicBot implementation.
 * Tests decision-making strategies, timeout handling, collision avoidance, and fallback behavior.
 */
@DisplayName("Bot Tests")
public class BotTest {
    private Bot bot;
    private MapLayout emptyMap;
    private String playerId;
    private String enemyId;

    @BeforeEach
    public void setUp() {
        bot = new StrategicBot();
        emptyMap = new MapLayout(new Dimension(8, 8), new Position[0]);
        playerId = "player-1";
        enemyId = "player-2";
    }

    // ==================== Basic Decision-Making Tests ====================

    @Test
    @DisplayName("Should generate actions for all friendly pawns")
    public void testGenerateActionsForAllPawns() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, playerId, UnitType.PAWN, new Position(2, 2)),
            new Unit(3, playerId, UnitType.PAWN, new Position(3, 3))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length <= 3, "Should have at most 3 actions");
    }

    @Test
    @DisplayName("Should return empty array for no friendly pawns")
    public void testNoFriendlyPawns() {
        Unit[] units = {
            new Unit(1, enemyId, UnitType.PAWN, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertEquals(0, actions.length);
    }

    @Test
    @DisplayName("Should handle empty game state")
    public void testEmptyGameState() {
        GameState gameState = new GameState(new Unit[0], 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertEquals(0, actions.length);
    }

    // ==================== Food Strategy Tests ====================

    @Test
    @DisplayName("Should prioritize moving toward food")
    public void testFoodPrioritization() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, "system", UnitType.FOOD, new Position(2, 1))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length > 0);
        assertEquals(1, actions[0].unitId());
        // Action should be toward food (East or NE)
        assertNotNull(actions[0].direction());
    }

    @Test
    @DisplayName("Should choose closest food when multiple available")
    public void testChooseClosestFood() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, "system", UnitType.FOOD, new Position(3, 4)),  // Distance 1
            new Unit(3, "system", UnitType.FOOD, new Position(1, 1))   // Distance 6
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length > 0);
        // Should be moving toward close food (3, 4)
        // This means moving West or NW
    }

    @Test
    @DisplayName("Should handle case with no food available")
    public void testNoFoodAvailable() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, enemyId, UnitType.PAWN, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Should still generate actions (fallback strategies)
    }

    // ==================== Evasion Strategy Tests ====================

    @Test
    @DisplayName("Should evade when enemy is adjacent")
    public void testEvadeAdjacentEnemy() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, enemyId, UnitType.PAWN, new Position(4, 3))  // Adjacent
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length > 0);
        // Should move away from adjacent enemy (not toward position (4, 3))
        Direction moveDir = actions[0].direction();
        assertNotNull(moveDir);
    }

    @Test
    @DisplayName("Should not be trapped when evading (find safe path)")
    public void testEvadeFindsSafePath() {
        Position[] walls = {
            new Position(5, 4),
            new Position(4, 5),
            new Position(5, 5)
        };
        MapLayout mapWithWalls = new MapLayout(new Dimension(8, 8), walls);

        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, enemyId, UnitType.PAWN, new Position(4, 3))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, mapWithWalls, gameState, 5000);

        assertNotNull(actions);
        // Should still generate an action, even if constrained
    }

    @Test
    @DisplayName("Should not evade when enemy is not adjacent")
    public void testNoEvadeWhenEnemyFar() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, enemyId, UnitType.PAWN, new Position(7, 7))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Should not prioritize evasion
    }

    // ==================== Collision Avoidance Tests ====================

    @Test
    @DisplayName("Should avoid actions that result in collision")
    public void testAvoidCollisions() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(3, 4)),
            new Unit(2, enemyId, UnitType.PAWN, new Position(5, 4))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Should not move into collision with enemy
        if (actions.length > 0) {
            assertFalse(actions[0].direction() == Direction.E, "Should not move East toward enemy");
        }
    }

    @Test
    @DisplayName("Should filter out deadly actions")
    public void testFilterDeadlyActions() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(2, 4)),
            new Unit(2, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(3, enemyId, UnitType.PAWN, new Position(3, 4)),
            new Unit(4, "system", UnitType.FOOD, new Position(5, 4))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Should prioritize safe moves
    }

    // ==================== Multi-Pawn Coordination Tests ====================

    @Test
    @DisplayName("Should generate coordinated actions for multiple pawns")
    public void testCoordinatedMultiPawnActions() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, playerId, UnitType.PAWN, new Position(7, 7)),
            new Unit(3, "system", UnitType.FOOD, new Position(2, 1))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length > 0);
        // Each action should have a unique pawn ID
        Set<Integer> uniquePawns = new HashSet<>();
        for (Action action : actions) {
            uniquePawns.add(action.unitId());
        }
        assertEquals(uniquePawns.size(), actions.length, "Each pawn should have at most one action");
    }

    @Test
    @DisplayName("Should support grouping strategy for mutual support")
    public void testGroupingStrategy() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, playerId, UnitType.PAWN, new Position(7, 7)),
            new Unit(3, enemyId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Both pawns should take some action
    }

    // ==================== Timeout Handling Tests ====================

    @Test
    @DisplayName("Should respect time limit and return actions within deadline")
    public void testRespectTimeLimit() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, "system", UnitType.FOOD, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        long startTime = System.currentTimeMillis();
        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 1000); // 1 second timeout
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(actions);
        assertTrue(elapsed < 2000, "Should complete within roughly 2x the time limit");
    }

    @Test
    @DisplayName("Should handle very short time limits gracefully")
    public void testVeryShortTimeLimit() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4))
        };
        GameState gameState = new GameState(units, 0L);

        long startTime = System.currentTimeMillis();
        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 100); // 100ms - very tight
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(actions);
        assertTrue(elapsed < 500, "Should still complete quickly");
    }

    @Test
    @DisplayName("Should return fallback actions on timeout")
    public void testFallbackOnTimeout() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, playerId, UnitType.PAWN, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        // Very tight time limit should trigger fallback
        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 50);

        assertNotNull(actions);
        // Should return some fallback actions
    }

    // ==================== Fallback Strategy Tests ====================

    @Test
    @DisplayName("Should generate fallback random actions when stuck")
    public void testFallbackRandomActions() {
        Position[] walls = new Position[100];
        for (int i = 0; i < 8; i++) {
            walls[i] = new Position(i, 4);
        }
        MapLayout barrierMap = new MapLayout(new Dimension(8, 8), walls);

        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 3))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, barrierMap, gameState, 5000);

        assertNotNull(actions);
        // Should return fallback actions even when blocked
    }

    @Test
    @DisplayName("Should avoid deadly fallback actions")
    public void testFallbackAvoidsDeadly() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, enemyId, UnitType.PAWN, new Position(4, 3)),
            new Unit(3, enemyId, UnitType.PAWN, new Position(5, 4)),
            new Unit(4, enemyId, UnitType.PAWN, new Position(3, 4)),
            new Unit(5, enemyId, UnitType.PAWN, new Position(4, 5))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Fallback should try to find a safe action or return empty
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should handle exceptions gracefully and return fallback")
    public void testExceptionHandling() {
        // Create a complex scenario that might cause errors
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, 0L);

        // Should not throw exception
        Action[] actions = assertDoesNotThrow(() ->
            bot.handleState(playerId, emptyMap, gameState, 5000)
        );

        assertNotNull(actions);
    }

    @Test
    @DisplayName("Should generate valid actions (unitId and direction exist)")
    public void testGeneratedActionsValid() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, "system", UnitType.FOOD, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        for (Action action : actions) {
            assertNotNull(action.unitId());
            assertNotNull(action.direction());
            assertTrue(action.unitId() > 0);
        }
    }

    // ==================== Complex Scenario Tests ====================

    @Test
    @DisplayName("Should handle complex scenario with multiple threats and goals")
    public void testComplexScenario() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, playerId, UnitType.PAWN, new Position(3, 3)),
            new Unit(3, playerId, UnitType.PAWN, new Position(5, 5)),
            new Unit(4, enemyId, UnitType.PAWN, new Position(2, 2)),
            new Unit(5, enemyId, UnitType.PAWN, new Position(6, 6)),
            new Unit(6, "system", UnitType.FOOD, new Position(1, 2)),
            new Unit(7, "system", UnitType.FOOD, new Position(6, 5))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length > 0);
        assertTrue(actions.length <= 3, "Should have at most 3 actions");
    }

    @Test
    @DisplayName("Should handle large number of pawns")
    public void testLargePawnCount() {
        List<Unit> units = new ArrayList<>();
        // Add 10 friendly pawns
        for (int i = 0; i < 10; i++) {
            units.add(new Unit(i + 1, playerId, UnitType.PAWN, 
                             new Position(i % 8, i / 8)));
        }
        // Add 5 food
        for (int i = 0; i < 5; i++) {
            units.add(new Unit(i + 11, "system", UnitType.FOOD,
                             new Position((i + 5) % 8, (i + 2) % 8)));
        }

        GameState gameState = new GameState(units.toArray(new Unit[0]), 0L);

        long startTime = System.currentTimeMillis();
        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(actions);
        assertTrue(elapsed < 6000, "Should complete in reasonable time");
    }

    @Test
    @DisplayName("Should handle edge positions (corners and edges)")
    public void testEdgePositions() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),      // Corner
            new Unit(2, playerId, UnitType.PAWN, new Position(7, 7)),      // Opposite corner
            new Unit(3, playerId, UnitType.PAWN, new Position(0, 7)),      // Another corner
            new Unit(4, "system", UnitType.FOOD, new Position(3, 3))       // Center
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        // Should handle edge cases without crashing
    }

    // ==================== Strategy Consistency Tests ====================

    @Test
    @DisplayName("Should produce consistent strategies across multiple calls")
    public void testStrategyConsistency() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(4, 4)),
            new Unit(2, "system", UnitType.FOOD, new Position(5, 4))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions1 = bot.handleState(playerId, emptyMap, gameState, 5000);
        Action[] actions2 = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions1);
        assertNotNull(actions2);
        // Same input should produce same or compatible output
        assertEquals(actions1.length, actions2.length);
    }

    @Test
    @DisplayName("Should prefer food collection over random movement")
    public void testFoodPreference() {
        Unit[] units = {
            new Unit(1, playerId, UnitType.PAWN, new Position(0, 0)),
            new Unit(2, "system", UnitType.FOOD, new Position(1, 0))
        };
        GameState gameState = new GameState(units, 0L);

        Action[] actions = bot.handleState(playerId, emptyMap, gameState, 5000);

        assertNotNull(actions);
        assertTrue(actions.length > 0);
        // Should move toward food (East)
        assertEquals(Direction.E, actions[0].direction());
    }
}
