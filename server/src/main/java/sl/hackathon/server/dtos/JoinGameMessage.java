package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message sent by a client to join a game.
 */
public final class JoinGameMessage extends Message {
    private final String playerId;

    @JsonCreator
    public JoinGameMessage(@JsonProperty("playerId") String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    @Override
    public String toString() {
        return "JoinGameMessage{" +
                "playerId='" + playerId + '\'' +
                '}';
    }
}
