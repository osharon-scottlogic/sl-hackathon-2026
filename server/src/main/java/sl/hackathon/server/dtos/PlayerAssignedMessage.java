package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Message sent from server to client upon connection to assign a player ID.
 * This message informs the client of their assigned player identifier.
 */
@Getter
public final class PlayerAssignedMessage extends Message {
    private final String playerId;

    @JsonCreator
    public PlayerAssignedMessage(@JsonProperty("playerId") String playerId) {
        this.playerId = playerId;
    }
}
