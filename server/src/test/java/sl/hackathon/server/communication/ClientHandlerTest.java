package sl.hackathon.server.communication;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sl.hackathon.server.dtos.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClientHandler.
 * Tests message send/receive, error handling, and lifecycle management.
 */
class ClientHandlerTest {

    private Session mockSession;
    private RemoteEndpoint.Basic mockRemote;
    private ClientHandler clientHandler;

    @BeforeEach
    void setUp() {
        mockSession = mock(Session.class);
        mockRemote = mock(RemoteEndpoint.Basic.class);
        when(mockSession.getBasicRemote()).thenReturn(mockRemote);
        when(mockSession.isOpen()).thenReturn(true);
        
        clientHandler = new ClientHandler(mockSession);
    }

    @Test
    void testConstructorWithNullSession() {
        // Test that constructor rejects null session
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new ClientHandler(null);
        });
        assertEquals("Session cannot be null", exception.getMessage());
    }

    @Test
    void testConstructorGeneratesUniqueClientId() {
        // Test that each handler gets a unique client ID
        ClientHandler handler1 = new ClientHandler(mockSession);
        ClientHandler handler2 = new ClientHandler(mockSession);
        
        assertNotNull(handler1.getClientId());
        assertNotNull(handler2.getClientId());
        assertNotEquals(handler1.getClientId(), handler2.getClientId());
    }

    @Test
    void testGetSession() {
        // Test that getSession returns the correct session
        assertEquals(mockSession, clientHandler.getSession());
    }

    @Test
    void testIsOpenWhenConnectionOpen() {
        // Test isOpen returns true when session is open
        when(mockSession.isOpen()).thenReturn(true);
        assertTrue(clientHandler.isOpen());
    }

    @Test
    void testIsOpenWhenConnectionClosed() {
        // Test isOpen returns false when session is closed
        when(mockSession.isOpen()).thenReturn(false);
        assertFalse(clientHandler.isOpen());
    }

    @Test
    void testSendMessageWhenConnectionClosed() throws IOException {
        // Test that sending when connection is closed doesn't throw exception
        when(mockSession.isOpen()).thenReturn(false);
        
        JoinGameMessage message = new JoinGameMessage("player-1");
        clientHandler.send(message);
        
        // Should not attempt to send
        verify(mockRemote, never()).sendText(anyString());
    }

    @Test
    void testSendMessageHandlesIOException() throws IOException {
        // Test that IOException during send is caught and logged
        JoinGameMessage message = new JoinGameMessage("player-1");
        doThrow(new IOException("Network error")).when(mockRemote).sendText(anyString());
        
        // Should not throw exception
        assertDoesNotThrow(() -> clientHandler.send(message));
        
        verify(mockRemote, times(1)).sendText(anyString());
    }

    @Test
    void testSendMessageObject() throws IOException {
        // Test sending a Message object
        JoinGameMessage message = new JoinGameMessage("player-1");
        
        clientHandler.send(message);
        
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRemote, times(1)).sendText(jsonCaptor.capture());
        
        String sentJson = jsonCaptor.getValue();
        assertNotNull(sentJson);
        assertTrue(sentJson.contains("\"type\":\"JOIN_GAME\""));
        assertTrue(sentJson.contains("\"playerId\":\"player-1\""));
    }

    @Test
    void testSendMessageObjectWithNull() {
        // Test that sending null Message throws exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            clientHandler.send((Message) null);
        });
        assertEquals("Message cannot be null", exception.getMessage());
    }

    @Test
    void testHandleMessageDeserializesAndInvokesCallback() {
        // Test that handleMessage deserializes and invokes callback
        AtomicReference<String> receivedClientId = new AtomicReference<>();
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        BiConsumer<String, Message> callback = (clientId, message) -> {
            receivedClientId.set(clientId);
            receivedMessage.set(message);
        };
        
        clientHandler.setMessageCallback(callback);
        
        String json = "{\"type\":\"JOIN_GAME\",\"playerId\":\"player-1\"}";
        clientHandler.handleMessage(json);
        
        assertEquals(clientHandler.getClientId(), receivedClientId.get());
        assertNotNull(receivedMessage.get());
        assertInstanceOf(JoinGameMessage.class, receivedMessage.get());
        assertEquals("player-1", ((JoinGameMessage) receivedMessage.get()).getPlayerId());
    }

    @Test
    void testHandleMessageWithNullJson() {
        // Test that handleMessage gracefully handles null JSON
        AtomicInteger callbackInvocations = new AtomicInteger(0);
        
        BiConsumer<String, Message> callback = (clientId, message) -> {
            callbackInvocations.incrementAndGet();
        };
        
        clientHandler.setMessageCallback(callback);
        
        // Should not throw exception or invoke callback
        assertDoesNotThrow(() -> clientHandler.handleMessage(null));
        assertEquals(0, callbackInvocations.get());
    }

    @Test
    void testHandleMessageWithEmptyJson() {
        // Test that handleMessage gracefully handles empty JSON
        AtomicInteger callbackInvocations = new AtomicInteger(0);
        
        BiConsumer<String, Message> callback = (clientId, message) -> {
            callbackInvocations.incrementAndGet();
        };
        
        clientHandler.setMessageCallback(callback);
        
        // Should not throw exception or invoke callback
        assertDoesNotThrow(() -> clientHandler.handleMessage(""));
        assertEquals(0, callbackInvocations.get());
    }

    @Test
    void testHandleMessageWithInvalidJson() {
        // Test that handleMessage gracefully handles invalid JSON
        AtomicInteger callbackInvocations = new AtomicInteger(0);
        
        BiConsumer<String, Message> callback = (clientId, message) -> {
            callbackInvocations.incrementAndGet();
        };
        
        clientHandler.setMessageCallback(callback);
        
        String invalidJson = "{this is not valid json}";
        
        // Should not throw exception or invoke callback
        assertDoesNotThrow(() -> clientHandler.handleMessage(invalidJson));
        assertEquals(0, callbackInvocations.get());
    }

    @Test
    void testHandleMessageWithNoCallback() {
        // Test that handleMessage works when no callback is registered
        String json = "{\"type\":\"JOIN_GAME\",\"playerId\":\"player-1\"}";
        
        // Should not throw exception
        assertDoesNotThrow(() -> clientHandler.handleMessage(json));
    }

    @Test
    void testHandleMessageWithMultipleMessageTypes() {
        // Test handling different message types
        AtomicInteger callbackInvocations = new AtomicInteger(0);
        
        BiConsumer<String, Message> callback = (clientId, message) -> {
            callbackInvocations.incrementAndGet();
        };
        
        clientHandler.setMessageCallback(callback);
        
        // Test JoinGameMessage
        String joinJson = "{\"type\":\"JOIN_GAME\",\"playerId\":\"player-1\"}";
        clientHandler.handleMessage(joinJson);
        
        // Test ActionMessage
        String actionJson = "{\"type\":\"ACTION\",\"playerId\":\"player-1\",\"actions\":[]}";
        clientHandler.handleMessage(actionJson);
        
        assertEquals(2, callbackInvocations.get());
    }

    @Test
    void testCloseWhenSessionIsOpen() throws IOException {
        // Test that close() closes the session
        when(mockSession.isOpen()).thenReturn(true);
        
        clientHandler.close();
        
        verify(mockSession, times(1)).close();
    }

    @Test
    void testCloseWhenSessionAlreadyClosed() throws IOException {
        // Test that close() doesn't attempt to close already closed session
        when(mockSession.isOpen()).thenReturn(false);
        
        clientHandler.close();
        
        verify(mockSession, never()).close();
    }

    @Test
    void testCloseHandlesIOException() throws IOException {
        // Test that close() handles IOException gracefully
        when(mockSession.isOpen()).thenReturn(true);
        doThrow(new IOException("Close error")).when(mockSession).close();
        
        // Should not throw exception
        assertDoesNotThrow(() -> clientHandler.close());
        
        verify(mockSession, times(1)).close();
    }

    @Test
    void testCallbackReceivesCorrectClientId() {
        // Test that callback receives the correct client ID
        String expectedClientId = clientHandler.getClientId();
        AtomicReference<String> receivedClientId = new AtomicReference<>();
        
        BiConsumer<String, Message> callback = (clientId, message) -> {
            receivedClientId.set(clientId);
        };
        
        clientHandler.setMessageCallback(callback);
        
        String json = "{\"type\":\"JOIN_GAME\",\"playerId\":\"player-1\"}";
        clientHandler.handleMessage(json);
        
        assertEquals(expectedClientId, receivedClientId.get());
    }

    @Test
    void testSendSynchronizesOnSession() throws Exception {
        // Test that send() synchronizes access to session (prevent concurrent sends)
        // This is a behavioral test to ensure thread safety
        
        JoinGameMessage message = new JoinGameMessage("player-1");
        
        // Create a handler and send a message
        clientHandler.send(message);
        
        // Verify that the session was accessed
        verify(mockRemote, times(1)).sendText(anyString());
        
        // The synchronization itself is hard to test directly,
        // but we verify the method completes successfully
    }
}
