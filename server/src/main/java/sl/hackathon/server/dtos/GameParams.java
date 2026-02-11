package sl.hackathon.server.dtos;

/**
 * Immutable record representing game parameters and configuration.
 */
public record GameParams(
    MapConfig mapConfig,
    int turnTimeLimit,
    float foodScarcity
) {
}
