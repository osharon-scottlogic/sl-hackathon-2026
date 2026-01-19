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
     * @param actions the actions to apply
     * @return the updated game state
     */
    GameState update(GameState gameState, Action[] actions);

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
