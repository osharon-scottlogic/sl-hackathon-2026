package sl.hackathon.client.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.client.api.MessageHandler;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameMessageRouter.
 * Tests message routing, deserialization, error handling, and delegation to handlers.
 */
class MessageRouterTest {
    
    private MessageRouter router;
    private TestMessageHandler testHandler;
    
    @BeforeEach
    void setUp() {
        testHandler = new TestMessageHandler();
        router = new MessageRouter(testHandler);
    }
    
    @Test
    void testConstructorWithNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> new MessageRouter(null));
    }
    
    @Test
    void testRouteStartGameMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        testHandler.setOnStartGame(msg -> latch.countDown());
        
        long now = System.currentTimeMillis();
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        Unit[] initialUnits = new Unit[]{new Unit(1, "p1", UnitType.PAWN, new Position(1, 1))};
        GameStart gameStart = new GameStart(mapLayout, initialUnits, now);

        StartGameMessage message = new StartGameMessage(gameStart);
        
        router.routeMessage(message);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.startGameCount.get());
    }
    
    @Test
    void testRouteNextTurnMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NextTurnMessage> receivedMsg = new AtomicReference<>();
        
        testHandler.setOnNextTurn(msg -> {
            receivedMsg.set(msg);
            latch.countDown();
        });
        
        GameState state = new GameState(new Unit[0], System.currentTimeMillis());
        NextTurnMessage message = new NextTurnMessage("player1", state, 15000);
        
        router.routeMessage(message);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.nextTurnCount.get());
        assertEquals("player1", receivedMsg.get().getPlayerId());
    }
    
    @Test
    void testRouteEndGameMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        testHandler.setOnEndGame(msg -> latch.countDown());
        
        long now = System.currentTimeMillis();
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameEnd gameEnd = new GameEnd(mapLayout, new GameDelta[0], "winner1", now);
        EndGameMessage message = new EndGameMessage(gameEnd);
        
        router.routeMessage(message);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.endGameCount.get());
    }
    
    @Test
    void testRouteInvalidOperationMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedReason = new AtomicReference<>();
        
        testHandler.setOnInvalidOperation(msg -> {
            receivedReason.set(msg.getReason());
            latch.countDown();
        });
        
        InvalidOperationMessage message = new InvalidOperationMessage("player1", "Invalid move");
        
        router.routeMessage(message);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.invalidOperationCount.get());
        assertEquals("Invalid move", receivedReason.get());
    }
    
    @Test
    void testRouteMessageFromJson() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        testHandler.setOnStartGame(msg -> latch.countDown());
        
        long now = System.currentTimeMillis();
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameStart gameStart = new GameStart(mapLayout, new Unit[0], now);
        StartGameMessage originalMessage = new StartGameMessage(gameStart);
        String json = MessageCodec.serialize(originalMessage);
        
        router.routeMessage(json);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.startGameCount.get());
    }
    
    @Test
    void testRouteInvalidJson() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        testHandler.setOnError(error -> latch.countDown());
        
        router.routeMessage("{invalid json}");
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.errorCount.get());
    }
    
    @Test
    void testRouteNullMessage() {
        router.routeMessage((Message) null);
        
        // Should not invoke any handlers
        assertEquals(0, testHandler.startGameCount.get());
        assertEquals(0, testHandler.nextTurnCount.get());
        assertEquals(0, testHandler.endGameCount.get());
        assertEquals(0, testHandler.invalidOperationCount.get());
    }
    
    @Test
    void testRouteNullJsonString() {
        router.routeMessage((String) null);
        
        // Should not invoke any handlers
        assertEquals(0, testHandler.startGameCount.get());
        assertEquals(0, testHandler.errorCount.get());
    }
    
    @Test
    void testRouteEmptyJsonString() {
        router.routeMessage("");
        
        // Should not invoke any handlers
        assertEquals(0, testHandler.startGameCount.get());
        assertEquals(0, testHandler.errorCount.get());
    }
    
    @Test
    void testRouteBlankJsonString() {
        router.routeMessage("   ");
        
        // Should not invoke any handlers
        assertEquals(0, testHandler.startGameCount.get());
        assertEquals(0, testHandler.errorCount.get());
    }
    
    @Test
    void testMultipleMessagesRouted() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        
        testHandler.setOnStartGame(msg -> latch.countDown());
        testHandler.setOnNextTurn(msg -> latch.countDown());
        testHandler.setOnEndGame(msg -> latch.countDown());
        
        long now = System.currentTimeMillis();
        GameState state = new GameState(new Unit[0], now);
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameStart gameStart = new GameStart(mapLayout, new Unit[0], now);
        
        router.routeMessage(new StartGameMessage(gameStart));
        router.routeMessage(new NextTurnMessage("p1", state, 15000));
        router.routeMessage(new EndGameMessage(new GameEnd(mapLayout, new GameDelta[0], "p1", now)));
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.startGameCount.get());
        assertEquals(1, testHandler.nextTurnCount.get());
        assertEquals(1, testHandler.endGameCount.get());
    }
    
    @Test
    void testErrorInHandlerCaptured() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        testHandler.setOnStartGame(msg -> {
            throw new RuntimeException("Handler error");
        });
        
        testHandler.setOnError(error -> latch.countDown());
        
        long now = System.currentTimeMillis();
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
        GameStart gameStart = new GameStart(mapLayout, new Unit[0], now);
        
        router.routeMessage(new StartGameMessage(gameStart));
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, testHandler.errorCount.get());
    }
    
    /**
     * Test implementation of MessageHandler for testing purposes.
     */
    private static class TestMessageHandler implements MessageHandler {
        
        final AtomicInteger startGameCount = new AtomicInteger(0);
        final AtomicInteger nextTurnCount = new AtomicInteger(0);
        final AtomicInteger endGameCount = new AtomicInteger(0);
        final AtomicInteger invalidOperationCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);

        private java.util.function.Consumer<StartGameMessage> onStartGame;
        private java.util.function.Consumer<NextTurnMessage> onNextTurn;
        private java.util.function.Consumer<EndGameMessage> onEndGame;
        private java.util.function.Consumer<InvalidOperationMessage> onInvalidOperation;
        private java.util.function.Consumer<Throwable> onError;
        
        void setOnStartGame(java.util.function.Consumer<StartGameMessage> handler) {
            this.onStartGame = handler;
        }
        
        void setOnNextTurn(java.util.function.Consumer<NextTurnMessage> handler) {
            this.onNextTurn = handler;
        }
        
        void setOnEndGame(java.util.function.Consumer<EndGameMessage> handler) {
            this.onEndGame = handler;
        }
        
        void setOnInvalidOperation(java.util.function.Consumer<InvalidOperationMessage> handler) {
            this.onInvalidOperation = handler;
        }
        
        void setOnError(java.util.function.Consumer<Throwable> handler) {
            this.onError = handler;
        }

        @Override
        public void handleStartGame(StartGameMessage message) {
            startGameCount.incrementAndGet();
            if (onStartGame != null) {
                onStartGame.accept(message);
            }
        }
        
        @Override
        public void handleNextTurn(NextTurnMessage message) {
            nextTurnCount.incrementAndGet();
            if (onNextTurn != null) {
                onNextTurn.accept(message);
            }
        }
        
        @Override
        public void handleGameEnd(EndGameMessage message) {
            endGameCount.incrementAndGet();
            if (onEndGame != null) {
                onEndGame.accept(message);
            }
        }
        
        @Override
        public void handleInvalidOperation(InvalidOperationMessage message) {
            invalidOperationCount.incrementAndGet();
            if (onInvalidOperation != null) {
                onInvalidOperation.accept(message);
            }
        }
        
        @Override
        public void handleError(Throwable error) {
            errorCount.incrementAndGet();
            if (onError != null) {
                onError.accept(error);
            }
        }
    }
}
