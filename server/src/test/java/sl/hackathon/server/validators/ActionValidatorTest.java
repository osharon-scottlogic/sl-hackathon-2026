package sl.hackathon.server.validators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionValidatorTest {
    private ActionValidator validator;
    private GameState gameState;

    @BeforeEach
    void setUp() {
        validator = new ActionValidatorImpl();

        // Create a sample game state with units
        Unit unit1 = new Unit("unit-1", "player-1", UnitType.PAWN, new Position(5, 5));
        Unit unit2 = new Unit("unit-2", "player-1", UnitType.BASE, new Position(10, 10));
        Unit unit3 = new Unit("unit-3", "player-2", UnitType.PAWN, new Position(3, 3));
        Unit unit4 = new Unit("unit-4", "player-2", UnitType.FOOD, new Position(7, 7));

        gameState = new GameState(new Unit[]{unit1, unit2, unit3, unit4}, System.currentTimeMillis());
    }

    // ===== VALID ACTION TESTS =====

    @Test
    void testValidAction() {
        Action action = new Action("unit-1", Direction.N);
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{action});
        assertTrue(invalidActions.isEmpty(), "Valid action should not be invalid");
    }

    @Test
    void testValidActionsMultiple() {
        Action[] actions = {
            new Action("unit-1", Direction.N),
            new Action("unit-2", Direction.E)
        };
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", actions);
        assertTrue(invalidActions.isEmpty(), "All valid actions should not be invalid");
    }

    @Test
    void testValidActionDifferentDirections() {
        Direction[] directions = {Direction.N, Direction.NE, Direction.E, Direction.SE, Direction.S, Direction.SW, Direction.W, Direction.NW};
        for (Direction dir : directions) {
            Action action = new Action("unit-1", dir);
            List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{action});
            assertTrue(invalidActions.isEmpty(), "Valid action with direction " + dir + " should be valid");
        }
    }

    // ===== UNIT NOT FOUND TESTS =====

    @Test
    void testActionWithNonExistentUnitId() {
        Action action = new Action("non-existent-unit", Direction.N);
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{action});
        assertEquals(1, invalidActions.size(), "Should have one invalid action");
        assertEquals("Unit with ID 'non-existent-unit' does not exist", invalidActions.getFirst().reason());
    }

    @Test
    void testActionWithNullUnitId() {
        Action action = new Action(null, Direction.N);
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{action});
        assertEquals(1, invalidActions.size(), "Should have one invalid action");
        assertEquals("Unit ID cannot be null", invalidActions.getFirst().reason());
    }

    // ===== WRONG OWNER TESTS =====

    @Test
    void testActionWithWrongOwner() {
        Action action = new Action("unit-1", Direction.N); // unit-1 belongs to player-1
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-2", new Action[]{action});
        assertEquals(1, invalidActions.size(), "Should have one invalid action");
        assertEquals("Unit 'unit-1' does not belong to player 'player-2'", invalidActions.getFirst().reason());
    }

    @Test
    void testActionWithWrongOwnerMultiple() {
        Action[] actions = {
            new Action("unit-1", Direction.N), // belongs to player-1
            new Action("unit-2", Direction.E)  // belongs to player-1
        };
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-2", actions);
        assertEquals(2, invalidActions.size(), "Both actions should be invalid");
        assertTrue(invalidActions.stream().allMatch(ia -> ia.reason().contains("does not belong to player 'player-2'")));
    }

    @Test
    void testActionWithCorrectOwnerPlayer2() {
        Action action = new Action("unit-3", Direction.S); // unit-3 belongs to player-2
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-2", new Action[]{action});
        assertTrue(invalidActions.isEmpty(), "Player-2 should be able to control unit-3");
    }

    // ===== NULL AND EMPTY TESTS =====

    @Test
    void testValidateNullActions() {
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", null);
        assertTrue(invalidActions.isEmpty(), "Null actions should result in empty invalid list");
    }

    @Test
    void testValidateEmptyActions() {
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{});
        assertTrue(invalidActions.isEmpty(), "Empty actions should result in empty invalid list");
    }

    @Test
    void testValidateWithNullAction() {
        Action[] actions = {null};
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", actions);
        assertEquals(1, invalidActions.size(), "Should have one invalid action");
        assertEquals("Action cannot be null", invalidActions.getFirst().reason());
    }

    // ===== MIXED VALID/INVALID TESTS =====

    @Test
    void testMixedValidAndInvalidActions() {
        Action[] actions = {
            new Action("unit-1", Direction.N),      // valid
            new Action("non-existent", Direction.E), // invalid
            new Action("unit-2", Direction.S)       // valid
        };
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", actions);
        assertEquals(1, invalidActions.size(), "Should have one invalid action");
        assertEquals("Unit with ID 'non-existent' does not exist", invalidActions.getFirst().reason());
    }

    @Test
    void testMultipleInvalidReasonsInSingleBatch() {
        Action[] actions = {
            new Action("non-existent-1", Direction.N), // unit doesn't exist
            new Action("unit-3", Direction.E),          // wrong owner
            new Action(null, Direction.S)               // null unit id
        };
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", actions);
        assertEquals(3, invalidActions.size(), "Should have three invalid actions");

        // Check each reason
        boolean hasNonExistentReason = invalidActions.stream().anyMatch(ia -> ia.reason().contains("does not exist"));
        boolean hasWrongOwnerReason = invalidActions.stream().anyMatch(ia -> ia.reason().contains("does not belong"));
        boolean hasNullIdReason = invalidActions.stream().anyMatch(ia -> ia.reason().contains("Unit ID cannot be null"));

        assertTrue(hasNonExistentReason, "Should have non-existent unit reason");
        assertTrue(hasWrongOwnerReason, "Should have wrong owner reason");
        assertTrue(hasNullIdReason, "Should have null ID reason");
    }

    // ===== GAME STATE PRESERVATION TESTS =====

    @Test
    void testInvalidActionContainsCorrectGameState() {
        Action action = new Action("non-existent-unit", Direction.N);
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{action});
        assertEquals(1, invalidActions.size());

        InvalidAction invalidAction = invalidActions.getFirst();
        assertEquals(gameState, invalidAction.state(), "Invalid action should contain the game state");
    }

    @Test
    void testInvalidActionPreservesActionData() {
        Action action = new Action("non-existent-unit", Direction.NE);
        List<InvalidAction> invalidActions = validator.validate(gameState, "player-1", new Action[]{action});
        assertEquals(1, invalidActions.size());

        InvalidAction invalidAction = invalidActions.getFirst();
        assertEquals(action, invalidAction.action(), "Invalid action should preserve the action data");
        assertEquals("non-existent-unit", invalidAction.action().unitId());
        assertEquals(Direction.NE, invalidAction.action().direction());
    }
}
