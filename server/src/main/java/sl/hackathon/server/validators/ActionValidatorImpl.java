package sl.hackathon.server.validators;

import sl.hackathon.server.dtos.Action;
import sl.hackathon.server.dtos.GameState;
import sl.hackathon.server.dtos.InvalidAction;
import sl.hackathon.server.dtos.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ActionValidator that validates player actions against game state.
 */
public class ActionValidatorImpl implements ActionValidator {

    /**
     * Validates a list of actions for a given player.
     *
     * @param gameState the current game state
     * @param playerId the player ID performing the actions
     * @param actions the actions to validate
     * @return a list of invalid actions; empty list if all actions are valid
     */
    @Override
    public List<InvalidAction> validate(GameState gameState, String playerId, Action[] actions) {
        List<InvalidAction> invalidActions = new ArrayList<>();

        if (actions == null) {
            return invalidActions;
        }

        for (Action action : actions) {
            InvalidAction invalid = validateSingleAction(gameState, playerId, action);
            if (invalid != null) {
                invalidActions.add(invalid);
            }
        }

        return invalidActions;
    }

    /**
     * Validates a single action.
     *
     * @param gameState the current game state
     * @param playerId the player ID performing the action
     * @param action the action to validate
     * @return an InvalidAction if the action is invalid; null if valid
     */
    private InvalidAction validateSingleAction(GameState gameState, String playerId, Action action) {
        // Validate action is not null
        if (action == null) {
            return new InvalidAction(action, "Action cannot be null", gameState);
        }

        // Validate unitId is not null
        if (action.unitId() == null) {
            return new InvalidAction(action, "Unit ID cannot be null", gameState);
        }

        // Check if unit with given ID exists
        Unit unit = findUnitById(gameState, action.unitId());
        if (unit == null) {
            return new InvalidAction(action, "Unit with ID '" + action.unitId() + "' does not exist", gameState);
        }

        // Check if unit belongs to the player
        if (!unit.owner().equals(playerId)) {
            return new InvalidAction(action, "Unit '" + action.unitId() + "' does not belong to player '" + playerId + "'", gameState);
        }

        // Validate direction is not null (should always be valid since it's an enum)
        if (action.direction() == null) {
            return new InvalidAction(action, "Direction cannot be null", gameState);
        }

        return null; // Action is valid
    }

    /**
     * Finds a unit by its ID in the game state.
     *
     * @param gameState the current game state
     * @param unitId the unit ID to search for
     * @return the Unit if found; null otherwise
     */
    private Unit findUnitById(GameState gameState, String unitId) {
        if (gameState == null || gameState.units() == null) {
            return null;
        }

        for (Unit unit : gameState.units()) {
            if (unitId.equals(unit.id())) {
                return unit;
            }
        }
        return null;
    }
}
