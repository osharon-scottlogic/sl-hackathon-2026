# Implementation Plan

## Project Overview

Two separate Java projects:

- **Server**: Authoritative game engine, communication manager, WebSocket endpoint
- **Client**: Bot orchestrator, action planner, server API wrapper

---

## Sprint 1: Foundation & Data Models (Week 1)

**Goal**: Establish shared data structures, serialization, and basic project scaffolding.

### Sprint 1 Server Tasks

- [ ] **S1.1**: Create Gradle project structure with dependencies (Jackson, SLF4J, Logback, JUnit, Mockito, javax.websocket)
- [ ] **S1.2**: Implement DTOs and records (as Java record objects):
  - `UnitType` (ENUM: BASE, FOOD, PAWN)
  - `Position` (int x, int y)
  - `Dimension` (int width, int height)
  - `MapLayout` (Dimension dimension, Position[] walls)
  - `MapConfig` (extends MapLayout, Position[] potentialBaseLocations)
  - `GameParams` (MapConfig mapConfig, turnTimeLimit, float foodScarcity)
  - `Unit` (Sting id, String owner, UnitType type, Position position)
  - `GameState` (Units[] units, startAt timestamp)
  - `Direction` (ENUM: N, NE, E, SE, S, SW, W, NW)
  - `Action` (string unitId, Direction direction)
  - `GameStatus` (ENUM: IDLE, START, PLAYING, END)
  - `GameStatusUpdate` (GameStatus status, MapLayout map, GameState[] history, string winnerId)
- [ ] **S1.3**: Implement `MessageCodec` with Jackson polymorphic serialization:
  - Base `Message` class with `@JsonTypeInfo`
  - Subtypes: `ActionMessage`, `JoinGameMessage`, `StartGameMessage`, `NextTurnMessage`, `EndGameMessage`, `InvalidOperationMessage`
  - Unit tests: polymorphic deserialization, null handling, round-trip serialization

**Testable Goal**: All DTOs serialize/deserialize correctly; `MessageCodecTest` passes (5+ test cases).

---

### Sprint 1 Client Tasks

- [ ] **C1.1**: Create Gradle project structure with same dependencies
- [ ] **C1.2**: Implement shared DTOs (mirror from server):
  - Copy/generate identical `GameParams`, `MapLayout`, `Unit`, `GameState`, `Action`, etc.
  - Ensure consistency with server (automated sync or shared JAR)

**Testable Goal**: DTOs match server; `DirectionTest` passes; serialization round-trip works.

---

## Sprint 2: Game Engine Core (Week 2)

**Goal**: Implement GameEngine state management, validation, and collision detection.

### Sprint 2 Server Tasks

- [ ] **S2.1**: Implement `ActionValidator` interface and validation logic:
  - `InvalidAction isValid(GameState, Action[])` record with action, reason, state
  - Validate: pawn ID exists, pawn belongs to player, direction is valid
  - Unit tests: valid/invalid actions, missing units, wrong owner

- [ ] **S2.2**: Implement `GameStateUpdater` component:
  - `GameState update(GameState, Action[])` — apply all actions, resolve collisions
  - `boolean hasGameEnded(GameState)` — check win conditions
  - Collision logic:
    - Enemy pawns on same tile → both die
    - Friendly pawns on same tile → survive
    - Pawn + food → food consumed, pawn survives
    - Pawn + enemy base → pawn dies, base destroyed, game ends
  - Unit tests: all collision scenarios (10+ test cases)

- [ ] **S2.3**: Implement `GameEngine` interface and basic orchestration:
  - `addPlayer(playerId)`, `removePlayer(playerId)`
  - `initialize(GameParams)` — create map, spawn initial pawns, return initial GameState
  - `getGameState()` — return current state snapshot
  - `handlePlayerActions(playerId, Action[])` — validate, update, check end conditions
  - Maintain mutable GameState history, player list, current player indicator
  - Unit tests: initialization, state progression, player management (8+ test cases)

- [ ] **S2.4**: Implement handler interfaces (no-op stubs for now):
  - `NextTurnHandler` interface with `handleNextTurn(playerId, GameState)`
  - `StatusUpdateHandler` interface with `handleStatusUpdate(GameStatusUpdate)`

**Testable Goal**: `GameEngineTest`, `GameStateUpdaterTest`, `ActionValidatorTest` all pass (20+ test cases total). Game state progresses correctly through turns.

