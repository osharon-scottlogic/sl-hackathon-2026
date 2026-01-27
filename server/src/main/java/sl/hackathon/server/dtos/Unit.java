package sl.hackathon.server.dtos;

/**
 * Immutable record representing a unit on the game map.
 * Unit ID is a sequential integer assigned by UnitIdGenerator.
 */
public record Unit(
    int id,
    String owner,
    UnitType type,
    Position position
) {
}
