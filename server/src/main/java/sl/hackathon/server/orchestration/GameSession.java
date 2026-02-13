package sl.hackathon.server.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.communication.ClientRegistry;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.engine.GameEngine;
import sl.hackathon.server.util.Ansi;
import sl.hackathon.server.validators.GameEndValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static sl.hackathon.server.util.Ansi.*;

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
    private final GameSettings gameSettings;
    
    private volatile boolean shutdown = false;
    
    @Getter
    private volatile boolean gameStarted = false;
    
    @Getter
    private volatile int currentRound = 0;

    private volatile List<String> turnOrder = List.of();
    private volatile String currentPlayerId = null;

    // Store actions submitted by players for the current turn
    private final Map<String, Action[]> pendingActions = new ConcurrentHashMap<>();
    private CountDownLatch turnLatch;
    
    /**
     * Constructs a new GameSession.
     * 
     * @param gameEngine the game engine to use
     * @param clientRegistry the client registry for player communication
     * @param gameSettings the game parameters (mapConfig, turnTimeLimit, etc.)
     * @throws IllegalArgumentException if any parameter is null
     */
    public GameSession(@NonNull GameEngine gameEngine,@NonNull ClientRegistry clientRegistry,@NonNull GameSettings gameSettings) {
        this.gameEngine = gameEngine;
        this.clientRegistry = clientRegistry;
        this.gameSettings = gameSettings;
    }
    
    /**
     * Submits player actions for the current turn.
     * Non-blocking and thread-safe.
     * 
     * @param playerId the player ID submitting actions
     * @param roundCount the turn ID these actions are for
     * @param actions the actions to submit
     */
    public void submitAction(String playerId, int roundCount, Action[] actions) {
        if (roundCount != currentRound) {
            logger.warn(yellow("Player {} submitted actions for round {} but current round is {}"),
                playerId,
                roundCount,
                currentRound
            );
            return;
        }

        if (currentPlayerId == null || !currentPlayerId.equals(playerId)) {
            logger.debug(yellow("Ignoring actions from non-active player {} for round {} (expected {})"),
                playerId,
                roundCount,
                    currentPlayerId
            );
            return;
        }

        logger.debug(
            "Player " + Ansi.YELLOW + "{}" + Ansi.RESET +
                " submitted " + Ansi.YELLOW + "{}" + Ansi.RESET +
                " actions for round " + Ansi.YELLOW + "{}" + Ansi.RESET,
            playerId,
            actions.length,
            roundCount
        );

        pendingActions.put(playerId, actions);

        if (turnLatch != null) {
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

            logger.debug(green("game initialized with {} units"), gameEngine.getGameState().units().length);
            // Main game loop
            runGameLoop();
            
            // Broadcast end game message
            broadcastEndGame();

            logger.info("GameSession completed successfully");
            
        } catch (InterruptedException e) {
            logger.warn("GameSession interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error(redBg("GameSession encountered an error"), e);
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
        logger.info(green("Initializing game with params: {}"), objectMapper.writeValueAsString(gameSettings));
        
        GameState initialState = gameEngine.initialize(gameSettings);
        gameStarted = true;
        
        // Broadcast StartGameMessage to all clients
        GameStart gameStart = new GameStart(
            new MapLayout(gameSettings.dimension(), gameSettings.walls()),
            initialState.units(),
            System.currentTimeMillis()
        );
        
        StartGameMessage startMessage = new StartGameMessage(gameStart);
        clientRegistry.broadcast(startMessage);
        
        // Freeze player turn order (join order) once the game starts
        this.turnOrder = new ArrayList<>(gameEngine.getActivePlayers());
        if (this.turnOrder.size() < 2) {
            logger.warn(yellow("Expected at least 2 active players, but got {}."), this.turnOrder.size());
        }

        logger.info("Game initialized and start message broadcast");
    }
    
    /**
     * Executes the main game loop: turn progression until game ends.
     * 
     * @throws InterruptedException if interrupted during turn processing
     */
    private void runGameLoop() throws InterruptedException {
        logger.info(green("\nStarting game loop with {} units"), gameEngine.getGameState().units().length);

        while (!gameEngine.isGameEnded() && !shutdown) {
            processRound();
            currentRound++;
        }

        logger.info(
            green("\nGame loop ended. Game ended: {}, Shutdown: {}"),
            gameEngine.isGameEnded(),
            shutdown
        );
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
    private void processRound() throws InterruptedException {
        logger.debug(yellow("Processing round {}"), currentRound);

        // Two half-turns per round: only one player acts per half-turn
        for (String currentPlayer : turnOrder) {
            processSinglePlayerTurn(currentPlayer);

            if (shutdown || gameEngine.isGameEnded()) {
                return;
            }
        }
    }

    private void processSinglePlayerTurn(String activePlayerId) throws InterruptedException {
        currentPlayerId = activePlayerId;

        // Clear previous actions for this player and set latch
        pendingActions.remove(activePlayerId);
        turnLatch = new CountDownLatch(1);

        // Send NextTurnMessage only to the active player
        GameState currentState = gameEngine.getGameState();
        NextTurnMessage turnMessage = new NextTurnMessage(activePlayerId, currentState, gameSettings.turnTimeLimit());
        try {
            clientRegistry.send(activePlayerId, turnMessage);
        } catch (Exception e) {
            logger.error(redBg(yellow("Failed to send NextTurnMessage to player {}: {}")), activePlayerId, e.getMessage());
        }

        long timeoutMs = gameSettings.turnTimeLimit();
        boolean actionReceived = turnLatch.await(timeoutMs, TimeUnit.MILLISECONDS);

        if (!actionReceived) {
            logger.warn(yellow("Round {}: player {} timed out after {}ms. Ending game."), currentRound, activePlayerId, timeoutMs);
            gameEngine.removePlayer(activePlayerId);
            return;
        }

        Action[] actions = pendingActions.getOrDefault(activePlayerId, new Action[0]);
        if (actions.length == 0) {
            logger.warn(yellow("Player {} submitted no actions for round {}"), activePlayerId, currentRound);
        }

        boolean success = gameEngine.handlePlayerActions(activePlayerId, actions);
        if (!success) {
            logger.warn(yellow("Failed to process actions for player {} on round {}"), activePlayerId, currentRound);
        }

        logger.debug(
            green("player {} turn completed: round={}, {} units on the board"),
                activePlayerId,
                currentRound,
                gameEngine.getGameState().units().length
        );
    }

    /**
     * Broadcasts the EndGameMessage with final game status and winner.
     */
    private void broadcastEndGame() {
        logger.info("Broadcasting end game message");

        String winner = GameEndValidator.getWinnerId(gameEngine.getGameState());
        GameEnd gameEnd = new GameEnd(
            new MapLayout(gameSettings.dimension(), gameSettings.walls()),
            gameEngine.getGameDeltaHistory().toArray(new GameDelta[0]),
            winner,
            System.currentTimeMillis()
        );
        
        EndGameMessage endMessage = new EndGameMessage(gameEnd);
        clientRegistry.broadcast(endMessage);
        
        logger.info(green("End game message broadcast. Winner: {} after {} rounds"), winner, gameEngine.getGameDeltaHistory().size());
    }
}
