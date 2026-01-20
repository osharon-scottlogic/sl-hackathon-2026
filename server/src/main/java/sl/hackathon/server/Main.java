package sl.hackathon.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.Dimension;
import sl.hackathon.server.dtos.MapConfig;
import sl.hackathon.server.dtos.Position;
import sl.hackathon.server.engine.GameEngine;
import sl.hackathon.server.engine.GameEngineImpl;
import sl.hackathon.server.orchestration.GameServer;
import sl.hackathon.server.orchestration.ServerConfig;

/**
 * Main entry point for the game server application.
 * Initializes and starts the game server with default configuration.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    // Default server configuration
    private static final int DEFAULT_PORT = 8080;
    private static final long DEFAULT_TURN_TIME_LIMIT = 15000L; // 15 seconds
    
    // Default map configuration (10x10 with some walls)
    private static final int DEFAULT_MAP_WIDTH = 10;
    private static final int DEFAULT_MAP_HEIGHT = 10;
    
    public static void main(String[] args) {
        logger.info("Starting Game Server...");
        
        try {
            // Create map configuration
            MapConfig mapConfig = createDefaultMapConfig();
            
            // Create server configuration
            ServerConfig config = new ServerConfig(DEFAULT_PORT, mapConfig, DEFAULT_TURN_TIME_LIMIT);
            logger.info("Server configuration: {}", config);
            
            // Create game engine
            GameEngine gameEngine = new GameEngineImpl();
            logger.info("Game engine initialized");
            
            // Create game server
            GameServer gameServer = new GameServer(config, gameEngine);
            logger.info("Game server created");
            
            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server gracefully...");
                try {
                    gameServer.stop();
                    logger.info("Server stopped successfully");
                } catch (Exception e) {
                    logger.error("Error during server shutdown", e);
                }
            }));
            
            // Start the server
            gameServer.start();
            logger.info("Game server started successfully on port {}", DEFAULT_PORT);
            logger.info("Server is ready to accept connections at ws://localhost:{}/game", DEFAULT_PORT);
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Failed to start game server", e);
            System.exit(1);
        }
    }
    
    /**
     * Creates a default map configuration for testing.
     * 
     * @return a MapConfig with a 10x10 grid, some walls, and two base locations
     */
    private static MapConfig createDefaultMapConfig() {
        Dimension dimension = new Dimension(DEFAULT_MAP_WIDTH, DEFAULT_MAP_HEIGHT);
        
        // Create some walls to make the map interesting
        Position[] walls = new Position[] {
            new Position(3, 3),
            new Position(3, 4),
            new Position(3, 5),
            new Position(6, 4),
            new Position(6, 5),
            new Position(6, 6)
        };
        
        // Base locations for two players (opposite corners)
        Position[] baseLocations = new Position[] {
            new Position(1, 1),  // Player 1 base
            new Position(8, 8)   // Player 2 base
        };
        
        return new MapConfig(dimension, walls, baseLocations);
    }
}
