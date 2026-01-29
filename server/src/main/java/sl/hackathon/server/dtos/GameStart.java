package sl.hackathon.server.dtos;

/**
 * Immutable record representing game start initialization.
 * Contains the initial game configuration and starting units.
 */
public record GameStart(
    MapLayout map,
    Unit[] initialUnits,
    long timestamp
) {
}
