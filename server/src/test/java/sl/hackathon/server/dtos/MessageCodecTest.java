package sl.hackathon.server.dtos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for MessageCodec class.
 * Tests polymorphic serialization/deserialization, null handling, and round-trip conversions.
 */
@DisplayName("MessageCodec Tests")
public class MessageCodecTest {

    // ==================== ActionMessage Tests ====================

    @Test
    @DisplayName("Should serialize ActionMessage to JSON")
    public void testSerializeActionMessage() {
        // Arrange
        Action[] actions = {
            new Action(1, Direction.N),
            new Action(2, Direction.SE)
        };
        ActionMessage message = new ActionMessage("player-1", actions);

        // Act
        String json = MessageCodec.serialize(message);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"ACTION\""));
        assertTrue(json.contains("\"playerId\":\"player-1\""));
        assertTrue(json.contains("\"unitId\":1"));
    }

    @Test
    @DisplayName("Should deserialize JSON to ActionMessage")
    public void testDeserializeActionMessage() {
        // Arrange
        String json = "{\"type\":\"ACTION\",\"playerId\":\"player-1\",\"actions\":[{\"unitId\":1,\"direction\":\"N\"},{\"unitId\":2,\"direction\":\"SE\"}]}";

        // Act
        Message message = MessageCodec.deserialize(json);

        // Assert
        assertInstanceOf(ActionMessage.class, message);
        ActionMessage actionMsg = (ActionMessage) message;
        assertEquals("player-1", actionMsg.getPlayerId());
        assertEquals(2, actionMsg.getActions().length);
        assertEquals(1, actionMsg.getActions()[0].unitId());
        assertEquals(Direction.N, actionMsg.getActions()[0].direction());
    }

    @Test
    @DisplayName("Should round-trip ActionMessage")
    public void testRoundTripActionMessage() {
        // Arrange
        Action[] actions = {new Action(1, Direction.NW)};
        ActionMessage originalMessage = new ActionMessage("player-2", actions);

        // Act
        Message roundTrippedMessage = MessageCodec.roundTrip(originalMessage);

        // Assert
        assertInstanceOf(ActionMessage.class, roundTrippedMessage);
        ActionMessage result = (ActionMessage) roundTrippedMessage;
        assertEquals("player-2", result.getPlayerId());
        assertEquals(1, result.getActions().length);
        assertEquals(1, result.getActions()[0].unitId());
    }

    // ==================== JoinGameMessage Tests ====================

    @Test
    @DisplayName("Should serialize JoinGameMessage to JSON")
    public void testSerializeJoinGameMessage() {
        // Arrange
        JoinGameMessage message = new JoinGameMessage("player-3");

        // Act
        String json = MessageCodec.serialize(message);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"JOIN_GAME\""));
        assertTrue(json.contains("\"playerId\":\"player-3\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to JoinGameMessage")
    public void testDeserializeJoinGameMessage() {
        // Arrange
        String json = "{\"type\":\"JOIN_GAME\",\"playerId\":\"player-3\"}";

        // Act
        Message message = MessageCodec.deserialize(json);

        // Assert
        assertInstanceOf(JoinGameMessage.class, message);
        JoinGameMessage joinMsg = (JoinGameMessage) message;
        assertEquals("player-3", joinMsg.getPlayerId());
    }

    @Test
    @DisplayName("Should round-trip JoinGameMessage")
    public void testRoundTripJoinGameMessage() {
        // Arrange
        JoinGameMessage originalMessage = new JoinGameMessage("player-test");

        // Act
        Message roundTrippedMessage = MessageCodec.roundTrip(originalMessage);

        // Assert
        assertInstanceOf(JoinGameMessage.class, roundTrippedMessage);
        JoinGameMessage result = (JoinGameMessage) roundTrippedMessage;
        assertEquals("player-test", result.getPlayerId());
    }

    // ==================== StartGameMessage Tests ====================

    @Test
    @DisplayName("Should serialize StartGameMessage to JSON")
    public void testSerializeStartGameMessage() {
        // Arrange
        Unit[] units = {
            new Unit(1, "player-1", UnitType.PAWN, new Position(0, 0))
        };
        GameState gameState = new GameState(units, System.currentTimeMillis());
        Dimension dimension = new Dimension(10, 10);
        MapLayout mapLayout = new MapLayout(dimension, new Position[0]);
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            mapLayout,
            new GameState[]{gameState},
            null
        );
        StartGameMessage message = new StartGameMessage(statusUpdate);

        // Act
        String json = MessageCodec.serialize(message);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"START_GAME\""));
        assertTrue(json.contains("\"gameStatusUpdate\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to StartGameMessage")
    public void testDeserializeStartGameMessage() {
        // Arrange
        String json = "{\"type\":\"START_GAME\",\"gameStatusUpdate\":{\"status\":\"START\",\"map\":{\"dimension\":{\"width\":10,\"height\":10},\"walls\":[]},\"history\":[{\"units\":[{\"id\":1,\"owner\":\"player-1\",\"type\":\"PAWN\",\"position\":{\"x\":0,\"y\":0}}],\"startAt\":1000}],\"winnerId\":null}}";

        // Act
        Message message = MessageCodec.deserialize(json);

        // Assert
        assertInstanceOf(StartGameMessage.class, message);
        StartGameMessage startMsg = (StartGameMessage) message;
        assertNotNull(startMsg.getGameStatusUpdate());
        assertEquals(1, startMsg.getGameStatusUpdate().history()[0].units().length);
        assertEquals(GameStatus.START, startMsg.getGameStatusUpdate().status());
    }

    @Test
    @DisplayName("Should round-trip StartGameMessage")
    public void testRoundTripStartGameMessage() {
        // Arrange
        Unit[] units = {
            new Unit(2, "player-2", UnitType.BASE, new Position(5, 5))
        };
        GameState gameState = new GameState(units, 2000L);
        Dimension dimension = new Dimension(10, 10);
        MapLayout mapLayout = new MapLayout(dimension, new Position[0]);
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            mapLayout,
            new GameState[]{gameState},
            null
        );
        StartGameMessage originalMessage = new StartGameMessage(statusUpdate);

        // Act
        Message roundTrippedMessage = MessageCodec.roundTrip(originalMessage);

        // Assert
        assertInstanceOf(StartGameMessage.class, roundTrippedMessage);
        StartGameMessage result = (StartGameMessage) roundTrippedMessage;
        assertEquals(1, result.getGameStatusUpdate().history()[0].units().length);
        assertEquals(2, result.getGameStatusUpdate().history()[0].units()[0].id());
    }

    // ==================== NextTurnMessage Tests ====================

    @Test
    @DisplayName("Should serialize NextTurnMessage to JSON")
    public void testSerializeNextTurnMessage() {
        // Arrange
        Unit[] units = {};
        GameState gameState = new GameState(units, System.currentTimeMillis());
        NextTurnMessage message = new NextTurnMessage("player-1", gameState);

        // Act
        String json = MessageCodec.serialize(message);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"NEXT_TURN\""));
        assertTrue(json.contains("\"playerId\":\"player-1\""));
        assertTrue(json.contains("\"gameState\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to NextTurnMessage")
    public void testDeserializeNextTurnMessage() {
        // Arrange
        String json = "{\"type\":\"NEXT_TURN\",\"playerId\":\"player-1\",\"gameState\":{\"units\":[],\"startAt\":1000}}";

        // Act
        Message message = MessageCodec.deserialize(json);

        // Assert
        assertInstanceOf(NextTurnMessage.class, message);
        NextTurnMessage nextMsg = (NextTurnMessage) message;
        assertEquals("player-1", nextMsg.getPlayerId());
        assertNotNull(nextMsg.getGameState());
    }

    @Test
    @DisplayName("Should round-trip NextTurnMessage")
    public void testRoundTripNextTurnMessage() {
        // Arrange
        Unit[] units = {
            new Unit(1, "player-1", UnitType.FOOD, new Position(2, 3))
        };
        GameState gameState = new GameState(units, 5000L);
        NextTurnMessage originalMessage = new NextTurnMessage("player-5", gameState);

        // Act
        Message roundTrippedMessage = MessageCodec.roundTrip(originalMessage);

        // Assert
        assertInstanceOf(NextTurnMessage.class, roundTrippedMessage);
        NextTurnMessage result = (NextTurnMessage) roundTrippedMessage;
        assertEquals("player-5", result.getPlayerId());
        assertEquals(1, result.getGameState().units().length);
    }

    // ==================== EndGameMessage Tests ====================

    @Test
    @DisplayName("Should serialize EndGameMessage to JSON")
    public void testSerializeEndGameMessage() {
        // Arrange
        MapLayout mapLayout = new MapLayout(new Dimension(10, 10), new Position[]{});
        GameStatusUpdate gameStatusUpdate = new GameStatusUpdate(
            GameStatus.END,
            mapLayout,
            new GameState[]{},
            "player-1"
        );
        EndGameMessage message = new EndGameMessage(gameStatusUpdate);

        // Act
        String json = MessageCodec.serialize(message);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"END_GAME\""));
        assertTrue(json.contains("\"gameStatusUpdate\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to EndGameMessage")
    public void testDeserializeEndGameMessage() {
        // Arrange
        String json = "{\"type\":\"END_GAME\",\"gameStatusUpdate\":{\"status\":\"END\",\"map\":{\"dimension\":{\"width\":10,\"height\":10},\"walls\":[]},\"history\":[],\"winnerId\":\"player-1\"}}";

        // Act
        Message message = MessageCodec.deserialize(json);

        // Assert
        assertInstanceOf(EndGameMessage.class, message);
        EndGameMessage endMsg = (EndGameMessage) message;
        assertNotNull(endMsg.getGameStatusUpdate());
        assertEquals(GameStatus.END, endMsg.getGameStatusUpdate().status());
    }

    @Test
    @DisplayName("Should round-trip EndGameMessage")
    public void testRoundTripEndGameMessage() {
        // Arrange
        MapLayout mapLayout = new MapLayout(new Dimension(15, 15), new Position[]{new Position(5, 5)});
        GameStatusUpdate gameStatusUpdate = new GameStatusUpdate(
            GameStatus.END,
            mapLayout,
            new GameState[]{},
            "player-2"
        );
        EndGameMessage originalMessage = new EndGameMessage(gameStatusUpdate);

        // Act
        Message roundTrippedMessage = MessageCodec.roundTrip(originalMessage);

        // Assert
        assertInstanceOf(EndGameMessage.class, roundTrippedMessage);
        EndGameMessage result = (EndGameMessage) roundTrippedMessage;
        assertEquals("player-2", result.getGameStatusUpdate().winnerId());
    }

    // ==================== InvalidOperationMessage Tests ====================

    @Test
    @DisplayName("Should serialize InvalidOperationMessage to JSON")
    public void testSerializeInvalidOperationMessage() {
        // Arrange
        InvalidOperationMessage message = new InvalidOperationMessage(
            "player-1",
            "Unit not found"
        );

        // Act
        String json = MessageCodec.serialize(message);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"INVALID_OPERATION\""));
        assertTrue(json.contains("\"playerId\":\"player-1\""));
        assertTrue(json.contains("\"reason\":\"Unit not found\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to InvalidOperationMessage")
    public void testDeserializeInvalidOperationMessage() {
        // Arrange
        String json = "{\"type\":\"INVALID_OPERATION\",\"playerId\":\"player-1\",\"reason\":\"Invalid direction\"}";

        // Act
        Message message = MessageCodec.deserialize(json);

        // Assert
        assertInstanceOf(InvalidOperationMessage.class, message);
        InvalidOperationMessage invalidMsg = (InvalidOperationMessage) message;
        assertEquals("player-1", invalidMsg.getPlayerId());
        assertEquals("Invalid direction", invalidMsg.getReason());
    }

    @Test
    @DisplayName("Should round-trip InvalidOperationMessage")
    public void testRoundTripInvalidOperationMessage() {
        // Arrange
        InvalidOperationMessage originalMessage = new InvalidOperationMessage(
            "player-3",
            "Action rejected: unit belongs to different player"
        );

        // Act
        Message roundTrippedMessage = MessageCodec.roundTrip(originalMessage);

        // Assert
        assertInstanceOf(InvalidOperationMessage.class, roundTrippedMessage);
        InvalidOperationMessage result = (InvalidOperationMessage) roundTrippedMessage;
        assertEquals("player-3", result.getPlayerId());
        assertEquals("Action rejected: unit belongs to different player", result.getReason());
    }

    // ==================== Polymorphic Deserialization Tests ====================

    @Test
    @DisplayName("Should correctly identify and deserialize polymorphic types")
    public void testPolymorphicDeserialization() {
        // Arrange
        String actionJson = "{\"type\":\"ACTION\",\"playerId\":\"p1\",\"actions\":[]}";
        String joinJson = "{\"type\":\"JOIN_GAME\",\"playerId\":\"p1\"}";
        String invalidJson = "{\"type\":\"INVALID_OPERATION\",\"playerId\":\"p1\",\"reason\":\"test\"}";

        // Act
        Message actionMsg = MessageCodec.deserialize(actionJson);
        Message joinMsg = MessageCodec.deserialize(joinJson);
        Message invalidMsg = MessageCodec.deserialize(invalidJson);

        // Assert
        assertInstanceOf(ActionMessage.class, actionMsg);
        assertInstanceOf(JoinGameMessage.class, joinMsg);
        assertInstanceOf(InvalidOperationMessage.class, invalidMsg);
    }

    // ==================== Null Handling Tests ====================

    @Test
    @DisplayName("Should throw IllegalArgumentException when serializing null message")
    public void testSerializeNullMessage() {
        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> MessageCodec.serialize(null),
            "Expected IllegalArgumentException for null message"
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when deserializing null JSON")
    public void testDeserializeNullJson() {
        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> MessageCodec.deserialize(null),
            "Expected IllegalArgumentException for null JSON"
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when deserializing empty JSON")
    public void testDeserializeEmptyJson() {
        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> MessageCodec.deserialize(""),
            "Expected IllegalArgumentException for empty JSON"
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when deserializing blank JSON")
    public void testDeserializeBlankJson() {
        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> MessageCodec.deserialize("   "),
            "Expected IllegalArgumentException for blank JSON"
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when round-tripping null message")
    public void testRoundTripNullMessage() {
        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> MessageCodec.roundTrip(null),
            "Expected IllegalArgumentException for null message"
        );
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should throw RuntimeException on malformed JSON")
    public void testDeserializeMalformedJson() {
        // Arrange
        String malformedJson = "{invalid json}";

        // Act & Assert
        assertThrows(
            RuntimeException.class,
            () -> MessageCodec.deserialize(malformedJson),
            "Expected RuntimeException for malformed JSON"
        );
    }

    @Test
    @DisplayName("Should throw RuntimeException on missing type field in JSON")
    public void testDeserializeJsonMissingType() {
        // Arrange
        String jsonMissingType = "{\"playerId\":\"player-1\"}";

        // Act & Assert
        assertThrows(
            RuntimeException.class,
            () -> MessageCodec.deserialize(jsonMissingType),
            "Expected RuntimeException for JSON missing type field"
        );
    }

    @Test
    @DisplayName("Should throw RuntimeException on unknown message type")
    public void testDeserializeUnknownType() {
        // Arrange
        String unknownTypeJson = "{\"type\":\"UNKNOWN_TYPE\",\"data\":\"test\"}";

        // Act & Assert
        assertThrows(
            RuntimeException.class,
            () -> MessageCodec.deserialize(unknownTypeJson),
            "Expected RuntimeException for unknown message type"
        );
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle ActionMessage with empty action array")
    public void testActionMessageWithEmptyActions() {
        // Arrange
        ActionMessage message = new ActionMessage("player-1", new Action[]{});

        // Act
        Message roundTripped = MessageCodec.roundTrip(message);

        // Assert
        assertInstanceOf(ActionMessage.class, roundTripped);
        ActionMessage result = (ActionMessage) roundTripped;
        assertEquals(0, result.getActions().length);
    }

    @Test
    @DisplayName("Should handle InvalidOperationMessage with long reason")
    public void testInvalidOperationMessageWithLongReason() {
        // Arrange
        String longReason = "This is a very long error message that explains in detail what went wrong: " +
                           "The unit could not be found at the specified location.";
        InvalidOperationMessage message = new InvalidOperationMessage("player-1", longReason);

        // Act
        Message roundTripped = MessageCodec.roundTrip(message);

        // Assert
        assertInstanceOf(InvalidOperationMessage.class, roundTripped);
        InvalidOperationMessage result = (InvalidOperationMessage) roundTripped;
        assertEquals(longReason, result.getReason());
    }

    @Test
    @DisplayName("Should handle StartGameMessage with multiple units")
    public void testStartGameMessageWithMultipleUnits() {
        // Arrange
        Unit[] units = {
            new Unit(1, "player-1", UnitType.PAWN, new Position(0, 0)),
            new Unit(2, "player-1", UnitType.PAWN, new Position(0, 1)),
            new Unit(3, "player-2", UnitType.BASE, new Position(9, 9))
        };
        GameState gameState = new GameState(units, 1000L);
        Dimension dimension = new Dimension(10, 10);
        MapLayout mapLayout = new MapLayout(dimension, new Position[0]);
        GameStatusUpdate statusUpdate = new GameStatusUpdate(
            GameStatus.START,
            mapLayout,
            new GameState[]{gameState},
            null
        );
        StartGameMessage message = new StartGameMessage(statusUpdate);

        // Act
        Message roundTripped = MessageCodec.roundTrip(message);

        // Assert
        assertInstanceOf(StartGameMessage.class, roundTripped);
        StartGameMessage result = (StartGameMessage) roundTripped;
        assertEquals(3, result.getGameStatusUpdate().history()[0].units().length);
    }

    @Test
    @DisplayName("Should handle special characters in player IDs")
    public void testSpecialCharactersInPlayerId() {
        // Arrange
        String playerId = "player-1@domain.com";
        JoinGameMessage message = new JoinGameMessage(playerId);

        // Act
        Message roundTripped = MessageCodec.roundTrip(message);

        // Assert
        assertInstanceOf(JoinGameMessage.class, roundTripped);
        JoinGameMessage result = (JoinGameMessage) roundTripped;
        assertEquals(playerId, result.getPlayerId());
    }
}
