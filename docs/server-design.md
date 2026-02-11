# Authoritative Game Server Design

This document describes a game server for a turn-based game. The server is responsible to initialise the `GameEngine` and connect it to the `CommManager`.
All game logic, state management, timeout enforcement, and turn orchestration are delegated to the `GameEngine`. 
The `CommManager` accepts client connections, broadcasts messages, and forwards game events. 
Communications follow a fire-and-forget notification model.

## Goals

- A thin wrapper around the `GameEngine` and `CommManager`.
- Support exactly one game per server process.
- Server is stateless (except for client connections); all game state owned by `GameEngine`.
- Transport-agnostic, fire-and-forget messaging model (supports TCP, WebSocket, UDP, message broker, etc.).
- The `GameEngine` owns game logic, timeout enforcement, state progression, and authoritative logging.

## Non-Functional Requirements

- **Single-game run** per server lifecycle.
- **No persistent storage** in server; all state and logs produced by the `GameEngine`.
- **Determinism**: `GameEngine` must produce deterministic results for identical inputs.
- **Fire-and-forget semantics**: messages are notifications; clients do not await synchronous responses.
- **Game parameters** (turnTimeLimit, mapConfig, etc.) passed to engine at initialization as `GameParams`.

## High-Level Architecture

The server consists of thin communication and orchestration layers that delegate all game decision-making to the GameEngine:

### Responsibilities

- Create all the required connections between `GameEngine` and `CommManager`;
  - `CommManager.onClientConnected(...)` -> `GameEngine.addPlayer (playerId)`
  - `CommManager.onClientDisconnected(...)` -> `GameEngine.removePlayer (playerId)`
  - `NextTurnHandler.handleNextTurn(playerId, GameState)` -> `CommManager.sendMessage(playerId, GameState)`
  - `GameStateUpdater.update(GameStatusUpdate)` -> `CommManager.broadcast(GameStatusUpdate)`
  - `CommManager.onClientMessage(...)` -> `GameEngine.handlePlayerActions(playerId, Action[])`
- Implements the handlers required by `GameEngine`:
  - NextTurnHandler
  - GameStateUpdater
- Implements the handlers required by `CommManager`:#
  - ClientConnectHandler
  - ClientDisconnectHandler
  - PlayerActionHandler
- Life cycle management — start/stop server, accept clients, wire components, handle graceful shutdown.
  - read `GameParams` from a config file (`game-config.json`).
  - initialises `GameEngine` with preset `GameParams` (includes map and turnTimeLimit).
  - shut down the server after broadcasting `end_game` message to the clients.
- Listen for client connections, forward registered players to GameEngine, Track connected clients, assign `playerId` (`player-1`,`player-2`...), route messages.
  - assign `playerID` (`player-1`/`player-2`) and connect them to the `GameEngine`.
  - communicate in-game messages between the players and the `GameEngine`.
  - identify players disconnects and notify `GameEngine` (`player_left`).
- Relay game events from engine to clients.
  - broadcast messages to clients (`start_game`,`end_game`)
  - send message to individual clients (`'next_turn`,`invalid_operation`)
  - accept messages from client and pass to `GameEngine` (`actions`)
  
## Failure & Edge Cases

- **Client disconnects**: Server detects and notifies `GameEngine`. Engine may decide to forfeit that player or continue with timeout.
- **Lost or delayed messages**: Transport adapter may drop or delay messages. Engine must handle missing actions gracefully (timeout and forfeit or default action per rules).
- **Malformed actions**: Engine validates incoming actions and ignores/logs invalid ones.
- **Transport latency**: Choose `turnTimeLimit` in `GameParams` to accommodate expected network conditions, or use a reliable/ordered transport adapter.
- **Server crash**: No persistence — restart creates a new game instance.

## Security & Observability

- **Authentication**: tokens or TLS client auth at transport layer.
- **Metrics**: expose engine processing times, turn latency, messages received, and error rates.

## Extensibility

- Transport adapters are pluggable — switch communication technology without changing `GameEngine`.
- `GameEngine` can be implemented in any language; if remote, use an adapter (IPC/gRPC/HTTP) and let the engine manage timeouts and logs.

## Tests

- Server shuts down after broadcasting to connected clients upon `end_game` message.
- Server broadcasts `start_game`.
- Server communicate `next_turn` and `invalid_operation` to the client and `actions` to the engine.
