package sl.hackathon.server.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single authority for generating unique sequential unit IDs.
 * Thread-safe implementation using AtomicInteger.
 */
public class UnitGenerator {
    private static final Logger logger = LoggerFactory.getLogger(UnitGenerator.class);

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Generates and returns the next unique unit ID.
     *
     * @return the next sequential unit ID
     */
    public int nextId() {
        return counter.incrementAndGet();
    }

    /**
     * Resets the counter to 0 (useful for testing).
     */
    public void reset() {
        counter.set(0);
    }

    /**
     * Gets the current counter value without incrementing.
     *
     * @return the current counter value
     */
    public int getCurrentCount() {
        return counter.get();
    }

    /**
     * Creates initial units based on game parameters.
     * Spawns a BASE at each potential base location for each player.
     * Spawns initial PAWN units for each player.
     *
     * @param gameSettings the game parameters
     * @return array of initial units
     */
    public Unit[] createInitialUnits(GameSettings gameSettings, List<String> players) {
        List<Unit> units = new ArrayList<>();

        // Get potential base locations and active players
        Position[] baseLocations = gameSettings.potentialBaseLocations();

        // Spawn bases and pawns for each player
        for (int i = 0; i < players.size() && i < baseLocations.length; i++) {
            String playerId = players.get(i);
            Position baseLocation = baseLocations[i];

            // Create base unit
            Unit base = new Unit(
                    nextId(),
                    playerId,
                    UnitType.BASE,
                    baseLocation
            );
            units.add(base);

            // Create initial pawn near the base (offset by 1)
            Unit pawn = new Unit(
                    nextId(),
                    playerId,
                    UnitType.PAWN,
                    new Position(baseLocation.x() + 1, baseLocation.y())
            );
            units.add(pawn);
        }

        // Optionally spawn food units (scattered across map)
        // This can be enhanced based on foodScarcity parameter
        spawnFood(units, gameSettings);

        return units.toArray(new Unit[0]);
    }

    /**
     * Spawns food units based on the food scarcity parameter.
     * Creates at most one food unit per call if a random float is less than foodScarcity.
     *
     * @param units the list of units to add food to
     * @param gameSettings the game parameters
     */
    public void spawnFood(List<Unit> units, GameSettings gameSettings) {
        Dimension mapDim = gameSettings.dimension();
        float scarcity = gameSettings.foodScarcity();

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

        Set<Position> wallPositions = new HashSet<>(Arrays.asList(gameSettings.walls()));

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
                        nextId(),
                        null,
                        UnitType.FOOD,
                        foodPos
                );
                units.add(food);
                return; // Successfully spawned one food
            }
            attempts++;
        }
    }

    public List<Unit> getNewlyAddedUnits(int unitsToAdd, Unit baseUnit) {
        List<Unit> units = new ArrayList<>();
        for (int i=0;i<unitsToAdd;i++) {
            units.add(new Unit(
                    nextId(),
                    baseUnit.owner(),
                    UnitType.PAWN,
                    new Position(baseUnit.position().x(), baseUnit.position().y())
            ));
        }

        return units;
    }
}
