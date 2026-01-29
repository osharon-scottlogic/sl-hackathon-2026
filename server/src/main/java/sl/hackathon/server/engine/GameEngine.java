package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.*;

import java.util.List;

/**
 * Interface for the authoritative game engine that manages game state and progression.
 */
public interface GameEngine {
    /**
     * Adds a player to the game.
     *
     * @param playerId the player ID to add
     */
    void addPlayer(String playerId);

    /**
     * Removes a player from the game.
     *
     * @param playerId the player ID to remove
     */
    void removePlayer(String playerId);

    /**
     * Initializes the game with the given parameters.
     *
     * @param gameParams the game parameters including map and turn time limit
     * @return the initial game state
     */
    GameState initialize(GameParams gameParams);

    /**
     * Gets the current game state.
     *
     * @return a snapshot of the current game state
     */
    GameState getGameState();

    /**
     * Gets the game delta history.
     *
     * @return a list of all game deltas from initialization to current
     */
    List<GameDelta> getGameDeltaHistory();

    /**
     * Handles player actions and updates the game state.
     *
     * @param playerId the player ID performing the actions
     * @param actions the actions to process
     * @return true if actions were successfully processed; false if invalid
     */
    boolean handlePlayerActions(String playerId, Action[] actions);

    /**
     * Gets the list of active players.
     *
     * @return a list of player IDs currently in the game
     */
    List<String> getActivePlayers();

    /**
     * Gets the current turn number.
     *
     * @return the current turn number (0-based)
     */
    int getCurrentTurn();

    /**
     * Checks if the game is initialized.
     *
     * @return true if the game has been initialized
     */
    boolean isInitialized();

    /**
     * Checks if the game has ended.
     *
     * @return true if the game has ended
     */
    boolean isGameEnded();

    /**
     * Gets the winner of the game if it has ended.
     *
     * @return the player ID of the winner, or null if game hasn't ended or no winner
     */
    String getWinnerId();
}
