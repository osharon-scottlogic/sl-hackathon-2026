package sl.hackathon.client.api;

import sl.hackathon.client.dtos.Action;

import java.io.IOException;

public interface ServerAPI {
    void connect(String serverURL) throws Exception;

    void send(String playerId, Action[] actions) throws IOException;

    void close();

    boolean isConnected();

    void setOnGameStart(java.util.function.Consumer<sl.hackathon.client.messages.StartGameMessage> onGameStart);

    void setOnPlayerAssigned(java.util.function.Consumer<sl.hackathon.client.messages.PlayerAssignedMessage> onPlayerAssigned);

    void setOnNextTurn(java.util.function.Consumer<sl.hackathon.client.messages.NextTurnMessage> onNextTurn);

    void setOnGameEnd(java.util.function.Consumer<sl.hackathon.client.messages.EndGameMessage> onGameEnd);

    void setOnInvalidOperation(java.util.function.Consumer<sl.hackathon.client.messages.InvalidOperationMessage> onInvalidOperation);

    void setOnError(java.util.function.Consumer<Throwable> onError);
}
