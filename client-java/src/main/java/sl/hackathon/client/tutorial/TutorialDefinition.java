package sl.hackathon.client.tutorial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import sl.hackathon.client.dtos.MapLayout;
import sl.hackathon.client.dtos.Position;

import java.util.Map;

public record TutorialDefinition(
    MapLayout map,
    TutorialUnitSeed[] initialUnits,
    float foodScarcity,
    Map<Integer, Position> foodSpawn,
    TutorialEndCriteria gameEnd
) {
    @JsonCreator
    public TutorialDefinition(
        @JsonProperty("map") MapLayout map,
        @JsonProperty("initialUnits") TutorialUnitSeed[] initialUnits,
        @JsonProperty("foodScarcity") float foodScarcity,
        @JsonProperty("foodSpawn") Map<Integer, Position> foodSpawn,
        @JsonProperty("gameEnd") TutorialEndCriteria gameEnd
    ) {
        this.map = map;
        this.initialUnits = initialUnits;
        this.foodScarcity = foodScarcity;
        this.foodSpawn = foodSpawn;
        this.gameEnd = gameEnd;
    }
}
