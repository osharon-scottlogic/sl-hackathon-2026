package sl.hackathon.server.communication;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClientRegistry.
 * Tests registration flow, broadcast, routing, and player limits.
 */
class ClientRegistryTest {

    private ClientRegistry registry;
    private Session mockSession1;
    private Session mockSession2;
    private Session mockSession3;
    private RemoteEndpoint.Basic mockRemote1;
    private RemoteEndpoint.Basic mockRemote2;
    private RemoteEndpoint.Basic mockRemote3;

    @BeforeEach
    void setUp() {
        registry = new ClientRegistry();
        
        // Setup mock sessions and remotes
        mockSession1 = mock(Session.class);
        mockRemote1 = mock(RemoteEndpoint.Basic.class);
        when(mockSession1.getBasicRemote()).thenReturn(mockRemote1);
        when(mockSession1.isOpen()).thenReturn(true);
        
        mockSession2 = mock(Session.class);
        mockRemote2 = mock(RemoteEndpoint.Basic.class);
        when(mockSession2.getBasicRemote()).thenReturn(mockRemote2);
        when(mockSession2.isOpen()).thenReturn(true);
        
        mockSession3 = mock(Session.class);
        mockRemote3 = mock(RemoteEndpoint.Basic.class);
        when(mockSession3.getBasicRemote()).thenReturn(mockRemote3);
        when(mockSession3.isOpen()).thenReturn(true);
    }

    // ===== REGISTRATION TESTS =====

    @Test
    void testRegisterFirstClientAsPlayer1() {
        ClientHandler handler = new ClientHandler(mockSession1);
        
        String playerId = registry.register(handler);
        
        assertEquals("player-1", playerId);
        assertEquals(1, registry.size());
        assertFalse(registry.isReady());
    }

    @Test
    void testRegisterSecondClientAsPlayer2() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        String playerId1 = registry.register(handler1);
        String playerId2 = registry.register(handler2);
        
