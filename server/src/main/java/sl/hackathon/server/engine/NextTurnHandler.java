package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.*;

/**
 * Handler interface for when it's a player's turn.
 */
public interface NextTurnHandler {
    /**
     * Called when it's a player's turn.
     *
     * @param playerId the player ID whose turn it is
     * @param gameState the current game state
     */
    void handleNextTurn(String playerId, GameState gameState);
}
