package sl.hackathon.server.dtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * MessageCodec provides serialization and deserialization of Message objects
 * using Jackson's polymorphic type handling.
 * 
 * This codec handles:
 * - Polymorphic deserialization (converting JSON to correct Message subtype)
 * - Polymorphic serialization (converting Message subtypes to JSON)
 * - Null handling
 * - Round-trip serialization/deserialization
 */
public class MessageCodec {
    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * Serializes a Message object to JSON string.
     * 
     * @param message the message to serialize (can be any Message subtype)
     * @return JSON string representation of the message
     * @throws IllegalArgumentException if message is null
     * @throws RuntimeException if serialization fails
     */
    public static String serialize(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message: " + message, e);
        }
    }

    /**
     * Deserializes a JSON string to a Message object.
     * Automatically handles polymorphic type detection based on the "type" property.
     * 
     * @param json the JSON string to deserialize
     * @return Message object (correct subtype based on JSON content)
     * @throws IllegalArgumentException if json is null or empty
     * @throws RuntimeException if deserialization fails or JSON is malformed
     */
    public static Message deserialize(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message: " + json, e);
        }
    }

    /**
     * Performs a round-trip serialization/deserialization of a message.
     * Useful for testing and validation.
     * 
     * @param message the message to round-trip
     * @return a new Message object deserialized from the serialized form
     * @throws IllegalArgumentException if message is null
     * @throws RuntimeException if serialization or deserialization fails
     */
    public static Message roundTrip(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        String json = serialize(message);
        return deserialize(json);
    }
}
