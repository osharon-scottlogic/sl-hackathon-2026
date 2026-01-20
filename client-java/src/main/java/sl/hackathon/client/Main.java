package sl.hackathon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.api.ServerAPI;

import java.util.concurrent.CountDownLatch;

/**
 * Main entry point for the game client application.
 * Connects to the game server and runs the bot orchestrator.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    // Default server URL
    private static final String DEFAULT_SERVER_URL = "ws://localhost:8080/game";
    
    // Environment variable names
    private static final String ENV_SERVER_URL = "GAME_SERVER_URL";
    private static final String ENV_PLAYER_ID = "PLAYER_ID";
    
    public static void main(String[] args) {
        logger.info("Starting Game Client...");
        
        // Parse server URL from arguments, environment, or use default
        String serverURL = getServerURL(args);
        logger.info("Server URL: {}", serverURL);
        
        // Parse player ID from environment or generate default
        String playerId = getPlayerId();
        logger.info("Player ID: {}", playerId);
        
        // Create components
        ServerAPI serverAPI = null;
        Orchestrator orchestrator = null;
        
        try {
            // Instantiate ServerAPI
            serverAPI = new ServerAPI();
            logger.info("ServerAPI created");
            
            // Instantiate Bot (using StrategicBot as default)
            Bot bot = new StrategicBot();
            logger.info("Bot created: {}", bot.getClass().getSimpleName());
            
            // Instantiate Orchestrator
            orchestrator = new Orchestrator();
            logger.info("Orchestrator created");
            
            // Initialize orchestrator with dependencies
            orchestrator.init(serverAPI, bot, playerId);
            logger.info("Orchestrator initialized");
            
            // Connect to server
            logger.info("Connecting to server at {}...", serverURL);
            serverAPI.connect(serverURL);
            logger.info("Successfully connected to server");
            
            // Create latch for keeping main thread alive
            CountDownLatch shutdownLatch = new CountDownLatch(1);
            
            // Store references for shutdown hook
            final ServerAPI finalServerAPI = serverAPI;
            final Orchestrator finalOrchestrator = orchestrator;
            
            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, cleaning up...");
                try {
                    finalOrchestrator.shutdown();
                    finalServerAPI.close();
                    logger.info("Client shutdown complete");
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
                shutdownLatch.countDown();
            }));
            
            logger.info("Client is running. Press Ctrl+C to stop.");
            
            // Keep main thread alive - background handlers will process server messages
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Fatal error in client", e);
            System.exit(1);
        } finally {
            // Cleanup on exit
            try {
                if (orchestrator != null) {
                    orchestrator.shutdown();
                }
                if (serverAPI != null) {
                    serverAPI.close();
                }
            } catch (Exception e) {
                logger.error("Error during cleanup", e);
            }
        }
    }
    
    /**
     * Gets the server URL from command-line arguments, environment variable, or default.
     * 
     * @param args command-line arguments
     * @return server URL to connect to
     */
    private static String getServerURL(String[] args) {
        // Check command-line argument first
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }
        
        // Check environment variable
        String envURL = System.getenv(ENV_SERVER_URL);
        if (envURL != null && !envURL.isBlank()) {
            return envURL;
        }
        
        // Return default
        return DEFAULT_SERVER_URL;
    }
    
    /**
     * Gets the player ID from environment variable or generates a default.
     * 
     * @return player ID for this client
     */
    private static String getPlayerId() {
        // Check environment variable
        String envPlayerId = System.getenv(ENV_PLAYER_ID);
        if (envPlayerId != null && !envPlayerId.isBlank()) {
            return envPlayerId;
        }
        
        // Generate default player ID with timestamp for uniqueness
        return "player-" + System.currentTimeMillis();
    }
}
