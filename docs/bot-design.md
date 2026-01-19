# Bot — Design (uses HelperTools)

## Overview

This document defines a `bot` component that receives immutable `GameState` snapshots and returns a List<Action> for the current turn. It relies on the previously defined `HelperTools` utilities for pathfinding, proximity analysis, and collision prediction.

## Design goals

- Simple, deterministic action selection that always returns a List<Action> quickly (well under the `turnTimeoutLimit` server timeout).
- Use HelperTools to avoid suicidal moves and to pursue strategic objectives (collect food, defend base, attack when appropriate).
- Keep `bot` stateless between calls to `handleState` except for light-weight persistent metadata (e.g., lastTurnId, playerId). This makes testing easier and avoids synchronization.

## API

- Action[] handleState(String playerId, MapLayout mapLayout, GameState state, int timeLimitMs);

### Notes

- `playerId` is a string identifier for this client (e.g. `player-1` or `player-2`).
- `mapLayout` is provided for convenience (immutable map data).
- `state` provides unit locations
- `handleState` must be fast; it must not block beyond `timeLimitMs` (client enforces the timeout).

## High-level algorithm for handleState

1. Build local views:
   - `friendlyPawns = filter all state.units where owner==playerId`
   - `enemyPawns = filter all state.units where owner.id <> playerId and type <> FOOD`
   - `foodLocations = filter all state.units where type==FOOD`
2. For each friendly pawn, generate a small set of candidate moves (0-3 candidates):
   - If there is nearby food and we can reach it before/at same time as enemy, candidate -> move toward nearest food (first step from HelperTools.findShortestPath).
   - Else if enemy is adjacent or will collide, candidate -> step away (flee) using findPathAvoiding or choose safe direction (HelperTools.generateRandomAction as fallback).
   - Else if offensive opportunity (enemy base reachable / weak), candidate -> move toward enemy base or closest enemy (use HelperTools.findShortestPath).
   - Else: move toward centroid of friendly pawns for grouping or hold position (no-op).
3. Merge candidate moves into a full action set (one Action per pawn). When multiple pawns target the same tile, allow it (same-player stacking). However consider predicting collisions if an enemy may enter.
4. Use `HelperTools.predictCollisions(newState, candidateActions)` to simulate the turn. If any candidate would cause a friendly pawn death (pawnWillDie true), remove or replace that pawn's action with a safer fallback (e.g., generateRandomAction or no-op).
5. If removing actions leaves pawns with no action, you may choose no-op or safe fallback.
6. Finalize actions and return Action[].
7. At any stage, if current time is close to timeLimitsMS + state.startTime, return actions gathered so far.

## Helper functions & strategies

- `decideAttackOrRegroup(p, newState)`:
  - If we have numerical advantage nearby (HelperTools.getFriendlyPawnCount vs getEnemyPawnCount in radius), move toward closest enemy.
  - Else move toward friendly centroid (HelperTools.getCentroidOfPawns) for support.
- `selectPawnForFoodClaim(foodPos)`:
  - Sort friendly pawns by distance to `foodPos` and select the nearest that can reach before enemies.
- `safeFallbackAction(p)`:
  - Use `HelperTools.generateRandomAction()` or no-op; prefer moving to a position that is not adjacent to enemy pawns.

## Time budgeting

- `handleState` should budget its computations: do cheap proximity checks first (linear scans), then pathfinding only for a small subset (e.g., nearest food, nearest enemy). Use approximate distances (Manhattan) to prune expensive BFS/A* calls.
- Optionally enforce a hard micro-timeout inside `handleState` (e.g., stop complex planning if > 2000ms have elapsed) and return best-effort actions.

## Edge cases

- No pawns: return empty actions list.
- All pawns blocked/unreachable: return safe random/no-op actions.
- State with missing fields: validate and return empty list.

## Tests

### Unit tests

- `HandleState_SimpleMoveTest`
  - Single friendly pawn and a nearby food: handler returns Action moving toward food.

- `HandleState_TimeoutSafeTest`
  - If planning takes too long, handler returns fallback actions within budget.

- `HandleState_EvadeTest`
  - Pawn adjacent to enemy: handler chooses a flee action (not a move into enemy).

- `HandleState_CollisionAvoidanceTest`
  - Candidate action that would die is replaced with safe fallback using `HelperTools.predictCollisions()`.

- `HandleState_NoActionTest`
  - If no pawns, return empty list.

- `HandleState_MultiPawnCoordinationTest`
  - Two pawns target same food: verify one is chosen by `selectPawnForFoodClaim` and other either moves to regroup.

### Performance tests

- Test handler under typical map sizes (50x50) with up to 20 pawns to ensure execution within a few hundred ms.

### Integration notes

- `GameClient` calls `bot.handleState(...)` and returns the resulting actions as RPC response.
- Keep `bot` implementation pluggable: unit tests should be able to inject a deterministic `HelperTools` or seeded `Random`.

## Implementation suggestions

- Implement shortest-path with A* (heuristic: Chebyshev or Euclidean) when performance is a concern.
- Precompute a walkability grid for faster neighbor iteration.
- Cache path results per (start, goal) within a turn.
