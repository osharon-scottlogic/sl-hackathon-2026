package sl.hackathon.client.tutorial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import sl.hackathon.client.dtos.Position;

public record TutorialEndCriteria(
    TutorialEndCriteriaType type,
    String playerId,
    Integer minUnits,
    Position corner1,
    Position corner2,
    Integer maxTurns
) {
    @JsonCreator
    public TutorialEndCriteria(
        @JsonProperty("type") TutorialEndCriteriaType type,
        @JsonProperty("playerId") String playerId,
        @JsonProperty("minUnits") Integer minUnits,
        @JsonProperty("corner1") Position corner1,
        @JsonProperty("corner2") Position corner2,
        @JsonProperty("maxTurns") Integer maxTurns
    ) {
        this.type = type;
        this.playerId = playerId;
        this.minUnits = minUnits;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.maxTurns = maxTurns;
    }
}
