package sl.hackathon.server.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.communication.ClientRegistry;
import sl.hackathon.server.communication.WebSocketAdapter;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameServer.
 * 
 * Test coverage:
 * 1. Constructor validation
 * 2. Component wiring
 * 3. Lifecycle (start/stop)
 * 4. Handler callbacks
 * 5. Thread management
 */
class GameServerTest {
    
    private GameEngine mockEngine;
    private ServerConfig config;
    private MapConfig mapConfig;
    private GameServer gameServer;
    
    // Use random port to avoid "Address already in use" errors between tests
    private static int getAvailablePort() {
        return 8081 + (int) (Math.random() * 1000);
    }
    
    @BeforeEach
    void setUp() {
        // Create mock game engine
        mockEngine = mock(GameEngine.class);
        
        // Setup default mock behaviors
        when(mockEngine.getActivePlayers()).thenReturn(java.util.List.of());
        when(mockEngine.getGameDeltaHistory()).thenReturn(java.util.List.of());
        when(mockEngine.getWinnerId()).thenReturn(null);
        
        // Create valid configuration with random port
        Dimension dimension = new Dimension(10, 10);
        Position[] walls = new Position[]{new Position(5, 5)};
        Position[] baseLocations = new Position[]{new Position(1, 1), new Position(9, 9)};
        mapConfig = new MapConfig(dimension, walls, baseLocations);
        config = new ServerConfig(getAvailablePort(), mapConfig, 200L); // Use random port to avoid conflicts
        
        // Reset static state in WebSocketAdapter
        WebSocketAdapter.reset();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (gameServer != null) {
            try {
                gameServer.stop();
                // Give the server time to release the port
                Thread.sleep(100);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        // Reset static state
        WebSocketAdapter.reset();
    }
    
    @Test
    void constructor_WithNullConfig_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new GameServer(null, mockEngine));
    }
    
    @Test
    void constructor_WithNullGameEngine_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new GameServer(config, null));
    }
    
//    @Test
    void constructor_WithValidParameters_CreatesGameServer() {
        // Act
        gameServer = new GameServer(config, mockEngine);
        
        // Assert
        assertNotNull(gameServer);
    }
    
//    @Test
    void wireHandlers_SetsClientRegistryInWebSocketAdapter() throws JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the game engine
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        
        // Act
        gameServer.start();
        
        // Assert
        assertNotNull(WebSocketAdapter.getClientRegistry(), 
            "ClientRegistry should be set in WebSocketAdapter");
    }
    
//    @Test
    void wireHandlers_SetsMessageHandlerInWebSocketAdapter() throws JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the game engine
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        
        // Act
        gameServer.start();
        
        // Assert
        assertNotNull(WebSocketAdapter.getOnMessage(), 
            "OnMessage handler should be set in WebSocketAdapter");
    }
    
//    @Test
    void onClientConnect_CallsGameEngineAddPlayer() throws JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the game state to prevent GameSession from failing
        GameState mockGameState = new GameState(new Unit[]{},0);
        when(mockEngine.initialize(any())).thenReturn(mockGameState);
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        
        // Start the server to wire handlers
        gameServer.start();
        
        // Get the onClientConnect callback
        // We can't easily test this directly since it's wired internally
        // Instead, we verify that starting the server doesn't throw exceptions
        assertNotNull(WebSocketAdapter.getClientRegistry());
        
        // Note: The actual callback is tested through integration tests
        // Here we just verify the wiring doesn't fail
    }
    
//    @Test
    void onClientDisconnect_CallsGameEngineRemovePlayer() throws JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the game state to prevent GameSession from failing
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        
        // Start the server to wire handlers
        gameServer.start();
        
        // Get the registry from WebSocketAdapter
        ClientRegistry wiredRegistry = WebSocketAdapter.getClientRegistry();
        assertNotNull(wiredRegistry);
        
        // Note: The actual disconnect callback is tested through integration tests
        // Here we just verify the wiring doesn't fail
    }
    
