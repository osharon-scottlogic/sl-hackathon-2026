package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message broadcast to all clients indicating the game has started.
 * Includes the initial game state.
 */
public final class StartGameMessage extends Message {
    private final GameStatusUpdate gameStatusUpdate;

    @JsonCreator
    public StartGameMessage(@JsonProperty("gameStatusUpdate") GameStatusUpdate gameStatusUpdate) {
        this.gameStatusUpdate = gameStatusUpdate;
    }

    public GameStatusUpdate getGameStatusUpdate() {
        return gameStatusUpdate;
    }

    @Override
    public String toString() {
        return "StartGameMessage{" +
                "gameStatusUpdate=" + gameStatusUpdate +
                '}';
    }
}
