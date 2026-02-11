package sl.hackathon.server.communication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketAdapter.
 * Tests endpoint lifecycle, message dispatch, and callback invocation.
 */
class WebSocketAdapterTest {

    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

    private WebSocketAdapter adapter;
    private ClientRegistry clientRegistry;
    private Session mockSession;

    private AtomicInteger connectCallbackCount;
    private AtomicInteger disconnectCallbackCount;
    private AtomicInteger messageCallbackCount;
    private AtomicReference<String> lastConnectedPlayerId;
    private AtomicReference<String> lastDisconnectedPlayerId;
    private AtomicReference<Message> lastReceivedMessage;

    @BeforeEach
    void setUp() {
        adapter = new WebSocketAdapter();
        clientRegistry = new ClientRegistry();
        
        // Setup mock session
        mockSession = mock(Session.class);
        RemoteEndpoint.Basic mockRemote = mock(RemoteEndpoint.Basic.class);
        when(mockSession.getBasicRemote()).thenReturn(mockRemote);
        when(mockSession.isOpen()).thenReturn(true);
        
        // Setup callback tracking
        connectCallbackCount = new AtomicInteger(0);
        disconnectCallbackCount = new AtomicInteger(0);
        messageCallbackCount = new AtomicInteger(0);
        lastConnectedPlayerId = new AtomicReference<>();
        lastDisconnectedPlayerId = new AtomicReference<>();
        lastReceivedMessage = new AtomicReference<>();
        
        // Configure WebSocketAdapter with registry and callbacks
        WebSocketAdapter.setClientRegistry(clientRegistry);
        WebSocketAdapter.setOnClientConnect(playerId -> {
            connectCallbackCount.incrementAndGet();
            lastConnectedPlayerId.set(playerId);
        });
        WebSocketAdapter.setOnClientDisconnect(playerId -> {
            disconnectCallbackCount.incrementAndGet();
            lastDisconnectedPlayerId.set(playerId);
        });
        WebSocketAdapter.setOnMessage((playerId, message) -> {
            messageCallbackCount.incrementAndGet();
            lastReceivedMessage.set(message);
        });
    }

    @AfterEach
    void tearDown() {
        WebSocketAdapter.reset();
    }

    // ===== CONNECTION LIFECYCLE TESTS =====

    @Test
    void testOnOpenRegistersClientAndInvokesCallback() {
        adapter.onOpen(mockSession);
        
        // Verify client was registered
        assertEquals(1, clientRegistry.size());
        assertFalse(clientRegistry.isReady()); // Only 1 player
        
        // Verify callback was invoked with player-1
        assertEquals(1, connectCallbackCount.get());
        assertEquals("player-1", lastConnectedPlayerId.get());
    }

    @Test
    void testOnOpenWithTwoClientsRegistersAsPlayer1AndPlayer2() {
        Session mockSession2 = mock(Session.class);
        RemoteEndpoint.Basic mockRemote2 = mock(RemoteEndpoint.Basic.class);
        when(mockSession2.getBasicRemote()).thenReturn(mockRemote2);
        when(mockSession2.isOpen()).thenReturn(true);
        
        WebSocketAdapter adapter2 = new WebSocketAdapter();
        
        adapter.onOpen(mockSession);
        adapter2.onOpen(mockSession2);
        
        // Verify both clients registered
        assertEquals(2, clientRegistry.size());
        assertTrue(clientRegistry.isReady());
        
        // Verify callbacks invoked for both
        assertEquals(2, connectCallbackCount.get());
    }

    @Test
    void testOnOpenWithMaxPlayersReachedClosesConnection() throws Exception {
        Session mockSession2 = mock(Session.class);
        Session mockSession3 = mock(Session.class);
        RemoteEndpoint.Basic mockRemote2 = mock(RemoteEndpoint.Basic.class);
        RemoteEndpoint.Basic mockRemote3 = mock(RemoteEndpoint.Basic.class);
        
        when(mockSession2.getBasicRemote()).thenReturn(mockRemote2);
        when(mockSession2.isOpen()).thenReturn(true);
        when(mockSession3.getBasicRemote()).thenReturn(mockRemote3);
        when(mockSession3.isOpen()).thenReturn(true);
        
        WebSocketAdapter adapter2 = new WebSocketAdapter();
        WebSocketAdapter adapter3 = new WebSocketAdapter();
        
        // Connect first two clients
        adapter.onOpen(mockSession);
        adapter2.onOpen(mockSession2);
        
        // Third client should be rejected
        adapter3.onOpen(mockSession3);
        
        // Only 2 clients should be registered
        assertEquals(2, clientRegistry.size());
        
        // Third session should be closed
        verify(mockSession3, times(1)).close(any(CloseReason.class));
        
        // Only 2 connect callbacks
        assertEquals(2, connectCallbackCount.get());
    }

