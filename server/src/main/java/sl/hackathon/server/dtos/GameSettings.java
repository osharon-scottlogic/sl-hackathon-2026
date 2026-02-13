package sl.hackathon.server.dtos;

/**
 * Immutable record representing game parameters and configuration.
 */
public record GameSettings(
    Dimension dimension,
    Position[] walls,
    Position[] potentialBaseLocations,
    int turnTimeLimit,
    float foodScarcity,
    boolean fogOfWar
) {
}
