package sl.hackathon.client.tutorial;

import sl.hackathon.client.dtos.GameDelta;
import sl.hackathon.client.dtos.GameState;
import sl.hackathon.client.dtos.Unit;

import java.util.*;

public final class TutorialGameDeltaFactory {
    private TutorialGameDeltaFactory() {
    }

    public static GameDelta get(GameState previous, GameState current) {
        Unit[] prevUnits = previous != null && previous.units() != null ? previous.units() : new Unit[0];
        Unit[] currUnits = current != null && current.units() != null ? current.units() : new Unit[0];

        Map<Integer, Unit> prevById = new HashMap<>();
        for (Unit unit : prevUnits) {
            if (unit != null) {
                prevById.put(unit.id(), unit);
            }
        }

        Map<Integer, Unit> currById = new HashMap<>();
        for (Unit unit : currUnits) {
            if (unit != null) {
                currById.put(unit.id(), unit);
            }
        }

        List<Unit> addedOrModified = new ArrayList<>();
        for (Unit curr : currById.values()) {
            Unit prev = prevById.get(curr.id());
            if (prev == null || !prev.equals(curr)) {
                addedOrModified.add(curr);
            }
        }

        List<Integer> removed = new ArrayList<>();
        for (Unit prev : prevById.values()) {
            if (!currById.containsKey(prev.id())) {
                removed.add(prev.id());
            }
        }

        return new GameDelta(
            addedOrModified.toArray(Unit[]::new),
            removed.stream().mapToInt(Integer::intValue).toArray(),
            System.currentTimeMillis()
        );
    }
}
