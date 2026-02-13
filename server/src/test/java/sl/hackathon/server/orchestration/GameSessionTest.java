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
import java.util.concurrent.atomic.AtomicInteger;
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
    private GameSettings gameSettings;
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

        gameSettings = new GameSettings(dimension, walls, baseLocations, 5000, 0.3f, false);
    }

    @Test
    void constructor_WithValidParams_CreatesInstance() {
        gameSession = new GameSession(mockEngine, mockRegistry, gameSettings);
        assertNotNull(gameSession);
        assertFalse(gameSession.isGameStarted());
        assertEquals(0, gameSession.getCurrentRound());
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
        when(mockEngine.initialize(gameSettings)).thenReturn(initialState);
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.getGameState()).thenReturn(initialState);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], initialState.startAt())));

        gameSession = new GameSession(mockEngine, mockRegistry, gameSettings);
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        sessionThread.join(3000);
        
        // Assert
        verify(mockEngine).initialize(gameSettings);
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

        AtomicInteger halfTurns = new AtomicInteger(0);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenAnswer(inv -> {
            halfTurns.incrementAndGet();
            return true;
        });
        when(mockEngine.isGameEnded()).thenAnswer(inv -> halfTurns.get() >= 6);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));

        // Use very short timeout for quick test execution (3 turns * 200ms = 600ms max)
        GameSettings shortTimeoutParams = new GameSettings(
                gameSettings.dimension(),
                gameSettings.walls(),
                gameSettings.potentialBaseLocations(),
                200,
                0.3f,
                false);
        gameSession = new GameSession(mockEngine, mockRegistry, shortTimeoutParams);

        // Auto-submit empty actions whenever a NextTurnMessage is sent
        doAnswer(inv -> {
            String playerId = inv.getArgument(0);
            gameSession.submitAction(playerId, gameSession.getCurrentRound(), new Action[0]);
            return null;
        }).when(mockRegistry).send(anyString(), any(NextTurnMessage.class));
        
        // Act
        CountDownLatch startLatch = new CountDownLatch(1);
        Thread sessionThread = new Thread(() -> {
            gameSession.run();
            startLatch.countDown();
        });
        sessionThread.start();
        
        // Wait for game to complete (3 turns * 200ms timeout + overhead = ~1s max)
        assertTrue(startLatch.await(2, TimeUnit.SECONDS));

        // Assert - verify turn messages were sent (3 rounds => 3 half-turns per player)
        verify(mockRegistry, times(3)).send(eq(PLAYER_1), any(NextTurnMessage.class));
        verify(mockRegistry, times(3)).send(eq(PLAYER_2), any(NextTurnMessage.class));
        verify(mockEngine, times(3)).handlePlayerActions(eq(PLAYER_1), any());
        verify(mockEngine, times(3)).handlePlayerActions(eq(PLAYER_2), any());
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
        when(mockEngine.initialize(gameSettings)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));

        AtomicInteger halfTurns = new AtomicInteger(0);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenAnswer(inv -> {
            halfTurns.incrementAndGet();
            return true;
        });
        when(mockEngine.isGameEnded()).thenAnswer(inv -> halfTurns.get() >= 2);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));

        gameSession = new GameSession(mockEngine, mockRegistry, gameSettings);

        Action[] player1Actions = new Action[]{new Action(1, Direction.N)};
        Action[] player2Actions = new Action[]{};

        doAnswer(inv -> {
            String playerId = inv.getArgument(0);

            // Attempt to submit from the non-active player while PLAYER_1 is active
            if (PLAYER_1.equals(playerId)) {
                gameSession.submitAction(PLAYER_2, gameSession.getCurrentRound(), new Action[]{new Action(99, Direction.S)});
                gameSession.submitAction(PLAYER_1, gameSession.getCurrentRound(), player1Actions);
            } else if (PLAYER_2.equals(playerId)) {
                gameSession.submitAction(PLAYER_2, gameSession.getCurrentRound(), player2Actions);
            }
            return null;
        }).when(mockRegistry).send(anyString(), any(NextTurnMessage.class));
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        
        // Wait for game to start
        Thread.sleep(200);
        
        sessionThread.join(3000);
        
        // Assert
        ArgumentCaptor<Action[]> p1ActionsCaptor = ArgumentCaptor.forClass(Action[].class);
        verify(mockEngine).handlePlayerActions(eq(PLAYER_1), p1ActionsCaptor.capture());
        assertArrayEquals(player1Actions, p1ActionsCaptor.getValue());

        ArgumentCaptor<Action[]> p2ActionsCaptor = ArgumentCaptor.forClass(Action[].class);
        verify(mockEngine).handlePlayerActions(eq(PLAYER_2), p2ActionsCaptor.capture());
        assertArrayEquals(player2Actions, p2ActionsCaptor.getValue());
    }
    
    @Test
    void submitAction_WithMismatchedTurnId_IgnoresAction() {
        // Arrange
        gameSession = new GameSession(mockEngine, mockRegistry, gameSettings);
        Action[] actions = new Action[]{new Action(1, Direction.N)};
        
        // Act - submit for turn 5 when current turn is 0
        gameSession.submitAction(PLAYER_1, 5, actions);
        
        // Assert - current turn should still be 0
        assertEquals(0, gameSession.getCurrentRound());
    }
    
    @Test
    @Timeout(5)
    void run_WithTimeout_EndsGameAndForfeitsTimedOutPlayer() throws Exception {
        // Arrange
        when(mockRegistry.isReady()).thenReturn(true);
        
        Unit[] units = new Unit[]{
            new Unit(1, PLAYER_1, UnitType.BASE, new Position(1, 2)),
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        
        // Use very short timeout
        GameSettings shortTimeoutParams = new GameSettings(
                gameSettings.dimension(),
                gameSettings.walls(),
                gameSettings.potentialBaseLocations(),
                1,
                0.3f,
                false);
        
        when(mockEngine.initialize(shortTimeoutParams)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.isGameEnded()).thenReturn(false).thenReturn(true);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));

        gameSession = new GameSession(mockEngine, mockRegistry, shortTimeoutParams);

        // Auto-submit only for PLAYER_1; PLAYER_2 will time out and forfeit
        doAnswer(inv -> {
            String playerId = inv.getArgument(0);
            if (PLAYER_1.equals(playerId)) {
                gameSession.submitAction(playerId, gameSession.getCurrentRound(), new Action[]{new Action(1, Direction.N)});
            }
            return null;
        }).when(mockRegistry).send(anyString(), any(NextTurnMessage.class));
        
        // Act
        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();
        
        sessionThread.join(3000);

        verify(mockEngine).initialize(eq(shortTimeoutParams));
        verify(mockEngine, times(1)).handlePlayerActions(eq(PLAYER_1), any());
        verify(mockEngine, never()).handlePlayerActions(eq(PLAYER_2), any());

        ArgumentCaptor<Message> broadcastCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mockRegistry, atLeast(1)).broadcast(broadcastCaptor.capture());

        EndGameMessage endMsg = broadcastCaptor.getAllValues().stream()
            .filter(m -> m instanceof EndGameMessage)
            .map(m -> (EndGameMessage) m)
            .findFirst()
            .orElse(null);

        assertNotNull(endMsg);
        assertEquals(PLAYER_1, endMsg.getGameEnd().winnerId());
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
        when(mockEngine.initialize(gameSettings)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));
        when(mockEngine.isGameEnded()).thenReturn(false); // Never ends naturally
        when(mockEngine.handlePlayerActions(anyString(), any())).thenReturn(true);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));

        gameSession = new GameSession(mockEngine, mockRegistry, gameSettings);
        
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
            new Unit(1, PLAYER_1, UnitType.PAWN, new Position(1, 1)),
            new Unit(1, PLAYER_1, UnitType.BASE, new Position(0, 1))
        };
        GameState state = new GameState(units, System.currentTimeMillis());
        when(mockEngine.initialize(gameSettings)).thenReturn(state);
        when(mockEngine.getGameState()).thenReturn(state);
        when(mockEngine.getActivePlayers()).thenReturn(Arrays.asList(PLAYER_1, PLAYER_2));

        AtomicInteger halfTurns = new AtomicInteger(0);
        when(mockEngine.handlePlayerActions(anyString(), any())).thenAnswer(inv -> {
            halfTurns.incrementAndGet();
            return true;
        });
        when(mockEngine.isGameEnded()).thenAnswer(inv -> halfTurns.get() >= 2);
        when(mockEngine.getGameDeltaHistory()).thenReturn(List.of(new GameDelta(units, new int[0], state.startAt())));

        gameSession = new GameSession(mockEngine, mockRegistry, gameSettings);

        // Auto-submit empty actions to complete the first round quickly
        doAnswer(inv -> {
            String playerId = inv.getArgument(0);
            gameSession.submitAction(playerId, gameSession.getCurrentRound(), new Action[0]);
            return null;
        }).when(mockRegistry).send(anyString(), any(NextTurnMessage.class));

        // Act

        Thread sessionThread = new Thread(gameSession);
        sessionThread.start();

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
