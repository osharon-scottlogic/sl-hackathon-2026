package sl.hackathon.client.tutorial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import sl.hackathon.client.dtos.GameStart;
import sl.hackathon.client.dtos.MapLayout;
import sl.hackathon.client.dtos.Position;
import sl.hackathon.client.orchestrator.ArenaParser;

import java.util.Arrays;
import java.util.Map;

public record TutorialDefinition(
    String arena,
    MapLayout map,
    TutorialUnitSeed[] initialUnits,
    float foodScarcity,
    Map<Integer, Position> foodSpawn,
    TutorialEndCriteria gameEnd
) {
    @JsonCreator
    public TutorialDefinition(
        @JsonProperty("arena") String arena,
        @JsonProperty("map") MapLayout map,
        @JsonProperty("initialUnits") TutorialUnitSeed[] initialUnits,
        @JsonProperty("foodScarcity") float foodScarcity,
        @JsonProperty("foodSpawn") Map<Integer, Position> foodSpawn,
        @JsonProperty("gameEnd") TutorialEndCriteria gameEnd
    ) {
        MapLayout effectiveMap = map;
        TutorialUnitSeed[] effectiveInitialUnits = initialUnits;

        if (arena != null && !arena.isBlank() && (effectiveMap == null || effectiveInitialUnits == null)) {
            GameStart gameStart = ArenaParser.parse(arena, 0L);
            effectiveMap = gameStart.map();
            effectiveInitialUnits = Arrays.stream(gameStart.initialUnits())
                .map(u -> new TutorialUnitSeed(u.owner(), u.type(), u.position()))
                .toArray(TutorialUnitSeed[]::new);
        }

        this.arena = arena;
        this.map = effectiveMap;
        this.initialUnits = effectiveInitialUnits;
        this.foodScarcity = foodScarcity;
        this.foodSpawn = foodSpawn;
        this.gameEnd = gameEnd;
    }
}
