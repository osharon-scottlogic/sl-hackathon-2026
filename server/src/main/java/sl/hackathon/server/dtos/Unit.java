package sl.hackathon.server.dtos;

/**
 * Immutable record representing a unit on the game map.
 */
public record Unit(
    String id,
    String owner,
    UnitType type,
    Position position
) {
}