---

### Sprint 2 Client Tasks

- [ ] **C2.1**: Implement `HelperTools` utility functions (stateless):
  - **Pathfinding**:
    - `findShortestPath(MapLayout, Position, Position): List<Direction>` (BFS)
    - `findPathAvoiding(MapLayout, Position, Position, Set<Position>): List<Direction>`
    - `getReachablePositions(MapLayout, Position, int): Set<Position>`
  - **Proximity**:
    - `findClosestFood(GameState, Position): Optional<Position>`
    - `findClosestEnemy(GameState, Position): Optional<Pawn>`
    - `distanceTo(Position, Position, MapLayout): int`
  - Unit tests: pathfinding with walls, unreachable goals, proximity calculations (10+ test cases)

- [ ] **C2.2**: Implement collision prediction helper:
  - `CollisionPrediction record: pawnWillDie (Map<String, Boolean>), foodWillBeConsumed (Set<Position>), baseWillBeLost (boolean)`
  - `predictCollisions(GameState, List<Action>): CollisionPrediction` — simulate turn without modifying state
  - Unit tests: collision scenarios match server logic (5+ test cases)

- [ ] **C2.3**: Implement additional `HelperTools` functions:
  - `findAllEnemiesWithinDistance(GameState, Position, int): List<Pawn>`
  - `isPositionSafe(GameState, Position): boolean`
  - `getCentroidOfPawns(List<Pawn>): Position`
  - `generateRandomAction(GameState, Pawn): Action`
  - Unit tests: (5+ test cases)

**Testable Goal**: `HelperToolsTest` and collision prediction tests pass (15+ test cases). Pathfinding correctly navigates around walls. Collision prediction matches GameEngine logic.

---

## Sprint 3: Bot Action Planning (Week 3)

**Goal**: Implement bot decision-making logic and action planner.

### Sprint 3 Client Tasks

- [ ] **C3.1**: Implement `Bot` interface and `handleState()` method:
  - `Action[] handleState(String playerId, MapLayout mapLayout, GameState state, int timeLimitMs)`
  - Algorithm:
    1. Extract friendly/enemy pawns, food locations
    2. For each friendly pawn, generate 2-3 candidate moves (food, evade, attack, regroup)
    3. Use `HelperTools.predictCollisions()` to filter suicidal actions
    4. Return merged action set
  - Stateless except for lightweight metadata (playerId, lastTurnId)
  - Respect `timeLimitMs` budget; return partial actions if timeout approaching
  - Unit tests: single pawn movement, multi-pawn coordination, timeout safety (8+ test cases)

- [ ] **C3.2**: Implement helper decision functions within Bot:
  - `selectBestMoveForFood(Pawn, GameState, timeLimitMs): Action` — pathfind to nearest food
  - `selectEvadeMove(Pawn, GameState): Action` — flee from adjacent enemies
  - `selectAggressiveMove(Pawn, GameState): Action` — move toward enemy base or closest enemy
  - `selectGroupingMove(Pawn, GameState): Action` — move toward friendly centroid
  - Unit tests: each strategy (5+ test cases)

- [ ] **C3.3**: Implement safe fallback strategies:
  - `generateSafeFallbackAction(Pawn, GameState): Action` — random or no-op if no safe move
  - Use `HelperTools.generateRandomAction()` as last resort
  - Unit tests: blocked pawns, edge cases (3+ test cases)

**Testable Goal**: `BotTest` and strategy tests pass (15+ test cases). Bot returns valid actions within time budget. Collision avoidance prevents suicidal moves.

---

## Sprint 4: Communication Infrastructure (Week 2)

**Goal**: Implement WebSocket transport, client registry, and message routing.

### Sprint 4 Server Tasks

- [ ] **S4.1**: Implement `ClientHandler` (per-connection wrapper):
  - Wraps `javax.websocket.Session`
  - Maintains unique client ID (UUID)
  - `send(String jsonMessage)` — transmit to WebSocket (catch IOException)
  - `handleMessage(String json)` — deserialize via `MessageCodec`, forward to callbacks
  - `close()` — graceful shutdown
  - Unit tests: message send/receive, error handling (4+ test cases)

