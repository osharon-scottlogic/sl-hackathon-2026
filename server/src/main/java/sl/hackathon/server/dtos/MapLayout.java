package sl.hackathon.server.dtos;

/**
 * Immutable record representing the map layout with dimensions and wall positions.
 */
public record MapLayout(Dimension dimension, Position[] walls) {
}
