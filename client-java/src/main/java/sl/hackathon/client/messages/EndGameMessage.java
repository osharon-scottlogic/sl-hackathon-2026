package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import sl.hackathon.client.dtos.GameStatusUpdate;

/**
 * Message broadcast to all clients when the game ends.
 * Includes the final game status and winner information.
 */
public final class EndGameMessage extends Message {
    private final GameStatusUpdate gameStatusUpdate;

    @JsonCreator
    public EndGameMessage(@JsonProperty("gameStatusUpdate") GameStatusUpdate gameStatusUpdate) {
        this.gameStatusUpdate = gameStatusUpdate;
    }

    public GameStatusUpdate getGameStatusUpdate() {
        return gameStatusUpdate;
    }

    @Override
    public String toString() {
        return "EndGameMessage{" +
                "gameStatusUpdate=" + gameStatusUpdate +
                '}';
    }
}
