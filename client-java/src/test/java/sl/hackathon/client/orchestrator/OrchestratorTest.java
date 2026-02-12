package sl.hackathon.client.orchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sl.hackathon.client.Bot;
import sl.hackathon.client.api.ServerAPI;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.io.File;
import java.nio.file.Files;
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
        
        assertNotNull(orchestrator.getMessageRouter(), "MessageRouter should be available");
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
        MessageRouter router = orchestrator.getMessageRouter();
        
        // Create start game message
        Unit[] initialUnits = new Unit[]{
            new Unit(1, testPlayerId, UnitType.PAWN, new Position(1, 1)),
            new Unit(2, testPlayerId, UnitType.BASE, new Position(2, 2))
        };
        long now = System.currentTimeMillis();
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameStart gameStart = new GameStart(mapLayout, initialUnits, now);
        StartGameMessage startMsg = new StartGameMessage(gameStart);

        router.accept(startMsg);
        
        // Verify state was stored
        assertNotNull(orchestrator.getCurrentGameState(), "Current state should be set");
        assertEquals(2, orchestrator.getCurrentGameState().units().length, "Should have 2 units");
    }
    
    @Test
    void testHandleNextTurn_invokesBot_sendsActions() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        MessageRouter router = orchestrator.getMessageRouter();
        
        // Set up map layout
        MapLayout testMap = new MapLayout(new Dimension(10, 10), new Position[0]);
        orchestrator.setMapLayout(testMap);
        
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
        NextTurnMessage turnMsg = new NextTurnMessage(testPlayerId, turnState, 15000);

        router.accept(turnMsg);
        
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
        MessageRouter router = orchestrator.getMessageRouter();
        
        // Create next turn message for different player
        GameState turnState = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage("player-2", turnState, 15000);
        
        router.accept(turnMsg);
        
        // Wait a bit
        Thread.sleep(100);
        
        // Verify bot was NOT invoked
        verify(mockBot, never()).handleState(any(), any(), any(), anyLong());
        verify(mockServerAPI, never()).send(any(), any());
    }
    
    @Test
    void testHandleNextTurn_botTimeout_sendsFallback() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        MessageRouter router = orchestrator.getMessageRouter();
        orchestrator.setMapLayout(new MapLayout(new Dimension(10, 10), new Position[0]));
        
        // Set up bot to timeout (sleep longer than allowed)
        when(mockBot.handleState(any(), any(), any(), anyLong()))
            .thenAnswer(invocation -> {
                Thread.sleep(2000); // Sleep longer than allowed to force a timeout
                return new Action[0];
            });
        
        // Create next turn message
        GameState turnState = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage(testPlayerId, turnState, 1500);
        
        router.accept(turnMsg);
        
        // Verify fallback actions (empty array) were sent
        ArgumentCaptor<Action[]> actionsCaptor = ArgumentCaptor.forClass(Action[].class);
        verify(mockServerAPI, timeout(4000)).send(eq(testPlayerId), actionsCaptor.capture());
        
        Action[] sentActions = actionsCaptor.getValue();
        assertEquals(0, sentActions.length, "Should send empty fallback actions on timeout");
    }
    
    @Test
    void testHandleNextTurn_botException_sendsFallback() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        MessageRouter router = orchestrator.getMessageRouter();
        orchestrator.setMapLayout(new MapLayout(new Dimension(10, 10), new Position[0]));
        
        // Set up bot to throw exception
        when(mockBot.handleState(any(), any(), any(), anyLong()))
            .thenThrow(new RuntimeException("Bot error"));
        
        // Create next turn message
        GameState turnState = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage turnMsg = new NextTurnMessage(testPlayerId, turnState, 15000);

        router.accept(turnMsg);
        
        // Verify fallback actions were sent
        ArgumentCaptor<Action[]> actionsCaptor = ArgumentCaptor.forClass(Action[].class);
        verify(mockServerAPI, timeout(2000)).send(eq(testPlayerId), actionsCaptor.capture());
        
        Action[] sentActions = actionsCaptor.getValue();
        assertEquals(0, sentActions.length, "Should send empty fallback actions on bot error");
    }
    
    @Test
    void testHandleInvalidOperation_logsWarning() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        MessageRouter router = orchestrator.getMessageRouter();
        
        // Create invalid operation message
        InvalidOperationMessage msg = new InvalidOperationMessage(testPlayerId, "Test error");
        
        assertDoesNotThrow(() -> router.accept(msg));
    }
    
    @Test
    void testHandleError_logsError() throws Exception {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        MessageRouter router = orchestrator.getMessageRouter();
        
        // Create error
        Exception testError = new Exception("Test error");
        
        assertDoesNotThrow(() -> router.accept(testError));
    }
    
    @Test
    void testShutdown_cleansUpResources() {
        orchestrator.init(mockServerAPI, mockBot, testPlayerId);
        
        // Shutdown should not throw
        assertDoesNotThrow(() -> orchestrator.shutdown());
    }
}
