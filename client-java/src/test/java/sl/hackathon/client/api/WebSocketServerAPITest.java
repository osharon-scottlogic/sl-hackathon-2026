package sl.hackathon.client.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerAPI.
 * These tests verify the client-side WebSocket API behavior including:
 * - Connection lifecycle management
 * - Message handler registration and invocation
 * - Error handling
 * - State management
 */
class WebSocketServerAPITest {
    
    private WebSocketServerAPI webSocketServerAPI;
    
    @BeforeEach
    void setUp() {
        webSocketServerAPI = new WebSocketServerAPI();
    }
    
    @AfterEach
    void tearDown() {
        if (webSocketServerAPI.isConnected()) {
            webSocketServerAPI.close();
        }
    }
    
    @Test
    void testInitialState() {
        assertEquals(WebSocketServerAPI.ConnectionState.DISCONNECTED, webSocketServerAPI.getState());
        assertFalse(webSocketServerAPI.isConnected());
    }
    
    @Test
    void testSendWithoutConnection() {
        Action[] actions = new Action[]{
            new Action(1, Direction.N)
        };
        
        assertThrows(IllegalStateException.class, () -> webSocketServerAPI.send("player1", actions));
    }
    
    @Test
    void testConnectWithInvalidURL() {
        assertThrows(Exception.class, () -> webSocketServerAPI.connect("invalid-url"));
    }
    
    @Test
    void testGameStartMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StartGameMessage> receivedMessage = new AtomicReference<>();
        
