package sl.hackathon.server.engine;

import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.validators.GameEndValidator;

import static org.junit.jupiter.api.Assertions.*;

class GameStatusUpdaterTest {
    // ===== BASIC MOVEMENT TESTS =====

    @Test
    void testSimpleMovement() {
        Unit unit = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

        Action action = new Action(1, Direction.N);
        GameState newState = GameStatusUpdater.update(gameState, "player-1", new Action[]{action}, new UnitGenerator());

        assertEquals(1, newState.units().length, "Should have 1 unit");
        assertEquals(5, newState.units()[0].position().x(), "X position should remain 5");
        assertEquals(4, newState.units()[0].position().y(), "Y position should be 4 (moved north)");
    }

    @Test
    void testMultipleUnitsMovement() {
        Unit unit1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit unit2 = new Unit(2, "player-1", UnitType.PAWN, new Position(10, 10));
        GameState gameState = new GameState(new Unit[]{unit1, unit2}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.E),
            new Action(2, Direction.S)
        };
        GameState newState = GameStatusUpdater.update(gameState, "player-1", actions, new UnitGenerator());

        assertEquals(2, newState.units().length, "Should have 2 units");
        Unit u1 = findUnitById(newState, 1);
        Unit u2 = findUnitById(newState, 2);

        assertNotNull(u1);
        assertEquals(6, u1.position().x(), "Unit-1 X should be 6 (moved east)");
        assertEquals(5, u1.position().y(), "Unit-1 Y should be 5");
        assertNotNull(u2);
        assertEquals(10, u2.position().x(), "Unit-2 X should be 10");
        assertEquals(11, u2.position().y(), "Unit-2 Y should be 11 (moved south)");
    }

    @Test
    void testAllDirections() {
        Position startPos = new Position(5, 5);

        Direction[] directions = {
            Direction.N,  // (5, 4)
            Direction.NE, // (6, 4)
            Direction.E,  // (6, 5)
            Direction.SE, // (6, 6)
            Direction.S,  // (5, 6)
            Direction.SW, // (4, 6)
            Direction.W,  // (4, 5)
            Direction.NW  // (4, 4)
        };

        int[][] expectedPositions = {
            {5, 4}, {6, 4}, {6, 5}, {6, 6},
            {5, 6}, {4, 6}, {4, 5}, {4, 4}
        };

        for (int i = 0; i < directions.length; i++) {
            Unit unit = new Unit(1, "player-1", UnitType.PAWN, startPos);
            GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

            Action action = new Action(1, directions[i]);
            GameState newState = GameStatusUpdater.update(gameState, "player-1", new Action[]{action}, new UnitGenerator());

            Unit moved = newState.units()[0];
            assertEquals(expectedPositions[i][0], moved.position().x(),
                "Direction " + directions[i] + " should have X=" + expectedPositions[i][0]);
            assertEquals(expectedPositions[i][1], moved.position().y(),
                "Direction " + directions[i] + " should have Y=" + expectedPositions[i][1]);
        }
    }

    // ===== NO ACTION TESTS =====

    @Test
    void testNoActions() {
        Unit unit = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

        GameState newState = GameStatusUpdater.update(gameState, "player-1", new Action[]{}, new UnitGenerator());

        assertEquals(1, newState.units().length, "Should still have 1 unit");
        assertEquals(5, newState.units()[0].position().x(), "X should not change");
        assertEquals(5, newState.units()[0].position().y(), "Y should not change");
    }

    @Test
    void testNullActions() {
        Unit unit = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

        GameState newState = GameStatusUpdater.update(gameState, "player-1", null, null);

        assertEquals(1, newState.units().length, "Should still have 1 unit");
        assertEquals(5, newState.units()[0].position().x(), "X should not change");
        assertEquals(5, newState.units()[0].position().y(), "Y should not change");
    }

    @Test
    void testActionForNonExistentUnit() {
        Unit unit = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

        Action action = new Action(999, Direction.N);
        GameState newState = GameStatusUpdater.update(gameState,"player-1",  new Action[]{action}, null);

        assertEquals(1, newState.units().length, "Should still have 1 unit");
        Unit moved = newState.units()[0];
        assertEquals(5, moved.position().x(), "X should not change");
        assertEquals(5, moved.position().y(), "Y should not change");
    }

    // ===== ENEMY PAWN COLLISION TESTS =====

    @Test
    void testEnemyPawnsCollide() {
        // Two enemy pawns move to the same position
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-2", UnitType.PAWN, new Position(7, 5));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.E), // move to (6, 5)
            new Action(2, Direction.W)  // move to (6, 5)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, null);
        assertEquals(0, newState.units().length, "Both enemy pawns should die in collision");
    }

    @Test
    void testEnemyPawnsCollideOnOneSquare() {
        // Both pawns move to same square from different directions
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-2", UnitType.PAWN, new Position(5, 7));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.S), // move to (5, 6)
            new Action(2, Direction.N)  // move to (5, 6)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, null);
        assertEquals(0, newState.units().length, "Both enemy pawns should die in collision");
    }

    // ===== FRIENDLY PAWN COLLISION TESTS =====

    @Test
    void testFriendlyPawnsCollide() {
        // Two friendly pawns move to the same position
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-1", UnitType.PAWN, new Position(7, 5));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.E), // move to (6, 5)
            new Action(2, Direction.W)  // move to (6, 5)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, null);
        assertEquals(2, newState.units().length, "Friendly pawns should survive collision");
        for (Unit unit : newState.units()) {
            assertEquals(6, unit.position().x(), "Both should be at position (6, 5)");
            assertEquals(5, unit.position().y(), "Both should be at position (6, 5)");
        }
    }

    @Test
    void testMultipleFriendlyPawnsCollide() {
        // Three friendly pawns converge on same position
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-1", UnitType.PAWN, new Position(7, 5));
        Unit pawn3 = new Unit(3, "player-1", UnitType.PAWN, new Position(6, 4));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2, pawn3}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.E), // move to (6, 5)
            new Action(2, Direction.W), // move to (6, 5)
            new Action(3, Direction.S)  // move to (6, 5)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, null);
        assertEquals(3, newState.units().length, "All friendly pawns should survive");
        for (Unit unit : newState.units()) {
            assertEquals(6, unit.position().x(), "All should be at position (6, 5)");
            assertEquals(5, unit.position().y(), "All should be at position (6, 5)");
        }
    }

    // ===== PAWN + FOOD COLLISION TESTS =====

    @Test
    void testPawnEatsFood() {
        Unit pawn = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit base1 = new Unit(2, "player-1", UnitType.BASE, new Position(1, 1));
        Unit food = new Unit(3, "none", UnitType.FOOD, new Position(6, 5));
        GameState gameState = new GameState(new Unit[]{pawn, food, base1}, System.currentTimeMillis());

        Action action = new Action(1, Direction.E);
        GameState newState = GameStatusUpdater.update(gameState,"player-1",  new Action[]{action}, new UnitGenerator());

        assertEquals(3, newState.units().length, "Pawn should remain, food should be consumed");
        assertEquals(1, newState.units()[0].id(), "Pawn should survive");
        assertEquals(6, newState.units()[0].position().x(), "Pawn should be at food position");
        assertEquals(5, newState.units()[0].position().y(), "Pawn should be at food position");
    }

    @Test
    void testMultiplePawnsEatFood() {
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-2", UnitType.PAWN, new Position(5, 7));
        Unit base1 = new Unit(3, "player-1", UnitType.BASE, new Position(1, 1));
        Unit base2 = new Unit(4, "player-2", UnitType.BASE, new Position(2, 2));
        Unit food = new Unit(5, "none", UnitType.FOOD, new Position(5, 6));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2, food, base1,base2}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.S), // move to (5, 6)
            new Action(2, Direction.N)  // move to (5, 6)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, new UnitGenerator());
        // When multiple pawns and food collide, food is consumed and pawns collide
        // Friendly behavior: if same owner they survive; if different owner they die
        assertEquals(3, newState.units().length, "Enemy pawns with food should all die");
    }

    // ===== PAWN + BASE COLLISION TESTS =====

    @Test
    void testPawnDestroysEnemyBase() {
        Unit pawn = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit base = new Unit(2, "player-2", UnitType.BASE, new Position(6, 5));
        GameState gameState = new GameState(new Unit[]{pawn, base}, System.currentTimeMillis());

        Action action = new Action(1, Direction.E);
        GameState newState = GameStatusUpdater.update(gameState,"player-1",  new Action[]{action}, null);

        assertEquals(0, newState.units().length, "Both pawn and base should be destroyed");
    }

    @Test
    void testMultiplePawnsDestroySingleBase() {
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-1", UnitType.PAWN, new Position(5, 7));
        Unit base = new Unit(3, "player-2", UnitType.BASE, new Position(5, 6));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2, base}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.S), // move to (5, 6)
            new Action(2, Direction.N)  // move to (5, 6)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, null);
        assertEquals(0, newState.units().length, "All pawns and base should be destroyed");
    }

    // ===== GAME END TESTS =====

    @Test
    void testGameNotEndedWithBothPlayers() {
        Unit base1 = new Unit(1, "player-1", UnitType.BASE, new Position(0, 0));
        Unit pawn1 = new Unit(2, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit base2 = new Unit(3, "player-2", UnitType.BASE, new Position(9, 9));
        Unit pawn2 = new Unit(4, "player-2", UnitType.PAWN, new Position(8, 8));
        GameState gameState = new GameState(new Unit[]{base1, pawn1, base2, pawn2}, System.currentTimeMillis());

        assertFalse(GameEndValidator.hasGameEnded(gameState), "Game should not end with both players having units");
    }

    @Test
    void testGameEndedWhenPlayerHasNoBase() {
        Unit base1 = new Unit(1, "player-1", UnitType.BASE, new Position(0, 0));
        Unit pawn1 = new Unit(2, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(3, "player-2", UnitType.PAWN, new Position(8, 8));
        GameState gameState = new GameState(new Unit[]{base1, pawn1, pawn2}, System.currentTimeMillis());

        assertTrue(GameEndValidator.hasGameEnded(gameState), "Game should end when a player has no BASE");
    }

    @Test
    void testGameEndedWhenPlayerHasNoPawns() {
        Unit base1 = new Unit(1, "player-1", UnitType.BASE, new Position(0, 0));
        Unit pawn1 = new Unit(2, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit base2 = new Unit(3, "player-2", UnitType.BASE, new Position(9, 9));
        GameState gameState = new GameState(new Unit[]{base1, pawn1, base2}, System.currentTimeMillis());

        assertTrue(GameEndValidator.hasGameEnded(gameState), "Game should end when a player has no PAWN units");
    }

    @Test
    void testGameEndedWithOnePlayer() {
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit base1 = new Unit(2, "player-1", UnitType.BASE, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{pawn1,base1}, System.currentTimeMillis());

        assertTrue(GameEndValidator.hasGameEnded(gameState), "Game should end with only one player");
    }

    @Test
    void testGameEndedWithNoUnits() {
        GameState gameState = new GameState(new Unit[]{}, System.currentTimeMillis());

        assertTrue(GameEndValidator.hasGameEnded(gameState), "Game should end with no units");
    }

    // ===== WINNER TESTS =====

    @Test
    void testGetWinnerWhenGameEnded() {
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit base1 = new Unit(2, "player-1", UnitType.BASE, new Position(5, 5));
        Unit base2 = new Unit(3, "player-1", UnitType.BASE, new Position(0, 0));
        GameState gameState = new GameState(new Unit[]{pawn1, base1, base2}, System.currentTimeMillis());

        String winner = GameEndValidator.getWinnerId(gameState);
        assertEquals("player-1", winner, "Player-1 should be the winner");
    }

    @Test
    void testGetWinnerWhenGameNotEnded() {
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-2", UnitType.PAWN, new Position(10, 10));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2}, System.currentTimeMillis());

        String winner = GameEndValidator.getWinnerId(gameState);
        assertNull(winner, "No winner when game is still playing");
    }

    @Test
    void testGetWinnerWhenNoUnits() {
        GameState gameState = new GameState(new Unit[]{}, System.currentTimeMillis());

        String winner = GameEndValidator.getWinnerId(gameState);
        assertNull(winner, "No winner when no units remain");
    }

    // ===== COMPLEX SCENARIO TESTS =====

    @Test
    void testComplexCollisionScenario() {
        // Player 1: pawn1, Player 2: pawn2, pawn3, Food: food1
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-2", UnitType.PAWN, new Position(7, 5));
        Unit pawn3 = new Unit(3, "player-2", UnitType.PAWN, new Position(6, 4));
        Unit base1 = new Unit(4, "player-1", UnitType.BASE, new Position(1, 1));
        Unit base2 = new Unit(5, "player-2", UnitType.BASE, new Position(2, 2));
        Unit base3 = new Unit(6, "player-3", UnitType.BASE, new Position(3, 3));

        Unit food = new Unit(7, "none", UnitType.FOOD, new Position(6, 5));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2, pawn3, food,base1,base2,base3}, System.currentTimeMillis());

        Action[] actions = {
            new Action(1, Direction.E), // move to (6, 5)
            new Action(2, Direction.W), // move to (6, 5)
            new Action(3, Direction.S)  // move to (6, 5)
        };

        GameState newState = GameStatusUpdater.update(gameState,"player-1",  actions, new UnitGenerator());
        // Food is consumed, but enemy pawns collide -> all die
        assertEquals(4, newState.units().length, "All units should be removed after collision");
    }

    @Test
    void testUnitPreservation() {
        // Multiple units, only some move
        Unit pawn1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit pawn2 = new Unit(2, "player-1", UnitType.PAWN, new Position(10, 10));
        GameState gameState = new GameState(new Unit[]{pawn1, pawn2}, System.currentTimeMillis());

        Action action = new Action(1, Direction.N);
        GameState newState = GameStatusUpdater.update(gameState,"player-1",  new Action[]{action}, null);

        assertEquals(2, newState.units().length, "Both units should remain");

        Unit u1 = findUnitById(newState, 1);
        Unit u2 = findUnitById(newState, 2);

        assertNotNull(u1);
        assertEquals(5, u1.position().x(), "Pawn-1 X should be 5");
        assertEquals(4, u1.position().y(), "Pawn-1 Y should be 4 (moved north)");
        assertNotNull(u2);
        assertEquals(10, u2.position().x(), "Pawn-2 X should remain 10");
        assertEquals(10, u2.position().y(), "Pawn-2 Y should remain 10");
    }

    // ===== DELTA GENERATION TESTS =====

    @Test
    void testGenerateDelta_UnitsAdded() {
        Unit unit1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState previousState = new GameState(new Unit[]{unit1}, System.currentTimeMillis());

        Unit unit2 = new Unit(2, "player-1", UnitType.PAWN, new Position(6, 6));
        GameState newState = new GameState(new Unit[]{unit1, unit2}, System.currentTimeMillis());

        GameDelta delta = GameDeltaFactory.get(previousState, newState);

        assertEquals(1, delta.addedOrModified().length, "Should have 1 added unit");
        assertEquals(2, delta.addedOrModified()[0].id(), "Added unit should have ID 2");
        assertEquals(0, delta.removed().length, "No units removed");
    }

    @Test
    void testGenerateDelta_UnitsRemoved() {
        Unit unit1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit unit2 = new Unit(2, "player-2", UnitType.PAWN, new Position(6, 6));
        GameState previousState = new GameState(new Unit[]{unit1, unit2}, System.currentTimeMillis());

        GameState newState = new GameState(new Unit[]{unit1}, System.currentTimeMillis());

        GameDelta delta = GameDeltaFactory.get(previousState, newState);

        assertEquals(0, delta.addedOrModified().length, "No units added or modified");
        assertEquals(1, delta.removed().length, "Should have 1 removed unit");
        assertEquals(2, delta.removed()[0], "Removed unit ID should be 2");
    }

    @Test
    void testGenerateDelta_UnitsModified() {
        Unit unit1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState previousState = new GameState(new Unit[]{unit1}, System.currentTimeMillis());

        Unit unit1Moved = new Unit(1, "player-1", UnitType.PAWN, new Position(6, 5));
        GameState newState = new GameState(new Unit[]{unit1Moved}, System.currentTimeMillis());

        GameDelta delta = GameDeltaFactory.get(previousState, newState);

        assertEquals(1, delta.addedOrModified().length, "Should have 1 modified unit");
        assertEquals(1, delta.addedOrModified()[0].id());
        assertEquals(6, delta.addedOrModified()[0].position().x(), "Position should be updated");
        assertEquals(0, delta.removed().length, "No units removed");
    }

    @Test
    void testGenerateDelta_NoChanges() {
        Unit unit1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        GameState previousState = new GameState(new Unit[]{unit1}, System.currentTimeMillis());
        GameState newState = new GameState(new Unit[]{unit1}, System.currentTimeMillis());

        GameDelta delta = GameDeltaFactory.get(previousState, newState);

        assertEquals(0, delta.addedOrModified().length, "No units changed");
        assertEquals(0, delta.removed().length, "No units removed");
    }

    @Test
    void testGenerateDelta_ComplexScenario() {
        // Previous state: 3 units
        Unit unit1 = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));
        Unit unit2 = new Unit(2, "player-1", UnitType.PAWN, new Position(6, 6));
        Unit unit3 = new Unit(3, "player-2", UnitType.PAWN, new Position(7, 7));
        GameState previousState = new GameState(new Unit[]{unit1, unit2, unit3}, System.currentTimeMillis());

        // New state: unit1 moved, unit2 unchanged, unit3 removed, unit4 added
        Unit unit1Moved = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 6));
        Unit unit4 = new Unit(4, "player-1", UnitType.PAWN, new Position(8, 8));
        GameState newState = new GameState(new Unit[]{unit1Moved, unit2, unit4}, System.currentTimeMillis());

        GameDelta delta = GameDeltaFactory.get(previousState, newState);

        assertEquals(2, delta.addedOrModified().length, "Should have 2 added/modified units (1 moved, 1 added)");
        assertEquals(1, delta.removed().length, "Should have 1 removed unit");
        assertEquals(3, delta.removed()[0], "Unit 3 should be removed");
        
        // Verify added/modified contains both unit1 (moved) and unit4 (added)
        boolean hasUnit1 = false, hasUnit4 = false;
        for (Unit u : delta.addedOrModified()) {
            if (u.id() == 1) hasUnit1 = true;
            if (u.id() == 4) hasUnit4 = true;
        }
        assertTrue(hasUnit1, "Delta should contain modified unit1");
        assertTrue(hasUnit4, "Delta should contain added unit4");
    }

    // ===== HELPER METHODS =====

    private Unit findUnitById(GameState gameState, int unitId) {
        for (Unit unit : gameState.units()) {
            if (unitId == unit.id()) {
                return unit;
            }
        }
        return null;
    }
}
