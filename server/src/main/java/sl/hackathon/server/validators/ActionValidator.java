package sl.hackathon.server.validators;

import sl.hackathon.server.dtos.Action;
import sl.hackathon.server.dtos.GameState;
import sl.hackathon.server.dtos.InvalidAction;

import java.util.List;

/**
 * Interface for validating player actions against the current game state.
 */
public interface ActionValidator {
    /**
     * Validates a list of actions for a given player.
     *
     * @param gameState the current game state
     * @param playerId the player ID performing the actions
     * @param actions the actions to validate
     * @return a list of invalid actions; empty list if all actions are valid
     */
    List<InvalidAction> validate(GameState gameState, String playerId, Action[] actions);
}
