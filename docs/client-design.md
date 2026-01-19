# Client — Technical Design Document

## Overview

The client application is a thin communication wrapper around a `bot` that orchestrates game logic and decision-making. The client initializes with a server URL, connects, waits for the server to send game-start parameters, and then delegates all game state management and action generation to the `bot`. When the `bot` has produced a list of actions, it passes them back to the client, which immediately returns them as the response to the server.

## Architecture

The client has three main layers:

1. **Orchestrator** (`client`)
   - Initializes and holds the `bot`.
   - Hold game's static information (e.g. `MapLayout`)
   - Holds and updates game state (map, pawns, game rules).
   - Receives method calls (e.g., `gameStart`, `nextTurn`).
   - Waits for `bot` to produce `Action[]`.
   - Send `Action[]` to server.

2. **Transport Layer** (`ServerAPI`)
   - Handles communication with the server.
   - Receives and dispatches call events from the server.
   - Sends responses (actions) back to the server.
   - Long-lived connection; blocks on incoming calls.

3. **Game Logic Layer** (`bot`)
   - Receives `gameStart` parameters (map config, initial state).
   - Receives `nextTurn` notifications (current game state, active player).
   - Produces action lists (pawn movements) on demand.
   - May use AI, heuristics, or human input to decide actions (pluggable strategy).

## Components

### ServerAPI

- connect (String serverURL)
- send (Action[] actions)
- onReceive (GameState state | GameStatusUpdate statusUpdate)

Responsibilities:

- Establish and maintain a connection to the server (e.g. TCP socket or HTTP/WebSocket if using a standard RPC library).
- Send and receive messages.
- Handle serialization/deserialization (e.g. Jackson or similar).
- Detect connection loss and signal errors to `client`.

### Orchestrator

- init(ServerApi server, Bot bot)

Responsibilities:

- user ServerAPI to connect to server upon load
- maintain static game information: playerId, MapLayout
- pass game state to `bot`
- pass `Action[]` from game bot to server
- when game ends, write the game log to a file

### Bot

see `[bot-design.md](docs/bot-design.md)`.

## Suggested Tests

### Unit Tests

- **RpcClientTest**
  - Test connection establishment and disconnection.
  - Test send/receive of JSON-RPC messages.
  - Test connection loss handling.

- **GameClientTest**
  - Test `onGameStart()` correctly initializes game state.
  - Test `onNextTurn()` calls `bot.handleState()`.
  - Test `onNextTurn()` waits for and returns actions from `bot`.
  - Test timeout handling: if `bot` takes too long, return fallback actions.
  - Test `onGameEnd()` triggers cleanup.

- **botTest**
  - Test `onGameStart()` stores game config.
  - Test `onNextTurn()` updates game state.
  - Test `getActions()` returns empty list when it's opponent's turn.
  - Test `getActions()` calls `ActionPlanner` when it's this client's turn.

- **ActionPlannerTest** (example: RandomActionPlanner)
  - Test planner returns actions for all friendly pawns.
  - Test directions are valid (N, NE, E, etc.).
  - Test timeout is respected (completes within timeoutMs).

---

### Integration with Server

The RPC server sends:

1. `gameStart` (initialization)
2. `nextTurn` (per-turn decision point)
3. `gameEnd` (termination)

The client responds with:

1. RPC response: ack/null
2. RPC response: { turnId, actions[] }
3. RPC response: ack/null

`GameParam` includes `turnTimeoutLimit` value. The server waits up to [`turnTimeoutLimit`] (seconds) for each `turnState` response. If the client times out, the server declares a loss and ends the game.

---

## Deployment & Runtime

- **Single-instance**: Each client connects to one game server.
- **Multiple games**: To run multiple clients (e.g., local testing), spawn separate JVM instances with different server URLs.
- **Containerization**: Package client as Docker image; inject server URL via environment variable or command-line argument.
