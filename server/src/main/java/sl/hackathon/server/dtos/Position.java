package sl.hackathon.server.dtos;

import lombok.ToString;

/**
 * Immutable record representing a 2D position on the game map.
 */
public record Position(int x, int y) {
}
