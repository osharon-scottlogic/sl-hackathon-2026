package sl.hackathon.server.communication;

import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.Message;
import sl.hackathon.server.dtos.MessageCodec;
import sl.hackathon.server.dtos.PlayerAssignedMessage;

import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * WebSocket server endpoint for game communication.
 * Manages WebSocket lifecycle and delegates to ClientRegistry for connection management.
 * Uses static injection pattern to allow references to be set before WebSocket container
 * instantiates the endpoint instances.
 * Endpoint path: /game
 */
@ServerEndpoint("/game")
public class WebSocketAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAdapter.class);

    // Static references set via dependency injection
    private static ClientRegistry clientRegistry;
    private static Consumer<String> onClientConnectCallback;
    private static Consumer<String> onClientDisconnectCallback;
    private static BiConsumer<String, Message> onMessageCallback;
    
    // Instance-specific handler for this connection
    private ClientHandler clientHandler;
    private String playerId;
    
    /**
     * Public no-arg constructor required by WebSocket specification.
     */
    public WebSocketAdapter() {
        // Default constructor for WebSocket container instantiation
    }

    /**
     * Sets the ClientRegistry to use for all connections.
     * Must be called before any WebSocket connections are established.
     * 
     * @param registry the ClientRegistry instance
     */
    public static void setClientRegistry(ClientRegistry registry) {
        clientRegistry = registry;
        logger.info("ClientRegistry injected into WebSocketAdapter");
    }
    
    /**
     * Gets the currently configured ClientRegistry.
     * 
     * @return the ClientRegistry instance, or null if not set
     */
    public static ClientRegistry getClientRegistry() {
        return clientRegistry;
    }
    
    /**
     * Sets the callback to invoke when a client connects.
     * Callback receives the player ID.
     * 
     * @param callback the callback (playerId) -> void
     */
    public static void setOnClientConnect(Consumer<String> callback) {
        onClientConnectCallback = callback;
        logger.debug("OnClientConnect callback registered");
    }
    
    /**
     * Sets the callback to invoke when a client disconnects.
     * Callback receives the player ID.
     * 
     * @param callback the callback (playerId) -> void
     */
    public static void setOnClientDisconnect(Consumer<String> callback) {
        onClientDisconnectCallback = callback;
        logger.debug("OnClientDisconnect callback registered");
    }
    
    /**
     * Sets the callback to invoke when a message is received from a client.
     * Callback receives the player ID and the deserialized message.
     * 
     * @param callback the callback (playerId, message) -> void
     */
    public static void setOnMessage(BiConsumer<String, Message> callback) {
        onMessageCallback = callback;
        logger.debug("OnMessage callback registered");
    }
    
    /**
     * Gets the currently configured message callback.
     * 
     * @return the message callback, or null if not set
     */
    public static BiConsumer<String, Message> getOnMessage() {
        return onMessageCallback;
    }

    /**
     * Called when a WebSocket connection is opened.
     * Creates a ClientHandler, registers it with the ClientRegistry,
     * and invokes the connection callback.
     * 
     * @param session the WebSocket session
     */
    @OnOpen
    public void onOpen(Session session) {
        if (clientRegistry == null) {
            logger.error("ClientRegistry not set - cannot handle connection");
            closeSession(session);
            return;
        }
        
        try {
            clientHandler = new ClientHandler(session);
            logger.info("WebSocket connection opened: {}", clientHandler.getClientId());
            
            // Set message callback to forward messages to onMessageCallback
            clientHandler.setMessageCallback((clientId, message) -> {
                if (onMessageCallback != null && playerId != null) {
                    onMessageCallback.accept(playerId, message);
                }
            });
            
            // Register with ClientRegistry
            playerId = clientRegistry.register(clientHandler);
            logger.info("Client {} registered as {}", clientHandler.getClientId(), playerId);
            
            // Send player assignment message to client
            PlayerAssignedMessage playerAssignedMsg = new PlayerAssignedMessage(playerId);
            String assignmentJson = MessageCodec.serialize(playerAssignedMsg);
            clientHandler.send(assignmentJson);
            logger.info("Sent player assignment to {}", playerId);

            // Invoke connection callback
            if (onClientConnectCallback != null) {
                onClientConnectCallback.accept(playerId);
            }
            
        } catch (IllegalStateException e) {
            // Maximum players reached
            logger.warn("Cannot register client: {}", e.getMessage());
            closeSession(session);
        } catch (Exception e) {
            logger.error("Error handling connection open: {}", e.getMessage(), e);
            closeSession(session);
        }
    }
    
    /**
     * Called when a message is received from the client.
     * Delegates to ClientHandler for deserialization and processing.
     * 
     * @param message the message string (JSON)
     * @param session the WebSocket session
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if (clientHandler == null) {
            logger.warn("Received message before handler initialized");
            return;
        }
        
        logger.debug("Received message from {}: {}", playerId, message);
        
        try {
            clientHandler.handleMessage(message);
        } catch (Exception e) {
            logger.error("Error handling message from {}: {}", playerId, e.getMessage(), e);
        }
    }
    
    /**
     * Called when the WebSocket connection is closed.
     * Unregisters the client and invokes the disconnect callback.
     * 
     * @param session the WebSocket session
     * @param closeReason the reason for closing
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("WebSocket connection closed for {}: {}", 
            playerId != null ? playerId : "unknown", 
            closeReason != null ? closeReason.getReasonPhrase() : "no reason");
        
        if (clientHandler != null && clientRegistry != null) {
            String clientId = clientHandler.getClientId();
            clientRegistry.unregister(clientId);
            
            if (onClientDisconnectCallback != null && playerId != null) {
                onClientDisconnectCallback.accept(playerId);
            }
        }
        
        cleanup();
    }
    
    /**
     * Called when an error occurs on the WebSocket connection.
     * Logs the error and ensures cleanup.
     * 
     * @param session the WebSocket session
     * @param throwable the error that occurred
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.error("WebSocket error for {}: {}", 
            playerId != null ? playerId : "unknown", 
            throwable.getMessage(), 
            throwable);
        
        cleanup();
    }
    
    /**
     * Closes a session gracefully, ignoring any errors.
     * 
     * @param session the session to close
     */
    private void closeSession(Session session) {
        if (session != null && session.isOpen()) {
            try {
                session.close(new CloseReason(
                    CloseReason.CloseCodes.CANNOT_ACCEPT, 
                    "Server cannot accept more connections"
                ));
            } catch (Exception e) {
                logger.error("Error closing session: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Cleans up instance state.
     */
    private void cleanup() {
        if (clientHandler != null) {
            clientHandler.close();
            clientHandler = null;
        }
        playerId = null;
    }
    
    /**
     * Resets all static references. Useful for testing and cleanup.
     */
    public static void reset() {
        clientRegistry = null;
        onClientConnectCallback = null;
        onClientDisconnectCallback = null;
        onMessageCallback = null;
        logger.info("WebSocketAdapter static state reset");
    }
}
