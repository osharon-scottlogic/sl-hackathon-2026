package sl.hackathon.server.orchestration;

import sl.hackathon.server.dtos.MapConfig;

/**
 * Configuration object for the game server.
 * Contains server port, map configuration, and turn time limit.
 * 
 * Responsibilities:
 * - Store server configuration parameters
 * - Validate configuration values
 * - Provide default values for optional parameters
 */
public class ServerConfig {
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_PORT = 8080;
    private static final long DEFAULT_TURN_TIME_LIMIT = 15000L; // 15 seconds
    
    private final int port;
    private final MapConfig mapConfig;
    private final long turnTimeLimit;
    
    /**
     * Creates a ServerConfig with specified parameters.
     * 
     * @param port the server port (1024-65535)
     * @param mapConfig the map configuration
     * @param turnTimeLimit the turn time limit in milliseconds
     * @throws IllegalArgumentException if validation fails
     */
    public ServerConfig(int port, MapConfig mapConfig, long turnTimeLimit) {
        this.port = port;
        this.mapConfig = mapConfig;
        this.turnTimeLimit = turnTimeLimit;
        validate();
    }
    
    /**
     * Creates a ServerConfig with default port and turn time limit.
     * 
     * @param mapConfig the map configuration
     */
    public ServerConfig(MapConfig mapConfig) {
        this(DEFAULT_PORT, mapConfig, DEFAULT_TURN_TIME_LIMIT);
    }
    
    /**
     * Validates the configuration parameters.
     * 
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public void validate() {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException(
                String.format("Port must be between %d and %d, got: %d", MIN_PORT, MAX_PORT, port));
        }
        
        if (mapConfig == null) {
            throw new IllegalArgumentException("MapConfig cannot be null");
        }
        
        if (turnTimeLimit <= 0) {
            throw new IllegalArgumentException(
                String.format("Turn time limit must be positive, got: %d", turnTimeLimit));
        }
    }
    
    /**
     * Gets the server port.
     * 
     * @return the port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Gets the map configuration.
     * 
     * @return the map configuration
     */
    public MapConfig getMapConfig() {
        return mapConfig;
    }
    
    /**
     * Gets the turn time limit in milliseconds.
     * 
     * @return the turn time limit
     */
    public long getTurnTimeLimit() {
        return turnTimeLimit;
    }
    
    @Override
    public String toString() {
        return "ServerConfig{" +
                "port=" + port +
                ", mapConfig=" + mapConfig +
                ", turnTimeLimit=" + turnTimeLimit +
                '}';
    }
}
