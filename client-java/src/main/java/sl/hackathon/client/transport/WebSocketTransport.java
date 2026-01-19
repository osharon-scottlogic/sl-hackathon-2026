package sl.hackathon.client.transport;

import jakarta.websocket.*;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Low-level WebSocket transport layer for client-server communication.
 * Handles connection lifecycle, sending/receiving raw text messages.
 * This class is transport-specific and can be replaced with other implementations (TCP, HTTP, etc.)
 */
public class WebSocketTransport {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketTransport.class);
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    
    private Session session;
    private TransportState state;
    private CountDownLatch connectLatch;
    
    // Callbacks for transport events
    @Setter private Consumer<String> onMessageReceived;
    @Setter private Consumer<Throwable> onError;
    @Setter private Runnable onConnected;
    @Setter private Runnable onDisconnected;
    
    public WebSocketTransport() {
        this.state = TransportState.DISCONNECTED;
    }
    
    /**
     * Establishes WebSocket connection to the specified URL.
     * 
     * @param url WebSocket URL (e.g., "ws://localhost:8080/game")
     * @throws Exception if connection fails or times out
     */
    public void connect(String url) throws Exception {
        if (state != TransportState.DISCONNECTED) {
            throw new IllegalStateException("Already connected or connecting. Current state: " + state);
        }
        
        logger.info("Connecting to WebSocket server: {}", url);
        state = TransportState.CONNECTING;
        connectLatch = new CountDownLatch(1);
        
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            URI uri = URI.create(url);
            session = container.connectToServer(new WebSocketClientEndpoint(), uri);
            
            // Wait for connection to be established with timeout
            if (!connectLatch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                state = TransportState.DISCONNECTED;
                throw new IOException("Connection timeout after " + CONNECTION_TIMEOUT_SECONDS + " seconds");
            }
            
            state = TransportState.CONNECTED;
            logger.info("Successfully connected to WebSocket server");
            
            if (onConnected != null) {
                onConnected.run();
            }
        } catch (Exception e) {
            state = TransportState.DISCONNECTED;
            logger.error("Failed to connect to WebSocket server", e);
            throw e;
        }
    }
    
    /**
     * Sends a text message through the WebSocket connection.
     * 
     * @param message the text message to send
     * @throws IOException if send fails or not connected
     */
    public void send(String message) throws IOException {
        if (state != TransportState.CONNECTED || session == null || !session.isOpen()) {
            throw new IllegalStateException("Cannot send message - not connected");
        }
        
        logger.debug("Sending message: {}", message.length() > 100 ? message.substring(0, 100) + "..." : message);
        
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            logger.error("Failed to send message", e);
            throw e;
        }
    }
    
    /**
     * Closes the WebSocket connection gracefully.
     */
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                logger.info("Disconnecting from WebSocket server");
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Client initiated disconnect"));
                state = TransportState.DISCONNECTED;
            } catch (IOException e) {
                logger.error("Error during disconnect", e);
            }
        } else {
            state = TransportState.DISCONNECTED;
        }
    }
    
    /**
     * Gets the current transport state.
     */
    public TransportState getState() {
        return state;
    }
    
    /**
     * Checks if transport is currently connected.
     */
    public boolean isConnected() {
        return state == TransportState.CONNECTED && session != null && session.isOpen();
    }
    
    /**
     * WebSocket client endpoint implementation.
     */
    @ClientEndpoint
    private class WebSocketClientEndpoint {
        
        @OnOpen
        public void onOpen(Session session) {
            logger.debug("WebSocket connection opened: {}", session.getId());
            connectLatch.countDown();
        }
        
        @OnMessage
        public void onMessage(String message) {
            logger.debug("Received message: {}", message.length() > 100 ? message.substring(0, 100) + "..." : message);
            
            if (onMessageReceived != null) {
                try {
                    onMessageReceived.accept(message);
                } catch (Exception e) {
                    logger.error("Error in message handler", e);
                    if (onError != null) {
                        onError.accept(e);
                    }
                }
            }
        }
        
        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            logger.info("WebSocket connection closed: {} - {}", 
                closeReason.getCloseCode(), 
                closeReason.getReasonPhrase());
            state = TransportState.DISCONNECTED;
            
            if (onDisconnected != null) {
                onDisconnected.run();
            }
        }
        
        @OnError
        public void onError(Session session, Throwable throwable) {
            logger.error("WebSocket error occurred", throwable);
            
            if (WebSocketTransport.this.onError != null) {
                WebSocketTransport.this.onError.accept(throwable);
            }
        }
    }
    
    /**
     * Transport state enumeration.
     */
    public enum TransportState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}
