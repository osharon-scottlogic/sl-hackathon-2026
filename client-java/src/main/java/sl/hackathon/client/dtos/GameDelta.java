package sl.hackathon.client.dtos;

/**
 * Immutable record representing changes in a single turn.
 * Contains only units that were added or modified, and IDs of units that were removed.
 */
public record GameDelta(
    Unit[] addedOrModified,
    int[] removed,
    long timestamp
) {
}
