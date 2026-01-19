package sl.hackathon.client.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message sent to a specific client when an invalid operation is attempted.
 * Includes details about the invalid action and reason.
 */
public final class InvalidOperationMessage extends Message {
    private final String playerId;
    private final String reason;

    @JsonCreator
    public InvalidOperationMessage(
            @JsonProperty("playerId") String playerId,
            @JsonProperty("reason") String reason) {
        this.playerId = playerId;
        this.reason = reason;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "InvalidOperationMessage{" +
                "playerId='" + playerId + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
