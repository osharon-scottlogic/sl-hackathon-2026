package sl.hackathon.server.dtos;

/**
 * Immutable record representing a game status update containing current state and history.
 */
public record GameStatusUpdate(
    GameStatus status,
    MapLayout map,
    GameState[] history,
    String winnerId
) {
}
