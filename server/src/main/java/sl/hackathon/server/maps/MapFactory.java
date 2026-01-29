package sl.hackathon.server.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.Main;
import sl.hackathon.server.dtos.Dimension;
import sl.hackathon.server.dtos.MapConfig;
import sl.hackathon.server.dtos.Position;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MapFactory {
    private static final Logger logger = LoggerFactory.getLogger(MapFactory.class);
    private static final String MAPS_FOLDER ="maps/";
    /**
     * Read map details from file and creates a map configuration.
     *
     * @param filename the file containing map data
     * @return a MapConfig based on file data
     */
    public static MapConfig createMapConfig(String filename) {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(MAPS_FOLDER + filename)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Map file not found: " + filename);
            }

            JsonNode root = mapper.readTree(inputStream);

            // Parse dimensions
            JsonNode dimensionsNode = root.get("dimensions");
            int width = dimensionsNode.get(0).asInt();
            int height = dimensionsNode.get(1).asInt();
            Dimension dimension = new Dimension(width, height);

            // Parse walls
            JsonNode wallsNode = root.get("walls");
            List<Position> wallsList = new ArrayList<>();
            for (JsonNode wallNode : wallsNode) {
                int x = wallNode.get(0).asInt();
                int y = wallNode.get(1).asInt();
                wallsList.add(new Position(x, y));
            }
            Position[] walls = wallsList.toArray(new Position[0]);

            // Parse bases
            JsonNode basesNode = root.get("bases");
            List<Position> basesList = new ArrayList<>();
            for (JsonNode baseNode : basesNode) {
                int x = baseNode.get(0).asInt();
                int y = baseNode.get(1).asInt();
                basesList.add(new Position(x, y));
            }
            Position[] bases = basesList.toArray(new Position[0]);

            return new MapConfig(dimension, walls, bases);

        } catch (IOException e) {
            logger.error("Failed to load map configuration from file: " + filename, e);
            throw new RuntimeException("Failed to load map configuration", e);
        }
    }
}
