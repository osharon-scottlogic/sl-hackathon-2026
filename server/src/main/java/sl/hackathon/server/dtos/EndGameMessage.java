package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Message broadcast to all clients when the game ends.
 * Includes the final game status and winner information.
 */
@Getter
@ToString
public final class EndGameMessage extends Message {
    private final GameStatusUpdate gameStatusUpdate;

    @JsonCreator
    public EndGameMessage(@JsonProperty("gameStatusUpdate") GameStatusUpdate gameStatusUpdate) {
        this.gameStatusUpdate = gameStatusUpdate;
    }
}
