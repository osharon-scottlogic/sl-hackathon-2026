package sl.hackathon.client.tutorial;

import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.dtos.GameState;
import sl.hackathon.client.dtos.Unit;

import java.util.ArrayList;
import java.util.List;

public final class TutorialActionValidator {
    private TutorialActionValidator() {
    }

    public static List<String> validate(GameState gameState, String playerId, Action[] actions) {
        List<String> reasons = new ArrayList<>();
        if (actions == null) {
            return reasons;
        }
        for (Action action : actions) {
            String reason = validateSingleAction(gameState, playerId, action);
            if (reason != null) {
                reasons.add(reason);
            }
        }
        return reasons;
    }

    private static String validateSingleAction(GameState gameState, String playerId, Action action) {
        if (action == null) {
            return "Action cannot be null";
        }
        Unit unit = findUnitById(gameState, action.unitId());
        if (unit == null) {
            return "Unit with ID '" + action.unitId() + "' does not exist";
        }
        if (unit.owner() == null || !unit.owner().equals(playerId)) {
            return "Unit '" + action.unitId() + "' does not belong to player '" + playerId + "'";
        }
        if (action.direction() == null) {
            return "Direction cannot be null";
        }
        return null;
    }

    private static Unit findUnitById(GameState gameState, int unitId) {
        if (gameState == null || gameState.units() == null) {
            return null;
        }
        for (Unit unit : gameState.units()) {
            if (unit != null && unit.id() == unitId) {
                return unit;
            }
        }
        return null;
    }
}
