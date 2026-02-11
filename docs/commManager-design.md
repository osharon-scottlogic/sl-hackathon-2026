# CommManager Design

This document describes a transport- and language-agnostic communication manager for a turn-based game
It accepts client connections, broadcasts messages, and forwards game events.
Communications follow a fire-and-forget notification model.

## Goals

- Transport-agnostic, fire-and-forget messaging model (supports TCP, WebSocket, UDP, message broker, etc.).

## Non-Functional Requirements

- **Fire-and-forget semantics**: messages are notifications; clients do not await synchronous responses.

## High-Level Architecture & Components

The CommManager has 3 components:

- ClientConnectionMap
- ClientConnectHandler (delegate)
- ClientDisconnectHandler (delegate)
- ClientActionHandler (delegate)

### CommManager interface

- sendMessage(String playerId, GameState gameState)
- broadcast(GameStatusUpdate gameStatusUpdate)
- setClientConnectHandler(clientConnectHandler)
- setClientDisconnectHandler(clientDisconnectHandler)
- setClientActionHandler(clientActionHandler)

#### ClientConnectHandler interface

- handleClientConnect(string playerId)

#### ClientDisconnectHandler interface

- handleClientDisconnect(string playerId)

#### ClientActionHandler interface

- handleClientAction(string playerId, Action[] actions)

### Responsibilities

- Abstract the underlying communication mechanism (WebSocket, TCP, etc.).
  - `sendMessage(playerId, message)` — send a message to a specific client.
  - `broadcast(message)` — send a message to all connected clients.
  - `onClientConnected(handler)` — callback when a client connects.
  - `onClientDisconnected(playerId)` — callback when a client disconnects.
- Listen for client connections, forward registered players to GameEngine, Track connected clients, assign `playerId` (`player-1`,`player-2`...), route messages.
  - assign `playerID` (`player-1`/`player-2`) and connect them to the `GameEngine`.
  - communicate in-game messages between the players and the `GameEngine`.
  - identify players disconnects and notify `GameEngine` (`player_left`).
- Relay game events from engine to clients.
  - broadcast messages to clients (`start_game`,`end_game`)
  - send message to individual clients (`'next_turn`,`invalid_operation`)
  - accept messages from client and pass to `GameEngine` (`actions`)
  
## Failure & Edge Cases

- **Client disconnects**: Server detects and notifies `GameEngine`.
- **Lost or delayed messages**: Transport adapter may drop or delay messages. Engine must handle missing actions gracefully (timeout and forfeit or default action per rules).
- **Malformed actions**: Engine validates incoming actions and ignores/logs invalid ones.
- **Transport latency**: Choose `turnTimeLimit` in `GameParams` to accommodate expected network conditions, or use a reliable/ordered transport adapter.
- **Server crash**: No persistence — restart creates a new game instance.

## Transport Recommendations

- Development: TCP or WebSocket with structured messages (JSON/protobuf).
- Low-latency/unreliable networks: UDP or customized overlay with sequence numbers, but ensure engine handles omissions.
- Scalability: use a broker (Kafka/RabbitMQ) to decouple clients from the server; server subscribes to client action topics.

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
