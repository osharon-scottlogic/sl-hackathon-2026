package sl.hackathon.client.dtos;

/**
 * Immutable record representing a player action on a unit.
 */
public record Action(
    int unitId,
    Direction direction
) {
}
