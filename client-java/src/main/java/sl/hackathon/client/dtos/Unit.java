package sl.hackathon.client.dtos;

/**
 * Immutable record representing a unit on the game map.
 * Unit ID is a sequential integer assigned by the server.
 */
public record Unit(
    int id,
    String owner,
    UnitType type,
    Position position
) {
}