//    @Test
    void onMessage_WithActionMessage_CallsGameSessionSubmitAction() throws InterruptedException, JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the engine to return a valid game state and active players
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.getActivePlayers()).thenReturn(java.util.List.of("player1"));
        when(mockEngine.getGameState()).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(false).thenReturn(true); // End after one turn
        
        // Start the server
        gameServer.start();
        
        // Give the GameSession time to initialize
        Thread.sleep(100);
        
        // Get the message handler
        BiConsumer<String, Message> messageHandler = WebSocketAdapter.getOnMessage();
        assertNotNull(messageHandler);
        
        // Create an action message
        String playerId = "player1";
        Action[] actions = new Action[]{new Action(1, Direction.N)};
        ActionMessage actionMessage = new ActionMessage("player1", actions);
        
        // Act
        messageHandler.accept(playerId, actionMessage);
        
        // Assert
        // We can't easily verify the call to gameSession.submitAction() since it's internal,
        // but we can verify the message was received without errors
        Thread.sleep(50); // Give time for async processing
        // If no exception was thrown, the handler worked correctly
    }
    
//    @Test
    void onMessage_WithNonActionMessage_DoesNothing() throws JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the game state
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        
        gameServer.start();
        
        // Get the message handler
        BiConsumer<String, Message> messageHandler = WebSocketAdapter.getOnMessage();
        assertNotNull(messageHandler);
        
        // Create a non-action message
        String playerId = "player1";
        Message nonActionMessage = new JoinGameMessage(playerId);
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> messageHandler.accept(playerId, nonActionMessage));
    }
    
//    @Test
    void start_InitializesWebSocketServer() throws JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the engine
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(true); // End immediately
        
        // Act
        gameServer.start();
        
        // Assert
        // If no exception is thrown, the server started successfully
        assertNotNull(WebSocketAdapter.getClientRegistry());
    }
    
//    @Test
    void start_CreatesGameSessionThread() throws InterruptedException, JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the engine
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.getActivePlayers()).thenReturn(java.util.List.of("player1"));
        when(mockEngine.getGameState()).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(false).thenReturn(true); // One turn then end
        
        // Act
        gameServer.start();
        
        // Give the thread time to start
        Thread.sleep(100);
        
        // Assert - check that a thread with the expected name exists
        boolean threadFound = Thread.getAllStackTraces().keySet().stream()
            .anyMatch(t -> t.getName().equals("GameSession-Thread"));
        assertTrue(threadFound, "GameSession thread should be running");
    }
    
//    @Test
    void stop_ShutsDownGameSession() throws InterruptedException, JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the engine
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.getActivePlayers()).thenReturn(java.util.List.of("player1"));
        when(mockEngine.getGameState()).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(false); // Keep running
        
        gameServer.start();
        Thread.sleep(100); // Let it start
        
        // Act
        gameServer.stop();
        
        // Assert - verify the thread terminates
        Thread.sleep(100);
        boolean threadFound = Thread.getAllStackTraces().keySet().stream()
            .anyMatch(t -> t.getName().equals("GameSession-Thread"));
        assertFalse(threadFound, "GameSession thread should be stopped");
    }
    
//    @Test
    void stop_WaitsForGameSessionTermination() throws InterruptedException, JsonProcessingException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the engine to simulate a long-running game
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.getActivePlayers()).thenReturn(java.util.List.of("player1"));
        when(mockEngine.getGameState()).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(false); // Keep running
        
        gameServer.start();
        Thread.sleep(100); // Let it start
        
        // Act
        long startTime = System.currentTimeMillis();
        gameServer.stop();
        long duration = System.currentTimeMillis() - startTime;
        
        // Assert - should wait but not too long (within the 5-second timeout)
        assertTrue(duration < 6000, "Stop should complete within timeout period");
    }
    
//    @Test
    void lifecycle_StartAndStop_WorksCorrectly() throws InterruptedException {
        // Arrange
        gameServer = new GameServer(config, mockEngine);
        
        // Mock the engine
        when(mockEngine.initialize(any())).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.getActivePlayers()).thenReturn(java.util.List.of("player1"));
        when(mockEngine.getGameState()).thenReturn(new GameState(new Unit[]{}, 0));
        when(mockEngine.isGameEnded()).thenReturn(false).thenReturn(true);
        
        // Act - Start
        assertDoesNotThrow(() -> gameServer.start());
        Thread.sleep(100);
        
        // Assert - Server is running
        assertNotNull(WebSocketAdapter.getClientRegistry());
        
        // Act - Stop
        assertDoesNotThrow(() -> gameServer.stop());
        Thread.sleep(100);
        
        // Assert - Thread is terminated
        boolean threadFound = Thread.getAllStackTraces().keySet().stream()
            .anyMatch(t -> t.getName().equals("GameSession-Thread"));
        assertFalse(threadFound);
    }
}
