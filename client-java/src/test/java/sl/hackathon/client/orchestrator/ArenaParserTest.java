package sl.hackathon.client.orchestrator;

import org.junit.jupiter.api.Test;
import sl.hackathon.client.dtos.GameStart;
import sl.hackathon.client.dtos.Position;
import sl.hackathon.client.dtos.Unit;
import sl.hackathon.client.dtos.UnitType;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ArenaParserTest {

    @Test
    void parse_parsesWallsBasesAndPawns_withBackslashesAndSpaces() {
        String arena = (
                """
                        . . . . .
                        . b1. . .
                        . . p1. .
                        . # . p2.
                        . # . b2."""
        );

        GameStart gs = ArenaParser.parse(arena, 123L);

        assertNotNull(gs);
        assertEquals(123L, gs.timestamp());
        assertEquals(5, gs.map().dimension().width());
        assertEquals(5, gs.map().dimension().height());

        assertArrayEquals(
            new Position[] { new Position(1, 3), new Position(1, 4) },
            gs.map().walls()
        );

        Unit[] units = gs.initialUnits();
        assertEquals(4, units.length);

        assertUnit(units[0], 1, "player1", UnitType.BASE, new Position(1, 1));
        assertUnit(units[1], 2, "player1", UnitType.PAWN, new Position(2, 2));
        assertUnit(units[2], 3, "player2", UnitType.PAWN, new Position(3, 3));
        assertUnit(units[3], 4, "player2", UnitType.BASE, new Position(3, 4));
    }

    @Test
    void parse_parsesCompactFormat_withoutSpaces() {
        String arena = String.join("\n",
            ".....",
            ".b1...",
            "..p1..",
            ".#.p2.",
            ".#.b2."
        );

        GameStart gs = ArenaParser.parse(arena, 0L);

        assertEquals(5, gs.map().dimension().width());
        assertEquals(5, gs.map().dimension().height());

        assertArrayEquals(
            new Position[] { new Position(1, 3), new Position(1, 4) },
            gs.map().walls()
        );

        Unit[] units = gs.initialUnits();
        assertEquals(4, units.length);

        assertEquals(
            Arrays.asList(new Position(1, 1), new Position(2, 2), new Position(3, 3), new Position(3, 4)),
            Arrays.stream(units).map(Unit::position).toList()
        );
    }

    @Test
    void parseArenaToGameStart_rejectsNonRectangularArena() {
        String arena = String.join("\n",
            "...",
            "...."
        );

        assertThrows(IllegalArgumentException.class, () -> ArenaParser.parse(arena, 0L));
    }

    @Test
    void parse_parsesFoodUnits_markedAsF() {
        String arena = (
                """
                        . f .
                        . b1.
                        p2. .
                        """
        );

        GameStart gs = ArenaParser.parse(arena, 999L);

        assertEquals(3, gs.map().dimension().width());
        assertEquals(3, gs.map().dimension().height());

        Unit[] units = gs.initialUnits();
        assertEquals(3, units.length);

        assertUnit(units[0], 1, null, UnitType.FOOD, new Position(1, 0));
        assertUnit(units[1], 2, "player1", UnitType.BASE, new Position(1, 1));
        assertUnit(units[2], 3, "player2", UnitType.PAWN, new Position(0, 2));
    }

    @Test
    void parse_parsesFoodUnits_compactFormat() {
        String arena = """
            .f.
            .b1.
            p2..""";

        GameStart gs = ArenaParser.parse(arena, 1L);
        Unit[] units = gs.initialUnits();

        assertEquals(3, units.length);
        assertUnit(units[0], 1, null, UnitType.FOOD, new Position(1, 0));
        assertUnit(units[1], 2, "player1", UnitType.BASE, new Position(1, 1));
        assertUnit(units[2], 3, "player2", UnitType.PAWN, new Position(0, 2));
    }

    private static void assertUnit(Unit unit, int id, String owner, UnitType type, Position pos) {
        assertEquals(id, unit.id());
        assertEquals(owner, unit.owner());
        assertEquals(type, unit.type());
        assertEquals(pos, unit.position());
    }
}
