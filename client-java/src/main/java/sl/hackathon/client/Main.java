package sl.hackathon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.api.ServerAPI;
import sl.hackathon.client.api.TutorialServerApi;
import sl.hackathon.client.api.WebSocketServerAPI;
import sl.hackathon.client.messages.MessageRouter;
import sl.hackathon.client.orchestrator.Orchestrator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static sl.hackathon.client.util.Ansi.*;

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

    private static final String ENV_CLIENT_VERSION = "CLIENT_VERSION";
    private static final String ENV_EXPECTED_SERVER_VERSION = "EXPECTED_SERVER_VERSION";

    private static final String CLIENT_VERSION_PREFIX = "java-";
    
    public static void main(String[] args) {
        String fullClientVersion = readBuildVersion();
        logger.info(green("Starting Game Client v{}..."), fullClientVersion);
        
        // Parse server URL from arguments, environment, or use default
        String serverURL = getServerURL(args);
        logger.info(green("Server URL: {}"), serverURL);

        String clientVersion = getClientVersion(fullClientVersion);
        int expectedServerVersion = getExpectedServerVersion(fullClientVersion);
        
        // Create components
        ServerAPI serverAPI = null;
        Orchestrator orchestrator = null;
        
        try {
            // Instantiate Orchestrator
            orchestrator = new Orchestrator();
            MessageRouter messageRouter = orchestrator.getMessageRouter();
            logger.info("Orchestrator created");

            // Instantiate ServerAPI
            // Tutorial mode is selected via a URL prefixed with "tutorial" (canonical: tutorial:<tutorialId>)
            serverAPI = serverURL.startsWith("tutorial") ? new TutorialServerApi(messageRouter) : new WebSocketServerAPI(messageRouter);
            logger.info("ServerAPI created");
            
            // Instantiate Bot (using StrategicBot as default)
            Bot bot = new StrategicBot();
            logger.info(green("{} created and named: {}"), bot.getClass().getSimpleName(), bot.getPlayerId());

            // Initialize orchestrator with dependencies
            orchestrator.init(serverAPI, bot);
            logger.info("Orchestrator initialized");
            
            // Connect to server
            String effectiveServerUrl = serverURL;
            if (!serverURL.startsWith("tutorial")) {
                effectiveServerUrl = appendConnectPayload(serverURL, bot.getPlayerId(), clientVersion, expectedServerVersion);
            }

            logger.info(yellow("Connecting to server at {}..."), effectiveServerUrl);
            serverAPI.connect(effectiveServerUrl);
            logger.info("Successfully connected to server");
            
            // Create latch for keeping main thread alive
            CountDownLatch shutdownLatch = new CountDownLatch(1);
            
            // Store references for shutdown hook
            final ServerAPI finalWebSocketServerAPI = serverAPI;
            final Orchestrator finalOrchestrator = orchestrator;
            
            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, cleaning up...");
                try {
                    finalOrchestrator.shutdown();
                    finalWebSocketServerAPI.close();
                    logger.info("Client shutdown complete");
                } catch (Exception e) {
                    logger.error(redBg("Error during shutdown"), e);
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
            logger.error(redBg("Fatal error in client"), e);
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
                logger.error(redBg("Error during cleanup"), e);
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

    private static String getClientVersion(String fullClientVersion) {
        String rawOverride = System.getenv(ENV_CLIENT_VERSION);
        String base = (rawOverride == null || rawOverride.isBlank()) ? fullClientVersion : rawOverride.trim();
        if (base.isBlank()) {
            base = "0.0.0";
        }
        return CLIENT_VERSION_PREFIX + base;
    }

    private static int getExpectedServerVersion(String fullClientVersion) {
        String raw = System.getenv(ENV_EXPECTED_SERVER_VERSION);
        int defaultExpectedServerVersion = majorVersionOf(fullClientVersion);
        if (defaultExpectedServerVersion <= 0) {
            defaultExpectedServerVersion = 1;
        }

        if (raw == null || raw.isBlank()) {
            return defaultExpectedServerVersion;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            logger.warn(yellow("Invalid EXPECTED_SERVER_VERSION value: {} (defaulting to {})"),
                    raw,
                    defaultExpectedServerVersion);
            return defaultExpectedServerVersion;
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
        int dot = trimmed.indexOf('.');
        String majorPart = dot >= 0 ? trimmed.substring(0, dot) : trimmed;
        try {
            return Integer.parseInt(majorPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String appendConnectPayload(String baseUrl, String callsign, String clientVersion, int expectedServerVersion) {
        String sep = baseUrl.contains("?") ? "&" : "?";

        return baseUrl
                + sep
                + "callsign=" + urlEncode(callsign)
                + "&clientVersion=" + urlEncode(clientVersion)
                + "&expectedServerVersion=" + expectedServerVersion;
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
