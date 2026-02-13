package sl.hackathon.client.api;

import sl.hackathon.client.dtos.Action;
import sl.hackathon.client.messages.*;
import sl.hackathon.client.tutorial.TutorialDefinition;
import sl.hackathon.client.tutorial.TutorialEngine;
import sl.hackathon.client.tutorial.TutorialLoader;

import java.util.Objects;
import java.util.Random;

public class TutorialServerApi implements ServerAPI {
    private static final String PREFIX = "tutorial:";
    private static final String DEFAULT_TUTORIAL_ID = "basics-01";
    private static final String DEFAULT_PLAYER_ID = "player1";

    private volatile boolean connected;
    private volatile boolean closed;

    private TutorialEngine engine;
    private final String assignedPlayerId = DEFAULT_PLAYER_ID;

    private final MessageRouter messageRouter;

    public TutorialServerApi (MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }

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

            if (messageRouter != null) {
                messageRouter.accept(engine.buildStartGameMessage());
                messageRouter.accept(engine.buildNextTurnMessage());
            }
        } catch (Exception e) {
            connected = false;
            if (messageRouter != null) {
                messageRouter.accept(e);
                return;
            }
            throw e;
        }

    }

    @Override
    public void send(String playerId, Action[] actions) {
        if (!connected || closed) {
            return;
        }
        if (engine == null) {
            return;
        }
        String effectivePlayerId = Objects.requireNonNullElse(playerId, assignedPlayerId);

        for (Message message : engine.handleActions(effectivePlayerId, actions)) {
            messageRouter.accept(message);
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
