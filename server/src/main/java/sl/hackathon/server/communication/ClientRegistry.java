package sl.hackathon.server.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static sl.hackathon.server.util.Ansi.*;

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
    
    // Maps clientId -> ClientHandler
    private final Map<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();
    
    // Maps playerId -> clientId
    private final Map<String, String> playerToClient = new ConcurrentHashMap<>();
    
    // Maps clientId -> playerId
    private final Map<String, String> clientToPlayer = new ConcurrentHashMap<>();
    
    /**
     * Registers a new client and assigns a player ID.
     * Player IDs are provided by the client as part of the connect payload.
     * 
     * @param handler the ClientHandler to register (must not be null)
     * @param playerId the requested player ID (must not be null/blank)
     * @return the registered player ID
     * @throws IllegalArgumentException if handler is null
     * @throws IllegalStateException if maximum players (2) already registered
     */
    public String register(ClientHandler handler, String playerId) {
        if (handler == null) {
            throw new IllegalArgumentException("ClientHandler cannot be null");
        }

        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be null or blank");
        }
        
        if (clientHandlers.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Maximum players (2) already registered");
        }
        
        String clientId = handler.getClientId();

        String normalizedPlayerId = playerId.trim();

        if (playerToClient.containsKey(normalizedPlayerId)) {
            throw new IllegalStateException("Player ID already registered: " + normalizedPlayerId);
        }
        
        clientHandlers.put(clientId, handler);
        playerToClient.put(normalizedPlayerId, clientId);
        clientToPlayer.put(clientId, normalizedPlayerId);
        
        logger.info(green("Registered client {} as {}"), clientId, normalizedPlayerId);
        
        return normalizedPlayerId;
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
        
        logger.debug(green("Broadcasting message to {} clients"), clientHandlers.size());
        
        for (ClientHandler handler : clientHandlers.values()) {
            try {
                handler.send(message);
            } catch (Exception e) {
                logger.error(redBg(yellow("Failed to broadcast to client {}: {}")),
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
        
        logger.debug(green("Sending message to player {}"), playerId);
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

            handler.close();

            String playerId = clientToPlayer.remove(clientId);
            if (playerId != null) {
                playerToClient.remove(playerId);
                logger.info(green("Unregistered client {} ({})"), clientId, playerId);
            } else {
                logger.warn(yellow("Client {} had no player ID mapping"), clientId);
            }
        } else {
            logger.debug(yellow("Client {} was not registered"), clientId);
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
