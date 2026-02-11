package sl.hackathon.client.orchestrator;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.Bot;
import sl.hackathon.client.api.ServerAPI;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;
import sl.hackathon.client.util.Ansi;

import java.io.IOException;
import java.util.concurrent.*;

import static sl.hackathon.client.orchestrator.ArenaParser.parse;

/**
 * Main client controller that orchestrates the game flow.
 * Coordinates between ServerAPI, Bot, and game state management.
 * Responsibilities:
 * - Initialize client session with server
 * - Receive game start/turn/end messages
 * - Invoke bot decision-making with timeout enforcement
 * - Send actions to server
 * - Maintain game state history
 * - Write game logs to file on completion
 */
@Getter
@Setter
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);
    private static final long TIMEOUT_BUFFER_MS = 1000L; // 1 second safety buffer

    private ServerAPI serverAPI;
    private Bot bot;
    private String playerId;
    
    private MapLayout mapLayout;
    private GameState currentGameState;
    
    private final ExecutorService botExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean initialized = false;
    
    /**
     * Initializes the orchestrator with dependencies and wires up callbacks.
     * 
     * @param serverAPI the server communication API
     * @param bot the bot implementation for decision-making
     * @param playerId the player ID for this client
     * @throws IllegalArgumentException if any parameter is null
     */
    public void init(ServerAPI serverAPI, Bot bot, String playerId) {
        if (serverAPI == null) {
            throw new IllegalArgumentException("ServerAPI cannot be null");
        }
        if (bot == null) {
            throw new IllegalArgumentException("Bot cannot be null");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("PlayerId cannot be null or blank");
        }
        
        this.serverAPI = serverAPI;
        this.bot = bot;
        this.playerId = playerId;
        
        wireCallbacks();
        initialized = true;
        
        logger.info("Orchestrator initialized for player: " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId);
    }
    
    /**
     * Wires ServerAPI callbacks to orchestrator handlers.
     */
    private void wireCallbacks() {
        serverAPI.setOnGameStart(this::handleGameStart);
        serverAPI.setOnPlayerAssigned(this::handlePlayerAssigned);
        serverAPI.setOnNextTurn(this::handleNextTurn);
        serverAPI.setOnGameEnd(this::handleGameEnd);
        serverAPI.setOnInvalidOperation(this::handleInvalidOperation);
        serverAPI.setOnError(this::handleError);
    }

    /**
     * Handles player assignment message from server.
     * Updates the orchestrator's player ID with the server-assigned value.
     * 
     * @param message the player assigned message containing the server-assigned player ID
     */
    private void handlePlayerAssigned(PlayerAssignedMessage message) {
        String assignedPlayerId = message.getPlayerId();
        logger.info("Received player assignment: " + Ansi.YELLOW + "{}" + Ansi.RESET, assignedPlayerId);
        
        // Update player ID with server-assigned value
        if (assignedPlayerId != null && !assignedPlayerId.isBlank()) {
            this.playerId = assignedPlayerId;
            logger.info("Player ID updated to: " + Ansi.YELLOW + "{}" + Ansi.RESET, this.playerId);
        } else {
            logger.warn("Received invalid player ID assignment");
        }
    }

    /**
     * Handles game start message from server.
     * Initializes map layout and game state.
     * 
     * @param message the start game message
     */
    private void handleGameStart(@NonNull StartGameMessage message) {
        logger.info("Game started!");
        
        GameStart gameStart = message.getGameStart();
        if (message.getArena() != null) {
            gameStart = parse(message.getArena(), message.getTimestamp());
        }

        if (gameStart != null) {
            // Extract map layout from status update
            this.mapLayout = gameStart.map();

            this.currentGameState = new GameState(gameStart.initialUnits(), gameStart.timestamp());

            logger.info("Map layout initialized: " + Ansi.YELLOW + "{}x{}" + Ansi.RESET, 
                mapLayout.dimension().width(), 
                mapLayout.dimension().height());
        }
    }
    
    /**
     * Handles next turn message from server.
     * Invokes bot decision-making with timeout enforcement and sends actions back.
     * 
     * @param message the next turn message
     */
    private void handleNextTurn(NextTurnMessage message) {
        if (!playerId.equals(message.getPlayerId())) {
            logger.debug(Ansi.CYAN + "Received next turn for different player: {} (I'm {})" + Ansi.RESET,
                    message.getPlayerId(),
                    playerId);
            return;
        }
        
        logger.info("Turn started for player: " + Ansi.YELLOW + "{}" + Ansi.RESET, playerId);
        
        GameState turnState = message.getGameState();
        if (turnState != null) {
            this.currentGameState = turnState;
        }
        
        // Calculate remaining time with buffer
        long botTimeLimit = Math.max(0, message.getTimeLimitMs() - TIMEOUT_BUFFER_MS);
        
        try {
            // Invoke bot with timeout enforcement
            Action[] actions = invokeBotWithTimeout(botTimeLimit);
            
            // Send actions to server
            serverAPI.send(playerId, actions);
            logger.info("Sent " + Ansi.YELLOW + "{}" + Ansi.RESET + " actions to server", actions.length);
            
        } catch (TimeoutException e) {
            logger.warn("Bot decision-making timed out, sending fallback actions");
            sendFallbackActions();
        } catch (Exception e) {
            logger.error(Ansi.RED + "Error during turn processing" + Ansi.RESET, e);
            sendFallbackActions();
        }
    }
    
    /**
     * Invokes bot decision-making with timeout enforcement.
     * Uses ExecutorService to interrupt bot if it exceeds time limit.
     * 
     * @param timeLimitMs maximum time allowed for bot decision-making
     * @return array of actions decided by bot
     * @throws TimeoutException if bot exceeds time limit
     * @throws Exception if bot execution fails
     */
    private Action[] invokeBotWithTimeout(long timeLimitMs) throws TimeoutException, Exception {
        if (mapLayout == null) {
            throw new Exception("Trying to invoke bot but there's no map");
        }
        
        Future<Action[]> future = botExecutor.submit(() -> 
            bot.handleState(playerId, mapLayout, currentGameState, timeLimitMs)
        );
        
        try {
            return future.get(timeLimitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw new Exception("Bot execution failed", e.getCause());
        }
    }
    
    /**
     * Sends fallback actions (empty action array) to server when bot fails or times out.
     */
    private void sendFallbackActions() {
        try {
            serverAPI.send(playerId, new Action[0]);
            logger.info("Sent fallback (empty) actions to server");
        } catch (IOException e) {
            logger.error(Ansi.RED + "Failed to send fallback actions" + Ansi.RESET, e);
        }
    }
    
    /**
     * Handles game end message from server.
     * Writes game log to file and performs cleanup.
     * 
     * @param message the end game message
     */
    private void handleGameEnd(EndGameMessage message) {
        logger.info("Game ended!");
        
        GameEnd gameEnd = message.getGameEnd();
        if (gameEnd != null) {
            String winnerId = gameEnd.winnerId();
            if (winnerId != null) {
                if (winnerId.equals(playerId)) {
                    logger.info("Victory! Player " + Ansi.YELLOW + "{}" + Ansi.RESET + " won the game", winnerId);
                } else {
                    logger.info("Defeat. Player " + Ansi.YELLOW + "{}" + Ansi.RESET + " won the game", winnerId);
                }
            } else {
                logger.info("Game ended (draw or no winner)");
            }
            
            // Write game log
            GameLogWriter.write(gameEnd);
        }
        
        cleanup();
        
        // Exit the client application after game ends
        logger.info("Exiting client application");
        System.exit(0);
    }
    
    /**
     * Handles invalid operation message from server.
     * 
     * @param message the invalid operation message
     */
    private void handleInvalidOperation(InvalidOperationMessage message) {
        logger.warn("Invalid operation: " + Ansi.YELLOW + "{}" + Ansi.RESET, message.getReason());
    }
    
    /**
     * Handles error from server API.
     * 
     * @param error the error that occurred
     */
    private void handleError(Throwable error) {
        logger.error(Ansi.RED + "Server API error" + Ansi.RESET, error);
    }

    /**
     * Performs cleanup when game ends or orchestrator is shut down.
     */
    private void cleanup() {
        logger.info("Cleaning up orchestrator resources");
        botExecutor.shutdown();
        try {
            if (!botExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                botExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            botExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Shuts down the orchestrator and releases resources.
     */
    public void shutdown() {
        logger.info("Orchestrator shutting down");
        cleanup();
    }
}
