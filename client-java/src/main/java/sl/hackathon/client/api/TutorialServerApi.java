package sl.hackathon.client.api;

import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.messages.*;

import java.io.IOException;
import java.util.function.Consumer;

public class TutorialServerApi implements ServerAPI {
    @Override
    public void connect(String serverURL) throws Exception {

    }

    @Override
    public void send(String playerId, Action[] actions) throws IOException {

    }

    @Override
    public void close() {

    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void setOnGameStart(Consumer<StartGameMessage> onGameStart) {

    }

    @Override
    public void setOnPlayerAssigned(Consumer<PlayerAssignedMessage> onPlayerAssigned) {

    }

    @Override
    public void setOnNextTurn(Consumer<NextTurnMessage> onNextTurn) {

    }

    @Override
    public void setOnGameEnd(Consumer<EndGameMessage> onGameEnd) {

    }

    @Override
    public void setOnInvalidOperation(Consumer<InvalidOperationMessage> onInvalidOperation) {

    }

    @Override
    public void setOnError(Consumer<Throwable> onError) {

    }
}
