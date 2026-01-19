package sl.hackathon.server.orchestration;

import sl.hackathon.server.dtos.MapConfig;

/**
 * Configuration object for the game server.
 * Contains server port, map configuration, and turn time limit.
 * <p>
 * Responsibilities:
 * - Store server configuration parameters
 * - Validate configuration values
 * - Provide default values for optional parameters
 */
public record ServerConfig(int port, MapConfig mapConfig, long turnTimeLimit) {
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_PORT = 8080;
    private static final long DEFAULT_TURN_TIME_LIMIT = 15000L; // 15 seconds

    /**
     * Creates a ServerConfig with specified parameters.
     *
     * @param port          the server port (1024-65535)
     * @param mapConfig     the map configuration
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
}
