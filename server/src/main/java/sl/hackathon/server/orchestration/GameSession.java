package sl.hackathon.server.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.communication.ClientRegistry;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * GameSession orchestrates the game loop and manages turn progression.
 * Implements Runnable to execute in a background thread.
 * 
 * Responsibilities:
 * - Wait for both players to be ready via ClientRegistry
 * - Initialize game with GameParams
 * - Execute main turn loop:
 *   1. Broadcast NextTurnMessage to all players
 *   2. Collect player actions (async, non-blocking)
 *   3. Process turn with timeout enforcement
 *   4. Check for game end conditions
 * - Broadcast EndGameMessage when game completes
 * - Support graceful shutdown
 */
public class GameSession implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GameSession.class);
    
    private final GameEngine gameEngine;
    private final ClientRegistry clientRegistry;
    private final GameParams gameParams;
    
    private volatile boolean shutdown = false;
    private volatile boolean gameStarted = false;
    private volatile int currentTurnId = 0;
    
    // Store actions submitted by players for the current turn
    private final Map<String, Action[]> pendingActions = new ConcurrentHashMap<>();
    private CountDownLatch turnLatch;
    
    /**
     * Constructs a new GameSession.
     * 
     * @param gameEngine the game engine to use
     * @param clientRegistry the client registry for player communication
     * @param gameParams the game parameters (mapConfig, turnTimeLimit, etc.)
     * @throws IllegalArgumentException if any parameter is null
     */
    public GameSession(GameEngine gameEngine, ClientRegistry clientRegistry, GameParams gameParams) {
        if (gameEngine == null) {
            throw new IllegalArgumentException("GameEngine cannot be null");
        }
        if (clientRegistry == null) {
            throw new IllegalArgumentException("ClientRegistry cannot be null");
        }
        if (gameParams == null) {
            throw new IllegalArgumentException("GameParams cannot be null");
        }
        
        this.gameEngine = gameEngine;
        this.clientRegistry = clientRegistry;
        this.gameParams = gameParams;
    }
    
    /**
     * Submits player actions for the current turn.
     * Non-blocking and thread-safe.
     * 
     * @param playerId the player ID submitting actions
     * @param turnId the turn ID these actions are for
     * @param actions the actions to submit
     */
    public void submitAction(String playerId, int turnId, Action[] actions) {
        if (turnId != currentTurnId) {
            logger.warn("Player {} submitted actions for turn {} but current turn is {}", 
                playerId, turnId, currentTurnId);
            return;
        }
        
        logger.debug("Player {} submitted {} actions for turn {}", playerId, actions.length, turnId);
        pendingActions.put(playerId, actions);
        
        // Check if all players have submitted
        if (turnLatch != null && pendingActions.size() == gameEngine.getActivePlayers().size()) {
            turnLatch.countDown();
        }
    }
    
    /**
     * Signals the session to shut down gracefully.
     * The game loop will terminate after the current turn completes.
     */
    public void shutdown() {
        logger.info("GameSession shutdown requested");
        shutdown = true;
        
        // Release any waiting threads
        if (turnLatch != null) {
            turnLatch.countDown();
        }
    }
    
    /**
     * Checks if the game has started.
     * 
     * @return true if game initialization is complete
     */
    public boolean isGameStarted() {
        return gameStarted;
    }
    
    /**
     * Gets the current turn ID.
     * 
     * @return the current turn ID (0-based)
     */
    public int getCurrentTurnId() {
        return currentTurnId;
    }
    
    @Override
    public void run() {
        try {
            logger.info("GameSession thread started");
            
            // Wait for both players to be ready
            waitForPlayers();
            
            if (shutdown) {
                logger.info("Shutdown requested before game start");
                return;
            }
            
            // Initialize the game
            initializeGame();
            
            // Main game loop
            runGameLoop();
            
            // Broadcast end game message
            broadcastEndGame();
            
            logger.info("GameSession completed successfully");
            
        } catch (InterruptedException e) {
            logger.warn("GameSession interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("GameSession encountered an error", e);
            throw new RuntimeException("GameSession failed", e);
        }
    }
    
    /**
     * Waits for both players to connect via ClientRegistry.
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    private void waitForPlayers() throws InterruptedException {
        logger.info("Waiting for players to connect...");
        
        while (!clientRegistry.isReady() && !shutdown) {
            Thread.sleep(100);
        }
        
        if (clientRegistry.isReady()) {
            logger.info("Both players connected and ready");
        }
    }
    
    /**
     * Initializes the game with the configured GameParams.
     */
    private void initializeGame() {
        logger.info("Initializing game with params: {}", gameParams);
        
        GameState initialState = gameEngine.initialize(gameParams);
        gameStarted = true;
        
        // Broadcast StartGameMessage to all clients
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            new MapLayout(gameParams.mapConfig().dimension(), gameParams.mapConfig().walls()),
            new GameState[]{initialState},
            null
        );
        
        StartGameMessage startMessage = new StartGameMessage(statusUpdate);
        clientRegistry.broadcast(startMessage);
        
        logger.info("Game initialized and start message broadcast");
    }
    
    /**
     * Executes the main game loop: turn progression until game ends.
     * 
     * @throws InterruptedException if interrupted during turn processing
     */
    private void runGameLoop() throws InterruptedException {
        logger.info("Starting game loop");
        
        while (!gameEngine.isGameEnded() && !shutdown) {
            processTurn();
            currentTurnId++;
        }
        
        logger.info("Game loop ended. Game ended: {}, Shutdown: {}", gameEngine.isGameEnded(), shutdown);
    }
    
    /**
     * Processes a single turn:
     * 1. Broadcast NextTurnMessage
     * 2. Wait for player actions (with timeout)
     * 3. Submit actions to engine
     * 4. Update game state
     * 
     * @throws InterruptedException if interrupted while waiting for actions
     */
    private void processTurn() throws InterruptedException {
        logger.debug("Processing turn {}", currentTurnId);
        
        // Clear previous turn's actions
        pendingActions.clear();
        turnLatch = new CountDownLatch(1);
        
        // Broadcast NextTurnMessage to all players
        GameState currentState = gameEngine.getGameState();
        for (String playerId : gameEngine.getActivePlayers()) {
            NextTurnMessage turnMessage = new NextTurnMessage(playerId, currentState);
            try {
                clientRegistry.send(playerId, turnMessage);
            } catch (Exception e) {
                logger.error("Failed to send NextTurnMessage to player {}: {}", playerId, e.getMessage());
            }
        }
        
        // Wait for all players to submit actions (with timeout)
        long timeoutMs = gameParams.turnTimeLimit();
        boolean actionsReceived = turnLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        
        if (!actionsReceived) {
            logger.warn("Turn {} timed out after {}ms. Processing partial actions.", currentTurnId, timeoutMs);
        }
        
        // Process each player's actions
        for (String playerId : gameEngine.getActivePlayers()) {
            Action[] actions = pendingActions.getOrDefault(playerId, new Action[0]);
            
            if (actions.length == 0) {
                logger.warn("Player {} submitted no actions for turn {}", playerId, currentTurnId);
            }
            
            boolean success = gameEngine.handlePlayerActions(playerId, actions);
            
            if (!success) {
                logger.warn("Failed to process actions for player {} on turn {}", playerId, currentTurnId);
            }
        }
        
        logger.debug("Turn {} completed", currentTurnId);
    }
    
    /**
     * Broadcasts the EndGameMessage with final game status and winner.
     */
    private void broadcastEndGame() {
        logger.info("Broadcasting end game message");
        
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.END,
            new MapLayout(gameParams.mapConfig().dimension(), gameParams.mapConfig().walls()),
            gameEngine.getGameStateHistory().toArray(new GameState[0]),
            gameEngine.getWinnerId()
        );
        
        EndGameMessage endMessage = new EndGameMessage(statusUpdate);
        clientRegistry.broadcast(endMessage);
        
        logger.info("End game message broadcast. Winner: {}", gameEngine.getWinnerId());
    }
}
