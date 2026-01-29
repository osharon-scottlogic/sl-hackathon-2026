package sl.hackathon.server.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import sl.hackathon.server.communication.ClientRegistry;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameSession.
 * Test coverage:
 * 1. Constructor validation
 * 2. Turn progression
 * 3. Timeout handling
 * 4. Game end detection
 * 5. Action submission
 * 6. Graceful shutdown
 */
class GameSessionTest {
    
    private GameEngine mockEngine;
    private ClientRegistry mockRegistry;
    private GameParams gameParams;
    private GameSession gameSession;
    
    private static final String PLAYER_1 = "player-1";
    private static final String PLAYER_2 = "player-2";
    
    @BeforeEach
    void setUp() {
        mockEngine = mock(GameEngine.class);
        mockRegistry = mock(ClientRegistry.class);
        
        // Create test game params
        Dimension dimension = new Dimension(10, 10);
        Position[] walls = new Position[]{new Position(5, 5)};
        MapLayout mapLayout = new MapLayout(dimension, walls);
        Position[] baseLocations = new Position[]{new Position(1, 1), new Position(9, 9)};
        MapConfig mapConfig = new MapConfig(dimension, walls, baseLocations);
        
        gameParams = new GameParams(mapConfig, 5000L, 0.3f);
    }
    
