package sl.hackathon.server.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;

import static org.junit.jupiter.api.Assertions.*;

class NextTurnHandlerTest {
    private NextTurnHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NextTurnHandlerImpl();
    }

    // ===== BASIC FUNCTIONALITY TESTS =====

    @Test
    void testHandleNextTurnWithValidInput() {
        Unit unit = new Unit("unit-1", "player-1", UnitType.PAWN, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

        // Should not throw exception
        assertDoesNotThrow(() -> handler.handleNextTurn("player-1", gameState));
    }

    @Test
    void testHandleNextTurnWithNullGameState() {
        // Should handle null gracefully
        assertDoesNotThrow(() -> handler.handleNextTurn("player-1", null));
    }

    @Test
    void testHandleNextTurnWithNullPlayerId() {
        Unit unit = new Unit("unit-1", "player-1", UnitType.PAWN, new Position(5, 5));
        GameState gameState = new GameState(new Unit[]{unit}, System.currentTimeMillis());

        // Should handle null player ID gracefully
        assertDoesNotThrow(() -> handler.handleNextTurn(null, gameState));
    }

    @Test
    void testHandleNextTurnWithEmptyGameState() {
        GameState gameState = new GameState(new Unit[]{}, System.currentTimeMillis());

        assertDoesNotThrow(() -> handler.handleNextTurn("player-1", gameState));
    }

    @Test
    void testHandleNextTurnMultipleCalls() {
        Unit unit1 = new Unit("unit-1", "player-1", UnitType.PAWN, new Position(5, 5));
        Unit unit2 = new Unit("unit-2", "player-1", UnitType.PAWN, new Position(6, 6));
        GameState gameState = new GameState(new Unit[]{unit1, unit2}, System.currentTimeMillis());

        // Multiple calls should work without issue
        assertDoesNotThrow(() -> {
            handler.handleNextTurn("player-1", gameState);
            handler.handleNextTurn("player-2", gameState);
            handler.handleNextTurn("player-1", gameState);
        });
    }
}
