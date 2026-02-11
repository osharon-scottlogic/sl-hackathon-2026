package sl.hackathon.server.dtos;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for all game messages using polymorphic serialization.
 * Uses Jackson's @JsonTypeInfo annotation to support polymorphic deserialization.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ActionMessage.class, name = "ACTION"),
    @JsonSubTypes.Type(value = StartGameMessage.class, name = "START_GAME"),
    @JsonSubTypes.Type(value = NextTurnMessage.class, name = "NEXT_TURN"),
    @JsonSubTypes.Type(value = EndGameMessage.class, name = "END_GAME"),
    @JsonSubTypes.Type(value = InvalidOperationMessage.class, name = "INVALID_OPERATION"),
    @JsonSubTypes.Type(value = PlayerAssignedMessage.class, name = "PLAYER_ASSIGNED")
})
public sealed class Message permits ActionMessage, StartGameMessage, NextTurnMessage, EndGameMessage, InvalidOperationMessage, PlayerAssignedMessage {
    /**
     * Protected constructor to prevent direct instantiation.
     */
    protected Message() {
    }
}
