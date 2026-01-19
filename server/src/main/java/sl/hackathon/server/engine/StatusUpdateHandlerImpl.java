package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.GameStatusUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of StatusUpdateHandler that logs status updates.
 */
public class StatusUpdateHandlerImpl implements StatusUpdateHandler {
    private static final Logger logger = LoggerFactory.getLogger(StatusUpdateHandlerImpl.class);

    /**
     * Called when the game status changes.
     *
     * @param update the game status update
     */
    @Override
    public void handleStatusUpdate(GameStatusUpdate update) {
        if (update != null) {
            logger.debug("Game status update: status={}, map dimensions={}x{}, history size={}, winner={}",
                update.status(),
                update.map() != null ? update.map().dimension().width() : "unknown",
                update.map() != null ? update.map().dimension().height() : "unknown",
                update.history() != null ? update.history().length : 0,
                update.winnerId() != null ? update.winnerId() : "none");
        } else {
            logger.debug("Game status update received: null");
        }
    }
}
