package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.*;


import java.util.*;

import static sl.hackathon.server.dtos.UnitType.*;

/**
 * Implementation of GameStatusUpdater that handles state updates and collision resolution.
 */
public class GameStatusUpdater {
    /**
     * Updates the game state by applying a set of actions and resolving collisions.
     *
     * @param gameState the current game state
     * @param actions the actions to apply
     * @return the updated game state
     */
    public static GameState update(GameState gameState, String playerId, Action[] actions, UnitGenerator unitGenerator) {
        if (gameState == null || gameState.units() == null) {
            return gameState;
        }

        // Create a copy of units for modification
        List<Unit> units = new ArrayList<>(Arrays.asList(gameState.units()));

        // If no actions, return unchanged state
        if (actions == null || actions.length == 0) {
            return gameState;
        }

        // Build a map of action by unit ID for quick lookup
        Map<Integer, Action> actionMap = new HashMap<>();
        for (Action action : actions) {
            if (action != null) {
                actionMap.put(action.unitId(), action);
            }
        }

        // Apply movements: update positions based on actions
        Map<Integer, Unit> movedUnits = new HashMap<>();
        for (Unit unit : units) {
            if (actionMap.containsKey(unit.id())) {
                Action action = actionMap.get(unit.id());
                Position newPosition = moveUnit(unit.position(), action.direction());
                Unit movedUnit = new Unit(unit.id(), unit.owner(), unit.type(), newPosition);
                movedUnits.put(unit.id(), movedUnit);
            } else {
                movedUnits.put(unit.id(), unit);
            }
        }

        // Resolve collisions
        Set<Integer> unitsToRemove = new HashSet<>();
        int unitsToAdd = 0;
        Map<Position, List<Unit>> positionMap = buildPositionMap(movedUnits.values());

        // Check for collisions at each position
        for (Position pos : positionMap.keySet()) {
            List<Unit> unitsAtPosition = positionMap.get(pos);

            if (unitsAtPosition.size() > 1) {
                unitsToAdd += resolveCollision(unitsAtPosition, unitsToRemove);
            }
        }

        // Build final unit list
        List<Unit> finalUnitsList = movedUnits.values().stream()
                .filter(unit -> !unitsToRemove.contains(unit.id()))
                .toList();

        if (unitsToAdd > 0) {
            finalUnitsList = new ArrayList<>(finalUnitsList);
            finalUnitsList.addAll(unitGenerator.getNewlyAddedUnits(unitsToAdd,findBase(playerId, units)));
        }
        Unit[] finalUnits = finalUnitsList.toArray(Unit[]::new);

        return new GameState(finalUnits, gameState.startAt());
    }

    /**
     * Moves a unit one step in the given direction.
     *
     * @param position the current position
     * @param direction the direction to move
     * @return the new position
     */
    private static Position moveUnit(Position position, Direction direction) {
        int x = position.x();
        int y = position.y();

        switch (direction) {
            case N:
                y--;
                break;
            case NE:
                x++;
                y--;
                break;
            case E:
                x++;
                break;
            case SE:
                x++;
                y++;
                break;
            case S:
                y++;
                break;
            case SW:
                x--;
                y++;
                break;
            case W:
                x--;
                break;
            case NW:
                x--;
                y--;
                break;
        }

        return new Position(x, y);
    }

    /**
     * Builds a map of positions to units at those positions.
     *
     * @param units the units to map
     * @return a map of position to list of units at that position
     */
    private static Map<Position, List<Unit>> buildPositionMap(Collection<Unit> units) {
        Map<Position, List<Unit>> map = new HashMap<>();
        for (Unit unit : units) {
            map.computeIfAbsent(unit.position(), k -> new ArrayList<>()).add(unit);
        }
        return map;
    }

    /**
     * Resolves a collision between multiple units at the same position.
     * Rules:
     * - Enemy pawns on same tile → both die
     * - Friendly pawns on same tile → survive
     * - Pawn + food → food consumed, pawn survives, new pawn created at owner's base
     * - Pawn + enemy base → pawn dies, base destroyed, game ends
     *
     * @param unitsAtPosition the units at the collision position
     * @param unitsToRemove the set of unit IDs to remove (modified by this method)
     */
    private static int resolveCollision(List<Unit> unitsAtPosition, Set<Integer> unitsToRemove) {
        // Separate units by type and owner
        List<Unit> pawns = unitsAtPosition.stream().filter(unit->unit.type().equals(PAWN)).toList();
        List<Unit> food = unitsAtPosition.stream().filter(unit->unit.type().equals(FOOD)).toList();
        List<Unit> bases = unitsAtPosition.stream().filter(unit->unit.type().equals(BASE)).toList();

        // Rule: Pawn + enemy base → pawn dies, base destroyed
        if (!pawns.isEmpty() && !bases.isEmpty()) {
            for (Unit base : bases) {
                for (Unit pawn : pawns) {
                    // Only remove if pawn and base belong to different owners
                    if (!pawn.owner().equals(base.owner())) {
                        unitsToRemove.add(base.id());
                        unitsToRemove.add(pawn.id());
                    }
                }
            }
            return 0;
        }

        // Rule: Enemy pawns on same tile → both die; Friendly pawns on same tile → survive
        if (pawns.size() > 1) {
            // Check if all pawns belong to the same owner
            Set<String> owners = new HashSet<>();
            for (Unit pawn : pawns) {
                owners.add(pawn.owner());
            }

            if (owners.size() > 1) {
                // Enemy pawns: remove all
                for (Unit pawn : pawns) {
                    unitsToRemove.add(pawn.id());
                }
            }
        }

        // Rule: Pawn + food → food consumed, pawn survives, new pawn created at owner's base
        if (!pawns.isEmpty() && !food.isEmpty()) {
            // Remove food units
            for (Unit f : food) {
                unitsToRemove.add(f.id());
            }
            return 1;
        }

        return 0;
    }

    /**
     * Finds the base position for a given player.
     *
     * @param owner the player ID
     * @param allUnits all units in the game
     * @return the player's base, or null if not found
     */
    private static Unit findBase(String owner, Collection<Unit> allUnits) {
        for (Unit unit : allUnits) {
            if (unit.type().equals(BASE) && unit.owner().equals(owner)) {
                return unit;
            }
        }
        return null;
    }
}
