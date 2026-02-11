package sl.hackathon.client.api;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.handlers.GameMessageRouter;
import sl.hackathon.client.handlers.MessageHandler;
import sl.hackathon.client.messages.*;

import java.io.IOException;
import java.util.function.Consumer;

import static sl.hackathon.client.util.Ansi.*;

/**
 * High-level client API for communicating with the game server.
 * Provides a simplified interface that wraps WebSocketTransport and GameMessageRouter.
 * This is the main entry point for client applications to interact with the server.
 */
public class WebSocketServerAPI implements ServerAPI {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerAPI.class);
    
    private final WebSocketTransport transport;

    // Message handlers (delegated via internal MessageHandler)
    @Setter Consumer<StartGameMessage> onGameStart;
    @Setter Consumer<PlayerAssignedMessage> onPlayerAssigned;
    @Setter Consumer<NextTurnMessage> onNextTurn;
    @Setter Consumer<EndGameMessage> onGameEnd;
    @Setter Consumer<InvalidOperationMessage> onInvalidOperation;
    @Setter Consumer<Throwable> onError;
    
    public WebSocketServerAPI() {
        this.transport = new WebSocketTransport();
        GameMessageRouter router = new GameMessageRouter(new InternalMessageHandler());
        
        // Wire transport to router
        transport.setOnMessageReceived(router::routeMessage);
        transport.setOnError(error -> {
            logger.error("Transport error", error);
            if (onError != null) {
                onError.accept(error);
            }
        });
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
    public ConnectionState getState() {
        return switch (transport.getState()) {
            case DISCONNECTED -> ConnectionState.DISCONNECTED;
            case CONNECTING -> ConnectionState.CONNECTING;
            case CONNECTED -> ConnectionState.CONNECTED;
        };
    }
    
    /**
     * Checks if currently connected.
     */
    @Override
    public boolean isConnected() {
        return transport.isConnected();
    }
    
    /**
     * Internal message handler that delegates to the registered callbacks.
     */
    private class InternalMessageHandler implements MessageHandler {

        @Override
        public void handlePlayerAssigned(PlayerAssignedMessage message) {
            logger.info(green("Player assigned: {}"), message.getPlayerId());
            if (onPlayerAssigned != null) {
                onPlayerAssigned.accept(message);
            }
        }

        @Override
        public void handleStartGame(StartGameMessage message) {
            logger.info("Game started");
            if (onGameStart != null) {
                onGameStart.accept(message);
            }
        }
        
        @Override
        public void handleNextTurn(NextTurnMessage message) {
            logger.debug(green("Next turn for player: {}"), message.getPlayerId());
            if (onNextTurn != null) {
                onNextTurn.accept(message);
            }
        }
        
        @Override
        public void handleGameEnd(EndGameMessage message) {
            logger.info(green("Game ended, winner: {}"), message.getGameEnd() != null ? message.getGameEnd().winnerId() : "unknown");
            if (onGameEnd != null) {
                onGameEnd.accept(message);
            }
        }
        
        @Override
        public void handleInvalidOperation(InvalidOperationMessage message) {
            logger.warn(yellow("Invalid operation for player {}: {}"), message.getPlayerId(), message.getReason());
            if (onInvalidOperation != null) {
                onInvalidOperation.accept(message);
            }
        }
        
        @Override
        public void handleError(Throwable error) {
            logger.error(redBg("Message handling error"), error);
            if (onError != null) {
                onError.accept(error);
            }
        }
    }
    
    /**
     * Connection state enumeration.
     */
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}