package sl.hackathon.client.api;

import sl.hackathon.client.messages.*;

/**
 * Interface for handling different types of game messages.
 * Implementations define the business logic for responding to each message type.
 */
public interface MessageHandler {

    /**
     * Handles the game start message.
     * Called when the server indicates the game has begun.
     * 
     * @param message the start game message containing initial game state
     */
    void handleStartGame(StartGameMessage message);
    
    /**
     * Handles the next turn message.
     * Called when it's a player's turn to make a move.
     * 
     * @param message the next turn message containing current game state
     */
    void handleNextTurn(NextTurnMessage message);
    
    /**
     * Handles the game end message.
     * Called when the game has finished.
     * 
     * @param message the end game message containing final results
     */
    void handleGameEnd(EndGameMessage message);
    
    /**
     * Handles invalid operation messages.
     * Called when the server rejects an action as invalid.
     * 
     * @param message the invalid operation message containing error details
     */
    void handleInvalidOperation(InvalidOperationMessage message);
    
    /**
     * Handles errors that occur during message processing.
     * 
     * @param error the error that occurred
     */
    void handleError(Throwable error);
}