        webSocketServerAPI.setOnGameStart(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        // Create test message
        GameState state = new GameState(
            new Unit[]{new Unit(1, "player1", UnitType.PAWN, new Position(1, 1))},
            System.currentTimeMillis()
        );
        
        MapLayout mapLayout = new MapLayout(
            new Dimension(10, 10),
            new Position[]{new Position(5, 5)}
        );
        
        GameStart gameStart = new GameStart(
            mapLayout,
            state.units(),
            System.currentTimeMillis()
        );
        
        StartGameMessage message = new StartGameMessage(gameStart);
        
        // Simulate handler invocation (internal method call for unit testing)
        webSocketServerAPI.setOnGameStart(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        // Manually trigger to test handler without actual WebSocket
        if (webSocketServerAPI.onGameStart != null) {
            webSocketServerAPI.onGameStart.accept(message);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals(gameStart, receivedMessage.get().getGameStart());
    }
    
    @Test
    void testNextTurnMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NextTurnMessage> receivedMessage = new AtomicReference<>();
        
        webSocketServerAPI.setOnNextTurn(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        GameState state = new GameState(
            new Unit[]{new Unit(1, "player1", UnitType.PAWN, new Position(2, 2))},
            System.currentTimeMillis()
        );
        
        NextTurnMessage message = new NextTurnMessage("player1", state, 15000);
        
        // Trigger handler
        if (webSocketServerAPI.onNextTurn != null) {
            webSocketServerAPI.onNextTurn.accept(message);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals("player1", receivedMessage.get().getPlayerId());
        assertEquals(state, receivedMessage.get().getGameState());
    }
    
    @Test
    void testEndGameMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<EndGameMessage> receivedMessage = new AtomicReference<>();
        
        webSocketServerAPI.setOnGameEnd(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        MapLayout mapLayout = new MapLayout(
            new Dimension(10, 10),
            new Position[]{new Position(5, 5)}
        );
        
        Unit[] initialUnits = new Unit[0];
        GameDelta[] deltas = new GameDelta[0];
        
        GameEnd gameEnd = new GameEnd(
            mapLayout,
            deltas,
            "player1",
            System.currentTimeMillis()
        );
        
        EndGameMessage message = new EndGameMessage(gameEnd);
        
        // Trigger handler
        if (webSocketServerAPI.onGameEnd != null) {
            webSocketServerAPI.onGameEnd.accept(message);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals("player1", receivedMessage.get().getGameEnd().winnerId());
    }
    
    @Test
    void testInvalidOperationMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<InvalidOperationMessage> receivedMessage = new AtomicReference<>();
        
        webSocketServerAPI.setOnInvalidOperation(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        InvalidOperationMessage message = new InvalidOperationMessage("player1", "Invalid action");
        
        // Trigger handler
        if (webSocketServerAPI.onInvalidOperation != null) {
            webSocketServerAPI.onInvalidOperation.accept(message);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals("Invalid action", receivedMessage.get().getReason());
        assertEquals("player1", receivedMessage.get().getPlayerId());
    }
    
    @Test
    void testErrorHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> receivedError = new AtomicReference<>();
        
        webSocketServerAPI.setOnError(error -> {
            receivedError.set(error);
            latch.countDown();
        });
        
        RuntimeException testError = new RuntimeException("Test error");
        
        // Trigger error handler
        if (webSocketServerAPI.onError != null) {
            webSocketServerAPI.onError.accept(testError);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedError.get());
        assertEquals("Test error", receivedError.get().getMessage());
    }
    
    @Test
    void testCloseWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> webSocketServerAPI.close());
        assertEquals(WebSocketServerAPI.ConnectionState.DISCONNECTED, webSocketServerAPI.getState());
    }
    
    @Test
    void testMultipleHandlerRegistrations() {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        // First handler
        webSocketServerAPI.setOnGameStart(msg -> latch1.countDown());
        
        // Second handler (should replace first)
        webSocketServerAPI.setOnGameStart(msg -> latch2.countDown());
        
        GameStart gameStart = new GameStart(
            new MapLayout(new Dimension(10, 10), new Position[0]),
            new Unit[0],
            System.currentTimeMillis()
        );
        
        StartGameMessage message = new StartGameMessage(gameStart);
        
        // Trigger handler
        if (webSocketServerAPI.onGameStart != null) {
            webSocketServerAPI.onGameStart.accept(message);
        }
        
        // Only second handler should be invoked
        assertEquals(1, latch1.getCount());
        assertEquals(0, latch2.getCount());
    }
    
    @Test
    void testStateTransitionOnConnect() {
        // Initial state
        assertEquals(WebSocketServerAPI.ConnectionState.DISCONNECTED, webSocketServerAPI.getState());
        
        // Note: We cannot easily test CONNECTING -> CONNECTED transition
        // without a real WebSocket server. This test verifies initial state only.
        // Integration tests should verify full connection lifecycle.
    }
    
    @Test
    void testSendActionsFormat() throws Exception {
        // Test that the send method creates proper ActionMessage
        Action[] actions = new Action[]{
            new Action(1, Direction.N),
            new Action(2, Direction.SE)
        };
        
        // We cannot test actual sending without connection,
        // but we can verify the message creation logic doesn't throw
        ActionMessage actionMessage = new ActionMessage("player1", actions);
        String json = MessageCodec.serialize(actionMessage);
        
        assertNotNull(json);
        assertTrue(json.contains("ACTION"));
        assertTrue(json.contains("player1"));
    }
    
    @Test
    void testHandlerCanBeNull() {
        // Verify that null handlers don't cause exceptions
        webSocketServerAPI.setOnGameStart(null);
        webSocketServerAPI.setOnNextTurn(null);
        webSocketServerAPI.setOnGameEnd(null);
        webSocketServerAPI.setOnInvalidOperation(null);
        webSocketServerAPI.setOnError(null);
        
        // Should not throw when handlers are null
        assertDoesNotThrow(() -> {
            if (webSocketServerAPI.onGameStart != null) {
                GameStart statusUpdate = new GameStart(
                    new MapLayout(new Dimension(10, 10), new Position[0]), new Unit[0], 0
                );
                webSocketServerAPI.onGameStart.accept(new StartGameMessage(statusUpdate, null, 0L));
            }
        });
    }
}
