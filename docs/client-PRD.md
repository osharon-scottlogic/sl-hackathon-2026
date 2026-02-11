# Client PRD (Cross‑Language)

## Purpose

Define a **language-agnostic client** that can connect to the existing game server, participate in a full game, and be **re-implemented in other languages** with identical behavior at the protocol boundary.

This document is intended to be used as the single source of requirements to generate a new client implementation per language in one pass (runtime + unit tests + acceptance tests).

## Goals

- Connect to the server over WebSocket and speak the **current JSON message protocol**.
- Maintain client-local session state (assigned playerId, map layout, last known game state).
- On each turn, invoke a bot strategy to produce actions within the server turn time limit.
- Persist a game log artifact after the game ends.
- Provide:
  - **Unit tests** for codec/routing/orchestration/bot timeouts.
  - **Acceptance tests** that start a real server, start two clients, and complete a simulated game end-to-end.

## Non‑Goals

- No UI.
- No matchmaking, reconnection, or multi-game sessions per process.
- No server changes required (client must interoperate with the current server).

## Runtime Contract

### Inputs

- Server URL: environment variable `GAME_SERVER_URL` or CLI argument.
  - Default: `ws://localhost:8080/game`.
- Optional: a human-chosen player name/id may be accepted, but **server-assigned** `playerId` is the authoritative identity.

### Outputs

- Exit code:
  - `0` when game completes normally (client receives `END_GAME`).
  - Non-zero on fatal initialization/transport errors.
- Game log file written under `./game-logs/` (relative to client working directory).

## Required Components (Conceptual)

### 1) Transport

- Persistent WebSocket connection to the server.
- Supports:
  - Connect with timeout.
  - Send text messages.
  - Receive text messages (async callback/event loop).
  - Clean close.

### 2) Message Codec

- Serialize/deserialize JSON messages with a polymorphic `type` discriminator.
- MUST reject empty/blank input.
- MUST fail fast on malformed JSON.

### 3) Message Router

- Dispatch decoded messages to typed handlers:
  - `PLAYER_ASSIGNED`
  - `START_GAME`
  - `NEXT_TURN`
  - `END_GAME`
  - `INVALID_OPERATION`

### 4) Orchestrator

- Wires transport + router + bot.
- Keeps:
  - `playerId` (server assigned)
  - `mapLayout` (from `START_GAME`)
  - `currentGameState` (from `NEXT_TURN`)
- On `NEXT_TURN` for this player:
  - Invoke bot with a strict deadline.
  - Send `ACTION` message (even if empty actions).
- On `END_GAME`:
  - Write game log.
  - Shut down transport + threads and terminate.

### 5) Bot

- Pure function style interface:
  - `handleState(playerId, mapLayout, gameState, timeLimitMs) -> Action[]`
- MUST return within `timeLimitMs`.
- MUST only output actions for **friendly PAWN units**.

## Wire Protocol (Normative)

All messages are JSON objects with a required string field `type`.

### Shared Types

#### Direction

Enum string: `N | NE | E | SE | S | SW | W | NW`

#### Position

