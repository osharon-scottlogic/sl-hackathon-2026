package sl.hackathon.client.tutorial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import sl.hackathon.client.dtos.Position;
import sl.hackathon.client.dtos.UnitType;

public record TutorialUnitSeed(
    String owner,
    UnitType type,
    Position position
) {
    @JsonCreator
    public TutorialUnitSeed(
        @JsonProperty("owner") String owner,
        @JsonProperty("type") UnitType type,
        @JsonProperty("position") Position position
    ) {
        this.owner = owner;
        this.type = type;
        this.position = position;
    }
}
