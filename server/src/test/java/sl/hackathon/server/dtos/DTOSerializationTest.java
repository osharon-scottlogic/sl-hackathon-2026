package sl.hackathon.server.dtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DTO serialization/deserialization.
 * Tests all record types and complex DTOs for JSON round-trip integrity.
 */
@DisplayName("DTO Serialization Tests")
public class DTOSerializationTest {
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    // ==================== Position Tests ====================

    @Test
    @DisplayName("Should serialize Position record to JSON")
    public void testSerializePosition() throws Exception {
        // Arrange
        Position position = new Position(5, 10);

        // Act
        String json = objectMapper.writeValueAsString(position);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"x\":5"));
        assertTrue(json.contains("\"y\":10"));
    }

    @Test
    @DisplayName("Should deserialize JSON to Position record")
    public void testDeserializePosition() throws Exception {
        // Arrange
        String json = "{\"x\":3,\"y\":7}";

        // Act
        Position position = objectMapper.readValue(json, Position.class);

        // Assert
        assertNotNull(position);
        assertEquals(3, position.x());
        assertEquals(7, position.y());
    }

    @Test
    @DisplayName("Should round-trip Position with edge cases")
    public void testRoundTripPositionEdgeCases() throws Exception {
        // Test with zero coordinates
        Position zeroPos = new Position(0, 0);
        String jsonZero = objectMapper.writeValueAsString(zeroPos);
        Position deserializedZero = objectMapper.readValue(jsonZero, Position.class);
        assertEquals(0, deserializedZero.x());
        assertEquals(0, deserializedZero.y());

        // Test with negative coordinates
        Position negPos = new Position(-5, -10);
        String jsonNeg = objectMapper.writeValueAsString(negPos);
        Position deserializedNeg = objectMapper.readValue(jsonNeg, Position.class);
        assertEquals(-5, deserializedNeg.x());
        assertEquals(-10, deserializedNeg.y());

        // Test with large coordinates
        Position largePos = new Position(1000, 2000);
        String jsonLarge = objectMapper.writeValueAsString(largePos);
        Position deserializedLarge = objectMapper.readValue(jsonLarge, Position.class);
        assertEquals(1000, deserializedLarge.x());
        assertEquals(2000, deserializedLarge.y());
    }

    // ==================== Action Tests ====================

    @Test
    @DisplayName("Should serialize Action record to JSON")
    public void testSerializeAction() throws Exception {
        // Arrange
        Action action = new Action(1, Direction.N);

        // Act
        String json = objectMapper.writeValueAsString(action);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"unitId\":1"));
        assertTrue(json.contains("\"direction\":\"N\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to Action record")
    public void testDeserializeAction() throws Exception {
        // Arrange
        String json = "{\"unitId\":2,\"direction\":\"SE\"}";

        // Act
        Action action = objectMapper.readValue(json, Action.class);

        // Assert
        assertNotNull(action);
        assertEquals(2, action.unitId());
        assertEquals(Direction.SE, action.direction());
    }

    @Test
    @DisplayName("Should round-trip Action with all directions")
    public void testRoundTripActionAllDirections() throws Exception {
        Direction[] directions = {Direction.N, Direction.NE, Direction.E, Direction.SE,
                                 Direction.S, Direction.SW, Direction.W, Direction.NW};

        for (Direction dir : directions) {
            Action action = new Action(1, dir);
            String json = objectMapper.writeValueAsString(action);
            Action deserialized = objectMapper.readValue(json, Action.class);
            assertEquals(dir, deserialized.direction());
            assertEquals(1, deserialized.unitId());
        }
    }

    // ==================== Unit Tests ====================

    @Test
    @DisplayName("Should serialize Unit record to JSON")
    public void testSerializeUnit() throws Exception {
        // Arrange
        Unit unit = new Unit(1, "player-1", UnitType.PAWN, new Position(5, 5));

        // Act
        String json = objectMapper.writeValueAsString(unit);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"owner\":\"player-1\""));
        assertTrue(json.contains("\"type\":\"PAWN\""));
        assertTrue(json.contains("\"position\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to Unit record")
    public void testDeserializeUnit() throws Exception {
        // Arrange
        String json = "{\"id\":2,\"owner\":\"player-2\",\"type\":\"PAWN\",\"position\":{\"x\":3,\"y\":4}}";

        // Act
        Unit unit = objectMapper.readValue(json, Unit.class);

        // Assert
        assertNotNull(unit);
        assertEquals(2, unit.id());
        assertEquals("player-2", unit.owner());
        assertEquals(UnitType.PAWN, unit.type());
        assertEquals(3, unit.position().x());
        assertEquals(4, unit.position().y());
    }

    @Test
    @DisplayName("Should round-trip Unit with all unit types")
    public void testRoundTripUnitAllTypes() throws Exception {
        UnitType[] types = {UnitType.PAWN, UnitType.FOOD, UnitType.BASE};

        for (UnitType type : types) {
            Unit unit = new Unit(1, "player-1", type, new Position(0, 0));
            String json = objectMapper.writeValueAsString(unit);
            Unit deserialized = objectMapper.readValue(json, Unit.class);
            assertEquals(type, deserialized.type());
            assertEquals(1, deserialized.id());
        }
    }

    // ==================== GameState Tests ====================

    @Test
    @DisplayName("Should serialize GameState record to JSON")
    public void testSerializeGameState() throws Exception {
        // Arrange
        Unit[] units = {
            new Unit(1, "player-1", UnitType.PAWN, new Position(0, 0)),
            new Unit(2, "player-2", UnitType.BASE, new Position(7, 7))
        };
        GameState gameState = new GameState(units, 1000L);

        // Act
        String json = objectMapper.writeValueAsString(gameState);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"units\""));
        assertTrue(json.contains("\"startAt\":1000"));
        assertTrue(json.contains("1"));
    }

    @Test
    @DisplayName("Should deserialize JSON to GameState record")
    public void testDeserializeGameState() throws Exception {
        // Arrange
        String json = "{\"units\":[{\"id\":1,\"owner\":\"player-1\",\"type\":\"PAWN\",\"position\":{\"x\":0,\"y\":0}}],\"startAt\":2000}";

        // Act
        GameState gameState = objectMapper.readValue(json, GameState.class);

        // Assert
        assertNotNull(gameState);
        assertEquals(2000L, gameState.startAt());
        assertEquals(1, gameState.units().length);
        assertEquals(1, gameState.units()[0].id());
    }

    @Test
    @DisplayName("Should round-trip GameState with empty units")
    public void testRoundTripGameStateEmptyUnits() throws Exception {
        // Arrange
        GameState gameState = new GameState(new Unit[0], 5000L);

        // Act
        String json = objectMapper.writeValueAsString(gameState);
        GameState deserialized = objectMapper.readValue(json, GameState.class);

        // Assert
        assertEquals(5000L, deserialized.startAt());
        assertEquals(0, deserialized.units().length);
    }

    // ==================== GameStatusUpdate Tests ====================

    @Test
    @DisplayName("Should serialize GameStatusUpdate record to JSON")
    public void testSerializeGameStatusUpdate() throws Exception {
        // Arrange
        GameState[] history = {
            new GameState(new Unit[0], 1000L),
            new GameState(new Unit[0], 2000L)
        };
        MapLayout mapLayout = new MapLayout(new Dimension(8, 8),new Position[]{});
        GameStatusUpdate update = new GameStatusUpdate(GameStatus.PLAYING, mapLayout, history, "player-1");

        // Act
        String json = objectMapper.writeValueAsString(update);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"status\":\"PLAYING\""));
        assertTrue(json.contains("\"winnerId\":\"player-1\""));
        assertTrue(json.contains("\"history\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to GameStatusUpdate record")
    public void testDeserializeGameStatusUpdate() throws Exception {
        // Arrange
        String json = "{\"status\":\"END\",\"map\":{\"dimension\":{\"width\":8,\"height\":8}},\"history\":[],\"winnerId\":\"player-2\"}";

        // Act
        GameStatusUpdate update = objectMapper.readValue(json, GameStatusUpdate.class);

        // Assert
        assertNotNull(update);
        assertEquals(GameStatus.END, update.status());
        assertEquals("player-2", update.winnerId());
        assertEquals(0, update.history().length);
    }

    @Test
    @DisplayName("Should round-trip GameStatusUpdate with all game statuses")
    public void testRoundTripGameStatusUpdateAllStatuses() throws Exception {
        GameStatus[] statuses = {GameStatus.IDLE, GameStatus.START, GameStatus.PLAYING, GameStatus.END};

        for (GameStatus status : statuses) {
            MapLayout mapLayout = new MapLayout(new Dimension(8, 8), new Position[]{});
            GameStatusUpdate update = new GameStatusUpdate(status, mapLayout, new GameState[0], "player-test");
            String json = objectMapper.writeValueAsString(update);
            GameStatusUpdate deserialized = objectMapper.readValue(json, GameStatusUpdate.class);
            assertEquals(status, deserialized.status());
        }
    }

    // ==================== Dimension Tests ====================

    @Test
    @DisplayName("Should serialize Dimension record to JSON")
    public void testSerializeDimension() throws Exception {
        // Arrange
        Dimension dimension = new Dimension(8, 8);

        // Act
        String json = objectMapper.writeValueAsString(dimension);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"width\":8"));
        assertTrue(json.contains("\"height\":8"));
    }

    @Test
    @DisplayName("Should deserialize JSON to Dimension record")
    public void testDeserializeDimension() throws Exception {
        // Arrange
        String json = "{\"width\":10,\"height\":12}";

        // Act
        Dimension dimension = objectMapper.readValue(json, Dimension.class);

        // Assert
        assertNotNull(dimension);
        assertEquals(10, dimension.width());
        assertEquals(12, dimension.height());
    }

    // ==================== MapLayout Tests ====================

    @Test
    @DisplayName("Should serialize MapLayout record to JSON")
    public void testSerializeMapLayout() throws Exception {
        // Arrange
        MapLayout mapLayout = new MapLayout(new Dimension(8, 8), new Position[]{});

        // Act
        String json = objectMapper.writeValueAsString(mapLayout);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"dimension\""));
    }

    @Test
    @DisplayName("Should deserialize JSON to MapLayout record")
    public void testDeserializeMapLayout() throws Exception {
        // Arrange
        String json = "{\"dimension\":{\"width\":16,\"height\":16}}";

        // Act
        MapLayout mapLayout = objectMapper.readValue(json, MapLayout.class);

        // Assert
        assertNotNull(mapLayout);
        assertEquals(16, mapLayout.dimension().width());
        assertEquals(16, mapLayout.dimension().height());
    }

    // ==================== Complex Nested Serialization Tests ====================

    @Test
    @DisplayName("Should handle complex nested structures in serialization")
    public void testComplexNestedStructures() throws Exception {
        // Arrange - Create a complex nested structure
        Unit[] units = {
            new Unit(1, "player-1", UnitType.PAWN, new Position(1, 1)),
            new Unit(2, "player-1", UnitType.BASE, new Position(3, 3)),
            new Unit(3, "player-2", UnitType.BASE, new Position(6, 6))
        };
        GameState gameState = new GameState(units, System.currentTimeMillis());
        GameState[] history = {gameState};
        MapLayout mapLayout = new MapLayout(new Dimension(8, 8), new Position[]{});
        GameStatusUpdate statusUpdate = new GameStatusUpdate(GameStatus.PLAYING, mapLayout, history, null);

        // Act
        String json = objectMapper.writeValueAsString(statusUpdate);
        GameStatusUpdate deserialized = objectMapper.readValue(json, GameStatusUpdate.class);

        // Assert
        assertEquals(GameStatus.PLAYING, deserialized.status());
        assertEquals(8, deserialized.map().dimension().width());
        assertEquals(3, deserialized.history()[0].units().length);
        assertEquals(1, deserialized.history()[0].units()[0].id());
    }

    @Test
    @DisplayName("Should preserve null values in nullable fields")
    public void testNullValuePreservation() throws Exception {
        // Arrange
        MapLayout mapLayout = new MapLayout(new Dimension(8, 8), new Position[]{});
        GameStatusUpdate update = new GameStatusUpdate(GameStatus.IDLE, mapLayout, new GameState[0], null);

        // Act
        String json = objectMapper.writeValueAsString(update);
        GameStatusUpdate deserialized = objectMapper.readValue(json, GameStatusUpdate.class);

        // Assert
        assertNull(deserialized.winnerId());
    }

    // ==================== Enum Tests ====================

    @Test
    @DisplayName("Should serialize and deserialize all Direction enum values")
    public void testAllDirectionValues() throws Exception {
        for (Direction direction : Direction.values()) {
            String json = objectMapper.writeValueAsString(direction);
            Direction deserialized = objectMapper.readValue(json, Direction.class);
            assertEquals(direction, deserialized);
        }
    }

    @Test
    @DisplayName("Should serialize and deserialize all UnitType enum values")
    public void testAllUnitTypeValues() throws Exception {
        for (UnitType unitType : UnitType.values()) {
            String json = objectMapper.writeValueAsString(unitType);
            UnitType deserialized = objectMapper.readValue(json, UnitType.class);
            assertEquals(unitType, deserialized);
        }
    }

    @Test
    @DisplayName("Should serialize and deserialize all GameStatus enum values")
    public void testAllGameStatusValues() throws Exception {
        for (GameStatus status : GameStatus.values()) {
            String json = objectMapper.writeValueAsString(status);
            GameStatus deserialized = objectMapper.readValue(json, GameStatus.class);
            assertEquals(status, deserialized);
        }
    }
}
