package sl.hackathon.server.dtos;

/**
 * Immutable record representing the map configuration including layout and base locations.
 */
public record MapConfig(Dimension dimension, Position[] walls, Position[] potentialBaseLocations) {
}
