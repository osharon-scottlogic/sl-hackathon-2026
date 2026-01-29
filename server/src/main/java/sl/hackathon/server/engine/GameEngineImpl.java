package sl.hackathon.server.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.*;
import sl.hackathon.server.util.Ansi;
import sl.hackathon.server.validators.ActionValidator;
import sl.hackathon.server.validators.ActionValidatorImpl;

import java.util.*;

/**
 * Implementation of GameEngine that manages the authoritative game state.
 */
public class GameEngineImpl implements GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngineImpl.class);
    private final List<String> activePlayers;
    private GameState previousGameState = null;
    private GameState currentGameState = null;
    private final List<GameDelta> gameDeltaHistory;
    private GameParams gameParams;
    private boolean initialized;
    private int currentTurn;
    private final ActionValidator actionValidator;
    private final GameStatusUpdater statusUpdater;
    private final UnitIdGenerator unitIdGenerator;

    public GameEngineImpl() {
        this.activePlayers = new ArrayList<>();
        this.gameDeltaHistory = new ArrayList<>();
        this.initialized = false;
        this.currentTurn = 0;
        this.actionValidator = new ActionValidatorImpl();
        this.unitIdGenerator = new UnitIdGenerator();
        this.statusUpdater = new GameStatusUpdaterImpl(unitIdGenerator);
    }

    /**
     * Adds a player to the game.
     *
     * @param playerId the player ID to add
     */
    @Override
    public void addPlayer(String playerId) {
        if (playerId != null && !activePlayers.contains(playerId)) {
            activePlayers.add(playerId);
        }
    }

    /**
     * Removes a player from the game.
     *
     * @param playerId the player ID to remove
     */
    @Override
    public void removePlayer(String playerId) {
        activePlayers.remove(playerId);
    }

    /**
     * Initializes the game with the given parameters.
     *
     * @param gameParams the game parameters including map and turn time limit
     * @return the initial game state
     */
    @Override
    public GameState initialize(GameParams gameParams) {
        if (gameParams == null) {
            throw new IllegalArgumentException("GameParams cannot be null");
        }

        this.gameParams = gameParams;
        initialized = true;
        currentTurn = 0;
        gameDeltaHistory.clear();

        // Create initial game state with units spawned at potential base locations
        Unit[] initialUnits = createInitialUnits(gameParams);
        previousGameState = null;
        currentGameState = new GameState(initialUnits, System.currentTimeMillis());

        // Add to history
        this.gameDeltaHistory.add(statusUpdater.generateDelta(this.previousGameState, this.currentGameState));
        return currentGameState;
    }

    /**
     * Gets the current game state.
     *
     * @return a snapshot of the current game state
     */
    @Override
    public GameState getGameState() {
        return currentGameState;
    }

    /**Gets the game delta history.
     *
     * @return a list of all game deltas from initialization to current
     */
    @Override
    public List<GameDelta> getGameDeltaHistory() {
        return new ArrayList<>(gameDeltaHistory);
    }

    /**
     * 
     * Handles player actions and updates the game state.
     *
     * @param playerId the player ID performing the actions
     * @param actions the actions to process
     * @return true if actions were successfully processed; false if invalid
     */
    @Override
    public boolean handlePlayerActions(String playerId, Action[] actions) {
        if (!initialized || currentGameState == null) {
            return false;
        }

        if (playerId == null || !activePlayers.contains(playerId)) {
            return false;
        }

        List<InvalidAction> invalidActions = actionValidator.validate(currentGameState, playerId, actions);
        if (!invalidActions.isEmpty()) {
            return false;
        }

        // Update game state
        previousGameState = currentGameState;
        currentGameState = statusUpdater.update(currentGameState, playerId, actions);

        // Spawn food after state update
        List<Unit> updatedUnits = new ArrayList<>(Arrays.asList(currentGameState.units()));
        spawnFood(updatedUnits, gameParams);
        currentGameState = new GameState(updatedUnits.toArray(new Unit[0]), currentGameState.startAt());

        gameDeltaHistory.add(statusUpdater.generateDelta(previousGameState, currentGameState));
        currentTurn++;

        return true;
    }

    /**
     * Gets the list of active players.
     *
     * @return a list of player IDs currently in the game
     */
    @Override
    public List<String> getActivePlayers() {
        return new ArrayList<>(activePlayers);
    }

    /**
     * Gets the current turn number.
     *
     * @return the current turn number (0-based)
     */
    @Override
    public int getCurrentTurn() {
        return currentTurn;
    }

    /**
     * Checks if the game is initialized.
     *
     * @return true if the game has been initialized
     */
    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if the game has ended.
     *
     * @return true if the game has ended
     */
    @Override
    public boolean isGameEnded() {
        if (!initialized || currentGameState == null) {
            return false;
        }
        return statusUpdater.hasGameEnded(currentGameState);
    }

    /**
     * Gets the winner of the game if it has ended.
     *
     * @return the player ID of the winner, or null if game hasn't ended or no winner
     */
    @Override
    public String getWinnerId() {
        if (!isGameEnded()) {
            return null;
        }
        return statusUpdater.getWinnerId(currentGameState);
    }

    /**
     * Creates initial units based on game parameters.
     * Spawns a BASE at each potential base location for each player.
     * Spawns initial PAWN units for each player.
     *
     * @param gameParams the game parameters
     * @return array of initial units
     */
    private Unit[] createInitialUnits(GameParams gameParams) {
        List<Unit> units = new ArrayList<>();

        // Get potential base locations and active players
        Position[] baseLocations = gameParams.mapConfig().potentialBaseLocations();
        List<String> players = getActivePlayers();

        // Spawn bases and pawns for each player
        for (int i = 0; i < players.size() && i < baseLocations.length; i++) {
            String playerId = players.get(i);
            Position baseLocation = baseLocations[i];

            // Create base unit
            Unit base = new Unit(
                unitIdGenerator.nextId(),
                playerId,
                UnitType.BASE,
                baseLocation
            );
            units.add(base);

            // Create initial pawn near the base (offset by 1)
            Unit pawn = new Unit(
                unitIdGenerator.nextId(),
                playerId,
                UnitType.PAWN,
                new Position(baseLocation.x() + 1, baseLocation.y())
            );
            units.add(pawn);
        }

        // Optionally spawn food units (scattered across map)
        // This can be enhanced based on foodScarcity parameter
        spawnFood(units, gameParams);

        return units.toArray(new Unit[0]);
    }

    /**
     * Spawns food units based on the food scarcity parameter.
     * Creates at most one food unit per call if a random float is less than foodScarcity.
     *
     * @param units the list of units to add food to
     * @param gameParams the game parameters
     */
    private void spawnFood(List<Unit> units, GameParams gameParams) {
        Dimension mapDim = gameParams.mapConfig().dimension();
        float scarcity = gameParams.foodScarcity();

        // Check if food should spawn based on scarcity
        Random random = new Random();
        if (random.nextFloat() <= scarcity) {
            return; // No food spawned this turn
        }
        logger.debug("Spawning one food unit");

        Set<Position> occupiedPositions = new HashSet<>();
        for (Unit unit : units) {
            occupiedPositions.add(unit.position());
        }

        Set<Position> wallPositions = new HashSet<>(Arrays.asList(gameParams.mapConfig().walls()));

        // Spawn one food at a random unoccupied position
        int mapArea = mapDim.width() * mapDim.height();
        int attempts = 0;
        int maxAttempts = mapArea * 2; // Prevent infinite loop

        while (attempts < maxAttempts) {
            int x = random.nextInt(mapDim.width());
            int y = random.nextInt(mapDim.height());
            Position foodPos = new Position(x, y);

            if (!occupiedPositions.contains(foodPos) && !wallPositions.contains(foodPos)) {
                // Generate unique food ID
                Unit food = new Unit(
                    unitIdGenerator.nextId(),
                    "none",
                    UnitType.FOOD,
                    foodPos
                );
                units.add(food);
                return; // Successfully spawned one food
            }
            attempts++;
        }
    }
}
