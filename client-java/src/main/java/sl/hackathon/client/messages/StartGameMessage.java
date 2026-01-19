package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import sl.hackathon.client.dtos.GameState;

/**
 * Message broadcast to all clients indicating the game has started.
 * Includes the initial game state.
 */
public final class StartGameMessage extends Message {
    private final GameState gameState;

    @JsonCreator
    public StartGameMessage(@JsonProperty("gameState") GameState gameState) {
        this.gameState = gameState;
    }

    public GameState getGameState() {
        return gameState;
    }

    @Override
    public String toString() {
        return "StartGameMessage{" +
                "gameState=" + gameState +
                '}';
    }
}
