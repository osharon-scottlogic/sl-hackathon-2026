package sl.hackathon.server.dtos;

/**
 * Immutable record representing an invalid action with details about why it's invalid.
 */
public record InvalidAction(
    Action action,
    String reason,
    GameState state
) {
}
