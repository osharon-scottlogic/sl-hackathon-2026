package sl.hackathon.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.websocket.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;
import sl.hackathon.server.engine.GameEngineImpl;
import sl.hackathon.server.orchestration.GameServer;
import sl.hackathon.server.orchestration.ServerConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GameServer with real WebSocket clients.
 * Tests the full game flow:
 * - Two clients connect
 * - Game starts
 * - Multiple turns are played
 * - Game ends
 * - Message sequence and state consistency are verified
 */
class GameServerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(GameServerIntegrationTest.class);
    
    private static final int TEST_PORT = 18080;
    private static final String SERVER_URL = "ws://localhost:" + TEST_PORT + "/game";
    private static final int TURN_TIME_LIMIT = 5000;
    private static final int SERVER_VERSION = 1;
    private static final String CLIENT_1_ID = "player-1";
    private static final String CLIENT_2_ID = "player-2";
    
    private GameServer gameServer;
    private Thread serverThread;
    
    private TestWebSocketClient client1;
    private TestWebSocketClient client2;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        objectMapper = JsonMapper.builder().build();
        // Create server configuration
        Dimension dimension = new Dimension(10, 10);
        Position[] walls = new Position[]{
            new Position(3, 3),
            new Position(3, 4),
            new Position(6, 6)
        };
        Position[] baseLocations = new Position[]{
            new Position(1, 1),
            new Position(8, 8)
        };
        MapConfig mapConfig = new MapConfig(dimension, walls, baseLocations);
        ServerConfig config = new ServerConfig(TEST_PORT, mapConfig, TURN_TIME_LIMIT, SERVER_VERSION);
        
        // Create game engine
        GameEngine gameEngine = new GameEngineImpl();
        
        // Create and start game server
        gameServer = new GameServer(config, gameEngine);
        serverThread = new Thread(() -> {
            try {
                gameServer.start();
            } catch (Exception e) {
                logger.error("Server failed to start", e);
            }
        });
        serverThread.start();
        
        // Give server time to initialize
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @AfterEach
    void tearDown() {
        // Close clients
        if (client1 != null) {
            client1.close();
        }
        if (client2 != null) {
            client2.close();
        }
        
        // Stop server
        if (gameServer != null) {
            gameServer.stop();
        }
        
        // Wait for server thread to terminate
        if (serverThread != null) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
//    @Test
    void testFullGameFlow_twoClientsConnectAndPlayGame() throws Exception {
        // Create and connect two clients
        client1 = new TestWebSocketClient();
        client2 = new TestWebSocketClient();

        Session session1 = client1.connect(SERVER_URL + "?callsign=" + CLIENT_1_ID + "&clientVersion=test&expectedServerVersion=" + SERVER_VERSION);
        Session session2 = client2.connect(SERVER_URL + "?callsign=" + CLIENT_2_ID + "&clientVersion=test&expectedServerVersion=" + SERVER_VERSION);

        assertNotNull(session1, "Client 1 should connect");
        assertNotNull(session2, "Client 2 should connect");
        assertTrue(session1.isOpen(), "Client 1 session should be open");
        assertTrue(session2.isOpen(), "Client 2 session should be open");

        // Wait for game start message
        Message startMsg1 = client1.waitForMessage(5000);
        Message startMsg2 = client2.waitForMessage(5000);

        assertNotNull(startMsg1, "Client 1 should receive game start");
        assertNotNull(startMsg2, "Client 2 should receive game start");
        assertInstanceOf(StartGameMessage.class, startMsg1);
        assertInstanceOf(StartGameMessage.class, startMsg2);

        StartGameMessage start1 = (StartGameMessage) startMsg1;
        StartGameMessage start2 = (StartGameMessage) startMsg2;

        assertNotNull(start1.getGameStart(), "Start message should contain game start");
        assertNotNull(start1.getGameStart().map(), "Game start should contain map");
        assertEquals(10, start1.getGameStart().map().dimension().width());
        assertEquals(10, start1.getGameStart().map().dimension().height());

        // Verify both clients received the same game state
        assertEquals(
            objectMapper.writeValueAsString(start1.getGameStart().map()),
            objectMapper.writeValueAsString(start2.getGameStart().map())
        );

        // Play 3 turns
        for (int turn = 0; turn < 3; turn++) {
            logger.info("Playing turn {}", turn);

            // Wait for next turn message
            Message turnMsg1 = client1.waitForMessage(5000);
            Message turnMsg2 = client2.waitForMessage(5000);

            assertNotNull(turnMsg1, "Client 1 should receive turn " + turn);
            assertNotNull(turnMsg2, "Client 2 should receive turn " + turn);
            assertInstanceOf(EndGameMessage.class, turnMsg1);
            assertInstanceOf(EndGameMessage.class, turnMsg2);

            NextTurnMessage next1 = (NextTurnMessage) turnMsg1;
            NextTurnMessage next2 = (NextTurnMessage) turnMsg2;

            assertEquals(CLIENT_1_ID, next1.getPlayerId(), "Player ID should match");
            assertEquals("player-2", next2.getPlayerId(), "Player ID should match");
            assertNotNull(next1.getGameState(), "Turn message should contain game state");

            // Send actions from both clients
            Action[] actions1 = new Action[]{};  // Empty actions (no movement)
            Action[] actions2 = new Action[]{};  // Empty actions (no movement)

            ActionMessage actionMsg1 = new ActionMessage("player-"+turn, actions1);
            ActionMessage actionMsg2 = new ActionMessage("player-"+turn, actions2);

            client1.sendMessage(actionMsg1);
            client2.sendMessage(actionMsg2);

            // Small delay to allow server to process
            Thread.sleep(200);
        }

        // Verify game is still running (or has ended naturally)
        assertTrue(session1.isOpen() || client1.hasReceivedEndGame(),
            "Client 1 should still be connected or received end game");
        assertTrue(session2.isOpen() || client2.hasReceivedEndGame(),
            "Client 2 should still be connected or received end game");
    }
    
//    @Test
//    void testMessageSequence_correctOrderAndTiming() throws Exception {
//        // Create and connect two clients
//        client1 = new TestWebSocketClient();
//        client2 = new TestWebSocketClient();
//
//        client1.connect(SERVER_URL);
//        client2.connect(SERVER_URL);
//
//        // Verify message sequence for client 1
//        List<Message> client1Messages = new ArrayList<>();
//
//        // Client 1: Should receive PlayerAssignedMessage first
//        Message msg = client1.waitForMessage(5000);
//        assertNotNull(msg, "Client 1 should receive player assignment");
//        assertInstanceOf(PlayerAssignedMessage.class, msg, "First message should be PlayerAssignedMessage but got: " + msg.getClass().getSimpleName());
//        client1Messages.add(msg);
//
//        // Client 2: Should also receive PlayerAssignedMessage
//        Message msg2 = client2.waitForMessage(1000);
//        assertNotNull(msg2, "Client 2 should receive player assignment");
//        assertInstanceOf(PlayerAssignedMessage.class, msg2);
//
//        // Client 1: Should receive StartGameMessage second
//        msg = client1.waitForMessage(1000);
//        assertNotNull(msg, "Client 1 should receive game start");
//        assertInstanceOf(StartGameMessage.class, msg, "Second message should be StartGameMessage but got: " + msg.getClass().getSimpleName());
//        client1Messages.add(msg);
//
//        // Client 2: Should also receive StartGameMessage
//        msg2 = client2.waitForMessage(1000);
//        assertNotNull(msg2, "Client 2 should receive game start");
//        assertInstanceOf(StartGameMessage.class, msg2);
//
//        // Client 1: Should receive NextTurnMessage third
//        msg = client1.waitForMessage(1000);
//        assertNotNull(msg, "Client 1 should receive next turn");
//        assertInstanceOf(NextTurnMessage.class, msg, "Third message should be NextTurnMessage but got: " + msg.getClass().getSimpleName());
//        client1Messages.add(msg);
//
//        // Client 2: Should also receive NextTurnMessage
//        msg2 = client2.waitForMessage(1000);
//        assertNotNull(msg2, "Client 2 should receive next turn");
//        assertInstanceOf(NextTurnMessage.class, msg2);
//
//        // Send actions from both clients with their correct player IDs
//        NextTurnMessage turnMsg1 = (NextTurnMessage) msg;
//        NextTurnMessage turnMsg2 = (NextTurnMessage) msg2;
//
//        client1.sendMessage(new ActionMessage(turnMsg1.getPlayerId(), new Action[]{}));
//        client2.sendMessage(new ActionMessage(turnMsg2.getPlayerId(), new Action[]{}));
//
//        // Wait a bit to see if we receive another turn or end game
//        Thread.sleep(1000);
//
//        // Verify sequence is correct
//        assertTrue(client1Messages.size() >= 3, "Should have received at least 3 messages");
//        assertInstanceOf(PlayerAssignedMessage.class, client1Messages.get(0));
//        assertInstanceOf(StartGameMessage.class, client1Messages.get(1));
//        assertInstanceOf(NextTurnMessage.class, client1Messages.get(2));
//    }
}