- [ ] **S4.2**: Implement `ClientRegistry` (player tracking & routing):
  - `register(ClientHandler): String playerId` — assign player-1 or player-2
  - `isReady(): boolean` — check if both players connected
  - `broadcast(Message)` — send to all clients
  - `send(playerId, Message)` — send to specific player
  - `unregister(clientId)` — remove on disconnect
  - ConcurrentHashMap for thread safety
  - Unit tests: registration flow, broadcast, routing, limits (5+ test cases)

- [ ] **S4.3**: Implement `WebSocketAdapter` (@ServerEndpoint("/game")):
  - Lifecycle: `onOpen`, `onMessage`, `onClose`, `onError`
  - Static setters to inject GameServer references
  - Delegate to `ClientRegistry` for connection mgmt
  - Delegate to callback handlers for message routing
  - Unit tests: endpoint lifecycle, message dispatch (4+ test cases)

- [ ] **S4.4**: Implement server container startup (Tyrus or embedded WebSocket):
  - `ServerContainer` configuration on port 8080
  - Register `WebSocketAdapter` with container
  - Graceful shutdown on Ctrl+C (Runtime.addShutdownHook)

**Testable Goal**: `ClientRegistryTest`, `ClientHandlerTest`, `WebSocketAdapterTest` pass (13+ test cases). Two clients can connect and receive messages. Registry enforces 2-player limit.

---

### Sprint 4 Client Tasks

- [ ] **C4.1**: Implement `ServerAPI` interface (communication wrapper):
  - `connect(String serverURL)` — establish WebSocket connection
  - `send(Action[] actions)` — serialize and transmit actions to server
  - `onReceive(Message)` — callback for incoming messages (gameStart, nextTurn, gameEnd, invalidOperation)
  - Use `MessageCodec` for serialization
  - Maintain connection state
  - Unit tests: connection lifecycle, message send/receive, error handling (5+ test cases)

- [ ] **C4.2**: Implement WebSocket client transport:
  - Use `javax.websocket.ClientEndpoint` or standard WebSocket client library (Tyrus client or similar)
  - `connect(URI)` — establish client-side WebSocket connection
  - `send(String jsonMessage)` — transmit over WebSocket
  - `close()` — graceful disconnect
  - Listener for server messages (onMessage, onClose, onError)

- [ ] **C4.3**: Implement message handlers in `ServerAPI`:
  - `onGameStart(StartGameMessage)` — extract MapLayout, initial GameState
  - `onNextTurn(NextTurnMessage)` — extract turnId, GameState, timeLimitMs
  - `onGameEnd(EndGameMessage)` — extract GameLog, winner
  - `onInvalidOperation(InvalidOperationMessage)` — log error, optionally retry or abort

**Testable Goal**: `ServerAPITest` passes (5+ test cases). Client can connect to server, send/receive messages. Message deserialization handles all types correctly.

---

## Sprint 5: Game Session Orchestration (Week 3)

**Goal**: Wire GameEngine with CommManager; implement turn loop and game orchestration.

### Sprint 5 Server Tasks

- [ ] **S5.1**: Implement `GameSession` (game orchestration loop):
  - Implements `Runnable` (executes in background thread)
  - Waits for two players via `ClientRegistry.isReady()`
  - Creates `GameParams` with mapConfig, turnTimeLimit, gameId
  - Calls `gameEngine.initialize(gameParams)` at startup
  - Main loop:
    1. Engine signals turn start; broadcast `NextTurnMessage(turnId, timeLimitMs)`
    2. Client actions arrive via `submitAction(playerId, turnId, action)` (non-blocking, async)
    3. Call `gameEngine.processTurn(turnId, timeLimitMs)` — engine enforces timeout, returns TurnResult
    4. Broadcast state updates (optional)
    5. Check `gameEngine.isGameOver()` for termination
  - On game end, fetch `gameEngine.getGameLog()`, broadcast `EndGameMessage`
  - `shutdown()` flag for graceful termination
  - Unit tests: turn progression, timeout handling, game end detection (6+ test cases)

- [ ] **S5.2**: Implement `GameServer` (main orchestrator & wiring):
  - Constructor: `GameServer(ServerConfig config, GameEngine engine)`
  - Wire handler connections:
    - `ClientRegistry.onClientConnect()` → `GameEngine.addPlayer(playerId)`
    - `ClientRegistry.onClientDisconnect()` → `GameEngine.removePlayer(playerId)`
    - `GameEngine.NextTurnHandler` → `CommManager.send(playerId, GameState)`
    - `GameEngine.StatusUpdateHandler` → `CommManager.broadcast(GameStatusUpdate)`
    - `CommManager.onClientAction()` → `GameEngine.handlePlayerActions(playerId, Action[])`
  - `start()` — initialize WebSocket server, start GameSession thread
  - `stop()` — shut down GameSession, close WebSocket server
  - Unit tests: component wiring, lifecycle (4+ test cases)

