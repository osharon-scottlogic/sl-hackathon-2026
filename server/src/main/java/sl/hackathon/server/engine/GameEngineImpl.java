package sl.hackathon.server.engine;

import lombok.Getter;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.validators.ActionValidator;
import sl.hackathon.server.validators.GameEndValidator;

import java.util.*;

/**
 * Implementation of GameEngine that manages the authoritative game state.
 */
public class GameEngineImpl implements GameEngine {
    @Getter
    private final List<String> activePlayers;
    private GameState previousGameState = null;
    private GameState currentGameState = null;
    private final List<GameDelta> gameDeltaHistory;
    private GameParams gameParams;
    @Getter
    private boolean initialized;
    @Getter
    private int currentTurn;
    private final ActionValidator actionValidator;
    private final UnitGenerator unitGenerator;

    public GameEngineImpl() {
        this.activePlayers = new ArrayList<>();
        this.gameDeltaHistory = new ArrayList<>();
        this.initialized = false;
        this.currentTurn = 0;
        this.actionValidator = new ActionValidator();
        this.unitGenerator = new UnitGenerator();
    }

    /**
     * Adds a player to the game.
     *
     * @param playerId the player ID to add
     */
    @Override
    public void addPlayer(String playerId) {
        if (playerId != null && !activePlayers.contains(playerId)) {
            activePlayers.add(playerId);
        }
    }

    /**
     * Removes a player from the game.
     *
     * @param playerId the player ID to remove
     */
    @Override
    public void removePlayer(String playerId) {
        activePlayers.remove(playerId);
    }

    /**
     * Initializes the game with the given parameters.
     *
     * @param gameParams the game parameters including map and turn time limit
     * @return the initial game state
     */
    @Override
    public GameState initialize(GameParams gameParams) {
        if (gameParams == null) {
            throw new IllegalArgumentException("GameParams cannot be null");
        }

        this.gameParams = gameParams;
        initialized = true;
        currentTurn = 0;
        gameDeltaHistory.clear();

        // Create initial game state with units spawned at potential base locations
        Unit[] initialUnits = unitGenerator.createInitialUnits(gameParams, activePlayers);
        previousGameState = null;
        currentGameState = new GameState(initialUnits, System.currentTimeMillis());

        // Add to history
        this.gameDeltaHistory.add(GameDeltaFactory.get(this.previousGameState, this.currentGameState));
        return currentGameState;
    }

    /**
     * Gets the current game state.
     *
     * @return a snapshot of the current game state
     */
    @Override
    public GameState getGameState() {
        return currentGameState;
    }

    /**Gets the game delta history.
     *
     * @return a list of all game deltas from initialization to current
     */
    @Override
    public List<GameDelta> getGameDeltaHistory() {
        return new ArrayList<>(gameDeltaHistory);
    }

    /**
     * 
     * Handles player actions and updates the game state.
     *
     * @param playerId the player ID performing the actions
     * @param actions the actions to process
     * @return true if actions were successfully processed; false if invalid
     */
    @Override
    public boolean handlePlayerActions(String playerId, Action[] actions) {
        if (!initialized || currentGameState == null) {
            return false;
        }

        if (playerId == null || !activePlayers.contains(playerId)) {
            return false;
        }

        List<InvalidAction> invalidActions = actionValidator.validate(currentGameState, playerId, actions);
        if (!invalidActions.isEmpty()) {
            return false;
        }

        // Update game state
        previousGameState = currentGameState;
        currentGameState = GameStatusUpdater.update(currentGameState, playerId, actions, unitGenerator);

        // Spawn food after state update
        List<Unit> updatedUnits = new ArrayList<>(Arrays.asList(currentGameState.units()));
        unitGenerator.spawnFood(updatedUnits, gameParams);
        currentGameState = new GameState(updatedUnits.toArray(new Unit[0]), currentGameState.startAt());

        gameDeltaHistory.add(GameDeltaFactory.get(previousGameState, currentGameState));
        currentTurn++;

        return true;
    }

    /**
     * Checks if the game has ended.
     *
     * @return true if the game has ended
     */
    @Override
    public boolean isGameEnded() {
        if (!initialized || currentGameState == null) {
            return false;
        }
        return GameEndValidator.hasGameEnded(currentGameState);
    }
}
