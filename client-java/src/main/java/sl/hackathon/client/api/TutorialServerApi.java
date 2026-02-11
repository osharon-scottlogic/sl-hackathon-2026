package sl.hackathon.client.api;

import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.messages.*;
import sl.hackathon.client.tutorial.TutorialDefinition;
import sl.hackathon.client.tutorial.TutorialEngine;
import sl.hackathon.client.tutorial.TutorialLoader;

import java.io.IOException;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

public class TutorialServerApi implements ServerAPI {
    private static final String PREFIX = "tutorial:";
    private static final String DEFAULT_TUTORIAL_ID = "basics-01";
    private static final String DEFAULT_PLAYER_ID = "player1";

    private volatile boolean connected;
    private volatile boolean closed;

    private TutorialEngine engine;
    private String assignedPlayerId = DEFAULT_PLAYER_ID;

    private Consumer<StartGameMessage> onGameStart;
    private Consumer<PlayerAssignedMessage> onPlayerAssigned;
    private Consumer<NextTurnMessage> onNextTurn;
    private Consumer<EndGameMessage> onGameEnd;
    private Consumer<InvalidOperationMessage> onInvalidOperation;
    private Consumer<Throwable> onError;

    @Override
    public void connect(String serverURL) throws Exception {
        if (closed) {
            throw new IllegalStateException("TutorialServerApi is closed");
        }

        String tutorialId = parseTutorialId(serverURL);
        try {
            TutorialDefinition definition = TutorialLoader.load(tutorialId);
            this.engine = new TutorialEngine(definition, assignedPlayerId, new Random(0L));
            this.connected = true;

            if (onPlayerAssigned != null) {
                onPlayerAssigned.accept(new PlayerAssignedMessage(assignedPlayerId));
            }
            if (onGameStart != null) {
                onGameStart.accept(engine.buildStartGameMessage());
            }
            if (onNextTurn != null) {
                onNextTurn.accept(engine.buildNextTurnMessage());
            }
        } catch (Exception e) {
            connected = false;
            if (onError != null) {
                onError.accept(e);
                return;
            }
            throw e;
        }

    }

    @Override
    public void send(String playerId, Action[] actions) throws IOException {
        if (!connected || closed) {
            return;
        }
        if (engine == null) {
            return;
        }
        String effectivePlayerId = Objects.requireNonNullElse(playerId, assignedPlayerId);

        for (Message message : engine.handleActions(effectivePlayerId, actions)) {
            dispatch(message);
        }

    }

    @Override
    public void close() {
        closed = true;
        connected = false;

    }

    @Override
    public boolean isConnected() {
        return connected && !closed;
    }

    @Override
    public void setOnGameStart(Consumer<StartGameMessage> onGameStart) {
        this.onGameStart = onGameStart;

    }

    @Override
    public void setOnPlayerAssigned(Consumer<PlayerAssignedMessage> onPlayerAssigned) {
        this.onPlayerAssigned = onPlayerAssigned;

    }

    @Override
    public void setOnNextTurn(Consumer<NextTurnMessage> onNextTurn) {
        this.onNextTurn = onNextTurn;

    }

    @Override
    public void setOnGameEnd(Consumer<EndGameMessage> onGameEnd) {
        this.onGameEnd = onGameEnd;

    }

    @Override
    public void setOnInvalidOperation(Consumer<InvalidOperationMessage> onInvalidOperation) {
        this.onInvalidOperation = onInvalidOperation;

    }

    @Override
    public void setOnError(Consumer<Throwable> onError) {
        this.onError = onError;

    }

    private void dispatch(Message message) {
        if (message instanceof StartGameMessage msg) {
            if (onGameStart != null) {
                onGameStart.accept(msg);
            }
        } else if (message instanceof PlayerAssignedMessage msg) {
            if (onPlayerAssigned != null) {
                onPlayerAssigned.accept(msg);
            }
        } else if (message instanceof NextTurnMessage msg) {
            if (onNextTurn != null) {
                onNextTurn.accept(msg);
            }
        } else if (message instanceof EndGameMessage msg) {
            if (onGameEnd != null) {
                onGameEnd.accept(msg);
            }
        } else if (message instanceof InvalidOperationMessage msg) {
            if (onInvalidOperation != null) {
                onInvalidOperation.accept(msg);
            }
        }
    }

    private String parseTutorialId(String serverURL) {
        if (serverURL == null || serverURL.isBlank()) {
            return DEFAULT_TUTORIAL_ID;
        }
        String trimmed = serverURL.trim();
        if (trimmed.startsWith(PREFIX)) {
            String id = trimmed.substring(PREFIX.length()).trim();
            return id.isEmpty() ? DEFAULT_TUTORIAL_ID : id;
        }
        if (trimmed.startsWith("tutorial")) {
            return DEFAULT_TUTORIAL_ID;
        }
        // If this is somehow used with a non-tutorial URL, treat it as an id.
        return trimmed;
    }
}
