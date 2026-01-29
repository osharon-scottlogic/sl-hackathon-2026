package sl.hackathon.server.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.communication.ClientRegistry;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;
import sl.hackathon.server.util.Ansi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * GameSession orchestrates the game loop and manages turn progression.
 * Implements Runnable to execute in a background thread.
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
    private static final ObjectMapper objectMapper = JsonMapper.builder().build();
    
    private final GameEngine gameEngine;
    private final ClientRegistry clientRegistry;
    private final GameParams gameParams;
    
    private volatile boolean shutdown = false;
    
    @Getter
    private volatile boolean gameStarted = false;
    
    @Getter
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
            logger.warn("Player " + Ansi.YELLOW + "{}" + Ansi.RESET + " submitted actions for turn " + Ansi.YELLOW + "{}" + Ansi.RESET + " but current turn is " + Ansi.YELLOW + "{}" + Ansi.RESET, 
                playerId, turnId, currentTurnId);
            return;
        }
        
        logger.debug("Player " + Ansi.YELLOW + "{}" + Ansi.RESET + " submitted " + Ansi.YELLOW + "{}" + Ansi.RESET + " actions for turn " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId, actions.length, turnId);
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

            logger.debug("game initialized with "+Ansi.YELLOW+"{}"+Ansi.RESET+" units", gameEngine.getGameState().units().length);
            // Main game loop
            runGameLoop();
            
            // Broadcast end game message
            broadcastEndGame();
            
            logger.info("GameSession completed successfully");
            
        } catch (InterruptedException e) {
            logger.warn("GameSession interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error(Ansi.RED + "GameSession encountered an error" + Ansi.RESET, e);
            throw new RuntimeException("GameSession failed", e);
        }
    }
    
    /**
     * Waits for both players to connect via ClientRegistry.
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
    private void initializeGame() throws JsonProcessingException {
        logger.info("Initializing game with params: " + Ansi.YELLOW + "{}" + Ansi.RESET, objectMapper.writeValueAsString(gameParams));
        
        GameState initialState = gameEngine.initialize(gameParams);
        gameStarted = true;
        
        // Broadcast StartGameMessage to all clients
        GameStart gameStart = new GameStart(
            new MapLayout(gameParams.mapConfig().dimension(), gameParams.mapConfig().walls()),
            initialState.units(),
            System.currentTimeMillis()
        );
        
        StartGameMessage startMessage = new StartGameMessage(gameStart);
        clientRegistry.broadcast(startMessage);
        
        logger.info("Game initialized and start message broadcast");
    }
    
    /**
     * Executes the main game loop: turn progression until game ends.
     * 
     * @throws InterruptedException if interrupted during turn processing
     */
    private void runGameLoop() throws InterruptedException {
        logger.info("\nStarting game loop with "+Ansi.YELLOW+"{}"+ Ansi.RESET+" units", gameEngine.getGameState().units().length);

        while (!gameEngine.isGameEnded() && !shutdown) {
            processTurn();
            currentTurnId++;
        }
        
        logger.info("\nGame loop ended. Game ended: {}, Shutdown: {}", gameEngine.isGameEnded(), shutdown);
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
        logger.debug("Processing turn " + Ansi.YELLOW + "{}" + Ansi.RESET, currentTurnId);
        
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
                logger.error(Ansi.RED + "Failed to send NextTurnMessage to player " + Ansi.YELLOW + "{}" + Ansi.RESET + ": " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId, e.getMessage());
            }
        }
        
        // Wait for all players to submit actions (with timeout)
        long timeoutMs = gameParams.turnTimeLimit();
        boolean actionsReceived = turnLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        
        if (!actionsReceived) {
            logger.warn("Turn " + Ansi.YELLOW + "{}" + Ansi.RESET + " timed out after " + Ansi.YELLOW + "{}" + Ansi.RESET + "ms. Processing partial actions.", currentTurnId, timeoutMs);
        }
        
        // Process each player's actions
        for (String playerId : gameEngine.getActivePlayers()) {
            Action[] actions = pendingActions.getOrDefault(playerId, new Action[0]);
            
            if (actions.length == 0) {
                logger.warn("Player " + Ansi.YELLOW + "{}" + Ansi.RESET + " submitted no actions for turn " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId, currentTurnId);
            }
            
            boolean success = gameEngine.handlePlayerActions(playerId, actions);
            
            if (!success) {
                logger.warn("Failed to process actions for player " + Ansi.YELLOW + "{}" + Ansi.RESET + " on turn " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId, currentTurnId);
            }
        }
        
        logger.debug("Turn " + Ansi.YELLOW + "{}" + Ansi.RESET + " completed, " + Ansi.YELLOW + "{}" + Ansi.RESET + " units on the board", currentTurnId, gameEngine.getGameState().units().length);
    }
    
    /**
     * Broadcasts the EndGameMessage with final game status and winner.
     */
    private void broadcastEndGame() throws JsonProcessingException {
        logger.info("Broadcasting end game message");

        logger.info(Ansi.MAGENTA+gameEngine.getGameDeltaHistory().size()+Ansi.RESET);

        GameEnd gameEnd = new GameEnd(
            new MapLayout(gameParams.mapConfig().dimension(), gameParams.mapConfig().walls()),
            gameEngine.getGameDeltaHistory().toArray(new GameDelta[0]),
            gameEngine.getWinnerId(),
            System.currentTimeMillis()
        );
        
        EndGameMessage endMessage = new EndGameMessage(gameEnd);
        clientRegistry.broadcast(endMessage);
        
        logger.info("End game message broadcast. Winner: " + Ansi.YELLOW + "{}" + Ansi.RESET + " after " + Ansi.YELLOW + "{}" + Ansi.RESET + " turns", gameEngine.getWinnerId(), gameEngine.getGameDeltaHistory().size());
    }
}
