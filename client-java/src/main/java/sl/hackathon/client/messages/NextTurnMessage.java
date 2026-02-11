package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import sl.hackathon.client.dtos.GameState;

/**
 * Message sent to a specific client indicating it's their turn to act.
 * Includes the current game state.
 */
@Getter
public final class NextTurnMessage extends Message {
    private final String playerId;
    private final GameState gameState;
    private final int timeLimitMs;

    @JsonCreator
    public NextTurnMessage(
            @JsonProperty("playerId") String playerId,
            @JsonProperty("gameState") GameState gameState,
            @JsonProperty("timeLimitMs") int timeLimitMs) {
        this.playerId = playerId;
        this.gameState = gameState;
        this.timeLimitMs = timeLimitMs;
    }
}
