package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.*;

/**
 * Handler interface for game status updates.
 */
public interface StatusUpdateHandler {
    /**
     * Called when the game status changes.
     *
     * @param update the game status update
     */
    void handleStatusUpdate(GameStatusUpdate update);
}
