package sl.hackathon.server.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.validators.GameEndValidator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {
    private GameEngine gameEngine;
    private GameSettings gameSettings;

    @BeforeEach
    void setUp() {
        gameEngine = new GameEngineImpl();

        // Create test game parameters
        Dimension dimension = new Dimension(10, 10);
        Position[] walls = new Position[]{
            new Position(5, 5),
            new Position(5, 6)
        };
        Position[] baseLocations = new Position[]{
            new Position(1, 1),
            new Position(8, 8)
        };
        gameSettings = new GameSettings(dimension, walls, baseLocations, 5000, 0.3f, false);
    }

    // ===== PLAYER MANAGEMENT TESTS =====

    @Test
    void testAddPlayer() {
        gameEngine.addPlayer("player-1");
        assertTrue(gameEngine.getActivePlayers().contains("player-1"), "Player should be added");
    }

    @Test
    void testAddMultiplePlayers() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        List<String> players = gameEngine.getActivePlayers();
        assertEquals(2, players.size(), "Should have 2 players");
        assertTrue(players.contains("player-1") && players.contains("player-2"));
    }

    @Test
    void testAddDuplicatePlayer() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-1");
        assertEquals(1, gameEngine.getActivePlayers().size(), "Duplicate player should not be added");
    }

    @Test
    void testAddNullPlayer() {
        gameEngine.addPlayer(null);
        assertEquals(0, gameEngine.getActivePlayers().size(), "Null player should not be added");
    }

    @Test
    void testRemovePlayer() {
        gameEngine.initialize(gameSettings);
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.removePlayer("player-1");
        
        List<String> players = gameEngine.getActivePlayers();
        assertEquals(1, players.size(), "Should have 1 player");
        assertTrue(players.contains("player-2"));
    }

    @Test
    void testRemoveNonExistentPlayer() {
        gameEngine.initialize(gameSettings);
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.removePlayer("player-3");
        assertEquals(2, gameEngine.getActivePlayers().size(), "Active player count should not change");
    }

    // ===== INITIALIZATION TESTS =====

    @Test
    void testInitializeGame() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        
        GameState initialState = gameEngine.initialize(gameSettings);
        
        assertTrue(gameEngine.isInitialized(), "Game should be initialized");
        assertNotNull(initialState, "Initial state should not be null");
        assertNotEquals(0, initialState.units().length, "Should have units");
    }

    @Test
    void testInitializeCreatesBaseUnits() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        
        GameState initialState = gameEngine.initialize(gameSettings);
        
        boolean hasPlayerOneBase = false;
        boolean hasPlayerTwoBase = false;
        
        for (Unit unit : initialState.units()) {
            if ("player-1".equals(unit.owner()) && unit.type() == UnitType.BASE) {
                hasPlayerOneBase = true;
            }
            if ("player-2".equals(unit.owner()) && unit.type() == UnitType.BASE) {
                hasPlayerTwoBase = true;
            }
        }
        
        assertTrue(hasPlayerOneBase, "Should create base for player-1");
        assertTrue(hasPlayerTwoBase, "Should create base for player-2");
    }

    @Test
    void testInitializeCreatesPawns() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        
        GameState initialState = gameEngine.initialize(gameSettings);
        
        boolean hasPlayerOnePawn = false;
        boolean hasPlayerTwoPawn = false;
        
        for (Unit unit : initialState.units()) {
            if ("player-1".equals(unit.owner()) && unit.type() == UnitType.PAWN) {
                hasPlayerOnePawn = true;
            }
            if ("player-2".equals(unit.owner()) && unit.type() == UnitType.PAWN) {
                hasPlayerTwoPawn = true;
            }
        }
        
        assertTrue(hasPlayerOnePawn, "Should create pawn for player-1");
        assertTrue(hasPlayerTwoPawn, "Should create pawn for player-2");
    }

    @Test
    void testInitializePlacesUnitsAtBaseLocations() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        
        GameState initialState = gameEngine.initialize(gameSettings);
        
        // Check that player-1 base is at (1, 1)
        Unit player1Base = findUnitById(initialState, 1);
        assertNotNull(player1Base);
        assertEquals(1, player1Base.position().x());
        assertEquals(1, player1Base.position().y());
        
        // Check that player-2 base is at (8, 8)
        Unit player2Base = findUnitById(initialState, 3);
        assertNotNull(player2Base);
        assertEquals(8, player2Base.position().x());
        assertEquals(8, player2Base.position().y());
    }

    // ===== GAME STATE TESTS =====

    @Test
    void testGetGameState() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        GameState state = gameEngine.getGameState();
        assertNotNull(state, "Game state should not be null");
        assertNotEquals(0, state.units().length, "Should have units");
    }

    @Test
    void testGetGameStateHistoryAfterInitialize() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        List<GameDelta> history = gameEngine.getGameDeltaHistory();
        assertEquals(1, history.size(), "History should have initial state");
    }

    @Test
    void testCurrentTurnInitial() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        assertEquals(0, gameEngine.getCurrentTurn(), "Initial turn should be 0");
    }

    // ===== PLAYER ACTIONS TESTS =====

    @Test
    void testHandlePlayerActionsBeforeInitialize() {
        gameEngine.addPlayer("player-1");
        Action[] actions = {new Action(1, Direction.N)};
        
        boolean result = gameEngine.handlePlayerActions("player-1", actions);
        assertFalse(result, "Should return false before initialization");
    }

    @Test
    void testHandlePlayerActionsFromInactivePlayer() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        Action[] actions = {new Action(1, Direction.N)};
        boolean result = gameEngine.handlePlayerActions("player-3", actions);
        
        assertFalse(result, "Should return false for inactive player");
    }

    @Test
    void testHandleValidPlayerActions() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        GameState initialState = gameEngine.getGameState();
        Unit player1Pawn = findUnitById(initialState, 1);
        
        if (player1Pawn != null) {
            Action[] actions = {new Action(player1Pawn.id(), Direction.E)};
            boolean result = gameEngine.handlePlayerActions("player-1", actions);
            
            assertTrue(result, "Should return true for valid actions");
            assertEquals(1, gameEngine.getCurrentTurn(), "Turn should increment");
        }
    }

    @Test
    void testHandleInvalidPlayerActions() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        // Try to move a non-existent unit
        Action[] actions = {new Action(0, Direction.N)};
        boolean result = gameEngine.handlePlayerActions("player-1", actions);
        
        assertFalse(result, "Should return false for invalid actions");
        assertEquals(0, gameEngine.getCurrentTurn(), "Turn should not increment");
    }

    @Test
    void testGameStateHistoryAfterActions() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        GameState initialState = gameEngine.getGameState();
        Unit player1Pawn = findUnitById(initialState, 1);
        
        if (player1Pawn != null) {
            Action[] actions = {new Action(player1Pawn.id(), Direction.E)};
            gameEngine.handlePlayerActions("player-1", actions);
            
            List<GameDelta> history = gameEngine.getGameDeltaHistory();
            assertEquals(2, history.size(), "History should have 2 states");
        }
    }

    // ===== GAME END TESTS =====

    @Test
    void testIsGameEndedInitially() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        assertFalse(gameEngine.isGameEnded(), "Game should not be ended initially");
    }

    @Test
    void testGetWinnerWhenGameNotEnded() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        
        assertNull(GameEndValidator.getWinnerId(gameEngine.getGameState()), "No winner when game is still playing");
    }

    // ===== STATE CONSISTENCY TESTS =====

    @Test
    void testGetActivePlayersAfterOperations() {
        gameEngine.addPlayer("player-1");
        gameEngine.addPlayer("player-2");
        gameEngine.initialize(gameSettings);
        gameEngine.removePlayer("player-1");
        
        List<String> players = gameEngine.getActivePlayers();
        assertEquals(1, players.size());
        assertTrue(players.contains("player-2"));
    }

    @Test
    void testIsInitializedFlag() {
        assertFalse(gameEngine.isInitialized(), "Should not be initialized initially");
        
        gameEngine.addPlayer("player-1");
        assertFalse(gameEngine.isInitialized(), "Should not be initialized without initialize call");
        
        gameEngine.initialize(gameSettings);
        assertTrue(gameEngine.isInitialized(), "Should be initialized after initialize call");
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
