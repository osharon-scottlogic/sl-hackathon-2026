package sl.hackathon.client.api;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.messages.*;

import java.util.function.Consumer;

import static sl.hackathon.client.util.Ansi.*;

@AllArgsConstructor
public class MessageHandlerImpl implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerImpl.class);

    // Message handlers (delegated via internal MessageHandler)
    @Setter
    Consumer<StartGameMessage> onGameStart;
    @Setter Consumer<NextTurnMessage> onNextTurn;
    @Setter Consumer<EndGameMessage> onGameEnd;
    @Setter Consumer<InvalidOperationMessage> onInvalidOperation;
    @Setter Consumer<Throwable> onError;

    @Override
    public void handleStartGame(StartGameMessage message) {
        logger.info("Game started");
        if (onGameStart != null) {
            onGameStart.accept(message);
        }
    }

    @Override
    public void handleNextTurn(NextTurnMessage message) {
        logger.debug(green("Next turn for player: {}"), message.getPlayerId());
        if (onNextTurn != null) {
            onNextTurn.accept(message);
        }
    }

    @Override
    public void handleGameEnd(EndGameMessage message) {
        logger.info(green("Game ended, winner: {}"), message.getGameEnd() != null ? message.getGameEnd().winnerId() : "unknown");
        if (onGameEnd != null) {
            onGameEnd.accept(message);
        }
    }

    @Override
    public void handleInvalidOperation(InvalidOperationMessage message) {
        logger.warn(yellow("Invalid operation for player {}: {}"), message.getPlayerId(), message.getReason());
        if (onInvalidOperation != null) {
            onInvalidOperation.accept(message);
        }
    }

    @Override
    public void handleError(Throwable error) {
        logger.error(redBg("Message handling error"), error);
        if (onError != null) {
            onError.accept(error);
        }
    }
}
