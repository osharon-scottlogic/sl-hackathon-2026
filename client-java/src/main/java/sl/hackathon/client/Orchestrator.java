package sl.hackathon.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.api.ServerAPI;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;
import sl.hackathon.client.util.Ansi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.*;

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
    private static final String GAME_LOGS_DIR = "./game-logs/";
    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

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
    private void handleGameStart(StartGameMessage message) {
        logger.info("Game started!");
        
        GameStatusUpdate statusUpdate = message.getGameStatusUpdate();
        if (statusUpdate != null) {
            // Extract map layout from status update
            this.mapLayout = statusUpdate.map();
            
            // Extract initial game state from history (first element)
            if (statusUpdate.history() != null && statusUpdate.history().length > 0) {
                this.currentGameState = statusUpdate.history()[0];
                logger.info("Initial game state received with " + Ansi.YELLOW + "{}" + Ansi.RESET + " units", currentGameState.units().length);
            } else {
                logger.warn("No initial game state in history");
            }
            
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
        long timeLimitMs = 5000L; // Default timeout; should come from message in real implementation
        long botTimeLimit = Math.max(0, timeLimitMs - TIMEOUT_BUFFER_MS);
        
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
            // Create a default map layout if not set
            mapLayout = new MapLayout(new Dimension(10, 10), new Position[0]);
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
        
        GameStatusUpdate statusUpdate = message.getGameStatusUpdate();
        if (statusUpdate != null) {
            String winnerId = statusUpdate.winnerId();
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
            writeGameLog(statusUpdate);
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
     * Writes game log to JSON file in the game-logs directory.
     * File name includes timestamp for uniqueness.
     * 
     * @param statusUpdate the final game status update
     */
    private void writeGameLog(GameStatusUpdate statusUpdate) {
        try {
            // Create game-logs directory if it doesn't exist
            File logsDir = new File(GAME_LOGS_DIR);
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            // Generate filename with timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = writeLogFile(statusUpdate, timestamp);

            logger.info("Game log written to: {}", filename);
            
        } catch (IOException e) {
            logger.warn("Failed to write game log", e);
        }
    }

    private String writeLogFile(GameStatusUpdate statusUpdate, String timestamp) throws IOException {
        String filename = GAME_LOGS_DIR + "game_" + timestamp + ".json";

        // Extract all unique players from first state
        java.util.Set<String> playerIds = new java.util.LinkedHashSet<>();
        if (statusUpdate.history() != null && statusUpdate.history().length > 0) {
            GameState firstState = statusUpdate.history()[0];
            if (firstState.units() != null) {
                for (Unit unit : firstState.units()) {
                    if (unit.owner() != null && !unit.owner().equals("none")) {
                        playerIds.add(unit.owner());
                    }
                }
            }
        }

        // Write game log as JSON
        try (FileWriter writer = new FileWriter(filename)) {
            // Simple JSON representation
            writer.write("{\n");
            
            // Players array
            writer.write("  \"players\": " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(playerIds.toArray(new String[0])) + ",\n");
            
            // Map dimensions
            writer.write("  \"mapDimensions\": ");
            if (statusUpdate.map() != null && statusUpdate.map().dimension() != null) {
                writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(statusUpdate.map().dimension()));
            } else {
                writer.write("null");
            }
            writer.write(",\n");
            
            // Wall positions
            writer.write("  \"walls\": ");
            if (statusUpdate.map() != null && statusUpdate.map().walls() != null) {
                writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(statusUpdate.map().walls()));
            } else {
                writer.write("[]");
            }
            writer.write(",\n");
            
            writer.write("  \"winner\": \"" + (statusUpdate.winnerId() != null ? statusUpdate.winnerId() : "none") + "\",\n");
            writer.write("  \"timestamp\": " + timestamp + ",\n");
            writer.write("  \"turns\": \n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(statusUpdate.history()) +"\n");
            writer.write("}\n");
        }
        return filename;
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
