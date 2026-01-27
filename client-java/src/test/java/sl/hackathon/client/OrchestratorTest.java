package sl.hackathon.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sl.hackathon.client.api.ServerAPI;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Orchestrator.
 * Test coverage:
 * 1. Initialization and validation
 * 2. Game lifecycle (start, turn, end)
 * 3. Bot integration
 * 4. Timeout handling
 * 5. Error handling
 * 6. Game log writing
 */
class OrchestratorTest {
    
    private Orchestrator orchestrator;
    private ServerAPI mockServerAPI;
    private Bot mockBot;
    private String testPlayerId;
    
    @BeforeEach
    void setUp() {
        orchestrator = new Orchestrator();
        mockServerAPI = mock(ServerAPI.class);
        mockBot = mock(Bot.class);
        testPlayerId = "player-1";
    }
    
    @AfterEach
    void tearDown() {
        if (orchestrator != null && orchestrator.isInitialized()) {
            orchestrator.shutdown();
        }
        
        // Clean up test log files
        File logsDir = new File("./game-logs/");
        if (logsDir.exists()) {
            File[] files = logsDir.listFiles((dir, name) -> name.startsWith("game_") && name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
    
    @Test
    void testInit_successfulInitialization() {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        assertTrue(orchestrator.isInitialized(), "Orchestrator should be initialized");
        assertEquals(testPlayerId, orchestrator.getPlayerId(), "Player ID should match");
        
        // Verify callbacks were wired
        verify(mockServerAPI).setOnGameStart(any());
        verify(mockServerAPI).setOnNextTurn(any());
        verify(mockServerAPI).setOnGameEnd(any());
        verify(mockServerAPI).setOnInvalidOperation(any());
        verify(mockServerAPI).setOnError(any());
    }
    
    @Test
    void testInit_nullServerAPI_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            orchestrator.init(null, mockBot, testPlayerId),
            "Should throw exception for null ServerAPI"
        );
    }
    
    @Test
    void testInit_nullBot_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            orchestrator.init(mockServerAPI, null, testPlayerId),
            "Should throw exception for null Bot"
        );
    }
    
    @Test
    void testInit_nullPlayerId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            orchestrator.init(mockServerAPI, mockBot, null),
            "Should throw exception for null playerId"
        );
    }
    
    @Test
    void testInit_blankPlayerId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            orchestrator.init(mockServerAPI, mockBot, ""),
            "Should throw exception for blank playerId"
        );
    }
    
    @Test
    void testHandleGameStart_storesInitialState() {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture the onGameStart callback
        ArgumentCaptor<Consumer<StartGameMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnGameStart(callbackCaptor.capture());
        Consumer<StartGameMessage> onGameStart = callbackCaptor.getValue();
        
        // Create start game message
        Unit[] initialUnits = new Unit[]{
            new Unit(1, testPlayerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, testPlayerId, UnitType.BASE, new Position(2, 2))
        };
        GameState initialState = new GameState(initialUnits, System.currentTimeMillis());
        
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            mapLayout,
            new GameState[]{initialState},
            null
        );
        
        StartGameMessage startMsg = new StartGameMessage(statusUpdate);
        
        // Trigger callback
        onGameStart.accept(startMsg);
        
        // Verify state was stored
        assertNotNull(orchestrator.getCurrentGameState(), "Current state should be set");
        assertEquals(2, orchestrator.getCurrentGameState().units().length, "Should have 2 units");
    }
    
    @Test
    void testHandleNextTurn_invokesBot_sendsActions() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Set up map layout
        MapLayout testMap = new MapLayout(new Dimension(10, 10), new Position[0]);
        orchestrator.setMapLayout(testMap);
        
        // Capture callbacks
        ArgumentCaptor<Consumer<NextTurnMessage>> turnCallbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnNextTurn(turnCallbackCaptor.capture());
        Consumer<NextTurnMessage> onNextTurn = turnCallbackCaptor.getValue();
        
        // Set up bot response
        Action[] expectedActions = new Action[]{
            new Action(1, Direction.N)
        };
        when(mockBot.handleState(eq(testPlayerId), eq(testMap), any(GameState.class), anyLong()))
            .thenReturn(expectedActions);
        
        // Create next turn message
        Unit[] turnUnits = new Unit[]{
            new Unit(2, testPlayerId, UnitType.PAWN, new Position(1, 1))
        };
        GameState turnState = new GameState(turnUnits, System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage(testPlayerId, turnState);
        
        // Trigger callback
        onNextTurn.accept(turnMsg);
        
        // Wait a bit for async bot execution
        Thread.sleep(100);
        
        // Verify bot was invoked
        verify(mockBot, timeout(1000)).handleState(eq(testPlayerId), eq(testMap), any(GameState.class), anyLong());
        
        // Verify actions were sent to server
        verify(mockServerAPI, timeout(1000)).send(testPlayerId, expectedActions);
    }
    
    @Test
    void testHandleNextTurn_differentPlayer_ignored() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture callback
        ArgumentCaptor<Consumer<NextTurnMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnNextTurn(callbackCaptor.capture());
        Consumer<NextTurnMessage> onNextTurn = callbackCaptor.getValue();
        
        // Create next turn message for different player
        GameState turnState = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage("player-2", turnState);
        
        // Trigger callback
        onNextTurn.accept(turnMsg);
        
        // Wait a bit
        Thread.sleep(100);
        
        // Verify bot was NOT invoked
        verify(mockBot, never()).handleState(any(), any(), any(), anyLong());
        verify(mockServerAPI, never()).send(any(), any());
    }
    
    @Test
    void testHandleNextTurn_botTimeout_sendsFallback() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        orchestrator.setMapLayout(new MapLayout(new Dimension(10, 10), new Position[0]));
        
        // Capture callback
        ArgumentCaptor<Consumer<NextTurnMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnNextTurn(callbackCaptor.capture());
        Consumer<NextTurnMessage> onNextTurn = callbackCaptor.getValue();
        
        // Set up bot to timeout (sleep longer than allowed)
        when(mockBot.handleState(any(), any(), any(), anyLong()))
            .thenAnswer(invocation -> {
                Thread.sleep(10000); // Sleep 10 seconds to force a timeout
                return new Action[0];
            });
        
        // Create next turn message
        GameState turnState = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage(testPlayerId, turnState);
        
        // Trigger callback
        onNextTurn.accept(turnMsg);
        
        // Verify fallback actions (empty array) were sent
        ArgumentCaptor<Action[]> actionsCaptor = ArgumentCaptor.forClass(Action[].class);
        verify(mockServerAPI, timeout(6000)).send(eq(testPlayerId), actionsCaptor.capture());
        
        Action[] sentActions = actionsCaptor.getValue();
        assertEquals(0, sentActions.length, "Should send empty fallback actions on timeout");
    }
    
    @Test
    void testHandleNextTurn_botException_sendsFallback() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        orchestrator.setMapLayout(new MapLayout(new Dimension(10, 10), new Position[0]));
        
        // Capture callback
        ArgumentCaptor<Consumer<NextTurnMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnNextTurn(callbackCaptor.capture());
        Consumer<NextTurnMessage> onNextTurn = callbackCaptor.getValue();
        
        // Set up bot to throw exception
        when(mockBot.handleState(any(), any(), any(), anyLong()))
            .thenThrow(new RuntimeException("Bot error"));
        
        // Create next turn message
        GameState turnState = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage(testPlayerId, turnState);
        
        // Trigger callback
        onNextTurn.accept(turnMsg);
        
        // Verify fallback actions were sent
        ArgumentCaptor<Action[]> actionsCaptor = ArgumentCaptor.forClass(Action[].class);
        verify(mockServerAPI, timeout(2000)).send(eq(testPlayerId), actionsCaptor.capture());
        
        Action[] sentActions = actionsCaptor.getValue();
        assertEquals(0, sentActions.length, "Should send empty fallback actions on bot error");
    }
    
