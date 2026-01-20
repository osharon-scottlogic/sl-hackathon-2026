package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import sl.hackathon.client.dtos.GameState;
import sl.hackathon.client.dtos.GameStatusUpdate;

/**
 * Message broadcast to all clients indicating the game has started.
 * Includes the initial game state.
 */
@Getter
public final class StartGameMessage extends Message {
    private final GameStatusUpdate gameStatusUpdate;

    @JsonCreator
    public StartGameMessage(@JsonProperty("gameState") GameStatusUpdate gameStatusUpdate) {
        this.gameStatusUpdate = gameStatusUpdate;
    }
}
