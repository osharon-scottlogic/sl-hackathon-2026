package sl.hackathon.client.api;

import org.junit.jupiter.api.Test;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for message serialization and deserialization round-trips.
 * Verifies that all message types can be properly serialized to JSON
 * and deserialized back to their original form.
 */
class ServerAPIMessageSerializationTest {
    
    @Test
    void testActionMessageSerialization() throws Exception {
        Action[] actions = new Action[]{
            new Action(1, Direction.N),
            new Action(2, Direction.SE)
        };
        
        ActionMessage original = new ActionMessage("player1", actions);
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(ActionMessage.class, deserialized);
        ActionMessage result = (ActionMessage) deserialized;
        assertEquals("player1", result.getPlayerId());
        assertEquals(2, result.getActions().length);
        assertEquals(1, result.getActions()[0].unitId());
        assertEquals(Direction.N, result.getActions()[0].direction());
    }
    
    @Test
    void testJoinGameMessageSerialization() throws Exception {
        JoinGameMessage original = new JoinGameMessage("player1");
        
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(JoinGameMessage.class, deserialized);
        JoinGameMessage result = (JoinGameMessage) deserialized;
        assertEquals("player1", result.getPlayerId());
    }
    
    @Test
    void testStartGameMessageSerialization() throws Exception {
        GameState state = new GameState(
            new Unit[]{new Unit(1, "p1", UnitType.BASE, new Position(5, 5))},
            System.currentTimeMillis()
        );
        
        MapLayout mapLayout = new MapLayout(
            new Dimension(20, 20),
            new Position[]{new Position(10, 10)}
        );
        
        GameStart gameStart = new GameStart(
            mapLayout,
            state.units(),
            System.currentTimeMillis()
        );
        
        StartGameMessage original = new StartGameMessage(gameStart);
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(StartGameMessage.class, deserialized);
        StartGameMessage result = (StartGameMessage) deserialized;
        assertNotNull(result.getGameStart());
        assertEquals(1, result.getGameStart().initialUnits().length);
        assertEquals(1, result.getGameStart().initialUnits()[0].id());
    }
    
    @Test
    void testNextTurnMessageSerialization() throws Exception {
        GameState state = new GameState(
            new Unit[]{new Unit(1, "p2", UnitType.PAWN, new Position(3, 3))},
            System.currentTimeMillis()
        );
        NextTurnMessage original = new NextTurnMessage("player2", state);
        
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(NextTurnMessage.class, deserialized);
        NextTurnMessage result = (NextTurnMessage) deserialized;
        assertEquals("player2", result.getPlayerId());
        assertNotNull(result.getGameState());
        assertEquals(1, result.getGameState().units().length);
    }
    
    @Test
    void testEndGameMessageSerialization() throws Exception {
        MapLayout mapLayout = new MapLayout(
            new Dimension(20, 20),
            new Position[]{new Position(10, 10)}
        );
        
        Unit[] initialUnits = new Unit[0];
        GameDelta[] deltas = new GameDelta[0];
        
        GameEnd gameEnd = new GameEnd(
            mapLayout,
            initialUnits,
            deltas,
            "winner1",
            System.currentTimeMillis()
        );
        
        EndGameMessage original = new EndGameMessage(gameEnd);
        
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(EndGameMessage.class, deserialized);
        EndGameMessage result = (EndGameMessage) deserialized;
        assertEquals("winner1", result.getGameEnd().winnerId());
        assertEquals(0, result.getGameEnd().deltas().length);
    }
    
    @Test
    void testInvalidOperationMessageSerialization() throws Exception {
        InvalidOperationMessage original = new InvalidOperationMessage("player3", "Test error");
        
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(InvalidOperationMessage.class, deserialized);
        InvalidOperationMessage result = (InvalidOperationMessage) deserialized;
        assertEquals("Test error", result.getReason());
        assertEquals("player3", result.getPlayerId());
    }
    
    @Test
    void testSerializationWithEmptyActions() throws Exception {
        ActionMessage original = new ActionMessage("player1", new Action[0]);
        
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(ActionMessage.class, deserialized);
        ActionMessage result = (ActionMessage) deserialized;
        assertEquals(0, result.getActions().length);
    }
    
    @Test
    void testSerializationWithMultipleUnits() throws Exception {
        Unit[] units = new Unit[]{
            new Unit(1, "p1", UnitType.PAWN, new Position(1, 1)),
            new Unit(2, "p1", UnitType.PAWN, new Position(2, 2)),
            new Unit(3, "p1", UnitType.BASE, new Position(0, 0)),
            new Unit(4, null, UnitType.FOOD, new Position(5, 5))
        };
        
        GameState state = new GameState(units, System.currentTimeMillis());
        
        MapLayout mapLayout = new MapLayout(
            new Dimension(20, 20),
            new Position[]{new Position(10, 10)}
        );
        
        GameStart gameStart = new GameStart(
            mapLayout,
            units,
            System.currentTimeMillis()
        );
        
        StartGameMessage original = new StartGameMessage(gameStart);
        
        String json = MessageCodec.serialize(original);
        Message deserialized = MessageCodec.deserialize(json);
        
        assertInstanceOf(StartGameMessage.class, deserialized);
        StartGameMessage result = (StartGameMessage) deserialized;
        assertEquals(4, result.getGameStart().initialUnits().length);
        
        // Verify FOOD unit has no owner
        Unit foodUnit = result.getGameStart().initialUnits()[3];
        assertEquals(UnitType.FOOD, foodUnit.type());
        assertNull(foodUnit.owner());
    }
    
    @Test
    void testRoundTripSerialization() throws Exception {
        // Test that round-trip serialization preserves data
        Action[] actions = new Action[]{
            new Action(1, Direction.NE),
            new Action(2, Direction.SW)
        };
        
        ActionMessage original = new ActionMessage("player1", actions);
        Message roundTripped = MessageCodec.roundTrip(original);
        
        assertInstanceOf(ActionMessage.class, roundTripped);
        ActionMessage result = (ActionMessage) roundTripped;
        assertEquals(original.getPlayerId(), result.getPlayerId());
        assertEquals(original.getActions().length, result.getActions().length);
    }
    
    @Test
    void testDeserializeInvalidJson() {
        assertThrows(RuntimeException.class, () -> MessageCodec.deserialize("{invalid json}"));
    }
    
    @Test
    void testDeserializeNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.deserialize(null));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.deserialize(""));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.deserialize("   "));
    }
    
    @Test
    void testSerializeNull() {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.serialize(null));
    }
    
    @Test
    void testRoundTripNull() {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.roundTrip(null));
    }
}
