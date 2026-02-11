package sl.hackathon.client.tutorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

public final class TutorialLoader {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private TutorialLoader() {
    }

    public static TutorialDefinition load(String tutorialId) throws IOException {
        if (tutorialId == null || tutorialId.isBlank()) {
            throw new IllegalArgumentException("tutorialId cannot be null/blank");
        }

        String normalized = tutorialId.trim();
        String resourcePath = "tutorials/" + normalized + ".json";

        try (InputStream input = TutorialLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Tutorial resource not found: " + resourcePath);
            }
            TutorialDefinition definition = OBJECT_MAPPER.readValue(input, TutorialDefinition.class);
            validate(definition, normalized);
            return definition;
        }
    }

    private static void validate(TutorialDefinition def, String tutorialId) {
        if (def == null) {
            throw new IllegalArgumentException("TutorialDefinition is null for tutorialId=" + tutorialId);
        }
        if (def.map() == null || def.map().dimension() == null) {
            throw new IllegalArgumentException("Tutorial map/dimension is required");
        }
        if (def.initialUnits() == null) {
            throw new IllegalArgumentException("Tutorial initialUnits is required (can be empty)");
        }
        if (def.gameEnd() == null || def.gameEnd().type() == null) {
            throw new IllegalArgumentException("Tutorial gameEnd.type is required");
        }

        if (def.gameEnd().type() == TutorialEndCriteriaType.PLAYER_UNITS_AT_LEAST) {
            if (def.gameEnd().minUnits() == null || def.gameEnd().minUnits() < 0) {
                throw new IllegalArgumentException("gameEnd.minUnits must be >= 0");
            }
        }

        if (def.gameEnd().type() == TutorialEndCriteriaType.ANY_PLAYER_UNIT_IN_RECT) {
            if (def.gameEnd().corner1() == null || def.gameEnd().corner2() == null) {
                throw new IllegalArgumentException("gameEnd.corner1 and gameEnd.corner2 are required");
            }
        }

        if (def.gameEnd().maxTurns() != null && def.gameEnd().maxTurns() < 0) {
            throw new IllegalArgumentException("gameEnd.maxTurns must be >= 0");
        }

        if (def.foodScarcity() < 0.0f || def.foodScarcity() > 1.0f) {
            throw new IllegalArgumentException("foodScarcity must be in [0,1]");
        }
    }
}
