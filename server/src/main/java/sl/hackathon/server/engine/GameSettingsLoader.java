package sl.hackathon.server.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.Main;
import sl.hackathon.server.dtos.Dimension;
import sl.hackathon.server.dtos.GameSettings;
import sl.hackathon.server.dtos.Position;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static sl.hackathon.server.util.Ansi.redBg;
import static sl.hackathon.server.util.Ansi.yellow;

public class GameSettingsLoader {
    private static final Logger logger = LoggerFactory.getLogger(GameSettingsLoader.class);
    private static final String MAPS_FOLDER ="maps/";

    private static final Pattern ARENA_TOKEN_PATTERN = Pattern.compile("(#|\\.|b\\d+|p\\d+|f)");
    /**
     * Read map details from file and creates a map configuration.
     *
     * @param filename the file containing map data
     * @return a MapConfig based on file data
     */
    public static GameSettings load(String filename) {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(MAPS_FOLDER + filename)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Map file not found: " + filename);
            }

            JsonNode root = mapper.readTree(inputStream);

            return parseGameSettings(root);
        } catch (IOException e) {
            logger.error(redBg(yellow("Failed to load map configuration from file: {}")), filename, e);
            throw new RuntimeException("Failed to load map configuration", e);
        }
    }


    private static GameSettings parseGameSettings(JsonNode root) {
        String arena = root.get("arena").asText();
        float foodScarcity = (float)root.get("foodScarcity").asDouble();
        int turnTimeLimit = root.get("turnTimeLimit").asInt();
        boolean fogOfWar = root.get("fogOfWar").asBoolean();

        if (arena == null || arena.isBlank()) {
            throw new IllegalArgumentException("Invalid map schema: 'arena' cannot be null/blank");
        }

        String normalized = arena
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\\n", "\n");

        List<String> rows = Arrays.stream(normalized.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Invalid map schema: 'arena' has no rows");
        }

        List<Position> walls = new ArrayList<>();
        Map<Integer, Position> baseById = new HashMap<>();

        int width = -1;
        for (int y = 0; y < rows.size(); y++) {
            List<String> tokens = tokenizeArenaRow(rows.get(y));
            if (width == -1) {
                width = tokens.size();
                if (width == 0) {
                    throw new IllegalArgumentException("Invalid map schema: arena row has no columns");
                }
            } else if (tokens.size() != width) {
                throw new IllegalArgumentException(
                        "Invalid map schema: arena is not rectangular (row " + y + ")"
                );
            }

            for (int x = 0; x < tokens.size(); x++) {
                String token = tokens.get(x);
                char type = token.charAt(0);
                if (type == '#') {
                    walls.add(new Position(x, y));
                } else if (type == 'b') {
                    int baseId;
                    try {
                        baseId = Integer.parseInt(token.substring(1));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid base token in arena: " + token);
                    }
                    Position previous = baseById.put(baseId, new Position(x, y));
                    if (previous != null) {
                        throw new IllegalArgumentException("Duplicate base token in arena: " + token);
                    }
                } else {
                    // '.', 'pN', and 'f' are ignored by the server map config.
                }
            }
        }

        Dimension dimension = new Dimension(width, rows.size());
        Position[] bases = baseById.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .toArray(Position[]::new);

        return new GameSettings(dimension, walls.toArray(new Position[0]), bases, turnTimeLimit, foodScarcity, fogOfWar);
    }

    private static List<String> tokenizeArenaRow(String row) {
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
}
