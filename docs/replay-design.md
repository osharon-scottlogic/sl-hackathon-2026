# Game Replay Viewer Design

## Overview

Web application for visualizing game replays from JSON log files. Consists of a local HTTP server serving static HTML/JS files that load and render game states on an HTML canvas.

## Architecture

### Components

1. **HTTP Server**
   - Serves static HTML, CSS, JavaScript files
   - Provides REST endpoint: `GET /api/games` - returns list of game files
   - Provides REST endpoint: `GET /api/games/:filename` - returns game JSON content
   - Configurable base path for game logs directory

2. **Web Client**
   - Single-page application (HTML + vanilla JavaScript)
   - Left panel: Game list (scrollable)
   - Right panel: Canvas renderer + playback controls

## Configuration Parameters

| Parameter | Default | Description |
| --------- | ------- | ----------- |
| `gameLogsPath` | `client-java/game-logs` | Directory containing game JSON files |
| `cellSize` | `5` | Canvas pixels per map cell (5x5) |
| `playbackSpeed` | `1000` | Milliseconds per state transition |
| `serverPort` | `8081` | HTTP server port |
| `graphHeight` | `150` | Height of units-over-time graph in pixels |

## UI Layout

```
┌─────────────────────────────────────────────┐
│  Game Replay Viewer                         │
├──────────────┬──────────────────────────────┤
│ Game List    │  Canvas Area                 │
│              │                              │
│ • game_123   │  ┌────────────────────────┐  │
│   2026-01-20 │  │                        │  │
│              │  │   [Game Canvas]        │  │
│ • game_456   │  │                        │  │
│   2026-01-21 │  └────────────────────────┘  │
│              │                              │
│              │  [◄────────⬤────────►]       │
│              │  [▶] Play    Turn: 0/10      │
│              │                              │
│              │  Units Over Time             │
│              │  ┌────────────────────────┐  │
│              │  │ [Stacked Bar Chart]    │  │
│              │  └────────────────────────┘  │
└──────────────┴──────────────────────────────┘
```

## Data Model

Game log structure (from JSON):

- `players[]`: Array of player IDs (e.g., ["player-1", "player-2"])
- `mapDimensions`: Object with `width` and `height` properties
- `walls[]`: Array of Position objects (x, y coordinates)
- `turns[]`: Array of GameUpdate objects
- `winner`: Player ID
- `status`: Game status (e.g., "END")
- `timestamp`: Game timestamp (milliseconds)

GameState structure:

- `units[]`: Array of Unit objects

Unit structure:

- `id`: Unique identifier (e.g., "base-player-1", "pawn-player-1-0", "food-0")
- `owner`: Player ID or "none" for food
- `type`: Unit type ("BASE", "PAWN", or "FOOD")
- `position`: Object with `x` and `y` properties

## Rendering Specification

### Canvas Coordinate System

- Map dimensions from `mapDimensions.width` and `mapDimensions.height`
- Map origin (0,0) at top-left
- Each cell rendered as `cellSize × cellSize` pixels
- Canvas dimensions: `mapDimensions.width × cellSize` by `mapDimensions.height × cellSize`
- Walls from `walls[]` array rendered at startup (static elements)

### Visual Elements

| Element | Shape | Color | Size |
|---------|-------|-------|------|
| Base (player-1) | Square | Blue (#0000FF) | 80% of cell |
| Base (player-2) | Square | Red (#FF0000) | 80% of cell |
| Pawn (player-1) | Circle | Blue (#0000FF) | 60% of cell |
| Pawn (player-2) | Circle | Red (#FF0000) | 60% of cell |
| Food | Triangle | Yellow (#FFFF00) | 50% of cell |
| Wall | Square | Gray (#808080) | 100% of cell |
| Empty | - | White (#FFFFFF) | - |

### Units Over Time Graph

- **Type**: Stacked bar chart (vertical bars)
- **Position**: Below playback controls
- **Dimensions**: Full canvas width × `graphHeight` pixels
- **X-axis**: Turn index (0 to turns.length - 1)
- **Y-axis**: Unit count (0 to max units across all turns)
- **Bar Width**: `canvasWidth / turns.length`

**Bar Composition** (stacked from bottom to top):
1. Player-1 units (BASE + PAWN) - Blue (#0000FF)
2. Player-2 units (BASE + PAWN) - Red (#FF0000)
3. Food units - Yellow (#FFFF00)

**Interactivity**:

- Current turn highlighted with vertical line or border
- Optional: Clicking bar navigates to that turn

## Playback Controls

### Range Slider

- Horizontal slider spanning turn count (0 to `turns.length - 1`)
- Displays current turn index
- Immediately updates canvas when dragged

### Play Button

- Toggles between Play (▶) and Pause (⏸)
- Auto-advances slider at `playbackSpeed` interval
- Pauses automatically at final turn
- Disabled if no game loaded

### Turn Counter

- Displays: "Turn: {current}/{total}"
- Updates with slider and playback

## Component Interactions

1. **Load Game List**

   ```
   Browser → GET /api/games → Server
   Server → Returns [{filename, timestamp}] → Browser
   Browser → Renders list in left panel
   ```

2. **Select Game**

   ```
   User clicks game → GET /api/games/{filename} → Server
   Server → Returns game JSON → Browser
   Browser → Parses JSON:
     - Extract mapDimensions and walls
     - Initialize canvas size
     - Set slider range to turns.length
     - Calculate unit counts per turn for graph
   Browser → Render walls (static), then turn 0 units
   Browser → Render units-over-time graph
   ```

3. **Navigate Turns**

   ```
   User drags slider → Update turn index
   Browser → Clear canvas (except walls), render units from turns[index]
   Browser → Update current turn indicator on graph
   ```

4. **Playback**

   ```
   User clicks Play → Start interval timer
   Every {playbackSpeed}ms:
     - Increment turn index
     - Render turn
     - Update slider position
     - Update graph turn indicator
     - Stop if turn >= max
   ```

## Technical Stack

- **Server**: Node.js with Express (or Python with Flask)
- **Client**: HTML5 Canvas API, vanilla JavaScript (ES6+)
- **Data Format**: JSON (existing game log format)

## File Structure

```
replayer/
├── server.js              # HTTP server + API endpoints
├── config.json            # Configuration parameters
├── public/
│   ├── index.html         # Main UI layout
│   ├── style.css          # Styling
│   ├── app.js             # Game list logic
│   ├── renderer.js        # Canvas rendering + playback
│   └── graph.js           # Units-over-time graph rendering
```

## Future Enhancements

- Speed control (0.5x, 1x, 2x, 4x)
- Step forward/backward buttons
- Export replay as GIF/video
- Overlay turn statistics (pawn count, food collected)
- Keyboard shortcuts (space = play/pause, arrow keys = step)