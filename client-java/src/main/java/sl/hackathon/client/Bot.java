package sl.hackathon.client;

import sl.hackathon.client.dtos.*;

/**
 * Bot interface for game-playing agents.
 * Implementations should generate strategic actions for pawns based on game state.
 */
public interface Bot {
    /**
     * Generate actions for this turn based on current game state.
     *
     * @param mapLayout     The game map layout
     * @param gameState     The current game state
     * @param timeLimitMs   Maximum time allowed for decision-making (milliseconds)
     * @return Array of actions (one per pawn) or empty array if no actions available
     */
    Action[] handleState(MapLayout mapLayout, GameState gameState, long timeLimitMs);

    String getPlayerId();
}