    @Test
    void constructor_WithNullEngine_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GameSession(null, mockRegistry, gameParams));
    }
    
    @Test
    void constructor_WithNullRegistry_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GameSession(mockEngine, null, gameParams));
    }
    
    @Test
    void constructor_WithNullParams_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GameSession(mockEngine, mockRegistry, null));
    }
    
    @Test
    void constructor_WithValidParams_CreatesInstance() {
        gameSession = new GameSession(mockEngine, mockRegistry, gameParams);
        assertNotNull(gameSession);
        assertFalse(gameSession.isGameStarted());
        assertEquals(0, gameSession.getCurrentTurnId());
    }
    
    @Test
    @Timeout(5)
    void run_WaitsForPlayersAndInitializesGame() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(false, false, true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState initialState = new GameState(units, System.currentTimeMillis());
        when(mockEngine.initialize(gameParams)).thenReturn(initialState);
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.getGameState()).thenReturn(initialState);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], initialState.startAt())));
        when(mockEngine.getWinnerId()).thenReturn(PLAYER_1);
        
        gameSession = new GameSession(mockEngine, mockRegistry, gameParams);
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        sessionThread.join(3000);
        
        // Assert
        verify(mockEngine).initialize(gameParams);
        verify(mockRegistry, atLeastOnce()).broadcast(any(StartGameMessage.class));
        assertTrue(gameSession.isGameStarted());
    }
    
    @Test
    @Timeout(5)
    void run_ProcessesTurnsUntilGameEnds() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        when(mockEngine.initialize(any())).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        
        // Game ends after 3 turns
        when(mockEngine.isGameEnded()).thenReturn(false, false, false, true);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));
        when(mockEngine.getWinnerId()).thenReturn(PLAYER_1);
        
        // Use very short timeout for quick test execution (3 turns * 200ms = 600ms max)
        GameParams shortTimeoutParams = new GameParams(gameParams.mapConfig(), 200L, 0.3f);
        gameSession = new GameSession(mockEngine, mockRegistry, shortTimeoutParams);
        
        // Act
        CountDownLatch startLatch = new CountDownLatch(1);
        Thread sessionThread = new Thread(() -> {
            gameSession.run();
            startLatch.countDown();
        });
        sessionThread.start();
        
        // Wait for game to complete (3 turns * 200ms timeout + overhead = ~1s max)
        assertTrue(startLatch.await(2, TimeUnit.SECONDS));
        
        // Assert - verify turn messages were sent (3 turns * 2 players = 6 messages minimum)
        verify(mockRegistry, atLeast(3)).send(eq(PLAYER_1), any(NextTurnMessage.class));
        verify(mockRegistry, atLeast(3)).send(eq(PLAYER_2), any(NextTurnMessage.class));
        verify(mockRegistry).broadcast(any(EndGameMessage.class));
    }
    
    @Test
    @Timeout(5)
    void submitAction_DuringValidTurn_ProcessesActions() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        when(mockEngine.initialize(gameParams)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.isGameEnded()).thenReturn(false, true);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));
        when(mockEngine.getWinnerId()).thenReturn(null);
        
        gameSession = new GameSession(mockEngine, mockRegistry, gameParams);
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        
        // Wait for game to start
        Thread.sleep(200);
        
        // Submit actions for both players
        Action[] player1Actions = new Action[]{new Action(1, Direction.N)};
        Action[] player2Actions = new Action[]{};
        
        gameSession.submitAction(PLAYER_1, 0, player1Actions);
        gameSession.submitAction(PLAYER_2, 0, player2Actions);
        
        sessionThread.join(3000);
        
        // Assert
        verify(mockEngine).handlePlayerActions(PLAYER_1, player1Actions);
        verify(mockEngine).handlePlayerActions(PLAYER_2, player2Actions);
    }
    
    @Test
    void submitAction_WithMismatchedTurnId_IgnoresAction() {
        // Arrange
        gameSession = new GameSession(mockEngine, mockRegistry, gameParams);
        Action[] actions = new Action[]{new Action(1, Direction.N)};
        
        // Act - submit for turn 5 when current turn is 0
        gameSession.submitAction(PLAYER_1, 5, actions);
        
        // Assert - current turn should still be 0
        assertEquals(0, gameSession.getCurrentTurnId());
    }
    
    @Test
    @Timeout(5)
    void run_WithTimeout_ProcessesPartialActions() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        
        // Use very short timeout
        GameParams shortTimeoutParams = new GameParams(gameParams.mapConfig(), 100L, 0.3f);
        
        when(mockEngine.initialize(shortTimeoutParams)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.isGameEnded()).thenReturn(false, true);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));
        when(mockEngine.getWinnerId()).thenReturn(null);
        
        gameSession = new GameSession(mockEngine, mockRegistry, shortTimeoutParams);
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        
        // Wait for game to start
        Thread.sleep(200);
        
        // Only player 1 submits (player 2 times out)
        Action[] player1Actions = new Action[]{new Action(1, Direction.N)};
        gameSession.submitAction(PLAYER_1, 0, player1Actions);
        // Player 2 doesn't submit - timeout expected
        
        sessionThread.join(3000);
        
        // Assert - both players should be processed, player 2 with empty actions
        verify(mockEngine).initialize(eq(shortTimeoutParams));
        verify(mockEngine).handlePlayerActions(eq(PLAYER_1), any());
        verify(mockEngine).handlePlayerActions(eq(PLAYER_2), any());
    }
    
    @Test
    @Timeout(5)
    void shutdown_StopsGameLoop() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        when(mockEngine.initialize(gameParams)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.isGameEnded()).thenReturn(false); // Never ends naturally
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));
        when(mockEngine.getWinnerId()).thenReturn(null);
        
        gameSession = new GameSession(mockEngine, mockRegistry, gameParams);
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        
        // Wait for game to start
        Thread.sleep(200);
        
        // Shutdown
        gameSession.shutdown();
        
        sessionThread.join(2000);
        
        // Assert - thread should have terminated
        assertFalse(sessionThread.isAlive());
    }
    
    @Test
    @Timeout(5)
    void run_BroadcastsCorrectMessages() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        when(mockEngine.initialize(gameParams)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.isGameEnded()).thenReturn(false, true);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));
        when(mockEngine.getWinnerId()).thenReturn(PLAYER_1);
        
        gameSession = new GameSession(mockEngine, mockRegistry, gameParams);
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        
        // Submit actions to complete turn quickly
        Thread.sleep(200);
        gameSession.submitAction(PLAYER_1, 0, new Action[0]);
        gameSession.submitAction(PLAYER_2, 0, new Action[0]);
        
        sessionThread.join(3000);
        
        // Assert
        ArgumentCaptor<Message> broadcastCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mockRegistry, atLeast(2)).broadcast(broadcastCaptor.capture());
        
        List<Message> broadcasts = broadcastCaptor.getAllValues();
        assertTrue(broadcasts.stream().anyMatch(m -> m instanceof StartGameMessage), 
            "Should broadcast StartGameMessage");
        assertTrue(broadcasts.stream().anyMatch(m -> m instanceof EndGameMessage), 
            "Should broadcast EndGameMessage");
        
        // Verify EndGameMessage contains winner
        EndGameMessage endMsg = broadcasts.stream()
            .filter(m -> m instanceof EndGameMessage)
            .map(m -> (EndGameMessage) m)
            .findFirst()
            .orElse(null);
        
        assertNotNull(endMsg);
        assertEquals(PLAYER_1, endMsg.getGameEnd().winnerId());
    }
}
