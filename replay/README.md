# Game Replay Viewer

Web application for visualizing game replays from JSON log files.

## Installation

1. Install Node.js (v14 or higher)
2. Install dependencies:

   ```bash
   npm install
   ```

## Configuration

Edit `config.json` to customize settings:

- `gameLogsPath`: Path to directory containing game log files (default: `../client-java/game-logs`)
- `cellSize`: Canvas pixels per map cell (default: `5`)
- `playbackSpeed`: Milliseconds per turn during playback (default: `1000`)
- `serverPort`: HTTP server port (default: `8081`)
- `graphHeight`: Height of units-over-time graph in pixels (default: `150`)

## Running

Start the server:

```bash
npm start
```

Then open your browser to: [http://localhost:8081](http://localhost:8081)

## Usage

1. **Select a game** from the list on the left panel
2. **Navigate turns** using the slider or play button
3. **View statistics** in the units-over-time graph below the canvas

### Controls

- **Range Slider**: Drag to navigate to specific turn
- **Play/Pause Button**: Auto-advance through turns
- **Units Graph**: Shows unit count distribution over time (blue=player-1, red=player-2, yellow=food)

## Visual Legend

- **Blue Square**: Player-1 base
- **Red Square**: Player-2 base
- **Blue Circle**: Player-1 pawn
- **Red Circle**: Player-2 pawn
- **Yellow Triangle**: Food
- **Gray Square**: Wall