- [ ] **S5.3**: Implement `ServerConfig` (configuration object):
  - Fields: port (1024–65535, default 8080), mapConfig, turnTimeLimit
  - `validate()` — throw IllegalArgumentException if invalid
  - Optionally load from `game-config.json` or environment variables
  - Unit tests: validation, defaults, bounds (3+ test cases)

- [ ] **S5.4**: Implement `Main` entry point:
  - Create `ServerConfig` with test mapConfig, turnTimeLimit = 15000ms, port = 8080
  - Instantiate `GameEngine` (use GameEngineStub or custom)
  - Create `GameServer` with config + engine
  - Call `start()`
  - Add shutdown hook for graceful termination
  - Logging via SLF4J

**Testable Goal**: `GameServerIntegrationTest`, `GameSessionTest` pass (10+ test cases). Full game flow works: 2 clients connect → game starts → turns progress → game ends. Timeout enforcement verified.

---

## Sprint 6: Client Orchestrator (Week 2)

**Goal**: Implement client-side game orchestration and bot integration.

### Sprint 6 Client Tasks

- [ ] **C6.1**: Implement `Orchestrator` (main client controller):
  - `init(ServerAPI server, Bot bot, String playerId)` — wire dependencies
  - Maintains: playerId, MapLayout, current GameState, game history
  - Callbacks from ServerAPI:
    - `onGameStart(MapLayout, GameState)` — store MapLayout, initialize game state
    - `onNextTurn(turnId, GameState, timeLimitMs)` — call `bot.handleState()`, send actions to server
    - `onGameEnd(GameLog, winner)` — write GameLog to file, cleanup
  - `handleNextTurn()` internally:
    1. Call `bot.handleState(playerId, mapLayout, gameState, timeLimitMs - elapsed)`
    2. Measure elapsed time; enforce fallback if timeout approaching
    3. Send actions via `serverAPI.send(actions)`
    4. Update local game state (optional optimistic update)
  - Unit tests: game lifecycle, bot integration, timeout handling (6+ test cases)

- [ ] **C6.2**: Implement timeout and fallback handling:
  - Orchestrator enforces client-side timeout: if bot.handleState() exceeds `timeLimitMs - buffer` (buffer = 1000ms), interrupt and return fallback actions
  - Fallback: empty list or random actions for all pawns via `HelperTools.generateRandomAction()`
  - Unit tests: timeout detection, fallback actions (3+ test cases)

- [ ] **C6.3**: Implement game log file writing:
  - `writeGameLog(GameLog log)` — serialize GameLog to JSON file in `./game-logs/` directory with timestamp filename
  - Handle file I/O errors gracefully (log warning, continue)
  - Unit tests: file creation, JSON format (2+ test cases)

- [ ] **C6.4**: Implement `Main` entry point:
  - Accept server URL via command-line argument or environment variable (default `ws://localhost:8080/game`)
  - Instantiate `ServerAPI`, `Bot` (default or custom), `Orchestrator`
  - Call `serverAPI.connect()` and `orchestrator.init()`
  - Main thread blocks; background handlers process server messages
  - Graceful shutdown on Ctrl+C (cleanup, close connection)

**Testable Goal**: `OrchestratorTest` passes (6+ test cases). Client can initialize, receive game start, execute turns, handle timeout, write logs. Full client-server integration works (2+ integration test cases).

---

## Sprint 7: Timeout Enforcement & Resilience (Week 2)

**Goal**: Implement timeout mechanisms, error recovery, and edge case handling.

### Sprint 7 Server Tasks

- [ ] **S7.1**: Implement timeout enforcement in `GameEngine.processTurn()`:
  - Use `BlockingQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)` to wait for player actions
  - If timeout expires before actions received, declare player forfeit (lost that turn or game ends)
  - Log timeout event in GameLog
  - Unit tests: timeout triggers, action buffer cleared, forfeit logic (4+ test cases)