    @Test
    void testOnOpenWithoutClientRegistryClosesConnection() throws Exception {
        WebSocketAdapter.setClientRegistry(null);
        
        adapter.onOpen(mockSession);
        
        // Session should be closed
        verify(mockSession, times(1)).close(any(CloseReason.class));
        
        // No callback should be invoked
        assertEquals(0, connectCallbackCount.get());
    }

    // ===== MESSAGE HANDLING TESTS =====

    @Test
    void testOnMessageForwardsToClientHandler() throws JsonProcessingException {
        adapter.onOpen(mockSession);

        String json = objectMapper.writeValueAsString(new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{}, 0L)));
        adapter.onMessage(json, mockSession);
        
        // Verify message callback was invoked
        assertEquals(1, messageCallbackCount.get());
        assertNotNull(lastReceivedMessage.get());
        assertInstanceOf(StartGameMessage.class, lastReceivedMessage.get());
    }

    @Test
    void testOnMessageBeforeConnectionIgnored() {
        // Call onMessage without onOpen
        String json = "{\"type\":\"START_GAME\",\"map\":{\"dimension\":{\"width\":10,\"height\":10},\"walls\":[]},\"initialUnits\":[],\"timestamp\":1000}";
        
        // Should not throw exception
        assertDoesNotThrow(() -> adapter.onMessage(json, mockSession));
        
        // No callback should be invoked
        assertEquals(0, messageCallbackCount.get());
    }

    @Test
    void testOnMessageWithInvalidJsonDoesNotCrash() {
        adapter.onOpen(mockSession);
        
        String invalidJson = "{this is not valid json}";
        
        // Should not throw exception
        assertDoesNotThrow(() -> adapter.onMessage(invalidJson, mockSession));
    }

    @Test
    void testOnMessageWithNullHandlerDoesNotCrash() {
        String json = "{\"type\":\"START_GAME\",\"map\":{\"dimension\":{\"width\":10,\"height\":10},\"walls\":[]},\"initialUnits\":[],\"timestamp\":1000}";
        
        // Should not throw exception when handler is null
        assertDoesNotThrow(() -> adapter.onMessage(json, mockSession));
    }

    // ===== DISCONNECTION TESTS =====

    @Test
    void testOnCloseUnregistersClientAndInvokesCallback() {
        adapter.onOpen(mockSession);
        
        assertEquals(1, clientRegistry.size());
        
        CloseReason closeReason = new CloseReason(
            CloseReason.CloseCodes.NORMAL_CLOSURE, 
            "Client disconnected"
        );
        adapter.onClose(mockSession, closeReason);
        
        // Verify client was unregistered
        assertEquals(0, clientRegistry.size());
        
        // Verify disconnect callback was invoked
        assertEquals(1, disconnectCallbackCount.get());
        assertEquals("player-1", lastDisconnectedPlayerId.get());
    }

    @Test
    void testOnCloseWithoutPriorOpenDoesNotCrash() {
        CloseReason closeReason = new CloseReason(
            CloseReason.CloseCodes.NORMAL_CLOSURE, 
            "Client disconnected"
        );
        
        // Should not throw exception
        assertDoesNotThrow(() -> adapter.onClose(mockSession, closeReason));
        
        // No disconnect callback
        assertEquals(0, disconnectCallbackCount.get());
    }

    @Test
    void testOnCloseWithNullCloseReasonDoesNotCrash() {
        adapter.onOpen(mockSession);
        
        // Should not throw exception
        assertDoesNotThrow(() -> adapter.onClose(mockSession, null));
        
        assertEquals(1, disconnectCallbackCount.get());
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    void testOnErrorLogsAndCleansUp() {
        adapter.onOpen(mockSession);
        
        Throwable error = new RuntimeException("Test error");
        
        // Should not throw exception
        assertDoesNotThrow(() -> adapter.onError(mockSession, error));
    }

    @Test
    void testOnErrorWithoutPriorOpenDoesNotCrash() {
        Throwable error = new RuntimeException("Test error");
        
        // Should not throw exception
        assertDoesNotThrow(() -> adapter.onError(mockSession, error));
    }

    // ===== CALLBACK CONFIGURATION TESTS =====

    @Test
    void testSetClientRegistry() {
        ClientRegistry newRegistry = new ClientRegistry();
        
        WebSocketAdapter.setClientRegistry(newRegistry);
        
        // Verify new registry is used
        WebSocketAdapter newAdapter = new WebSocketAdapter();
        newAdapter.onOpen(mockSession);
        
        assertEquals(1, newRegistry.size());
    }

    @Test
    void testCallbacksCanBeNull() {
        // Set all callbacks to null
        WebSocketAdapter.setOnClientConnect(null);
        WebSocketAdapter.setOnClientDisconnect(null);
        WebSocketAdapter.setOnMessage(null);
        
        adapter.onOpen(mockSession);
        
        String json = "{\"type\":\"START_GAME\",\"playerId\":\"test-player\"}";
        adapter.onMessage(json, mockSession);
        
        CloseReason closeReason = new CloseReason(
            CloseReason.CloseCodes.NORMAL_CLOSURE, 
            "Client disconnected"
        );
        adapter.onClose(mockSession, closeReason);
        
        // Should not crash - all operations should complete
        assertEquals(0, connectCallbackCount.get());
        assertEquals(0, messageCallbackCount.get());
        assertEquals(0, disconnectCallbackCount.get());
    }

    // ===== RESET TESTS =====

    @Test
    void testResetClearsStaticState() throws IOException {
        WebSocketAdapter.reset();
        
        // Try to open connection without setting registry
        adapter.onOpen(mockSession);
        
        // Should close the session due to missing registry
        verify(mockSession, times(1)).close(any(CloseReason.class));
    }

    // ===== INTEGRATION TESTS =====

    @Test
    void testFullLifecycle() throws JsonProcessingException {
        // Connect
        adapter.onOpen(mockSession);
        assertEquals(1, connectCallbackCount.get());
        assertEquals("player-1", lastConnectedPlayerId.get());
        
        // Send message
        String json = objectMapper.writeValueAsString(new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{}, 0L)));
        adapter.onMessage(json, mockSession);
        assertEquals(1, messageCallbackCount.get());
        
        // Disconnect
        CloseReason closeReason = new CloseReason(
            CloseReason.CloseCodes.NORMAL_CLOSURE, 
            "Client disconnected"
        );
        adapter.onClose(mockSession, closeReason);
        assertEquals(1, disconnectCallbackCount.get());
        assertEquals("player-1", lastDisconnectedPlayerId.get());
        
        // Registry should be empty
        assertEquals(0, clientRegistry.size());
    }

    @Test
    void testTwoClientFullLifecycle() throws JsonProcessingException {
        Session mockSession2 = mock(Session.class);
        RemoteEndpoint.Basic mockRemote2 = mock(RemoteEndpoint.Basic.class);
        when(mockSession2.getBasicRemote()).thenReturn(mockRemote2);
        when(mockSession2.isOpen()).thenReturn(true);
        
        WebSocketAdapter adapter2 = new WebSocketAdapter();
        
        // Connect both clients
        adapter.onOpen(mockSession);
        adapter2.onOpen(mockSession2);
        
        assertEquals(2, connectCallbackCount.get());
        assertTrue(clientRegistry.isReady());
        
        // Both send messages
        String json = objectMapper.writeValueAsString(new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{}, 0L)));
        adapter.onMessage(json, mockSession);
        adapter2.onMessage(json, mockSession2);
        
        assertEquals(2, messageCallbackCount.get());
        
        // Disconnect first client
        CloseReason closeReason = new CloseReason(
            CloseReason.CloseCodes.NORMAL_CLOSURE, 
            "Client disconnected"
        );
        adapter.onClose(mockSession, closeReason);
        
        assertEquals(1, disconnectCallbackCount.get());
        assertEquals(1, clientRegistry.size());
        assertFalse(clientRegistry.isReady());
        
        // Disconnect second client
        adapter2.onClose(mockSession2, closeReason);
        
        assertEquals(2, disconnectCallbackCount.get());
        assertEquals(0, clientRegistry.size());
    }
}
