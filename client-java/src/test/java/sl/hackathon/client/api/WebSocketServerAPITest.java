package sl.hackathon.client.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.dtos.Direction;
import sl.hackathon.client.messages.*;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static sl.hackathon.client.api.TransportState.DISCONNECTED;
import static org.mockito.Mockito.*;

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
    private WebSocketTransport transport;
    private MessageHandler messageHandler;
    private MessageRouter messageRouter;
    
    @BeforeEach
    void setUp() {
        transport = mock(WebSocketTransport.class);
        when(transport.getState()).thenReturn(DISCONNECTED);
        when(transport.isConnected()).thenReturn(false);

        messageHandler = mock(MessageHandler.class);
        messageRouter = new MessageRouter(messageHandler);

        webSocketServerAPI = new WebSocketServerAPI(messageRouter, transport);
    }
    
    @Test
    void testInitialState() {
        assertEquals(DISCONNECTED, webSocketServerAPI.getState());
        assertFalse(webSocketServerAPI.isConnected());
    }
    
    @Test
    void testSendWithoutConnection() throws Exception {
        doThrow(new IllegalStateException("Cannot send message - not connected"))
            .when(transport)
            .send(anyString());

        Action[] actions = new Action[]{new Action(1, Direction.N)};
        assertThrows(IllegalStateException.class, () -> webSocketServerAPI.send("player1", actions));
    }
    
    @Test
    void testConnectDelegatesToTransport() throws Exception {
        webSocketServerAPI.connect("ws://localhost:8080/game");
        verify(transport).connect("ws://localhost:8080/game");
    }
    
    @Test
    void testConstructorWiresTransportCallbacks() {
        verify(transport).setOnMessageReceived(any());
        verify(transport).setOnError(any());
    }
    
    @Test
    void testIncomingJsonIsRoutedToMessageHandler() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> onMessageCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(transport).setOnMessageReceived(onMessageCaptor.capture());

        String json = MessageCodec.serialize(new InvalidOperationMessage("player1", "Test"));
        onMessageCaptor.getValue().accept(json);

        ArgumentCaptor<InvalidOperationMessage> msgCaptor = ArgumentCaptor.forClass(InvalidOperationMessage.class);
        verify(messageHandler).handleInvalidOperation(msgCaptor.capture());
        assertEquals("player1", msgCaptor.getValue().getPlayerId());
    }
    
    @Test
    void testTransportErrorIsForwardedToMessageHandler() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> onErrorCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(transport).setOnError(onErrorCaptor.capture());

        RuntimeException error = new RuntimeException("Test error");
        onErrorCaptor.getValue().accept(error);

        verify(messageHandler).handleError(error);
    }
    
    @Test
    void testCloseDelegatesToTransport() {
        assertDoesNotThrow(() -> webSocketServerAPI.close());
        verify(transport).disconnect();
    }
    
    @Test
    void testSendActionsFormat() throws Exception {
        Action[] actions = new Action[]{
            new Action(1, Direction.N),
            new Action(2, Direction.SE)
        };

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        webSocketServerAPI.send("player1", actions);

        verify(transport).send(jsonCaptor.capture());
        Message decoded = MessageCodec.deserialize(jsonCaptor.getValue());
        assertInstanceOf(ActionMessage.class, decoded);

        ActionMessage actionMessage = (ActionMessage) decoded;
        assertEquals("player1", actionMessage.getPlayerId());
        assertArrayEquals(actions, actionMessage.getActions());
    }
}
