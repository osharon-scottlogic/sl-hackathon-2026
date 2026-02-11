package sl.hackathon.client.dtos;

/**
 * Immutable record representing game end with complete history.
 * Contains the map, initial units, all deltas, winner, and timestamp.
 */
public record GameEnd(
    MapLayout map,
    GameDelta[] deltas,
    String winnerId,
    long timestamp
) {
}
