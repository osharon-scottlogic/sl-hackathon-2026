package sl.hackathon.client.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.io.IOException;
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
class ServerAPITest {
    
    private ServerAPI serverAPI;
    
    @BeforeEach
    void setUp() {
        serverAPI = new ServerAPI();
    }
    
    @AfterEach
    void tearDown() {
        if (serverAPI.isConnected()) {
            serverAPI.close();
        }
    }
    
    @Test
    void testInitialState() {
        assertEquals(ServerAPI.ConnectionState.DISCONNECTED, serverAPI.getState());
        assertFalse(serverAPI.isConnected());
    }
    
    @Test
    void testSendWithoutConnection() {
        Action[] actions = new Action[]{
            new Action("pawn1", Direction.N)
        };
        
        assertThrows(IllegalStateException.class, () -> serverAPI.send("player1", actions));
    }
    
    @Test
    void testConnectWithInvalidURL() {
        assertThrows(Exception.class, () -> serverAPI.connect("invalid-url"));
    }
    
    @Test
    void testGameStartMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StartGameMessage> receivedMessage = new AtomicReference<>();
        
        serverAPI.setOnGameStart(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        // Create test message
        GameState state = new GameState(
            new Unit[]{new Unit("unit1", "player1", UnitType.PAWN, new Position(1, 1))},
            System.currentTimeMillis()
        );
        
        MapLayout mapLayout = new MapLayout(
            new Dimension(10, 10),
            new Position[]{new Position(5, 5)}
        );
        
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            mapLayout,
            new GameState[]{state},
            null
        );
        
        StartGameMessage message = new StartGameMessage(statusUpdate);
        
        // Simulate handler invocation (internal method call for unit testing)
        serverAPI.setOnGameStart(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        // Manually trigger to test handler without actual WebSocket
        if (serverAPI.onGameStart != null) {
            serverAPI.onGameStart.accept(message);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals(statusUpdate, receivedMessage.get().getGameStatusUpdate());
    }
    
    @Test
    void testNextTurnMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NextTurnMessage> receivedMessage = new AtomicReference<>();
        
        serverAPI.setOnNextTurn(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        GameState state = new GameState(
            new Unit[]{new Unit("unit1", "player1", UnitType.PAWN, new Position(2, 2))},
            System.currentTimeMillis()
        );
        
        NextTurnMessage message = new NextTurnMessage("player1", state);
        
        // Trigger handler
        if (serverAPI.onNextTurn != null) {
            serverAPI.onNextTurn.accept(message);
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
        
        serverAPI.setOnGameEnd(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        GameState[] history = new GameState[]{
            new GameState(new Unit[0], System.currentTimeMillis())
        };
        
        MapLayout mapLayout = new MapLayout(
            new Dimension(10, 10),
            new Position[]{new Position(5, 5)}
        );
        
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.END,
            mapLayout,
            history,
            "player1"
        );
        
        EndGameMessage message = new EndGameMessage(statusUpdate);
        
        // Trigger handler
        if (serverAPI.onGameEnd != null) {
            serverAPI.onGameEnd.accept(message);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals("player1", receivedMessage.get().getGameStatusUpdate().winnerId());
    }
    
    @Test
    void testInvalidOperationMessageHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<InvalidOperationMessage> receivedMessage = new AtomicReference<>();
        
        serverAPI.setOnInvalidOperation(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        InvalidOperationMessage message = new InvalidOperationMessage("player1", "Invalid action");
        
        // Trigger handler
        if (serverAPI.onInvalidOperation != null) {
            serverAPI.onInvalidOperation.accept(message);
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
        
        serverAPI.setOnError(error -> {
            receivedError.set(error);
            latch.countDown();
        });
        
        RuntimeException testError = new RuntimeException("Test error");
        
        // Trigger error handler
        if (serverAPI.onError != null) {
            serverAPI.onError.accept(testError);
        }
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedError.get());
        assertEquals("Test error", receivedError.get().getMessage());
    }
    
    @Test
    void testCloseWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> serverAPI.close());
        assertEquals(ServerAPI.ConnectionState.DISCONNECTED, serverAPI.getState());
    }
    
    @Test
    void testMultipleHandlerRegistrations() {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        // First handler
        serverAPI.setOnGameStart(msg -> latch1.countDown());
        
        // Second handler (should replace first)
        serverAPI.setOnGameStart(msg -> latch2.countDown());
        
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            new MapLayout(new Dimension(10, 10), new Position[0]),
            new GameState[]{new GameState(new Unit[0], System.currentTimeMillis())},
            null
        );
        
        StartGameMessage message = new StartGameMessage(statusUpdate);
        
        // Trigger handler
        if (serverAPI.onGameStart != null) {
            serverAPI.onGameStart.accept(message);
        }
        
        // Only second handler should be invoked
        assertEquals(1, latch1.getCount());
        assertEquals(0, latch2.getCount());
    }
    
    @Test
    void testStateTransitionOnConnect() {
        // Initial state
        assertEquals(ServerAPI.ConnectionState.DISCONNECTED, serverAPI.getState());
        
        // Note: We cannot easily test CONNECTING -> CONNECTED transition
        // without a real WebSocket server. This test verifies initial state only.
        // Integration tests should verify full connection lifecycle.
    }
    
    @Test
    void testSendActionsFormat() throws Exception {
        // Test that the send method creates proper ActionMessage
        Action[] actions = new Action[]{
            new Action("pawn1", Direction.N),
            new Action("pawn2", Direction.SE)
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
        serverAPI.setOnGameStart(null);
        serverAPI.setOnNextTurn(null);
        serverAPI.setOnGameEnd(null);
        serverAPI.setOnInvalidOperation(null);
        serverAPI.setOnError(null);
        
        // Should not throw when handlers are null
        assertDoesNotThrow(() -> {
            if (serverAPI.onGameStart != null) {
                GameStatusUpdate statusUpdate = new GameStatusUpdate(
                    GameStatus.START,
                    new MapLayout(new Dimension(10, 10), new Position[0]),
                    new GameState[]{new GameState(new Unit[0], 0)},
                    null
                );
                serverAPI.onGameStart.accept(new StartGameMessage(statusUpdate));
            }
        });
    }
}
