package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.GameDelta;
import sl.hackathon.server.dtos.GameState;
import sl.hackathon.server.dtos.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameDeltaFactory {
    /**
     * Generates a delta representing changes from previous state to new state.
     *
     * @param previousState the previous game state
     * @param newState the new game state
     * @return the delta representing changes
     */
    public static GameDelta get(GameState previousState, GameState newState) {
        if (previousState == null) {
            return new GameDelta(newState.units(), new int[0], System.currentTimeMillis());
        }

        // Build maps for quick lookup
        Map<Integer, Unit> previousUnits = getMapById(previousState.units());
        Map<Integer, Unit> newUnits = getMapById(newState.units());

        // Find added or modified units
        List<Unit> addedOrModified = new ArrayList<>();
        for (Unit newUnit : newState.units()) {
            Unit previousUnit = previousUnits.get(newUnit.id());
            if (previousUnit == null || !previousUnit.equals(newUnit)) {
                // Unit was added or Unit was modified (position or other properties changed)
                addedOrModified.add(newUnit);
            }
        }

        // Find removed units
        List<Integer> removed = new ArrayList<>();
        for (Unit previousUnit : previousState.units()) {
            if (!newUnits.containsKey(previousUnit.id())) {
                removed.add(previousUnit.id());
            }
        }

        return new GameDelta(
                addedOrModified.toArray(new Unit[0]),
                removed.stream().mapToInt(Integer::intValue).toArray(),
                System.currentTimeMillis()
        );
    }

    private static Map<Integer, Unit> getMapById(Unit[] units) {
        Map<Integer, Unit> map = new HashMap<>();
        for (Unit unit : units) {
            map.put(unit.id(), unit);
        }
        return map;
    }

}
