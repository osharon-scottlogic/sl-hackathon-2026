package sl.hackathon.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.MapConfig;
import sl.hackathon.server.engine.GameEngineImpl;
import sl.hackathon.server.maps.MapFactory;
import sl.hackathon.server.orchestration.GameServer;
import sl.hackathon.server.orchestration.ServerConfig;

import static sl.hackathon.server.util.Ansi.*;

/**
 * Main entry point for the game server application.
 * Initializes and starts the game server with default configuration.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Default server configuration
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_TURN_TIME_LIMIT = 15000; // 15 seconds
    
    public static void main(String[] args) {       
        try {
            String buildVersion = readBuildVersion();
            
            logger.info(green("Starting Game Server v{}..."), buildVersion);

            // Create map configuration
            MapConfig mapConfig = MapFactory.createMapConfig("map-001.json");

            

            // Create game engine + server
            GameServer gameServer = new GameServer(
                new ServerConfig(DEFAULT_PORT,
                    mapConfig,
                    DEFAULT_TURN_TIME_LIMIT,
                    majorVersionOf(buildVersion)),
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
                    logger.error(redBg("Error during server shutdown"), e);
                }
            }));
            
            // Start the server
            gameServer.start();
            logger.info(green("Game server started successfully on port {}"), DEFAULT_PORT);
            logger.info(green("Server is ready to accept connections at {}"), "ws://localhost:"+DEFAULT_PORT+"/game");
            
            // Wait for game session to complete
            waitForGameCompletion(gameServer);
            
            // Game ended, stop server
            logger.info("Game has ended, shutting down server...");
            gameServer.stop();
            logger.info("Server stopped successfully");
            System.exit(0);
            
        } catch (Exception e) {
            logger.error(red("Failed to start game server"), e);
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

    private static String readBuildVersion() {
        String fromProperty = System.getProperty("app.version");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }

        Package pkg = Main.class.getPackage();
        if (pkg == null) {
            return "0.0.0";
        }

        String implVersion = pkg.getImplementationVersion();
        if (implVersion == null || implVersion.isBlank()) {
            return "0.0.0";
        }
        return implVersion.trim();
    }

    private static int majorVersionOf(String version) {
        if (version == null) {
            return 0;
        }
        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(trimmed.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
