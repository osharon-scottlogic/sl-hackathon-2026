package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of NextTurnHandler that logs turn notifications.
 */
public class NextTurnHandlerImpl implements NextTurnHandler {
    private static final Logger logger = LoggerFactory.getLogger(NextTurnHandlerImpl.class);

    /**
     * Called when it's a player's turn.
     *
     * @param playerId the player ID whose turn it is
     * @param gameState the current game state
     */
    @Override
    public void handleNextTurn(String playerId, GameState gameState) {
        if (gameState != null) {
            logger.debug("Next turn for player: {} with {} units on the board",
                playerId, gameState.units() != null ? gameState.units().length : 0);
        } else {
            logger.debug("Next turn for player: {}", playerId);
        }
    }
}
