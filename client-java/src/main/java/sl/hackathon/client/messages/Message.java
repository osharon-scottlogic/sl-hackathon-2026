package sl.hackathon.client.messages;

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
    @JsonSubTypes.Type(value = InvalidOperationMessage.class, name = "INVALID_OPERATION")
})
public sealed class Message permits ActionMessage, StartGameMessage, NextTurnMessage, EndGameMessage, InvalidOperationMessage {
    /**
     * Protected constructor to prevent direct instantiation.
     */
    protected Message() {
    }
}
