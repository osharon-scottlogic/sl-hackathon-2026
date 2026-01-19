package sl.hackathon.client.dtos;

import java.util.Map;
import java.util.Set;

/**
 * Immutable record representing the predicted outcomes of applying actions in a turn.
 * Used to simulate collisions without modifying the actual game state.
 */
public record CollisionPrediction(
    Map<String, Boolean> pawnWillDie,      // unitId -> true if pawn will die
    Set<Position> foodWillBeConsumed,      // food positions that will be consumed
    boolean baseWillBeLost                 // true if a base will be destroyed
) {
}