```json
{ "x": 1, "y": 2 }

#### Dimension

```json
{ "width": 20, "height": 20 }
```

#### MapLayout

```json
{
  "dimension": { "width": 20, "height": 20 },
  "walls": [ {"x": 1, "y": 2} ]
}
```

#### UnitType

Enum string: `BASE | PAWN | FOOD`

#### Unit

```json
{
  "id": 1,
  "owner": "player-1",  // null for FOOD
  "type": "PAWN",
  "position": {"x": 1, "y": 1}
}
```

Notes:

- FOOD units may have `owner: null`.

#### GameState

```json
{ "units": [/* Unit */], "startAt": 1730000000000 }
```

#### Action

```json
{ "unitId": 1, "direction": "NE" }
```

#### GameDelta

```json
{ "addedOrModified": [/* Unit */], "removed": [1,2], "timestamp": 1730000000000 }
```

### Server → Client Messages

#### PLAYER_ASSIGNED

```json
{ "type": "PLAYER_ASSIGNED", "playerId": "player-1" }
```

Client behavior:

- Store this value as the authoritative identity for the remainder of the session.

#### START_GAME

```json
{
  "type": "START_GAME",
  "gameStart": {
    "map": { /* MapLayout */ },
    "initialUnits": [ /* Unit */ ],
    "timestamp": 1730000000000
  }
}
```

Client behavior:

- Store `map` as immutable layout.
- Initialize `currentGameState` using `initialUnits`.

#### NEXT_TURN

```json
{ "type": "NEXT_TURN", "playerId": "player-1", "gameState": { /* GameState */ } }
```

Client behavior:

- If `playerId` does not match the assigned player id, ignore.
- Otherwise, compute and send actions promptly.

#### INVALID_OPERATION

```json
{ "type": "INVALID_OPERATION", "playerId": "player-1", "reason": "..." }
```

Client behavior:

- Log and continue. Client should not crash.

#### END_GAME

```json
{
  "type": "END_GAME",
  "gameEnd": {
    "map": { /* MapLayout */ },
    "deltas": [ /* GameDelta */ ],
    "winnerId": "player-1",
    "timestamp": 1730000000000
  }
}
```

Client behavior:

- Persist a game log file.
- Exit successfully.

### Client → Server Messages

#### ACTION

```json
{ "type": "ACTION", "playerId": "player-1", "actions": [ /* Action */ ] }
```

Client behavior:

- MUST send an ACTION message in response to its `NEXT_TURN`.
- If bot fails or times out, send `actions: []`.

## Bot Requirements

- Deterministic for a fixed RNG seed (recommended for tests).
- Must be safe under time constraints (hard timeout enforced by orchestrator).
- Must not throw uncaught exceptions.

## Game Log Requirements

Client writes a JSON file under `./game-logs/` with:

- `players` (array of playerIds encountered)
- `mapDimensions`
- `walls`
- `winner`
- `timestamp` (string or number)
- `deltas` (as received from server)

Exact schema may vary per language, but MUST be valid JSON and include the above fields.

## Testing Requirements

### Unit Tests (required)

1) **Message codec round-trip**

- For each message type, serialize then deserialize and assert fields.

1) **Router dispatch**

- Given each message JSON, correct handler fires once.
- Malformed JSON triggers error handler.

1) **Orchestrator turn flow**

- When receiving `NEXT_TURN` for this player, orchestrator calls bot and sends `ACTION`.
- When receiving `NEXT_TURN` for other playerId, orchestrator does not call bot.

1) **Timeout fallback**

- If bot exceeds deadline, orchestrator sends `actions: []`.

### Acceptance Tests (required, end-to-end)

Acceptance tests MUST:

1) Start the real server (Java) as a subprocess.
   - Command (Windows): `server/gradlew.bat run`
   - Command (Unix): `server/./gradlew run`
2) Wait until the server is reachable at `ws://localhost:8080/game`.
3) Start **two instances** of the generated client (same language) as subprocesses.
   - Both connect to the same server.
4) Assert:

   - Both clients receive `PLAYER_ASSIGNED` and `START_GAME`.
   - The game completes and both clients receive `END_GAME`.
   - Both client processes exit with code `0` within a global timeout (suggested: 120s).
   - Each client writes a game log file to `./game-logs/`.
5) Tear down:

   - Terminate server process if still running.

Notes:

- The acceptance harness may treat “no END_GAME within timeout” as failure.
- Tests should be robust to nondeterminism (use seeded bot if supported).

## Observed Inconsistencies / Clarifications Needed

Please confirm these, as they impact cross-language correctness:

1) **Fire-and-forget vs turn-response**

- Server-side docs describe “fire-and-forget notifications”, but the implementation is effectively request/response per `NEXT_TURN` (server waits up to `turnTimeLimit` for actions). Which model is authoritative?

If you answer these, I can tighten this PRD further so it’s unambiguous for code generation across languages.
