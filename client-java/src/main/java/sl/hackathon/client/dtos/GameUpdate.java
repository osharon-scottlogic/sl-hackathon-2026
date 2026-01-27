package sl.hackathon.client.dtos;

/**
 * Immutable record representing a delta update to the game state.
 * Contains only units that were added or modified, and IDs of units that were removed.
 */
public record GameUpdate(
        Unit[] addedOrModified,
        int[] removed,
        long startAt
) {
}
