package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import sl.hackathon.client.dtos.GameStart;

/**
 * Message broadcast to all clients indicating the game has started.
 * Includes the initial game state.
 */
@Getter
public final class StartGameMessage extends Message {
    private final GameStart gameStart;

    @JsonCreator
    public StartGameMessage(@JsonProperty("gameStart") GameStart gameStart) {
        this.gameStart = gameStart;
    }
}
