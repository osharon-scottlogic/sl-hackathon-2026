package sl.hackathon.client.api;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.messages.MessageRouter;
import sl.hackathon.client.messages.*;

import java.io.IOException;

import static sl.hackathon.client.util.Ansi.*;

/**
 * High-level client API for communicating with the game server.
 * Provides a simplified interface that wraps WebSocketTransport and GameMessageRouter.
 * This is the main entry point for client applications to interact with the server.
 */
public class WebSocketServerAPI implements ServerAPI {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerAPI.class);
    
    private final WebSocketTransport transport;

    public WebSocketServerAPI(MessageRouter messageRouter) {
        this(messageRouter, new WebSocketTransport());
    }

    WebSocketServerAPI(MessageRouter messageRouter, WebSocketTransport transport) {
        if (messageRouter == null) {
            throw new IllegalArgumentException("MessageRouter cannot be null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("WebSocketTransport cannot be null");
        }

        this.transport = transport;

        // Wire transport to router
        transport.setOnMessageReceived(messageRouter::routeMessage);
        transport.setOnError(messageRouter::accept);
    }
    
    /**
     * Establishes WebSocket connection to the server.
     * 
     * @param serverURL WebSocket URL (e.g., "ws://localhost:8080/game")
     * @throws Exception if connection fails
     */
    @Override
    public void connect(String serverURL) throws Exception {
        logger.info("Connecting to server: {}", serverURL);
        transport.connect(serverURL);
        logger.info("Successfully connected to server");
    }
    
    /**
     * Sends player actions to the server.
     * 
     * @param playerId the player ID sending the actions
     * @param actions array of actions to send
     * @throws IOException if send fails
     */
    @Override
    public void send(String playerId, Action[] actions) throws IOException {
        String json = MessageCodec.serialize(new ActionMessage(playerId, actions));

        if(actions.length > 0) {
            logger.debug(green("Sending {} actions to server for player {}"), actions.length, playerId);
        } else {
            logger.debug(redBg(yellow("Sending {} actions to server for player {}")), actions.length, playerId);
        }

        transport.send(json);
    }
    
    /**
     * Closes the WebSocket connection gracefully.
     */
    @Override
    public void close() {
        logger.info("Closing connection to server");
        transport.disconnect();
    }
    
    /**
     * Gets current connection state.
     */
    public TransportState getState() {
        return transport.getState();
    }
    
    /**
     * Checks if currently connected.
     */
    @Override
    public boolean isConnected() {
        return transport.isConnected();
    }
}