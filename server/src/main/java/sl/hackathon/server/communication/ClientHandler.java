package sl.hackathon.server.communication;

import jakarta.websocket.Session;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.Message;
import sl.hackathon.server.dtos.MessageCodec;

import java.io.IOException;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * ClientHandler wraps a WebSocket session and provides message handling capabilities.
 * Each client connection has its own unique handler instance.
 * Responsibilities:
 * - Maintains unique client ID
 * - Sends JSON messages to the connected client
 * - Receives and deserializes messages from the client
 * - Handles errors gracefully
 * - Manages connection lifecycle
 */
@Getter
public class ClientHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    
    private final String clientId;
    private final Session session;
    
    @Setter
    private BiConsumer<String, Message> messageCallback;
    
    /**
     * Creates a new ClientHandler wrapping the given WebSocket session.
     * Generates a unique client ID (UUID).
     * 
     * @param session the WebSocket session to wrap (must not be null)
     * @throws IllegalArgumentException if session is null
     */
    public ClientHandler(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        this.session = session;
        this.clientId = UUID.randomUUID().toString();
        logger.info("Created ClientHandler with ID: {}", clientId);
    }
    
    /**
     * Checks if the WebSocket connection is currently open.
     * 
     * @return true if the connection is open, false otherwise
     */
    public boolean isOpen() {
        return session != null && session.isOpen();
    }
    
    /**
     * Sends a JSON message to the connected client.
     * Catches and logs IOException if the send fails.
     * 
     * @param jsonMessage the JSON message string to send (must not be null or empty)
     * @throws IllegalArgumentException if jsonMessage is null or empty
     */
    public void send(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            throw new IllegalArgumentException("JSON message cannot be null or empty");
        }
        
        if (!isOpen()) {
            logger.warn("Cannot send message to client {}: connection is closed", clientId);
            return;
        }
        
        try {
            synchronized (session) {
                session.getBasicRemote().sendText(jsonMessage);
            }
            logger.debug("Sent message to client {}: {}", clientId, jsonMessage);
        } catch (IOException e) {
            logger.error("Failed to send message to client {}: {}", clientId, e.getMessage(), e);
        }
    }
    
    /**
     * Sends a Message object to the connected client.
     * Serializes the message using MessageCodec before sending.
     * 
     * @param message the Message object to send (must not be null)
     * @throws IllegalArgumentException if message is null
     */
    public void send(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        try {
            String json = MessageCodec.serialize(message);
            send(json);
        } catch (Exception e) {
            logger.error("Failed to serialize and send message to client {}: {}", clientId, e.getMessage(), e);
        }
    }
    
    /**
     * Handles an incoming JSON message from the client.
     * Deserializes the message and forwards it to the registered callback.
     * 
     * @param json the JSON message string received from the client
     */
    public void handleMessage(String json) {
        if (json == null || json.isEmpty()) {
            logger.warn("Received null or empty message from client {}", clientId);
            return;
        }
        
        try {
            Message message = MessageCodec.deserialize(json);
            logger.debug("Received message from client {}: {}", clientId, message.getClass().getSimpleName());
            
            if (messageCallback != null) {
                messageCallback.accept(clientId, message);
            } else {
                logger.warn("No message callback registered for client {}", clientId);
            }
        } catch (Exception e) {
            logger.error("Failed to deserialize message from client {}: {}", clientId, e.getMessage(), e);
        }
    }
    
    /**
     * Closes the WebSocket connection gracefully.
     * Logs any errors that occur during closing.
     */
    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                logger.info("Closed connection for client {}", clientId);
            } catch (IOException e) {
                logger.error("Error closing connection for client {}: {}", clientId, e.getMessage(), e);
            }
        }
    }
}
