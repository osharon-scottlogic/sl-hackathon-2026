package sl.hackathon.server.validators;

import sl.hackathon.server.dtos.GameState;
import sl.hackathon.server.dtos.Unit;
import sl.hackathon.server.dtos.UnitType;

import java.util.HashMap;
import java.util.Map;

import static sl.hackathon.server.dtos.UnitType.BASE;
import static sl.hackathon.server.dtos.UnitType.PAWN;

public class GameEndValidator {
    /**
     * Checks if the game has ended based on the current game state.
     *
     * @param gameState the current game state
     * @return true if the game has ended; false otherwise
     */
    public static boolean hasGameEnded(GameState gameState) {
        if (gameState == null || gameState.units() == null || gameState.units().length == 0) {
            return true;
        }

        // Game ends if a player has no BASE or doesn't have any PAWN units.
        Map<String, Map<UnitType, Integer>> playerUnitCounts = new HashMap<>();
        for (Unit unit : gameState.units()) {
            if (unit == null || unit.owner() == null || unit.owner().equals("none") || unit.type() == null) {
                continue;
            }
            if (!playerUnitCounts.containsKey(unit.owner())) {
                playerUnitCounts.put(unit.owner(), new HashMap<>());
                playerUnitCounts.get(unit.owner()).put(PAWN, 0);
                playerUnitCounts.get(unit.owner()).put(BASE, 0);
            }

            Map<UnitType, Integer> unitCounts = playerUnitCounts.get(unit.owner());
            unitCounts.put(unit.type(), unitCounts.getOrDefault(unit.type(), 0) + 1);
        }

        return playerUnitCounts.size() == 1 || playerUnitCounts.values().stream()     // Stream<Map<String, Integer>>
                .flatMap(inner -> inner.values().stream()) // Stream<Integer>
                .anyMatch(i -> i == 0);
    }

    /**
     * Gets the winner of the game based on the current game state.
     *
     * @param gameState the current game state
     * @return the player ID of the winner, or null if no winner
     */
    public static String getWinnerId(GameState gameState) {
        if (gameState == null || gameState.units() == null || gameState.units().length == 0) {
            return null;
        }

        if (!hasGameEnded(gameState)) {
            return null;
        }

        // Game ends if a player has no BASE or doesn't have any PAWN units.
        Map<String, Map<UnitType, Integer>> playerUnitCounts = new HashMap<>();
        for (Unit unit : gameState.units()) {
            if (unit == null || unit.owner() == null || unit.owner().equals("none") || unit.type() == null) {
                continue;
            }

            playerUnitCounts.putIfAbsent(unit.owner(), new HashMap<>());
            Map<UnitType, Integer> unitCounts = playerUnitCounts.get(unit.owner());
            unitCounts.put(unit.type(), unitCounts.getOrDefault(unit.type(), 0) + 1);
        }

        for (String playerId : playerUnitCounts.keySet()) {
            Map<UnitType, Integer> unitCounts = playerUnitCounts.get(playerId);
            int baseCount = unitCounts.getOrDefault(BASE, 0);
            int pawnCount = unitCounts.getOrDefault(PAWN, 0);
            if (baseCount > 0 && pawnCount > 0) {
                return playerId;
            }
        }

        return null;
    }
}
