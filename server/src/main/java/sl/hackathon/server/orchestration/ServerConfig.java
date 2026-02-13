package sl.hackathon.server.orchestration;

import lombok.NonNull;
import sl.hackathon.server.dtos.GameSettings;

/**
 * Configuration object for the game server.
 * Contains server port, map configuration, and turn time limit.
 * <p>
 * Responsibilities:
 * - Store server configuration parameters
 * - Validate configuration values
 * - Provide default values for optional parameters
 */
public record ServerConfig(GameSettings gameSettings, int port, int serverVersion) {
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_SERVER_VERSION = 1;

    /**
     * Creates a ServerConfig with specified parameters.
     *
     * @param port          the server port (1024-65535)
     * @param gameSettings     the map configuration
     * @throws IllegalArgumentException if validation fails
     */
    public ServerConfig(@NonNull GameSettings gameSettings,int port, int serverVersion) {
        this.port = port;
        this.gameSettings = gameSettings;
        this.serverVersion = serverVersion;
        validate();
    }

    /**
     * Creates a ServerConfig with default port and turn time limit.
     *
     * @param gameSettings the game settings
     */
    public ServerConfig(GameSettings gameSettings) {
        this(gameSettings, DEFAULT_PORT, DEFAULT_SERVER_VERSION);
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

        if (gameSettings == null) {
            throw new IllegalArgumentException("gameSettings cannot be null");
        }

        if (serverVersion <= 0) {
            throw new IllegalArgumentException(
                    String.format("Server version must be positive, got: %d", serverVersion));
        }
    }
}
