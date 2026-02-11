package sl.hackathon.client.tutorial;

import sl.hackathon.client.dtos.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static sl.hackathon.client.dtos.UnitType.FOOD;
import static sl.hackathon.client.dtos.UnitType.PAWN;

public final class TutorialUnitGenerator {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Random random;

    public TutorialUnitGenerator(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public int nextId() {
        return counter.incrementAndGet();
    }

    public List<Unit> getNewlyAddedUnits(int unitsToAdd, Unit baseUnit) {
        if (unitsToAdd <= 0) {
            return List.of();
        }
        if (baseUnit == null) {
            return List.of();
        }

        List<Unit> units = new ArrayList<>(unitsToAdd);
        for (int i = 0; i < unitsToAdd; i++) {
            units.add(new Unit(
                nextId(),
                baseUnit.owner(),
                PAWN,
                new Position(baseUnit.position().x(), baseUnit.position().y())
            ));
        }
        return units;
    }

    /**
     * Spawns a single food unit with probability (1 - scarcity), matching server semantics.
     */
    public void maybeSpawnRandomFood(List<Unit> units, MapLayout mapLayout, float foodScarcity) {
        if (units == null || mapLayout == null || mapLayout.dimension() == null) {
            return;
        }

        if (random.nextFloat() <= foodScarcity) {
            return; // no food spawned this turn
        }

        Dimension mapDim = mapLayout.dimension();

        Set<Position> occupiedPositions = new HashSet<>();
        for (Unit unit : units) {
            occupiedPositions.add(unit.position());
        }

        Set<Position> wallPositions = new HashSet<>();
        if (mapLayout.walls() != null) {
            wallPositions.addAll(Arrays.asList(mapLayout.walls()));
        }

        int mapArea = mapDim.width() * mapDim.height();
        int attempts = 0;
        int maxAttempts = mapArea * 2;

        while (attempts < maxAttempts) {
            int x = random.nextInt(mapDim.width());
            int y = random.nextInt(mapDim.height());
            Position foodPos = new Position(x, y);

            if (!occupiedPositions.contains(foodPos) && !wallPositions.contains(foodPos)) {
                units.add(new Unit(nextId(), null, FOOD, foodPos));
                return;
            }
            attempts++;
        }
    }

    public void spawnFoodAt(List<Unit> units, Position position) {
        if (units == null || position == null) {
            return;
        }
        units.add(new Unit(nextId(), null, FOOD, position));
    }
}
