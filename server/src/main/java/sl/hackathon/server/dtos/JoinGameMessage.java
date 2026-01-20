package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Message sent by a client to join a game.
 */
@Getter
@ToString
public final class JoinGameMessage extends Message {
    private final String playerId;

    @JsonCreator
    public JoinGameMessage(@JsonProperty("playerId") String playerId) {
        this.playerId = playerId;
    }
}
