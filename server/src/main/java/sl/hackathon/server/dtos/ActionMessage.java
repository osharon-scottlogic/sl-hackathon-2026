package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Message containing player actions for their units.
 */
@Getter
@ToString
public final class ActionMessage extends Message {
    private final String playerId;
    private final Action[] actions;

    @JsonCreator
    public ActionMessage(
            @JsonProperty("playerId") String playerId,
            @JsonProperty("actions") Action[] actions) {
        this.playerId = playerId;
        this.actions = actions;
    }
}
