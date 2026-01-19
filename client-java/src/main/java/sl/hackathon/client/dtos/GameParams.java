package sl.hackathon.client.dtos;

/**
 * Immutable record representing game parameters and configuration.
 */
public record GameParams(
    MapConfig mapConfig,
    long turnTimeLimit,
    float foodScarcity
) {
}