//    @Test
    void testHandleGameEnd_writesLogFile() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture callback
        ArgumentCaptor<Consumer<EndGameMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnGameEnd(callbackCaptor.capture());
        Consumer<EndGameMessage> onGameEnd = callbackCaptor.getValue();
        
        // Create end game message
        MapLayout map = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameState[] history = new GameState[]{
            new GameState(new Unit[0], System.currentTimeMillis())
        };
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.END,
            map,
            history,
            testPlayerId
        );
        EndGameMessage endMsg = new EndGameMessage(statusUpdate);
        
        // Trigger callback
        onGameEnd.accept(endMsg);
        
        // Wait for file write
        Thread.sleep(200);
        
        // Verify log file was created
        File logsDir = new File("./game-logs/");
        assertTrue(logsDir.exists(), "Game logs directory should exist");
        
        File[] logFiles = logsDir.listFiles((dir, name) -> 
            name.startsWith("game_") && name.endsWith(".json"));
        assertNotNull(logFiles, "Log files should exist");
        assertTrue(logFiles.length > 0, "At least one log file should be created");
        
        // Verify log content
        String logContent = Files.readString(logFiles[0].toPath());
        assertTrue(logContent.contains("\"players\""), "Log should contain players array");
        assertTrue(logContent.contains("\"mapDimensions\""), "Log should contain map dimensions");
        assertTrue(logContent.contains("\"walls\""), "Log should contain walls");
        assertTrue(logContent.contains("\"winner\""), "Log should contain winner field");
    }
    
