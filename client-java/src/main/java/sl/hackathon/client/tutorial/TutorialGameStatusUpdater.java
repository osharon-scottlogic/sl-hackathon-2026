package sl.hackathon.client.tutorial;

import sl.hackathon.client.dtos.*;

import java.util.*;

import static sl.hackathon.client.dtos.UnitType.*;

/**
 * Client-side mirror of the server's movement/collision rules for tutorial mode.
 */
public final class TutorialGameStatusUpdater {
    private TutorialGameStatusUpdater() {
    }

    public static GameState update(GameState gameState, String playerId, Action[] actions, TutorialUnitGenerator unitGenerator) {
        if (gameState == null || gameState.units() == null) {
            return gameState;
        }
        if (actions == null || actions.length == 0) {
            return gameState;
        }

        List<Unit> units = new ArrayList<>(Arrays.asList(gameState.units()));

        Map<Integer, Action> actionMap = new HashMap<>();
        for (Action action : actions) {
            if (action != null) {
                actionMap.put(action.unitId(), action);
            }
        }

        Map<Integer, Unit> movedUnits = new HashMap<>();
        for (Unit unit : units) {
            if (actionMap.containsKey(unit.id())) {
                Action action = actionMap.get(unit.id());
                Position newPosition = moveUnit(unit.position(), action.direction());
                movedUnits.put(unit.id(), new Unit(unit.id(), unit.owner(), unit.type(), newPosition));
            } else {
                movedUnits.put(unit.id(), unit);
            }
        }

        Set<Integer> unitsToRemove = new HashSet<>();
        int unitsToAdd = 0;
        Map<Position, List<Unit>> positionMap = buildPositionMap(movedUnits.values());

        for (Position pos : positionMap.keySet()) {
            List<Unit> unitsAtPosition = positionMap.get(pos);
            if (unitsAtPosition.size() > 1) {
                unitsToAdd += resolveCollision(unitsAtPosition, unitsToRemove);
            }
        }

        List<Unit> finalUnitsList = movedUnits.values().stream()
            .filter(unit -> !unitsToRemove.contains(unit.id()))
            .toList();

        if (unitsToAdd > 0) {
            finalUnitsList = new ArrayList<>(finalUnitsList);
            finalUnitsList.addAll(unitGenerator.getNewlyAddedUnits(unitsToAdd, findBase(playerId, units)));
        }

        return new GameState(finalUnitsList.toArray(Unit[]::new), gameState.startAt());
    }

    private static Position moveUnit(Position position, Direction direction) {
        int x = position.x();
        int y = position.y();

        switch (direction) {
            case N -> y--;
            case NE -> {
                x++;
                y--;
            }
            case E -> x++;
            case SE -> {
                x++;
                y++;
            }
            case S -> y++;
            case SW -> {
                x--;
                y++;
            }
            case W -> x--;
            case NW -> {
                x--;
                y--;
            }
        }

        return new Position(x, y);
    }

    private static Map<Position, List<Unit>> buildPositionMap(Collection<Unit> units) {
        Map<Position, List<Unit>> map = new HashMap<>();
        for (Unit unit : units) {
            map.computeIfAbsent(unit.position(), k -> new ArrayList<>()).add(unit);
        }
        return map;
    }

    private static int resolveCollision(List<Unit> unitsAtPosition, Set<Integer> unitsToRemove) {
        List<Unit> pawns = unitsAtPosition.stream().filter(unit -> unit.type().equals(PAWN)).toList();
        List<Unit> food = unitsAtPosition.stream().filter(unit -> unit.type().equals(FOOD)).toList();
        List<Unit> bases = unitsAtPosition.stream().filter(unit -> unit.type().equals(BASE)).toList();

        if (!pawns.isEmpty() && !bases.isEmpty()) {
            for (Unit base : bases) {
                for (Unit pawn : pawns) {
                    if (pawn.owner() != null && base.owner() != null && !pawn.owner().equals(base.owner())) {
                        unitsToRemove.add(base.id());
                        unitsToRemove.add(pawn.id());
                    }
                }
            }
            return 0;
        }

        if (pawns.size() > 1) {
            Set<String> owners = new HashSet<>();
            for (Unit pawn : pawns) {
                owners.add(pawn.owner());
            }
            if (owners.size() > 1) {
                for (Unit pawn : pawns) {
                    unitsToRemove.add(pawn.id());
                }
            }
        }

        if (!pawns.isEmpty() && !food.isEmpty()) {
            for (Unit f : food) {
                unitsToRemove.add(f.id());
            }
            return 1;
        }

        return 0;
    }

    private static Unit findBase(String owner, Collection<Unit> allUnits) {
        if (owner == null) {
            return null;
        }
        for (Unit unit : allUnits) {
            if (unit.type().equals(BASE) && owner.equals(unit.owner())) {
                return unit;
            }
        }
        return null;
    }
}
