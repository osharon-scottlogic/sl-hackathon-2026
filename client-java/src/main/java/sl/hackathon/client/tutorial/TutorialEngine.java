package sl.hackathon.client.tutorial;

import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.util.*;

import static sl.hackathon.client.dtos.UnitType.BASE;

public final class TutorialEngine {
    private static final int DEFAULT_TIME_LIMIT_MS = 5000;
    private static final String SELF = "SELF";

    private final TutorialDefinition definition;
    private final String assignedPlayerId;
    private final TutorialUnitGenerator unitGenerator;

    private final List<GameDelta> deltas = new ArrayList<>();

    private final long startAt;

    private GameState currentState;
    private int currentTurnId = 0;

    public TutorialEngine(TutorialDefinition definition, String assignedPlayerId, Random random) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.assignedPlayerId = Objects.requireNonNull(assignedPlayerId, "assignedPlayerId");
        this.unitGenerator = new TutorialUnitGenerator(Objects.requireNonNull(random, "random"));
        this.startAt = System.currentTimeMillis();
        this.currentState = new GameState(seedInitialUnits(definition.initialUnits()), startAt);
    }

    public StartGameMessage buildStartGameMessage() {
        GameStart gameStart = new GameStart(
            definition.map(),
            currentState.units(),
            startAt
        );
        return new StartGameMessage(gameStart);
    }

    public NextTurnMessage buildNextTurnMessage() {
        return new NextTurnMessage(assignedPlayerId, currentState, DEFAULT_TIME_LIMIT_MS);
    }

    public List<Message> handleActions(String playerId, Action[] actions) {
        List<Message> messages = new ArrayList<>();

        GameState previousState = currentState;

        List<String> invalidReasons = TutorialActionValidator.validate(currentState, playerId, actions);
        if (!invalidReasons.isEmpty()) {
            messages.add(new InvalidOperationMessage(playerId, invalidReasons.get(0)));
            // Continue the turn as no-op (but still progress food spawning/end criteria)
            actions = new Action[0];
        }

        if (actions.length > 0) {
            currentState = TutorialGameStatusUpdater.update(currentState, playerId, actions, unitGenerator);
        }

        // Food spawn (scheduled first, then random), after movement/collisions.
        List<Unit> updatedUnits = new ArrayList<>(Arrays.asList(currentState.units()));
        spawnScheduledFoodIfAny(updatedUnits);
        unitGenerator.maybeSpawnRandomFood(updatedUnits, definition.map(), definition.foodScarcity());
        currentState = new GameState(updatedUnits.toArray(Unit[]::new), currentState.startAt());

        deltas.add(TutorialGameDeltaFactory.get(previousState, currentState));

        boolean ended = hasTutorialEnded(currentState);
        if (ended) {
            messages.add(buildEndGameMessage());
        } else {
            messages.add(buildNextTurnMessage());
        }

        currentTurnId++;
        return messages;
    }

    private Unit[] seedInitialUnits(TutorialUnitSeed[] seeds) {
        if (seeds == null || seeds.length == 0) {
            return new Unit[0];
        }

        List<Unit> units = new ArrayList<>(seeds.length);
        for (TutorialUnitSeed seed : seeds) {
            if (seed == null) {
                continue;
            }
            units.add(new Unit(
                unitGenerator.nextId(),
                seed.owner(),
                seed.type(),
                seed.position()
            ));
        }

        return units.toArray(Unit[]::new);
    }

    private void spawnScheduledFoodIfAny(List<Unit> units) {
        Map<Integer, Position> spawn = definition.foodSpawn();
        if (spawn == null || spawn.isEmpty()) {
            return;
        }
        Position pos = spawn.get(currentTurnId);
        if (pos == null) {
            return;
        }

        // Avoid spawning food on top of an existing unit.
        for (Unit unit : units) {
            if (unit != null && unit.position().equals(pos)) {
                return;
            }
        }

        unitGenerator.spawnFoodAt(units, pos);
    }

    private boolean hasTutorialEnded(GameState gameState) {
        TutorialEndCriteria end = definition.gameEnd();
        if (end == null || end.type() == null) {
            return false;
        }

        return switch (end.type()) {
            case PLAYER_UNITS_AT_LEAST -> {
                String targetPlayer = normalizeTargetPlayerId(end.playerId());
                int minUnits = end.minUnits() != null ? end.minUnits() : 0;
                int count = countUnitsOwnedBy(gameState, targetPlayer);
                yield count >= minUnits;
            }
            case ANY_PLAYER_UNIT_IN_RECT -> {
                Position c1 = end.corner1();
                Position c2 = end.corner2();
                yield isAnyOwnedUnitInRect(gameState, assignedPlayerId, c1, c2);
            }
        };
    }

    private String normalizeTargetPlayerId(String configured) {
        if (configured == null || configured.isBlank() || SELF.equalsIgnoreCase(configured)) {
            return assignedPlayerId;
        }
        return configured;
    }

    private int countUnitsOwnedBy(GameState gameState, String owner) {
        if (gameState == null || gameState.units() == null || owner == null) {
            return 0;
        }
        int count = 0;
        for (Unit unit : gameState.units()) {
            if (unit != null && owner.equals(unit.owner())) {
                count++;
            }
        }
        return count;
    }

    private boolean isAnyOwnedUnitInRect(GameState gameState, String owner, Position corner1, Position corner2) {
        if (gameState == null || gameState.units() == null || owner == null || corner1 == null || corner2 == null) {
            return false;
        }

        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minY = Math.min(corner1.y(), corner2.y());
        int maxY = Math.max(corner1.y(), corner2.y());

        for (Unit unit : gameState.units()) {
            if (unit == null || unit.owner() == null || !owner.equals(unit.owner())) {
                continue;
            }
            Position p = unit.position();
            if (p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY) {
                return true;
            }
        }

        return false;
    }

    private EndGameMessage buildEndGameMessage() {
        GameEnd gameEnd = new GameEnd(
            definition.map(),
            deltas.toArray(GameDelta[]::new),
            assignedPlayerId,
            System.currentTimeMillis()
        );
        return new EndGameMessage(gameEnd);
    }

    public Unit findPlayerBase() {
        if (currentState == null || currentState.units() == null) {
            return null;
        }
        for (Unit unit : currentState.units()) {
            if (unit != null && BASE.equals(unit.type()) && assignedPlayerId.equals(unit.owner())) {
                return unit;
            }
        }
        return null;
    }
}
