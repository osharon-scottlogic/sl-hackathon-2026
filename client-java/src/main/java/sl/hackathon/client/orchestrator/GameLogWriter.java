package sl.hackathon.client.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.dtos.GameEnd;
import sl.hackathon.client.dtos.GameLog;
import sl.hackathon.client.dtos.Unit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static sl.hackathon.client.util.Ansi.redBg;
import static sl.hackathon.client.util.Ansi.yellow;

public class GameLogWriter {
    private static final Logger logger = LoggerFactory.getLogger(GameLogWriter.class);
    private static final String GAME_LOGS_DIR = "./game-logs/";
    private static final ObjectMapper objectMapper = JsonMapper.builder().build();


    /**
     * Writes game log to JSON file in the game-logs directory.
     * File name includes timestamp for uniqueness.
     *
     * @param gameEnd the final game status update
     */
    public static void write(GameEnd gameEnd) {
        try {
            // Create game-logs directory if it doesn't exist
            File logsDir = new File(GAME_LOGS_DIR);
            if (!logsDir.exists()) {
                if (!logsDir.mkdirs()) {
                    logger.error(redBg(yellow("failed to create {} folder")),GAME_LOGS_DIR);
                }
            }

            // Generate filename with timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = writeLogFile(gameEnd, timestamp);

            logger.info("Game log written to: {}", filename);

        } catch (IOException e) {
            logger.warn("Failed to write game log", e);
        }
    }

    private static @NonNull String writeLogFile(GameEnd gameEnd, String timestamp) throws IOException {
        String filename = GAME_LOGS_DIR + "game_" + timestamp + ".json";

        // Extract all unique players from first state
        java.util.Set<String> playerIds = new java.util.LinkedHashSet<>();
        if (gameEnd.deltas() != null && gameEnd.deltas().length > 0) {
            for (Unit unit : gameEnd.deltas()[0].addedOrModified()) {
                if (unit.owner() != null) {
                    playerIds.add(unit.owner());
                }
            }
        }

        // Write game log as JSON
        try (FileWriter writer = new FileWriter(filename)) {
            GameLog gameLog = new GameLog(playerIds.toArray(new String[0]),gameEnd.map().dimension(), gameEnd.map().walls(), gameEnd.winnerId(), timestamp, gameEnd.deltas());
            writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(gameLog));
        }
        return filename;
    }
}