        assertEquals("player-1", playerId1);
        assertEquals("player-2", playerId2);
        assertEquals(2, registry.size());
        assertTrue(registry.isReady());
    }

    @Test
    void testRegisterNullHandlerThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.register(null);
        });
        assertEquals("ClientHandler cannot be null", exception.getMessage());
    }

    @Test
    void testRegisterThirdClientThrowsException() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        ClientHandler handler3 = new ClientHandler(mockSession3);
        
        registry.register(handler1);
        registry.register(handler2);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            registry.register(handler3);
        });
        assertTrue(exception.getMessage().contains("Maximum players (2) already registered"));
    }

    @Test
    void testIsReadyWhenNoPlayers() {
        assertFalse(registry.isReady());
    }

    @Test
    void testIsReadyWhenOnePlayer() {
        ClientHandler handler = new ClientHandler(mockSession1);
        registry.register(handler);
        
        assertFalse(registry.isReady());
    }

    @Test
    void testIsReadyWhenTwoPlayers() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        assertTrue(registry.isReady());
    }

    // ===== BROADCAST TESTS =====

    @Test
    void testBroadcastToAllClients() throws IOException {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        Message message = new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{},0L));
        registry.broadcast(message);
        
        verify(mockRemote1, times(1)).sendText(anyString());
        verify(mockRemote2, times(1)).sendText(anyString());
    }

    @Test
    void testBroadcastWithNoClients() {
        Message message = new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{},0L));
        
        // Should not throw exception
        assertDoesNotThrow(() -> registry.broadcast(message));
    }

    @Test
    void testBroadcastWithNullMessageThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.broadcast(null);
        });
        assertEquals("Message cannot be null", exception.getMessage());
    }

    @Test
    void testBroadcastContinuesOnIndividualFailure() throws IOException {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        // Make first send fail
        doThrow(new IOException("Network error")).when(mockRemote1).sendText(anyString());

        Message message = new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{},0L));
        
        // Should not throw exception
        assertDoesNotThrow(() -> registry.broadcast(message));
        
        // Second client should still receive the message
        verify(mockRemote2, times(1)).sendText(anyString());
    }

    // ===== SEND TO SPECIFIC PLAYER TESTS =====

    @Test
    void testSendToSpecificPlayer() throws IOException {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);

        Message message = new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{},0L));
        registry.send("player-1", message);
        
        verify(mockRemote1, times(1)).sendText(anyString());
        verify(mockRemote2, never()).sendText(anyString());
    }

    @Test
    void testSendToNonExistentPlayerThrowsException() {
        Message message = new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{},0L));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            registry.send("player-99", message);
        });
        assertTrue(exception.getMessage().contains("Player not registered"));
    }

    @Test
    void testSendWithNullPlayerIdThrowsException() {
        Message message = new StartGameMessage(new GameStart(new MapLayout(new Dimension(10,10), new Position[]{}), new Unit[]{},0L));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.send(null, message);
        });
        assertEquals("Player ID cannot be null", exception.getMessage());
    }

    @Test
    void testSendWithNullMessageThrowsException() {
        ClientHandler handler = new ClientHandler(mockSession1);
        registry.register(handler);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.send("player-1", null);
        });
        assertEquals("Message cannot be null", exception.getMessage());
    }

    // ===== UNREGISTER TESTS =====

    @Test
    void testUnregisterClient() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        assertEquals(2, registry.size());
        assertTrue(registry.isReady());
        
        registry.unregister(handler1.getClientId());
        
        assertEquals(1, registry.size());
        assertFalse(registry.isReady());
        assertEquals(List.of("player-2"), registry.getRegisteredPlayers());
    }

    @Test
    void testUnregisterNonExistentClient() {
        ClientHandler handler = new ClientHandler(mockSession1);
        registry.register(handler);
        
        // Should not throw exception
        assertDoesNotThrow(() -> registry.unregister("non-existent-id"));
        
        assertEquals(1, registry.size());
    }

    @Test
    void testUnregisterNullClientId() {
        // Should not throw exception
        assertDoesNotThrow(() -> registry.unregister(null));
    }

    @Test
    void testUnregisterAndReregister() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        registry.unregister(handler1.getClientId());
        
        // Should be able to register a new client as player-1
        ClientHandler handler3 = new ClientHandler(mockSession3);
        String playerId = registry.register(handler3);
        
        assertEquals("player-1", playerId);
        assertEquals(2, registry.size());
        assertTrue(registry.isReady());
    }

    // ===== LOOKUP TESTS =====

    @Test
    void testGetPlayerId() {
        ClientHandler handler = new ClientHandler(mockSession1);
        String playerId = registry.register(handler);
        
        Optional<String> foundPlayerId = registry.getPlayerId(handler.getClientId());
        
        assertTrue(foundPlayerId.isPresent());
        assertEquals(playerId, foundPlayerId.get());
    }

    @Test
    void testGetPlayerIdForNonExistentClient() {
        Optional<String> playerId = registry.getPlayerId("non-existent");
        
        assertFalse(playerId.isPresent());
    }

    @Test
    void testGetClientId() {
        ClientHandler handler = new ClientHandler(mockSession1);
        registry.register(handler);
        
        Optional<String> foundClientId = registry.getClientId("player-1");
        
        assertTrue(foundClientId.isPresent());
        assertEquals(handler.getClientId(), foundClientId.get());
    }

    @Test
    void testGetClientIdForNonExistentPlayer() {
        Optional<String> clientId = registry.getClientId("player-99");
        
        assertFalse(clientId.isPresent());
    }

    @Test
    void testGetHandler() {
        ClientHandler handler = new ClientHandler(mockSession1);
        registry.register(handler);
        
        Optional<ClientHandler> foundHandler = registry.getHandler("player-1");
        
        assertTrue(foundHandler.isPresent());
        assertEquals(handler, foundHandler.get());
    }

    @Test
    void testGetHandlerForNonExistentPlayer() {
        Optional<ClientHandler> handler = registry.getHandler("player-99");
        
        assertFalse(handler.isPresent());
    }

    @Test
    void testGetRegisteredPlayers() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        List<String> players = registry.getRegisteredPlayers();
        
        assertEquals(2, players.size());
        assertEquals(List.of("player-1", "player-2"), players);
    }

    @Test
    void testGetRegisteredPlayersWhenEmpty() {
        List<String> players = registry.getRegisteredPlayers();
        
        assertTrue(players.isEmpty());
    }

    // ===== CLEAR TESTS =====

    @Test
    void testClear() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        assertEquals(2, registry.size());
        
        registry.clear();
        
        assertEquals(0, registry.size());
        assertFalse(registry.isReady());
        assertTrue(registry.getRegisteredPlayers().isEmpty());
    }

    @Test
    void testClearWhenEmpty() {
        // Should not throw exception
        assertDoesNotThrow(() -> registry.clear());
        assertEquals(0, registry.size());
    }

    // ===== SIZE TESTS =====

    @Test
    void testSizeInitiallyZero() {
        assertEquals(0, registry.size());
    }

    @Test
    void testSizeIncrements() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        assertEquals(0, registry.size());
        
        registry.register(handler1);
        assertEquals(1, registry.size());
        
        registry.register(handler2);
        assertEquals(2, registry.size());
    }

    @Test
    void testSizeDecrementsOnUnregister() {
        ClientHandler handler1 = new ClientHandler(mockSession1);
        ClientHandler handler2 = new ClientHandler(mockSession2);
        
        registry.register(handler1);
        registry.register(handler2);
        
        assertEquals(2, registry.size());
        
        registry.unregister(handler1.getClientId());
        assertEquals(1, registry.size());
        
        registry.unregister(handler2.getClientId());
        assertEquals(0, registry.size());
    }
}
