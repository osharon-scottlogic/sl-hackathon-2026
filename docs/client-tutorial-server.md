# Client Tutorial Mode

Tutorial mode runs a local, in-memory server inside the Java client.

## Selecting tutorial mode

Pass a `serverUrl` that starts with `tutorial`.

- Canonical form: `tutorial:<tutorialId>`
- Example: `tutorial:basics-01`
- If no id is provided (e.g. `tutorial`), the client loads the default tutorial `basics-01`.

Tutorials are loaded from the client classpath at:

- `client-java/src/main/resources/tutorials/<tutorialId>.json`

## Tutorial definition schema

Top-level JSON:

- `map` (required): `MapLayout`
  - `dimension` (required): `{ "width": int, "height": int }`
  - `walls` (required): `[{"x":int,"y":int}, ...]`
- `initialUnits` (required): array of units without ids
  - `owner`: string or null (FOOD uses null)
  - `type`: `"BASE" | "PAWN" | "FOOD"`
  - `position`: `{ "x": int, "y": int }`
- `foodScarcity` (required): float in `[0,1]`
  - Matches current server semantics: chance of random food spawn is `(1 - foodScarcity)`.
- `foodSpawn` (optional): object mapping `turnNumber` to `position`
  - Example: `{ "2": {"x":5,"y":5} }`
- `gameEnd` (required): end criteria

### End criteria

1) Player has at least N units

```json
{
  "type": "PLAYER_UNITS_AT_LEAST",
  "playerId": "player1",
  "minUnits": 3
}
```

2) Any player unit enters rectangle

The rectangle is axis-aligned, defined by two corners, inclusive bounds.

```json
{
  "type": "ANY_PLAYER_UNIT_IN_RECT",
  "corner1": {"x": 2, "y": 2},
  "corner2": {"x": 4, "y": 4}
}
```

## Notes

- Tutorial mode is currently single-player and assigns player id `player1`.
- The tutorial runtime mirrors the server's movement/collision rules, then applies food spawns and checks end criteria.
