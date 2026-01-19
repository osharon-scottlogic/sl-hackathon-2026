package sl.hackathon.server.dtos;

/**
 * Immutable record representing the current game state.
 */
public record GameState(
    Unit[] units,
    long startAt
) {
}
