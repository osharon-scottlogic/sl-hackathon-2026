package sl.hackathon.client.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.messages.*;
import sl.hackathon.client.util.Ansi;

/**
 * Routes incoming messages to the appropriate handler methods.
 * Acts as a dispatcher that deserializes messages and delegates to the MessageHandler.
 */
public class GameMessageRouter {
    
    private static final Logger logger = LoggerFactory.getLogger(GameMessageRouter.class);
    
    private final MessageHandler messageHandler;
    
    /**
     * Creates a new message router with the specified handler.
     * 
     * @param messageHandler the handler that will process routed messages
     */
    public GameMessageRouter(MessageHandler messageHandler) {
        if (messageHandler == null) {
            throw new IllegalArgumentException("MessageHandler cannot be null");
        }
        this.messageHandler = messageHandler;
    }
    
    /**
     * Routes a raw JSON message string to the appropriate handler method.
     * 
     * @param jsonMessage the JSON message received from the server
     */
    public void routeMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.isBlank()) {
            logger.warn("Received null or empty message - ignoring");
            return;
        }
        
        try {
            Message message = MessageCodec.deserialize(jsonMessage);
            routeMessage(message);
        } catch (Exception e) {
            logger.error(Ansi.RED + "Failed to deserialize message ({}): \n"+Ansi.YELLOW+"{}\n"+ Ansi.RESET,
                    e.getMessage(),
                    jsonMessage, e);
            messageHandler.handleError(new MessageRoutingException("Deserialization failed", e));
        }
    }
    
    /**
     * Routes a deserialized message to the appropriate handler method.
     * 
     * @param message the message to route
     */
    public void routeMessage(Message message) {
        if (message == null) {
            logger.warn("Received null message - ignoring");
            return;
        }
        
        try {
            switch (message) {
                case PlayerAssignedMessage msg -> {
                    logger.info("Routing PlayerAssignedMessage for player: "+Ansi.YELLOW+"{}"+Ansi.RESET, msg.getPlayerId());
                    messageHandler.handlePlayerAssigned(msg);
                }
                case StartGameMessage msg -> {
                    logger.info("Routing StartGameMessage");
                    messageHandler.handleStartGame(msg);
                }
                case NextTurnMessage msg -> {
                    logger.debug("Routing NextTurnMessage for player: "+Ansi.YELLOW+"{}"+Ansi.RESET, msg.getPlayerId());
                    messageHandler.handleNextTurn(msg);
                }
                case EndGameMessage msg -> {
                    logger.info("Routing EndGameMessage");
                    messageHandler.handleGameEnd(msg);
                }
                case InvalidOperationMessage msg -> {
                    logger.warn("Routing InvalidOperationMessage: "+Ansi.YELLOW+"{}"+Ansi.RESET, msg.getReason());
                    messageHandler.handleInvalidOperation(msg);
                }
                default -> {
                    logger.warn("Unhandled message type: "+Ansi.YELLOW+"{}"+Ansi.RESET, message.getClass().getSimpleName());
                    messageHandler.handleError(
                        new MessageRoutingException("Unknown message type: " + message.getClass().getSimpleName())
                    );
                }
            }
        } catch (Exception e) {
            logger.error(Ansi.RED+"Error while routing message: "+Ansi.YELLOW+"{}"+Ansi.RESET, e.getMessage(), e);
            messageHandler.handleError(new MessageRoutingException("Error in message handler", e));
        }
    }
    
    /**
     * Exception thrown when message routing fails.
     */
    public static class MessageRoutingException extends RuntimeException {
        public MessageRoutingException(String message) {
            super(message);
        }
        
        public MessageRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
