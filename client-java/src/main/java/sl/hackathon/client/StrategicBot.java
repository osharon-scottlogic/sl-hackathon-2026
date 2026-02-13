package sl.hackathon.client;

import lombok.Getter;
import sl.hackathon.client.dtos.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategic bot implementation that makes game-aware decisions.
 * Uses pathfinding, threat assessment, and collision prediction to generate safe, strategic actions.
 */
public class StrategicBot implements Bot {
    private static final long DECISION_BUFFER_MS = 1000; // 1 second buffer for safety

    @Getter
    private final String playerId;

    public StrategicBot() {
        this.playerId = generatePlayerId();
    }

    @Override
    public Action[] handleState(MapLayout mapLayout, GameState gameState, long timeLimitMs) {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + timeLimitMs - DECISION_BUFFER_MS;

        try {
            // Extract game entities
            List<Unit> friendlyPawns = Arrays.stream(gameState.units())
                    .filter(u -> u.type() == UnitType.PAWN && u.owner().equals(playerId))
                    .collect(Collectors.toList());

            List<Unit> enemyPawns = Arrays.stream(gameState.units())
                    .filter(u -> u.type() == UnitType.PAWN && !u.owner().equals(playerId))
                    .collect(Collectors.toList());

            List<Position> foodLocations = HelperTools.findAllFoodLocations(gameState);

            // Generate candidate moves for each friendly pawn
            List<Action> candidateActions = new ArrayList<>();
            for (Unit pawn : friendlyPawns) {
                if (System.currentTimeMillis() > deadline) {
                    // Timeout approaching - return fallback
                    return generateFallbackActions(friendlyPawns, gameState, mapLayout, playerId);
                }

                // Generate candidate strategies for this pawn
                List<Action> pawnCandidates = generateCandidatesForPawn(
                        pawn, gameState, mapLayout, playerId, foodLocations, enemyPawns, deadline);
                candidateActions.addAll(pawnCandidates);
            }

            // Filter out deadly actions using collision prediction
            List<Action> safeActions = HelperTools.filterOutDeadlyActions(
                    gameState, candidateActions, mapLayout, playerId);

            if (safeActions.isEmpty()) {
                // No safe actions found - return fallback
                return generateFallbackActions(friendlyPawns, gameState, mapLayout, playerId);
            }

            // Select best action for each pawn (prioritize by strategy)
            return selectBestActionsPerPawn(friendlyPawns, safeActions);

        } catch (Exception e) {
            // On any error, return safe fallback
            return generateFallbackActions(
                    Arrays.stream(gameState.units())
                            .filter(u -> u.type() == UnitType.PAWN && u.owner().equals(playerId))
                            .collect(Collectors.toList()),
                    gameState, mapLayout, playerId);
        }
    }

    /**
     * Generate 2-3 candidate moves for a single pawn based on strategic priorities.
     */
    private List<Action> generateCandidatesForPawn(
            Unit pawn,
            GameState gameState,
            MapLayout mapLayout,
            String playerId,
            List<Position> foodLocations,
            List<Unit> enemyPawns,
            long deadline) {

        List<Action> candidates = new ArrayList<>();

        // Strategy 1: Move toward food (highest priority)
        if (!foodLocations.isEmpty()) {
            Action foodAction = selectBestMoveForFood(pawn, gameState, mapLayout, foodLocations);
            if (foodAction != null) {
                candidates.add(foodAction);
            }
        }

        // Strategy 2: Evade if adjacent to enemy
        if (!enemyPawns.isEmpty() && HelperTools.isPositionAdjacentToEnemy(gameState, pawn.position(), playerId)) {
            Action evadeAction = selectEvadeMove(pawn, gameState, mapLayout, enemyPawns);
            if (evadeAction != null && !candidates.contains(evadeAction)) {
                candidates.add(evadeAction);
            }
        }

        // Strategy 3: Attack if enemy nearby (but not immediately adjacent)
        if (!enemyPawns.isEmpty()) {
            Optional<Unit> closeEnemy = HelperTools.findClosestEnemy(gameState, pawn.position(), playerId);
            if (closeEnemy.isPresent() && HelperTools.distanceTo(pawn.position(), closeEnemy.get().position()) <= 3) {
                Action attackAction = selectAggressiveMove(pawn, gameState, mapLayout, closeEnemy.get());
                if (attackAction != null && !candidates.contains(attackAction)) {
                    candidates.add(attackAction);
                }
            }
        }

        // Strategy 4: Fallback to grouping with friendly units for mutual support
        if (candidates.isEmpty()) {
            List<Unit> friendlyUnits = Arrays.stream(gameState.units())
                    .filter(u -> u.type() == UnitType.PAWN && u.owner().equals(playerId) && !u.equals(pawn))
                    .collect(Collectors.toList());

            if (!friendlyUnits.isEmpty()) {
                Action groupAction = selectGroupingMove(pawn, gameState, mapLayout, friendlyUnits);
                if (groupAction != null) {
                    candidates.add(groupAction);
                }
            }
        }

        // If no candidates generated, add safe random move as fallback
        if (candidates.isEmpty()) {
            Action randomAction = HelperTools.generateRandomAction(gameState, pawn, mapLayout);
            candidates.add(randomAction);
        }

        return candidates;
    }

    /**
     * Select best move toward food for a pawn.
     */
    private Action selectBestMoveForFood(
            Unit pawn,
            GameState gameState,
            MapLayout mapLayout,
            List<Position> foodLocations) {

        // Find closest food
        Optional<Position> closestFood = foodLocations.stream()
                .min(Comparator.comparingInt(food -> HelperTools.distanceTo(pawn.position(), food)));

        if (closestFood.isEmpty()) {
            return null;
        }

        Position goal = closestFood.get();

        // Try to find path to food
        Optional<List<Direction>> path = HelperTools.findShortestPath(mapLayout, pawn.position(), goal);

        if (path.isPresent() && !path.get().isEmpty()) {
            Direction nextMove = path.get().getFirst();
            return new Action(pawn.id(), nextMove);
        }

        return null;
    }

