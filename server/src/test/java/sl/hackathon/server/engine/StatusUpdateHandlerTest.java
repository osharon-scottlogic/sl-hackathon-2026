package sl.hackathon.server.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;

import static org.junit.jupiter.api.Assertions.*;

class StatusUpdateHandlerTest {
    private StatusUpdateHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StatusUpdateHandlerImpl();
    }

    // ===== BASIC FUNCTIONALITY TESTS =====

    @Test
    void testHandleStatusUpdateWithValidInput() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        GameState[] history = new GameState[]{
            new GameState(new Unit[]{}, System.currentTimeMillis())
        };
        GameStatusUpdate update = new GameStatusUpdate(
            GameStatus.PLAYING,
            map,
            history,
            null
        );

        // Should not throw exception
        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }

    @Test
    void testHandleStatusUpdateWithNullUpdate() {
        // Should handle null gracefully
        assertDoesNotThrow(() -> handler.handleStatusUpdate(null));
    }

    @Test
    void testHandleStatusUpdateWithGameStart() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        GameState[] history = new GameState[]{
            new GameState(new Unit[]{}, System.currentTimeMillis())
        };
        GameStatusUpdate update = new GameStatusUpdate(
            GameStatus.START,
            map,
            history,
            null
        );

        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }

    @Test
    void testHandleStatusUpdateWithGameEnd() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        GameState[] history = new GameState[]{
            new GameState(new Unit[]{}, System.currentTimeMillis()),
            new GameState(new Unit[]{}, System.currentTimeMillis())
        };
        GameStatusUpdate update = new GameStatusUpdate(
            GameStatus.END,
            map,
            history,
            "player-1"
        );

        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }

    @Test
    void testHandleStatusUpdateWithNullMap() {
        GameState[] history = new GameState[]{};
        GameStatusUpdate update = new GameStatusUpdate(
            GameStatus.PLAYING,
            null,
            history,
            null
        );

        // Should handle null map gracefully
        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }

    @Test
    void testHandleStatusUpdateWithNullHistory() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        GameStatusUpdate update = new GameStatusUpdate(
            GameStatus.PLAYING,
            map,
            null,
            null
        );

        // Should handle null history gracefully
        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }

    @Test
    void testHandleStatusUpdateWithWinner() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        GameState[] history = new GameState[]{};
        GameStatusUpdate update = new GameStatusUpdate(
            GameStatus.END,
            map,
            history,
            "player-2"
        );

        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }

    @Test
    void testHandleStatusUpdateMultipleCalls() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        GameState[] history = new GameState[]{};

        GameStatusUpdate update1 = new GameStatusUpdate(GameStatus.START, map, history, null);
        GameStatusUpdate update2 = new GameStatusUpdate(GameStatus.PLAYING, map, history, null);
        GameStatusUpdate update3 = new GameStatusUpdate(GameStatus.END, map, history, "player-1");

        // Multiple calls should work without issue
        assertDoesNotThrow(() -> {
            handler.handleStatusUpdate(update1);
            handler.handleStatusUpdate(update2);
            handler.handleStatusUpdate(update3);
        });
    }

    @Test
    void testHandleStatusUpdateWithLargeHistory() {
        Dimension dimension = new Dimension(10, 10);
        MapLayout map = new MapLayout(dimension, new Position[]{});
        
        // Create a large history
        GameState[] history = new GameState[100];
        for (int i = 0; i < 100; i++) {
            history[i] = new GameState(new Unit[]{}, System.currentTimeMillis() + i);
        }

        GameStatusUpdate update = new GameStatusUpdate(GameStatus.PLAYING, map, history, null);

        assertDoesNotThrow(() -> handler.handleStatusUpdate(update));
    }
}
