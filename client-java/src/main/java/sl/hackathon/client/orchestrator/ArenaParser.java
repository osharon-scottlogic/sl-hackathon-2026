package sl.hackathon.client.orchestrator;

import sl.hackathon.client.dtos.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArenaParser {
    private static final Pattern ARENA_TOKEN_PATTERN = Pattern.compile("(#|\\.|b\\d+|p\\d+|f)");

    public static GameStart parse(String arena, long timestamp) {
        if (arena == null || arena.isBlank()) {
            throw new IllegalArgumentException("Arena cannot be null/blank");
        }

        // Support both newline-separated arenas and arenas using '\\' as a row delimiter.
        String normalized = arena
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\\n", "\n")
                .replace('\\', '\n');

        List<String> rows = Arrays.stream(normalized.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Arena has no rows");
        }

        List<Position> walls = new ArrayList<>();
        List<Unit> initialUnits = new ArrayList<>();
        int width = -1;
        int nextUnitId = 1;

        for (int y = 0; y < rows.size(); y++) {
            List<String> tokens = tokenizeArenaRow(rows.get(y));
            if (width == -1) {
                width = tokens.size();
                if (width == 0) {
                    throw new IllegalArgumentException("Arena row has no columns");
                }
            } else if (tokens.size() != width) {
                throw new IllegalArgumentException(
                        "Arena is not rectangular: row " + y + " has " + tokens.size() + " columns, expected " + width
                );
            }

            for (int x = 0; x < tokens.size(); x++) {
                String token = tokens.get(x);
                switch (token.charAt(0)) {
                    case '.' -> {
                        // empty
                    }
                    case '#' -> walls.add(new Position(x, y));
                    case 'b' -> {
                        String owner = normalizePlayerId(token.substring(1));
                        initialUnits.add(new Unit(nextUnitId++, owner, UnitType.BASE, new Position(x, y)));
                    }
                    case 'p' -> {
                        String owner = normalizePlayerId(token.substring(1));
                        initialUnits.add(new Unit(nextUnitId++, owner, UnitType.PAWN, new Position(x, y)));
                    }
                    case 'f' -> initialUnits.add(new Unit(nextUnitId++, null, UnitType.FOOD, new Position(x, y)));
                    default -> throw new IllegalArgumentException("Unexpected arena token: " + token);
                }
            }
        }

        MapLayout layout = new MapLayout(new Dimension(width, rows.size()), walls.toArray(Position[]::new));
        return new GameStart(layout, initialUnits.toArray(Unit[]::new), timestamp);
    }

    private static List<String> tokenizeArenaRow(String row) {
        // Remove all whitespace; tokens are ., #, bN, pN (N = one or more digits)
        String compact = row.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = ARENA_TOKEN_PATTERN.matcher(compact);
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() != index) {
                throw new IllegalArgumentException(
                        "Invalid arena row: unexpected character(s) at index " + index + " in '" + row + "'"
                );
            }
            tokens.add(matcher.group(1));
            index = matcher.end();
        }
        if (index != compact.length()) {
            throw new IllegalArgumentException(
                    "Invalid arena row: trailing character(s) at index " + index + " in '" + row + "'"
            );
        }
        return tokens;
    }

    private static String normalizePlayerId(String numericId) {
        if (numericId == null || numericId.isBlank()) {
            throw new IllegalArgumentException("Invalid player id in arena token");
        }
        return "player" + numericId;
    }
}