    /**
     * Select move to evade nearby enemies.
     */
    private Action selectEvadeMove(
            Unit pawn,
            GameState gameState,
            MapLayout mapLayout,
            List<Unit> enemyPawns) {

        // Find furthest direction away from closest enemy
        Optional<Unit> closestEnemy = enemyPawns.stream()
                .min(Comparator.comparingInt(enemy -> HelperTools.distanceTo(pawn.position(), enemy.position())));

        if (closestEnemy.isEmpty()) {
            return null;
        }

        Unit threat = closestEnemy.get();
        Set<Position> dangerZones = new HashSet<>();
        dangerZones.add(threat.position());

        // Find a safe position away from threat
        for (int radius = 2; radius <= 5; radius++) {
            Set<Position> reachable = HelperTools.getReachablePositions(mapLayout, pawn.position(), radius);

            Optional<Position> safestPos = reachable.stream()
                    .filter(pos -> !dangerZones.contains(pos))
                    .max(Comparator.comparingInt(pos -> HelperTools.distanceTo(pos, threat.position())));

            if (safestPos.isPresent()) {
                Optional<List<Direction>> path = HelperTools.findShortestPath(mapLayout, pawn.position(), safestPos.get());
                if (path.isPresent() && !path.get().isEmpty()) {
                    return new Action(pawn.id(), path.get().getFirst());
                }
            }
        }

        return null;
    }

    /**
     * Select aggressive move toward enemy base or closest enemy.
     */
    private Action selectAggressiveMove(
            Unit pawn,
            GameState gameState,
            MapLayout mapLayout,
            Unit enemy) {

        // Try to path toward enemy
        Optional<List<Direction>> path = HelperTools.findShortestPath(mapLayout, pawn.position(), enemy.position());

        if (path.isPresent() && !path.get().isEmpty()) {
            Direction nextMove = path.get().getFirst();
            return new Action(pawn.id(), nextMove);
        }

        return null;
    }

    /**
     * Select move to group with friendly units for mutual support.
     */
    private Action selectGroupingMove(
            Unit pawn,
            GameState gameState,
            MapLayout mapLayout,
            List<Unit> friendlyUnits) {

        // Calculate centroid of friendly units
        Position centroid = HelperTools.getCentroidOfUnits(friendlyUnits);

        // Path toward centroid
        Optional<List<Direction>> path = HelperTools.findShortestPath(mapLayout, pawn.position(), centroid);

        if (path.isPresent() && !path.get().isEmpty()) {
            Direction nextMove = path.get().getFirst();
            return new Action(pawn.id(), nextMove);
        }

        return null;
    }

    /**
     * Select the best action for each pawn from the safe candidates.
     * Ensures one action per pawn.
     */
    private Action[] selectBestActionsPerPawn(List<Unit> friendlyPawns, List<Action> safeActions) {
        Map<Integer, Action> actionsByPawn = new HashMap<>();

        // Group safe actions by pawn ID
        Map<Integer, List<Action>> groupedByPawn = safeActions.stream()
                .collect(Collectors.groupingBy(Action::unitId));

        // Select one action per pawn (first in the list, which has priority)
        for (Unit pawn : friendlyPawns) {
            List<Action> pawnActions = groupedByPawn.getOrDefault(pawn.id(), new ArrayList<>());
            if (!pawnActions.isEmpty()) {
                actionsByPawn.put(pawn.id(), pawnActions.getFirst());
            }
        }

        return actionsByPawn.values().toArray(new Action[0]);
    }

    /**
     * Generate fallback actions when normal decision-making fails or times out.
     * Falls back to random safe moves.
     */
    private Action[] generateFallbackActions(
            List<Unit> friendlyPawns,
            GameState gameState,
            MapLayout mapLayout,
            String playerId) {

        List<Action> fallbackActions = new ArrayList<>();

        for (Unit pawn : friendlyPawns) {
            try {
                // Generate a safe random action for this pawn
                Action randomAction = HelperTools.generateRandomAction(gameState, pawn, mapLayout);

                // Verify it's not deadly
                if (!HelperTools.wouldResultInDeath(gameState, randomAction, mapLayout, playerId)) {
                    fallbackActions.add(randomAction);
                }
            } catch (Exception e) {
                // If even the fallback fails, skip this pawn
            }
        }

        return fallbackActions.toArray(new Action[0]);
    }

    private static String generatePlayerId() {
        List<String> colors = List.of(
            "red", "blue", "green", "yellow", "purple",
            "orange", "black", "white", "silver", "gold",
            "crimson", "emerald"
        );

        List<String> adjectives = List.of(
            "fast", "brave", "cunning", "mighty", "swift",
            "wise", "bold", "fierce", "sneaky", "stubborn",
            "tiny", "fat"
        );

        List<String> mythicalCreatures = List.of(
            "unicorn", "dragon", "phoenix", "griffin", "kraken",
            "hydra", "minotaur", "pegasus", "basilisk", "chimera",
            "sphinx", "wyvern"
        );

        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        String color = colors.get(random.nextInt(colors.size()));
        String adjective = adjectives.get(random.nextInt(adjectives.size()));
        String creature = mythicalCreatures.get(random.nextInt(mythicalCreatures.size()));

        return color + "-" + adjective + "-" + creature;
    }
}
