package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import sl.hackathon.client.dtos.GameStatusUpdate;

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
