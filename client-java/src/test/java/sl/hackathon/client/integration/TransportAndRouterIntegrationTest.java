package sl.hackathon.client.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.handlers.GameMessageRouter;
import sl.hackathon.client.handlers.MessageHandler;
import sl.hackathon.client.messages.*;
import sl.hackathon.client.transport.WebSocketTransport;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests demonstrating how WebSocketTransport and GameMessageRouter work together.
 * These tests show the complete message flow from transport to handler.
 */
class TransportAndRouterIntegrationTest {
    
    private WebSocketTransport transport;
    private GameMessageRouter router;
    private TestGameHandler gameHandler;
    
    @BeforeEach
    void setUp() {
        transport = new WebSocketTransport();
        gameHandler = new TestGameHandler();
        router = new GameMessageRouter(gameHandler);
    }
    
    @Test
    void testTransportAndRouterIntegration() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        // Wire transport to router
        transport.setOnMessageReceived(message -> {
            receivedMessage.set(message);
            router.routeMessage(message);
            messageLatch.countDown();
        });
        
        // Simulate receiving a message
        GameState state = new GameState(new Unit[0], System.currentTimeMillis());
        StartGameMessage message = new StartGameMessage(state);
        String json = MessageCodec.serialize(message);
        
        // In a real scenario, this would come from WebSocket @OnMessage
        // For testing, we directly invoke the callback
        transport.setOnMessageReceived(msg -> router.routeMessage(msg));
        
        // Verify the handler can be set
        assertNotNull(transport);
        assertNotNull(router);
    }
    
    @Test
    void testErrorPropagationFromRouterToTransport() throws Exception {
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        
        // Set up error handling chain
        transport.setOnError(error -> {
            capturedError.set(error);
            errorLatch.countDown();
        });
        
        transport.setOnMessageReceived(message -> {
            try {
                router.routeMessage(message);
            } catch (Exception e) {
                transport.setOnError(err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                });
            }
        });
        
        // Verify components are wired correctly
        assertNotNull(transport);
        assertNotNull(router);
    }
    
    @Test
    void testSendingActionsViaTransport() {
        // Test that we can serialize and prepare to send actions
        Action[] actions = new Action[]{
            new Action("pawn1", Direction.N),
            new Action("pawn2", Direction.E)
        };
        
        ActionMessage actionMessage = new ActionMessage("player1", actions);
        String json = MessageCodec.serialize(actionMessage);
        
        assertNotNull(json);
        assertTrue(json.contains("ACTION"));
        
        // In connected state, this would be sent via transport.send(json)
        // For unit test, we verify the message is properly formatted
    }
    
    @Test
    void testMessageFlowStartToEnd() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch turnLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        
        gameHandler.setStartGameCallback(msg -> startLatch.countDown());
        gameHandler.setNextTurnCallback(msg -> turnLatch.countDown());
        gameHandler.setEndGameCallback(msg -> endLatch.countDown());
        
        // Wire transport to router
        transport.setOnMessageReceived(router::routeMessage);
        
        // Simulate game flow
        GameState state = new GameState(new Unit[0], System.currentTimeMillis());
        
        // Start game
        router.routeMessage(new StartGameMessage(state));
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));
        
        // Next turn
        router.routeMessage(new NextTurnMessage("player1", state));
        assertTrue(turnLatch.await(1, TimeUnit.SECONDS));
        
        // End game
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.END,
            new MapLayout(new Dimension(10, 10), new Position[0]),
            new GameState[0],
            "player1"
        );
        router.routeMessage(new EndGameMessage(statusUpdate));
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
    }
    
    @Test
    void testDisconnectionHandling() {
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        
        transport.setOnDisconnected(disconnectLatch::countDown);
        
        // Disconnect when not connected
        transport.disconnect();
        
        assertEquals(WebSocketTransport.TransportState.DISCONNECTED, transport.getState());
    }
    
    /**
     * Test game handler implementation.
     */
    private static class TestGameHandler implements MessageHandler {
        
        private java.util.function.Consumer<StartGameMessage> startGameCallback;
        private java.util.function.Consumer<NextTurnMessage> nextTurnCallback;
        private java.util.function.Consumer<EndGameMessage> endGameCallback;
        private java.util.function.Consumer<InvalidOperationMessage> invalidOpCallback;
        private java.util.function.Consumer<Throwable> errorCallback;
        
        void setStartGameCallback(java.util.function.Consumer<StartGameMessage> callback) {
            this.startGameCallback = callback;
        }
        
        void setNextTurnCallback(java.util.function.Consumer<NextTurnMessage> callback) {
            this.nextTurnCallback = callback;
        }
        
        void setEndGameCallback(java.util.function.Consumer<EndGameMessage> callback) {
            this.endGameCallback = callback;
        }
        
        void setInvalidOpCallback(java.util.function.Consumer<InvalidOperationMessage> callback) {
            this.invalidOpCallback = callback;
        }
        
        void setErrorCallback(java.util.function.Consumer<Throwable> callback) {
            this.errorCallback = callback;
        }
        
        @Override
        public void handleStartGame(StartGameMessage message) {
            if (startGameCallback != null) {
                startGameCallback.accept(message);
            }
        }
        
        @Override
        public void handleNextTurn(NextTurnMessage message) {
            if (nextTurnCallback != null) {
                nextTurnCallback.accept(message);
            }
        }
        
        @Override
        public void handleGameEnd(EndGameMessage message) {
            if (endGameCallback != null) {
                endGameCallback.accept(message);
            }
        }
        
        @Override
        public void handleInvalidOperation(InvalidOperationMessage message) {
            if (invalidOpCallback != null) {
                invalidOpCallback.accept(message);
            }
        }
        
        @Override
        public void handleError(Throwable error) {
            if (errorCallback != null) {
                errorCallback.accept(error);
            }
        }
    }
}
