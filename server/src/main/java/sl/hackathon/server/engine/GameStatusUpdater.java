package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.*;

/**
 * Interface for updating game state and detecting end conditions.
 */
public interface GameStatusUpdater {
    /**
     * Updates the game state by applying a set of actions and resolving collisions.
     *
     * @param gameState the current game state
     * @param playerId id of the current player
     * @param actions the actions to apply
     * @return the updated game state
     */
    GameState update(GameState gameState, String playerId, Action[] actions);

    /**
     * Generates a delta representing changes from previous state to new state.
     *
     * @param previousState the previous game state
     * @param newState the new game state
     * @return the delta representing changes
     */
    GameDelta generateDelta(GameState previousState, GameState newState);

    /**
     * Checks if the game has ended based on the current game state.
     *
     * @param gameState the current game state
     * @return true if the game has ended; false otherwise
     */
    boolean hasGameEnded(GameState gameState);

    /**
     * Gets the winner of the game based on the current game state.
     *
     * @param gameState the current game state
     * @return the player ID of the winner, or null if no winner
     */
    String getWinnerId(GameState gameState);
}
