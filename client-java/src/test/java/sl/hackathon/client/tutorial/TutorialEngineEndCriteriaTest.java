package sl.hackathon.client.tutorial;

import org.junit.jupiter.api.Test;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.EndGameMessage;
import sl.hackathon.client.messages.Message;
import sl.hackathon.client.messages.NextTurnMessage;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TutorialEngineEndCriteriaTest {
    @Test
    void endsWhenUnitCountReached() {
        TutorialDefinition def = new TutorialDefinition(
            null,
            new MapLayout(new Dimension(5, 5), new Position[0]),
            new TutorialUnitSeed[] {
                new TutorialUnitSeed("player1", UnitType.BASE, new Position(0, 0)),
                new TutorialUnitSeed("player1", UnitType.PAWN, new Position(1, 0)),
                new TutorialUnitSeed(null, UnitType.FOOD, new Position(2, 0))
            },
            1.0f,
            Map.of(),
            new TutorialEndCriteria(TutorialEndCriteriaType.PLAYER_UNITS_AT_LEAST, "player1", 3, null, null, 0)
        );

        TutorialEngine engine = new TutorialEngine(def, "player1", new Random(0));

        // Move pawn east onto food to trigger +1 pawn at base.
        Unit pawn = findFirstOwned(engine.buildStartGameMessage().getGameStart().initialUnits(), "player1", UnitType.PAWN);
        assertNotNull(pawn);

        List<Message> out = engine.handleActions("player1", new Action[] { new Action(pawn.id(), Direction.E) });
        assertTrue(out.stream().anyMatch(m -> m instanceof EndGameMessage));
    }

    @Test
    void endsWhenUnitEntersRectangle() {
        TutorialDefinition def = new TutorialDefinition(
            null,
            new MapLayout(new Dimension(5, 5), new Position[0]),
            new TutorialUnitSeed[] {
                new TutorialUnitSeed("player1", UnitType.BASE, new Position(0, 0)),
                new TutorialUnitSeed("player1", UnitType.PAWN, new Position(0, 1))
            },
            1.0f,
            Map.of(),
            new TutorialEndCriteria(
                TutorialEndCriteriaType.ANY_PLAYER_UNIT_IN_RECT,
                null,
                null,
                new Position(2, 2),
                new Position(4, 4),
                0
            )
        );

        TutorialEngine engine = new TutorialEngine(def, "player1", new Random(0));
        Unit pawn = findFirstOwned(engine.buildStartGameMessage().getGameStart().initialUnits(), "player1", UnitType.PAWN);
        assertNotNull(pawn);

        // Move pawn into (2,2) using SE then E.
        engine.handleActions("player1", new Action[] { new Action(pawn.id(), Direction.SE) }); // (1,2)
        List<Message> out = engine.handleActions("player1", new Action[] { new Action(pawn.id(), Direction.E) }); // (2,2)

        assertTrue(out.stream().anyMatch(m -> m instanceof EndGameMessage));
    }

    @Test
    void losesWhenMaxTurnsReachedBeforeGoal() {
        TutorialDefinition def = new TutorialDefinition(
            null,
            new MapLayout(new Dimension(5, 5), new Position[0]),
            new TutorialUnitSeed[] {
                new TutorialUnitSeed("player1", UnitType.BASE, new Position(0, 0))
            },
            1.0f,
            Map.of(),
            new TutorialEndCriteria(TutorialEndCriteriaType.PLAYER_UNITS_AT_LEAST, "player1", 2, null, null, 1)
        );

        TutorialEngine engine = new TutorialEngine(def, "player1", new Random(0));

        List<Message> out = engine.handleActions("player1", new Action[0]);
        EndGameMessage end = out.stream()
            .filter(m -> m instanceof EndGameMessage)
            .map(m -> (EndGameMessage) m)
            .findFirst()
            .orElse(null);

        assertNotNull(end);
        assertNotNull(end.getGameEnd());
        assertNotNull(end.getGameEnd().winnerId());
        assertNotEquals("player1", end.getGameEnd().winnerId());
    }

    @Test
    void maxTurnsZeroMeansNoTurnLimit() {
        TutorialDefinition def = new TutorialDefinition(
            null,
            new MapLayout(new Dimension(5, 5), new Position[0]),
            new TutorialUnitSeed[] {
                new TutorialUnitSeed("player1", UnitType.BASE, new Position(0, 0))
            },
            1.0f,
            Map.of(),
            new TutorialEndCriteria(TutorialEndCriteriaType.PLAYER_UNITS_AT_LEAST, "player1", 2, null, null, 0)
        );

        TutorialEngine engine = new TutorialEngine(def, "player1", new Random(0));

        for (int i = 0; i < 3; i++) {
            List<Message> out = engine.handleActions("player1", new Action[0]);
            assertTrue(out.stream().anyMatch(m -> m instanceof NextTurnMessage));
            assertFalse(out.stream().anyMatch(m -> m instanceof EndGameMessage));
        }
    }

    private static Unit findFirstOwned(Unit[] units, String owner, UnitType type) {
        for (Unit unit : units) {
            if (unit != null && owner.equals(unit.owner()) && type.equals(unit.type())) {
                return unit;
            }
        }
        return null;
    }
}
