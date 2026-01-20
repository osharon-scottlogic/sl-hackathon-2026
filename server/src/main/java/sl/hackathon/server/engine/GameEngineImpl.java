package sl.hackathon.server.engine;

import sl.hackathon.server.dtos.*;
import sl.hackathon.server.validators.ActionValidator;
import sl.hackathon.server.validators.ActionValidatorImpl;

import java.util.*;

/**
 * Implementation of GameEngine that manages the authoritative game state.
 */
public class GameEngineImpl implements GameEngine {
    private final List<String> activePlayers;
    private GameState currentGameState;
    private final List<GameState> gameStateHistory;
    private GameParams gameParams;
    private boolean initialized;
    private int currentTurn;
    private final ActionValidator actionValidator;
    private final GameStatusUpdater statusUpdater;

    public GameEngineImpl() {
        this.activePlayers = new ArrayList<>();
        this.gameStateHistory = new ArrayList<>();
        this.initialized = false;
        this.currentTurn = 0;
        this.actionValidator = new ActionValidatorImpl();
        this.statusUpdater = new GameStatusUpdaterImpl();
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
        this.initialized = true;
        this.currentTurn = 0;
        this.gameStateHistory.clear();

        // Create initial game state with units spawned at potential base locations
        Unit[] initialUnits = createInitialUnits(gameParams);
        this.currentGameState = new GameState(initialUnits, System.currentTimeMillis());

        // Add to history
        this.gameStateHistory.add(currentGameState);

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

    /**
     * Gets the game state history.
     *
     * @return a list of all game states from initialization to current
     */
    @Override
    public List<GameState> getGameStateHistory() {
        return new ArrayList<>(gameStateHistory);
    }

    /**
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

        if (!activePlayers.contains(playerId)) {
            return false;
        }

        // Validate actions
        List<InvalidAction> invalidActions = actionValidator.validate(currentGameState, playerId, actions);
        if (!invalidActions.isEmpty()) {
            return false;
        }

        // Update game state
        currentGameState = statusUpdater.update(currentGameState, actions);

        // Spawn food after state update
        List<Unit> updatedUnits = new ArrayList<>(Arrays.asList(currentGameState.units()));
        spawnFood(updatedUnits, gameParams);
        currentGameState = new GameState(updatedUnits.toArray(new Unit[0]), currentGameState.startAt());

        gameStateHistory.add(currentGameState);
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
                "base-" + playerId,
                playerId,
                UnitType.BASE,
                baseLocation
            );
            units.add(base);

            // Create initial pawn near the base (offset by 1)
            Unit pawn = new Unit(
                "pawn-" + playerId + "-0",
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
     *
     * @param units the list of units to add food to
     * @param gameParams the game parameters
     */
    private void spawnFood(List<Unit> units, GameParams gameParams) {
        Dimension mapDim = gameParams.mapConfig().dimension();
        float scarcity = gameParams.foodScarcity();

        // Calculate number of food units (inverse of scarcity)
        // Higher scarcity = fewer food units
        int mapArea = mapDim.width() * mapDim.height();
        int foodCount = Math.max(1, Math.round(mapArea * (1.0f - scarcity) / 10.0f));

        Set<Position> occupiedPositions = new HashSet<>();
        for (Unit unit : units) {
            occupiedPositions.add(unit.position());
        }

        Set<Position> wallPositions = new HashSet<>(Arrays.asList(gameParams.mapConfig().walls()));

        // Spawn food at random unoccupied positions
        Random random = new Random();
        int foodSpawned = 0;

        while (foodSpawned < foodCount && foodSpawned < mapArea) {
            int x = random.nextInt(mapDim.width());
            int y = random.nextInt(mapDim.height());
            Position foodPos = new Position(x, y);

            if (!occupiedPositions.contains(foodPos) && !wallPositions.contains(foodPos)) {
                Unit food = new Unit(
                    "food-" + foodSpawned,
                    "none",
                    UnitType.FOOD,
                    foodPos
                );
                units.add(food);
                occupiedPositions.add(foodPos);
                foodSpawned++;
            }
        }
    }
}
