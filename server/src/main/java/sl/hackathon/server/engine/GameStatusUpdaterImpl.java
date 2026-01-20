package sl.hackathon.server.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.orchestration.GameSession;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of GameStatusUpdater that handles state updates and collision resolution.
 */
public class GameStatusUpdaterImpl implements GameStatusUpdater {

    private static final Logger logger = LoggerFactory.getLogger(GameStatusUpdaterImpl.class);

    /**
     * Updates the game state by applying a set of actions and resolving collisions.
     *
     * @param gameState the current game state
     * @param actions the actions to apply
     * @return the updated game state
     */
    @Override
    public GameState update(GameState gameState, Action[] actions) {
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
        Map<String, Action> actionMap = new HashMap<>();
        for (Action action : actions) {
            if (action != null && action.unitId() != null) {
                actionMap.put(action.unitId(), action);
            }
        }

        // Apply movements: update positions based on actions
        Map<String, Unit> movedUnits = new HashMap<>();
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
        Set<String> unitsToRemove = new HashSet<>();
        Map<Position, List<Unit>> positionMap = buildPositionMap(movedUnits.values());

        // Check for collisions at each position
        for (Position pos : positionMap.keySet()) {
            List<Unit> unitsAtPosition = positionMap.get(pos);

            if (unitsAtPosition.size() > 1) {
                resolveCollision(unitsAtPosition, unitsToRemove);
            }
        }

        // Build final unit list
        List<Unit> finalUnits = movedUnits.values().stream()
            .filter(unit -> !unitsToRemove.contains(unit.id()))
            .toList();

        return new GameState(finalUnits.toArray(new Unit[0]), gameState.startAt());
    }

    /**
     * Checks if the game has ended based on the current game state.
     *
     * @param gameState the current game state
     * @return true if the game has ended; false otherwise
     */
    @Override
    public boolean hasGameEnded(GameState gameState) {
        if (gameState == null || gameState.units() == null || gameState.units().length == 0) {
            return true;
        }

        // Game ends if only one player has units
        Set<String> owners = new HashSet<>();
        for (Unit unit : gameState.units()) {
            if (!unit.owner().equals("none")) {
                owners.add(unit.owner());
            }
        }

        return owners.size() <= 1;
    }

    /**
     * Gets the winner of the game based on the current game state.
     *
     * @param gameState the current game state
     * @return the player ID of the winner, or null if no winner
     */
    @Override
    public String getWinnerId(GameState gameState) {
        if (gameState == null || gameState.units() == null || gameState.units().length == 0) {
            return null;
        }

        Set<String> owners = new HashSet<>();
        for (Unit unit : gameState.units()) {
            if (!unit.owner().equals("none")) {
                owners.add(unit.owner());
            }
        }

        if (owners.size() == 1) {
            return owners.iterator().next();
        }

        return null;
    }

    /**
     * Moves a unit one step in the given direction.
     *
     * @param position the current position
     * @param direction the direction to move
     * @return the new position
     */
    private Position moveUnit(Position position, Direction direction) {
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
    private Map<Position, List<Unit>> buildPositionMap(Collection<Unit> units) {
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
     * - Pawn + food → food consumed, pawn survives
     * - Pawn + enemy base → pawn dies, base destroyed, game ends
     *
     * @param unitsAtPosition the units at the collision position
     * @param unitsToRemove the set of unit IDs to remove (modified by this method)
     */
    private void resolveCollision(List<Unit> unitsAtPosition, Set<String> unitsToRemove) {
        // Separate units by type and owner
        List<Unit> pawns = new ArrayList<>();
        List<Unit> food = new ArrayList<>();
        List<Unit> bases = new ArrayList<>();

        for (Unit unit : unitsAtPosition) {
            switch (unit.type()) {
                case PAWN:
                    pawns.add(unit);
                    break;
                case FOOD:
                    food.add(unit);
                    break;
                case BASE:
                    bases.add(unit);
                    break;
            }
        }

        // Rule: Pawn + enemy base → pawn dies, base destroyed
        if (!pawns.isEmpty() && !bases.isEmpty()) {
            for (Unit base : bases) {
                unitsToRemove.add(base.id());
            }
            for (Unit pawn : pawns) {
                unitsToRemove.add(pawn.id());
            }
            return;
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

        // Rule: Pawn + food → food consumed, pawn survives
        if (!pawns.isEmpty() && !food.isEmpty()) {
            for (Unit f : food) {
                unitsToRemove.add(f.id());
            }
        }
    }
}