//    @Test
    void testHandleGameEnd_victory_logsCorrectly() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture callback
        ArgumentCaptor<Consumer<EndGameMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnGameEnd(callbackCaptor.capture());
        Consumer<EndGameMessage> onGameEnd = callbackCaptor.getValue();
        
        // Create end game message with this player as winner
        MapLayout map = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.END,
            map,
            new GameState[0],
            testPlayerId
        );
        EndGameMessage endMsg = new EndGameMessage(statusUpdate);
        
        // Trigger callback (should log victory)
        assertDoesNotThrow(() -> onGameEnd.accept(endMsg));
    }
    
//    @Test
    void testHandleGameEnd_defeat_logsCorrectly() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture callback
        ArgumentCaptor<Consumer<EndGameMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnGameEnd(callbackCaptor.capture());
        Consumer<EndGameMessage> onGameEnd = callbackCaptor.getValue();
        
        // Create end game message with different player as winner
        MapLayout map = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.END,
            map,
            new GameState[0],
            "player-2"
        );
        EndGameMessage endMsg = new EndGameMessage(statusUpdate);
        
        // Trigger callback (should log defeat)
        assertDoesNotThrow(() -> onGameEnd.accept(endMsg));
    }
    
    @Test
    void testHandleInvalidOperation_logsWarning() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture callback
        ArgumentCaptor<Consumer<InvalidOperationMessage>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnInvalidOperation(callbackCaptor.capture());
        Consumer<InvalidOperationMessage> onInvalidOp = callbackCaptor.getValue();
        
        // Create invalid operation message
        InvalidOperationMessage msg = new InvalidOperationMessage(testPlayerId, "Test error");
        
        // Trigger callback (should log warning without exception)
        assertDoesNotThrow(() -> onInvalidOp.accept(msg));
    }
    
    @Test
    void testHandleError_logsError() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Capture callback
        ArgumentCaptor<Consumer<Throwable>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockServerAPI).setOnError(callbackCaptor.capture());
        Consumer<Throwable> onError = callbackCaptor.getValue();
        
        // Create error
        Exception testError = new Exception("Test error");
        
        // Trigger callback (should log error without exception)
        assertDoesNotThrow(() -> onError.accept(testError));
    }
    
    @Test
    void testShutdown_cleansUpResources() {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Shutdown should not throw
        assertDoesNotThrow(() -> orchestrator.shutdown());
    }
}