- [ ] **S7.2**: Implement error recovery in `GameSession`:
  - Handle `GameEngine.processTurn()` exceptions gracefully (log, signal forfeit)
  - Handle client disconnects mid-game (notify GameEngine, trigger forfeit)
  - Graceful cleanup without crashing the server
  - Unit tests: error scenarios, recovery (3+ test cases)

- [ ] **S7.3**: Implement validation in `GameEngine.handlePlayerActions()`:
  - Reject actions if not player's turn (log, send InvalidOperationMessage)
  - Reject actions if turn ID mismatches (log, send error)
  - Reject malformed actions (missing pawn ID, invalid direction)
  - Unit tests: validation edge cases (4+ test cases)

**Testable Goal**: `TimeoutTest`, `ErrorRecoveryTest` pass (11+ test cases). Timeouts trigger correctly; invalid actions rejected; server remains stable under error conditions.

---

### Sprint 7 Client Tasks

- [ ] **C7.1**: Implement connection error handling in `ServerAPI`:
  - Detect WebSocket close/error events
  - Notify `Orchestrator` of connection loss
  - Attempt graceful shutdown (don't hang indefinitely)
  - Unit tests: connection loss, error signals (3+ test cases)

- [ ] **C7.2**: Implement bot timeout safeguard in `Orchestrator`:
  - Use `Thread.interrupt()` or `ExecutorService` with timeout to enforce hard deadline on `bot.handleState()`
  - If bot doesn't return by deadline, cancel and use fallback actions
  - Unit tests: bot timeout, fallback actions (2+ test cases)

- [ ] **C7.3**: Implement retry logic for failed sends:
  - If `serverAPI.send()` fails (network error), optionally retry (max 3 times)
  - Log failures; abort if retries exhausted
  - Unit tests: send failure, retry logic (2+ test cases)

**Testable Goal**: `ConnectionErrorTest`, `BotTimeoutTest` pass (7+ test cases). Client remains stable under network errors and bot timeouts.

---

## Sprint 8: Integration Testing & Deployment (Week 2)

**Goal**: Full end-to-end testing, performance validation, and deployment readiness.

### Sprint 8 Server Tasks

- [ ] **S8.1**: Implement `GameEngineStub` (reference implementation for testing):
  - Deterministic game logic (seeded Random for reproducibility)
  - Simple turn-based progression: 2 players, 5 turns, then game ends
  - Logs all turns to GameLog
  - Used for integration tests without real bot overhead
  - Unit tests: game progression, determinism (2+ test cases)

- [ ] **S8.2**: Create end-to-end integration tests:
  - **ServerIntegrationTest**:
    - Two clients connect → game starts → 3 turns complete → game ends
    - Verify message sequence and state consistency
    - (3+ test cases)
  - **TimeoutIntegrationTest**:
    - Client delays response beyond timeout → server declares forfeit
    - (2+ test cases)
  - **DisconnectIntegrationTest**:
    - Client disconnects mid-game → server handles gracefully
    - (2+ test cases)

- [ ] **S8.3**: Performance & stress tests:
  - **LargeMapTest**: game on 100×100 map with 50 pawns; verify pathfinding completes in <5s per turn
  - **ConcurrencyTest**: rapid action submissions; verify no race conditions or deadlocks
  - (2+ test cases)

- [ ] **S8.4**: Deployment packaging:
  - `gradle build` produces executable JAR with all dependencies
  - README with deployment instructions (port configuration, mapConfig, turnTimeLimit)
  - Docker support (optional): Dockerfile, docker-compose.yml

**Testable Goal**: `ServerIntegrationTest` and all E2E tests pass (9+ test cases). Server runs stable for full game.

---

### Sprint 8 Client Tasks

- [ ] **C8.1**: Create end-to-end integration tests:
  - **ClientIntegrationTest**:
    - Client connects to running server → receives gameStart → sends actions → receives nextTurn → game ends
    - Verify game log written to file
    - (3+ test cases)
  - **BotIntegrationTest**:
    - Bot makes strategic decisions: collects food, avoids enemies, progresses toward win
    - (2+ test cases)

- [ ] **C8.2**: Performance & stress tests:
  - **BotPerformanceTest**: bot.handleState() completes within 10s on large maps with many pawns
  - (1+ test case)

- [ ] **C8.3**: Deployment packaging:
  - `gradle build` produces executable JAR with dependencies
  - README with usage instructions (server URL, bot configuration)
  - Docker support (optional)

- [ ] **C8.4**: Game log validation:
  - Verify game logs are written correctly after each game
  - Logs contain all turns, actions, winner, timestamps
  - Logs are parseable JSON
  - (2+ test cases)

**Testable Goal**: `ClientIntegrationTest` and log validation tests pass (8+ test cases). Client runs through full game and writes valid logs.

---

## Sprint 9: Polish & Optimization (Week 2)

**Goal**: Code quality, documentation, performance tuning, and final testing.

### Both Projects

- [ ] **Refactoring & Code Quality**:
  - Code review, consolidate duplicate logic
  - Ensure naming consistency across projects (e.g., `playerId`, `pawnId`, `unitId`)
  - Add Javadoc to public APIs
  - Fix any code smells identified in prior sprints

- [ ] **Logging & Observability**:
  - Add structured logging (SLF4J + Logback) to all components
  - Log key events: game start, turn progression, timeouts, errors
  - Enable debug logging via configuration
  - Unit tests: verify log output (2+ test cases)

- [ ] **Performance Optimization**:
  - Profile pathfinding (BFS vs A*); optimize if needed
  - Cache walkability grid in MapLayout for repeated queries
  - Benchmark bot decision-making; optimize if >5s on typical maps
  - (Benchmark report)

- [ ] **Documentation**:
  - Update design docs if implementation deviated
  - Add implementation notes (build, run, test)
  - Add troubleshooting guide
  - Add architecture diagrams (as ASCII art or links)

### Server

- [ ] **S9.1**: Final integration test with real client:
  - Run server + client in separate JVMs
  - Verify full game execution
  - (1+ end-to-end test)

- [ ] **S9.2**: Load testing:
  - Simulate multiple sequential games (stress server lifecycle)
  - Verify no resource leaks (threads, memory)
  - (1+ stress test)

### Client

- [ ] **C9.1**: Final integration test with real server:
  - Connect to running server; play multiple games
  - Verify bot behavior consistency
  - (1+ end-to-end test)

- [ ] **C9.2**: Bot strategy evaluation:
  - Run bot against GameEngineStub; measure win rate, pawn count, food collected
  - Document bot performance characteristics
  - (Strategy report)

**Testable Goal**: All code passes checkstyle/linting. Documentation complete. Final E2E tests pass. Performance benchmarks show acceptable performance (<10s per turn on typical maps).

---

## Test Summary

| Component | Unit Tests | Integration Tests | Stress Tests | Total |
|-----------|------------|-------------------|--------------|-------|
| **Server** | 65+ | 9+ | 2+ | **76+** |
| **Client** | 45+ | 8+ | 1+ | **54+** |
| **Shared** | - | - | - | - |
| **Total** | **110+** | **17+** | **3+** | **130+** |

---

## Key Milestones

1. **End of Sprint 2**: GameEngine core functional; state progression and collision detection working.
2. **End of Sprint 3**: Bot making decisions; pathfinding and collision avoidance in place.
3. **End of Sprint 5**: Full server orchestration; two clients can connect and play a game.
4. **End of Sprint 6**: Client fully integrated; bot and orchestrator working end-to-end.
5. **End of Sprint 8**: All integration tests passing; deployment ready.
6. **End of Sprint 9**: Production-ready; all documentation complete; performance validated.

---

## Definition of Done (Per Task)

✓ Code written and peer-reviewed  
✓ Unit tests written and passing (minimum 85% code coverage for new code)  
✓ Integration tests passing (if applicable)  
✓ Javadoc added to public APIs  
✓ No compiler warnings  
✓ Logging added for key operations  
✓ Committed to version control with clear commit messages  

---

## Risk Mitigation

| Risk                           | Mitigation                                                               |
|--------------------------------|--------------------------------------------------------------------------|
| Pathfinding performance        | Use A* heuristic; benchmark early (Sprint 2); cache results              |
| Timeout enforcement complexity | Prototype with BlockingQueue early; test thoroughly (Sprint 7)           |
| Client-server message sync     | Use turnId consistently; add validation (Sprint 5)                       |
| Concurrency bugs               | Use ConcurrentHashMap; minimize shared state; test with thread sanitizer |
| Integration complexity         | Mock GameEngine in early client tests; integrate server/client late (Sprint 5) |
