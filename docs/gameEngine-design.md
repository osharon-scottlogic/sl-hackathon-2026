# GameEngine — Technical Design Document

## Overview

The `GameEngine` encapsulates all game logic for a turn-based strategy game on a tile-based map. Players spawn pawns at their bases, move them around, collect food to spawn additional pawns, and win by destroying all opponent pawns or reaching the opponent's base. This document specifies the data model, rules, and component design.

### Game Rules Summary

- **Map**: rectangular grid with player units (one base per player + pawns), walls (impassable), and dynamic food units.
- **Pawns**: Each player starts with one pawn spawned at their base. Pawns move one step per turn in cardinal/diagonal directions (N, NE, E, SE, S, SW, W, NW).
- **Collisions**:
  - Two enemy pawns collide → both die.
  - Pawn collides with enemy base → pawn and base both "die" (enemy loses, collider's player wins).
  - Multiple friendly pawns can occupy the same tile; if an enemy pawn enters that tile, all pawns on that tile die.
  - Pawn collides with food → pawn survives, food is consumed; a new pawn spawns at the pawn's base on the next turn.
- **Food**: spawns at random intervals at unoccupied tiles (not occupied by pawns, bases, or walls).
- **Win conditions**: opponent has no pawns, or opponent's base is destroyed.

---

## High-Level Architecture & Components

The GameEngine has 6 components:

- GameEngine which orchastrate the different components (statefull)
- ActionValidator
- GameStateUpdater
- NextTurnHandler (delegate)
- HandlePlayerActions (delegate)
- StatusUpdateHandler (delegate)

### GameEngine interface

- Constructor accepts the GameParams
- addPlayer (playerId)
- removePlayer (playerId)
- setNextTurnHandler(nextTurnHandler)
- setStatusUpdateHandler(statusUpdateHandler)
- handlePlayerActions(playerId, Action[])

#### GameEngine component

- once adequate number of players join (by `addPlayer`), it should start the game by calling `StatusUpdateHandler.handleStatusUpdate` and `NextTurnHandler.handleNextTurn`;
- It should start a timer after the calling `handleNextTurn` and if it reaches `GameParams.turnTimeoutLimit` is should `handleStatusUpdate` and inform current player has lost
- For `handlePlayerActions`, assuming `Actionvalidator.isValid` passed, it will get a new `GameState` from `GameStateUpdater.update`, check if `GameStateUpdater.hasGameEnded` and accordingly;will issue trigger either `hansleStatusUpdate` or `handleNextTurn`;
- if user's action failed to pass `Actionvalidator.isValid` it should notify the client without resetting the timer;
- if a player send Action[] out of his turn, it will be ignored;
- if a player leaves (`removePlayer`) the game will finish through a normal `game-ended` handling;

### ActionValidator component

- InvalidAction isValid(GameState, Action[])

#### InvalidAction record

- Action action
- String reason
- GameState state

### GameStateUpdater component

- void GameState update(GameState, Action[])
- boolean hasGameEnded(GameState)

### GameParams record

- integer turnTimeoutLimit: seconds
- float foodScarcity (number between 0 to 1): likeliness of food unit appearing at any turn
- MapConfig map

#### MapLayout record

- Dimension dimension
- Position[] walls

#### MapConfig record extends MapLayout

- Position[] potentialBaseLocations

### NextTurnHandler interface

- void handleNextTurn(String playerId, GameState gameState)

#### GameState record

- unit[] units
- Timestamp startAt

##### Unit record

- String id
- String owner = playerID|undefined
- ENUM type (BASE|PAWN|FOOD)
- Position position

### StatusUpdateHandler interface

- void handleStatusUpdate(GameStatusUpdate)

#### GameStatusUpdate record

- ENUM status (START|END)
- MapLayout map
- GameState[] history
- String winner = playerId|undefined

### Action record

- String unitId
- ENUM direction (N, NE, E, SE, S, SW, W, NW)

Responsibilities:

- Hold a mutable list of `GameState` history.
- Hold a list of all players IDs and an indicator to the current player.
- Hold internal mutable state (current `GameState`, pawn list, food locations, collision results).
- Initialize map, bases, and spawn initial pawns and adds the staring `GameState` to the list.
- Process player actions each turn.
- Apply collision detection and resolution.
- Spawn food at random intervals.
- Detect game-end conditions.
- Adds the new immutable `GameState` to the list
- Return the `GameState` to clients.

## State Transitions & Turn Flow

- Once two players have connected to the server, they both receive `game-started` message;
- Then each player will get a `next-turn` message with a `GameState`, in which they'll need to reply with a list of actions;
- After a player sends their list of action, these are being processed and a `next-turn` message is sent to the next player with an updated `GameState`.
- If the updated state constitutes a `game-over` condition, both players will receive `game-ended` message and the game will finish.

### Initialization

When `initializeGame(MapConfig mapConfig)` is called:

1. Create `MapLayout` from `MapConfig` and set the bases at the potential base positions.
2. Spawn initial pawns (one per player at their base).
3. send `game-started` to both player.
4. sent `next-turn` to the first player

### Handling user's `Action[]`

- validate it is indeed the player's turn;
- validate all actions (all units belong to the user and can move);
- perform all the actions; if unit tried to move against wall or the end of the map the action is ignored;
- check for collisions (enemy and food);
- kill units collided with enemy;
- spawn new units for any food reached;
- check `game-over` conditions;
- spawn new food units (according to provided chances);

### Collision handling

- player unit colliding with a food unit - the food unit will be removed and a new player unit will be spawned at the player's base.
- two units or more with different owners will be considered 'killed' and removed from the game

### Food units spawning

- Food units are spaned at random based on `GameParam.foodScarcity` probability;
- Food units appear at random empty points on the map (no units nor walls)

### 'game-over' conditions

- Unit collided with enemy base
- Player has no available mobile units
- Player didn't responded before the timeout
- Number of units hasn't changed for more than X (default 1000) turns

## Suggested Unit Tests for GameEngine

### InitializationTest

- Test that game initializes with correct map, bases, and one pawn per player.
- Test that `getGameState()` returns correct initial state.

### MovementTest

- Test pawn moves in all 8 directions correctly.
- Test pawn cannot move through walls (treated as no-op).
- Test pawn cannot move out of bounds (treated as no-op).
- Test missing pawn action is treated as no-op.

### CollisionTest_PawnVsPawn

- Test two enemy pawns colliding → both die.
- Test two friendly pawns sharing a tile → both survive.
- Test friendly pawn + enemy pawn on same tile → both die.
- Test three pawns (2 friendly, 1 enemy) on same tile → all die.

### CollisionTest_PawnVsFood

- Test pawn collides with food → food consumed, pawn survives.
- Test next turn: new pawn spawns at pawn's base.
- Test multiple pawns consume food in same turn → multiple new pawns spawn next turn.

### CollisionTest_PawnVsBase

- Test enemy pawn reaches opponent's base → pawn dies, base destroyed.
- Test game-over condition triggered.

### FoodSpawnTest

- Test food spawns at random intervals.
- Test food spawns at unoccupied positions only.
- Test food doesn't spawn on walls, bases, or pawns.

### GameOverTest

- Test game ends when a player has no pawns and base is destroyed.
- Test correct winner is returned.

### ActionValidationTest

- Test invalid pawn ID raises exception or is ignored.
- Test invalid direction is rejected.

## Implementation Notes

- **Concurrency**: GameEngine is **not thread-safe**. The RPC server (single-threaded) calls it sequentially, so no locks are needed.
- **Food spawning randomness**: Use a seeded `Random` instance if reproducibility is needed for testing; otherwise use `new Random()`.
- **Pawn IDs**: Generate unique IDs per pawn (e.g., "pA1", "pA2", "pB1", "pB2", ...) when spawning.
- **Immutability**: Only `gameState` is muttable, all other variables are immutables.