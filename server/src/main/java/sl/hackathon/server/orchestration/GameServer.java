package sl.hackathon.server.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.communication.ClientRegistry;
import sl.hackathon.server.communication.WebSocketAdapter;
import sl.hackathon.server.communication.WebSocketServerContainer;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;

/**
 * Main orchestrator for the game server.
 * Wires together GameEngine, ClientRegistry, GameSession, and WebSocket communication.
 * 
 * Responsibilities:
 * - Initialize and configure all server components
 * - Wire handler callbacks between components
 * - Manage server lifecycle (start/stop)
 * - Coordinate GameSession thread execution
 * - Handle component shutdown
 */
public class GameServer {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    
    private final ServerConfig config;
    private final GameEngine gameEngine;
    private final ClientRegistry clientRegistry;
    private final WebSocketServerContainer webSocketServer;
    private GameSession gameSession;
    private Thread gameSessionThread;
    
    /**
     * Constructs a GameServer with the given configuration and game engine.
     * 
     * @param config the server configuration
     * @param gameEngine the game engine instance
     * @throws IllegalArgumentException if config or gameEngine is null
     */
    public GameServer(ServerConfig config, GameEngine gameEngine) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig cannot be null");
        }
        if (gameEngine == null) {
            throw new IllegalArgumentException("GameEngine cannot be null");
        }
        
        this.config = config;
        this.gameEngine = gameEngine;
        this.clientRegistry = new ClientRegistry();
        this.webSocketServer = new WebSocketServerContainer(config.getPort());
        
        wireHandlers();
    }
    
    /**
     * Wires handler callbacks between components.
     * 
     * Connections:
     * - ClientRegistry.onClientConnect → GameEngine.addPlayer
     * - ClientRegistry.onClientDisconnect → GameEngine.removePlayer
     * - WebSocketAdapter callbacks → ClientRegistry and GameSession
     */
    private void wireHandlers() {
        logger.info("Wiring component handlers");
        
        // Inject ClientRegistry into WebSocketAdapter
        WebSocketAdapter.setClientRegistry(clientRegistry);
        
        // Wire client connect/disconnect to GameEngine
        WebSocketAdapter.setOnClientConnect(playerId -> {
            logger.info("Client connected: {}", playerId);
            gameEngine.addPlayer(playerId);
        });
        
        WebSocketAdapter.setOnClientDisconnect(playerId -> {
            logger.info("Client disconnected: {}", playerId);
            gameEngine.removePlayer(playerId);
        });
        
        // Wire incoming action messages to GameEngine via GameSession
        WebSocketAdapter.setOnMessage((playerId, message) -> {
            logger.debug("Received message from {}: {}", playerId, message.getClass().getSimpleName());
            
            if (message instanceof ActionMessage actionMsg) {
                if (gameSession != null) {
                    gameSession.submitAction(playerId, gameSession.getCurrentTurnId(), actionMsg.getActions());
                }
            }
        });
    }
    
    /**
     * Starts the game server.
     * 
     * Initializes WebSocket server, creates GameParams, starts GameSession thread.
     * 
     * @throws IllegalStateException if server is already running
     */
    public void start() {
        logger.info("Starting GameServer with config: {}", config);
        
        try {
            // Start WebSocket server
            webSocketServer.start();
            logger.info("WebSocket server started on port {}", config.getPort());
            
            // Create GameParams
            GameParams gameParams = new GameParams(
                config.getMapConfig(),
                config.getTurnTimeLimit(),
                0.3f // default food scarcity
            );
            
            // Create and start GameSession
            gameSession = new GameSession(gameEngine, clientRegistry, gameParams);
            gameSessionThread = new Thread(gameSession, "GameSession-Thread");
            gameSessionThread.start();
            logger.info("GameSession thread started");
            
            logger.info("GameServer started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start GameServer", e);
            stop();
            throw new RuntimeException("Failed to start GameServer", e);
        }
    }
    
    /**
     * Stops the game server.
     * 
     * Shuts down GameSession thread and WebSocket server gracefully.
     */
    public void stop() {
        logger.info("Stopping GameServer");
        
        try {
            // Stop GameSession
            if (gameSession != null) {
                gameSession.shutdown();
                logger.info("GameSession shutdown requested");
            }
            
            // Wait for GameSession thread to finish
            if (gameSessionThread != null && gameSessionThread.isAlive()) {
                gameSessionThread.join(5000);
                if (gameSessionThread.isAlive()) {
                    logger.warn("GameSession thread did not terminate within timeout");
                } else {
                    logger.info("GameSession thread terminated");
                }
            }
            
            // Stop WebSocket server
            webSocketServer.stop();
            logger.info("WebSocket server stopped");
            
            logger.info("GameServer stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error during GameServer shutdown", e);
        }
    }
    
    /**
     * Checks if the server is currently running.
     * 
     * @return true if server is running
     */
    public boolean isRunning() {
        return webSocketServer.isRunning();
    }
    
    /**
     * Gets the server configuration.
     * 
     * @return the server configuration
     */
    public ServerConfig getConfig() {
        return config;
    }
    
    /**
     * Gets the game session.
     * 
     * @return the game session, or null if not started
     */
    public GameSession getGameSession() {
        return gameSession;
    }
}
