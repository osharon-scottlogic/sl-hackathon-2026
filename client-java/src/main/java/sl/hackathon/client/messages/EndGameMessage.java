package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import sl.hackathon.client.dtos.GameEnd;

/**
 * Message broadcast to all clients when the game ends.
 * Includes the final game status and winner information.
 */
@Getter
@ToString
public final class EndGameMessage extends Message {
    private final GameEnd gameEnd;

    @JsonCreator
    public EndGameMessage(@JsonProperty("gameEnd") GameEnd gameEnd) {
        this.gameEnd = gameEnd;
    }
}
