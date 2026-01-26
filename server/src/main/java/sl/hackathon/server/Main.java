package sl.hackathon.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.Dimension;
import sl.hackathon.server.dtos.MapConfig;
import sl.hackathon.server.dtos.Position;
import sl.hackathon.server.engine.GameEngine;
import sl.hackathon.server.engine.GameEngineImpl;
import sl.hackathon.server.orchestration.GameServer;
import sl.hackathon.server.orchestration.ServerConfig;
import sl.hackathon.server.util.Ansi;

/**
 * Main entry point for the game server application.
 * Initializes and starts the game server with default configuration.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

    // Default server configuration
    private static final int DEFAULT_PORT = 8080;
    private static final long DEFAULT_TURN_TIME_LIMIT = 15000L; // 15 seconds
    
    // Default map configuration (10x10 with some walls)
    private static final int DEFAULT_MAP_WIDTH = 10;
    private static final int DEFAULT_MAP_HEIGHT = 10;
    
    public static void main(String[] args) {
        logger.info(Ansi.MAGENTA+"Starting Game Server..."+Ansi.RESET);
        
        try {
            // Create map configuration
            MapConfig mapConfig = createDefaultMapConfig();

            // Create game engine + server
            GameServer gameServer = new GameServer(
                new ServerConfig(DEFAULT_PORT, mapConfig, DEFAULT_TURN_TIME_LIMIT),
                new GameEngineImpl()
            );
            logger.info("Game engine initialized and game server created");
            
            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server gracefully...");
                try {
                    gameServer.stop();
                    logger.info("Server stopped successfully");
                } catch (Exception e) {
                    logger.error(Ansi.RED + "Error during server shutdown" + Ansi.RESET, e);
                }
            }));
            
            // Start the server
            gameServer.start();
            logger.info("Game server started successfully on port " + Ansi.YELLOW + "{}" + Ansi.RESET, DEFAULT_PORT);
            logger.info("Server is ready to accept connections at " + Ansi.YELLOW + "ws://localhost:{}/game" + Ansi.RESET, DEFAULT_PORT);
            
            // Wait for game session to complete
            waitForGameCompletion(gameServer);
            
            // Game ended, stop server
            logger.info("Game has ended, shutting down server...");
            gameServer.stop();
            logger.info("Server stopped successfully");
            System.exit(0);
            
        } catch (Exception e) {
            logger.error(Ansi.RED + "Failed to start game server" + Ansi.RESET, e);
            System.exit(1);
        }
    }
    
    /**
     * Waits for the game session to complete.
     * 
     * @param gameServer the game server to monitor
     * @throws InterruptedException if interrupted while waiting
     */
    private static void waitForGameCompletion(GameServer gameServer) throws InterruptedException {
        Thread gameSessionThread = gameServer.getGameSessionThread();
        if (gameSessionThread != null) {
            logger.info("Waiting for game session to complete...");
            gameSessionThread.join();
            logger.info("Game session completed");
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
