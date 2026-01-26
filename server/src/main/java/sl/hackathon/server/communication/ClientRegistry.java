package sl.hackathon.server.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.Message;
import sl.hackathon.server.util.Ansi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ClientRegistry manages player tracking and message routing for connected clients.
 * Thread-safe implementation supporting exactly 2 players.
 * Responsisbilities:
 * - Register clients and assign player IDs (player-1, player-2)
 * - Track client-to-player mappings
 * - Route messages to specific players or broadcast to all
 * - Enforce 2-player limit
 * - Handle client disconnections
 */
public class ClientRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ClientRegistry.class);
    private static final int MAX_PLAYERS = 2;
    private static final String PLAYER_1_ID = "player-1";
    private static final String PLAYER_2_ID = "player-2";
    
    // Maps clientId -> ClientHandler
    private final Map<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();
    
    // Maps playerId -> clientId
    private final Map<String, String> playerToClient = new ConcurrentHashMap<>();
    
    // Maps clientId -> playerId
    private final Map<String, String> clientToPlayer = new ConcurrentHashMap<>();
    
    /**
     * Registers a new client and assigns a player ID.
     * Players are assigned in order: player-1, player-2.
     * 
     * @param handler the ClientHandler to register (must not be null)
     * @return the assigned player ID (player-1 or player-2)
     * @throws IllegalArgumentException if handler is null
     * @throws IllegalStateException if maximum players (2) already registered
     */
    public String register(ClientHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("ClientHandler cannot be null");
        }
        
        if (clientHandlers.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Maximum players (2) already registered");
        }
        
        String clientId = handler.getClientId();
        
        // Assign player ID based on which slots are available
        String playerId;
        if (!playerToClient.containsKey(PLAYER_1_ID)) {
            playerId = PLAYER_1_ID;
        } else if (!playerToClient.containsKey(PLAYER_2_ID)) {
            playerId = PLAYER_2_ID;
        } else {
            throw new IllegalStateException("Maximum players (2) already registered");
        }
        
        clientHandlers.put(clientId, handler);
        playerToClient.put(playerId, clientId);
        clientToPlayer.put(clientId, playerId);
        
        logger.info("Registered client " + Ansi.YELLOW + "{}" + Ansi.RESET + " as " + Ansi.YELLOW + "{}" + Ansi.RESET, clientId, playerId);
        
        return playerId;
    }
    
    /**
     * Checks if both players are connected and ready.
     * 
     * @return true if exactly 2 players are registered, false otherwise
     */
    public boolean isReady() {
        return clientHandlers.size() == MAX_PLAYERS;
    }
    
    /**
     * Broadcasts a message to all registered clients.
     * 
     * @param message the Message to broadcast (must not be null)
     * @throws IllegalArgumentException if message is null
     */
    public void broadcast(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        logger.debug("Broadcasting message to " + Ansi.YELLOW + "{}" + Ansi.RESET + " clients", clientHandlers.size());
        
        for (ClientHandler handler : clientHandlers.values()) {
            try {
                handler.send(message);
            } catch (Exception e) {
                logger.error(Ansi.RED + "Failed to broadcast to client " + Ansi.YELLOW + "{}" + Ansi.RESET + ": " + Ansi.YELLOW + "{}" + Ansi.RESET, 
                    handler.getClientId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Sends a message to a specific player.
     * 
     * @param playerId the player ID (player-1 or player-2)
     * @param message the Message to send (must not be null)
     * @throws IllegalArgumentException if message is null or playerId is null
     * @throws IllegalStateException if player is not registered
     */
    public void send(String playerId, Message message) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        String clientId = playerToClient.get(playerId);
        if (clientId == null) {
            throw new IllegalStateException("Player not registered: " + playerId);
        }
        
        ClientHandler handler = clientHandlers.get(clientId);
        if (handler == null) {
            throw new IllegalStateException("Client handler not found for player: " + playerId);
        }
        
        logger.debug("Sending message to player " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId);
        handler.send(message);
    }
    
    /**
     * Unregisters a client by client ID.
     * Removes all mappings for the client.
     * 
     * @param clientId the client ID to unregister
     */
    public void unregister(String clientId) {
        if (clientId == null) {
            logger.warn("Attempted to unregister null clientId");
            return;
        }
        
        ClientHandler handler = clientHandlers.remove(clientId);
        if (handler != null) {
            String playerId = clientToPlayer.remove(clientId);
            if (playerId != null) {
                playerToClient.remove(playerId);
                logger.info("Unregistered client " + Ansi.YELLOW + "{}" + Ansi.RESET + " (" + Ansi.YELLOW + "{}" + Ansi.RESET + ")", clientId, playerId);
            } else {
                logger.warn("Client " + Ansi.YELLOW + "{}" + Ansi.RESET + " had no player ID mapping", clientId);
            }
        } else {
            logger.debug("Client " + Ansi.YELLOW + "{}" + Ansi.RESET + " was not registered", clientId);
        }
    }
    
    /**
     * Gets the player ID for a given client ID.
     * 
     * @param clientId the client ID
     * @return Optional containing the player ID if found, empty otherwise
     */
    public Optional<String> getPlayerId(String clientId) {
        return Optional.ofNullable(clientToPlayer.get(clientId));
    }
    
    /**
     * Gets the client ID for a given player ID.
     * 
     * @param playerId the player ID
     * @return Optional containing the client ID if found, empty otherwise
     */
    public Optional<String> getClientId(String playerId) {
        return Optional.ofNullable(playerToClient.get(playerId));
    }
    
    /**
     * Gets the ClientHandler for a given player ID.
     * 
     * @param playerId the player ID
     * @return Optional containing the ClientHandler if found, empty otherwise
     */
    public Optional<ClientHandler> getHandler(String playerId) {
        String clientId = playerToClient.get(playerId);
        if (clientId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(clientHandlers.get(clientId));
    }
    
    /**
     * Gets a list of all registered player IDs.
     * 
     * @return list of player IDs (e.g., ["player-1", "player-2"])
     */
    public List<String> getRegisteredPlayers() {
        return playerToClient.keySet().stream()
            .sorted()
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the current number of registered clients.
     * 
     * @return number of registered clients
     */
    public int size() {
        return clientHandlers.size();
    }
    
    /**
     * Clears all registered clients.
     * Useful for cleanup or resetting between games.
     */
    public void clear() {
        clientHandlers.clear();
        playerToClient.clear();
        clientToPlayer.clear();
        logger.info("Cleared all registered clients");
    }
}
