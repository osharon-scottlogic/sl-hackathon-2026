package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Message sent to a specific client indicating it's their turn to act.
 * Includes the current game state.
 */
@Getter
@ToString
public final class NextTurnMessage extends Message {
    private final String playerId;
    private final GameState gameState;

    @JsonCreator
    public NextTurnMessage(
            @JsonProperty("playerId") String playerId,
            @JsonProperty("gameState") GameState gameState) {
        this.playerId = playerId;
        this.gameState = gameState;
    }
}
