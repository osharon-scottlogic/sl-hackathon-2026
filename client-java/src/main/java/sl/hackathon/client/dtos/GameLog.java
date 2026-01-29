package sl.hackathon.client.dtos;

public record GameLog (String[] players, Dimension mapDimensions, Position[] walls, String winner, String timestamp, GameDelta[] turns){
}
