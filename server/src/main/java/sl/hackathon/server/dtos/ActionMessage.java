package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message containing player actions for their units.
 */
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

    public String getPlayerId() {
        return playerId;
    }

    public Action[] getActions() {
        return actions;
    }

    @Override
    public String toString() {
        return "ActionMessage{" +
                "playerId='" + playerId + '\'' +
                ", actions=" + java.util.Arrays.toString(actions) +
                '}';
    }
}
