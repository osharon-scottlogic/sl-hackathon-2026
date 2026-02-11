package sl.hackathon.client.tutorial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.client.dtos.*;
import sl.hackathon.client.messages.*;

import java.util.*;

import static sl.hackathon.client.dtos.UnitType.BASE;
import static sl.hackathon.client.util.Ansi.green;

public final class TutorialEngine {
    private static final Logger logger = LoggerFactory.getLogger(TutorialEngine.class);

    private static final int DEFAULT_TIME_LIMIT_MS = 5000;
    private static final String SELF = "SELF";
    private static final String TUTORIAL_OPPONENT = "TUTORIAL";

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

        TutorialEndCriteria end = definition.gameEnd();
        logger.info(
            green("Tutorial end criteria: type={}, playerId={}, minUnits={}, corner1={}, corner2={}, maxTurns={}"),
            end != null ? end.type() : null,
            end != null ? end.playerId() : null,
            end != null ? end.minUnits() : null,
            end != null ? end.corner1() : null,
            end != null ? end.corner2() : null,
            end != null ? end.maxTurns() : null
        );
    }

    public StartGameMessage buildStartGameMessage() {
        GameStart gameStart = new GameStart(
            definition.map(),
            currentState.units(),
            startAt
        );
        return new StartGameMessage(gameStart, null, startAt);
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

        TutorialOutcome outcome = evaluateTutorialOutcome(currentState);
        if (outcome.ended()) {
            messages.add(buildEndGameMessage(outcome.winnerId()));
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

    private TutorialOutcome evaluateTutorialOutcome(GameState gameState) {
        TutorialEndCriteria end = definition.gameEnd();
        if (end == null || end.type() == null) {
            return TutorialOutcome.continueGame();
        }

        boolean goalAchieved = switch (end.type()) {
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

        if (goalAchieved) {
            return TutorialOutcome.ended(assignedPlayerId);
        }

        int maxTurns = end.maxTurns() != null ? end.maxTurns() : 0;
        if (maxTurns > 0 && (currentTurnId + 1) >= maxTurns) {
            return TutorialOutcome.ended(TUTORIAL_OPPONENT);
        }

        return TutorialOutcome.continueGame();
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

    private EndGameMessage buildEndGameMessage(String winnerId) {
        GameEnd gameEnd = new GameEnd(
            definition.map(),
            deltas.toArray(GameDelta[]::new),
            winnerId,
            System.currentTimeMillis()
        );
        return new EndGameMessage(gameEnd);
    }

    private record TutorialOutcome(boolean ended, String winnerId) {
        static TutorialOutcome continueGame() {
            return new TutorialOutcome(false, null);
        }

        static TutorialOutcome ended(String winnerId) {
            return new TutorialOutcome(true, winnerId);
        }
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
