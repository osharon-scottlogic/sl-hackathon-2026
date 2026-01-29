package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Message broadcast to all clients indicating the game has started.
 * Includes the initial game state.
 */
@Getter
@ToString
public final class StartGameMessage extends Message {
    private final GameStart gameStart;

    @JsonCreator
    public StartGameMessage(@JsonProperty("gameStart") GameStart gameStart) {
        this.gameStart = gameStart;
    }
}
