# SL Hackathon 2026 - Turn-Based Strategy Game

A multiplayer turn-based strategy game where players compete to collect food, spawn pawns, and destroy their opponent's base. The project consists of a Java game server, Java client with bot AI, and a web-based replay viewer.

## Table of Contents

- [Background](#background)
- [Game Rules](#game-rules)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Running the Components](#running-the-components)
  - [Server](#server)
  - [Client](#client)
  - [Replay Viewer](#replay-viewer)
- [Development](#development)
- [Architecture](#architecture)

## Background

This project was inspired by an older, cloud-based version of a similar game. It implements a complete turn-based strategy game system with:

- **Authoritative game server** that manages game state, validates actions, handles collisions, and enforces timeouts
- **Smart client bots** that use pathfinding algorithms and strategies to compete autonomously
- **Web-based replay viewer** for visualizing and analyzing game sessions

The game features a tile-based map where players control pawns that move, collect food resources, spawn new units, and engage in tactical combat to destroy the opponent's base.

## Game Rules

### Map & Units

- **Map**: Rectangular grid with walls (impassable), food (collectible), player bases, and pawns
- **Bases**: Each player has one base that spawns pawns
- **Pawns**: Move one tile per turn in 8 directions (N, NE, E, SE, S, SW, W, NW)
- **Food**: Randomly spawns on empty tiles; collecting food spawns a new pawn at your base

### Combat & Collisions

- **Enemy pawn collision**: Both pawns die
- **Friendly pawn collision**: Both pawns survive (can stack on same tile)
- **Pawn + food collision**: Pawn survives, food consumed, new pawn spawns at base next turn
- **Pawn + enemy base collision**: Pawn and base destroyed, attacker wins

### Win Conditions

A player wins by:

1. Destroying the opponent's base, OR
2. Eliminating all opponent pawns

### Turn System

- Players take turns submitting actions for all their pawns
- Each turn has a configurable time limit (default based on `GameParams`)
- Actions are validated before execution
- Invalid actions result in warnings but don't end the player's turn

## Project Structure

```
sl-hackathon-2026/
├── server/              # Java game server (WebSocket-based)
│   ├── src/main/java/   # Game engine, validators, state management
│   └── build.gradle     # Gradle build configuration
├── client-java/         # Java client with bot AI
│   ├── src/main/java/   # Bot logic, pathfinding, action planning
│   ├── game-logs/       # Generated game log files (JSON)
│   └── build.gradle     # Gradle build configuration
├── replay/              # Node.js web-based replay viewer
│   ├── server.js        # HTTP server
│   ├── public/          # HTML, CSS, JavaScript frontend
│   └── config.json      # Configuration file
└── docs/                # Design documentation
```

## Prerequisites

### For Server and Client

- **Java Development Kit (JDK) 21** or higher
- **Gradle** (wrapper included, so manual installation is optional)

### For Replay Viewer

- **Node.js** (v14 or higher)
- **npm** (comes with Node.js)

## Quick Start

For a quick test run:

```bash
# Terminal 1 - Start the server
cd server
./gradlew run                    # On Unix/Mac/WSL
# OR
gradlew.bat run                  # On Windows

# Terminal 2 - Start first client
cd client-java
./gradlew run                    # On Unix/Mac/WSL
# OR
gradlew.bat run                  # On Windows

# Terminal 3 - Start second client
cd client-java
./gradlew run                    # On Unix/Mac/WSL
# OR
gradlew.bat run                  # On Windows

# Terminal 4 - Start replay viewer
cd replay
npm install
npm start
# Then open http://localhost:8081 in your browser
```

## Running the Components

### Server

The server manages the game engine, validates actions, handles player connections, and broadcasts game state updates.

#### Build

```bash
cd server
./gradlew build          # Unix/Mac/WSL
# OR
gradlew.bat build        # Windows
```

#### Run

```bash
cd server
./gradlew run            # Unix/Mac/WSL
# OR
gradlew.bat run          # Windows
```

The server will:

- Start a WebSocket server (default port configured in code)
- Wait for two players to connect
- Initialize the game with configured parameters
- Orchestrate turns between players
- Broadcast game state updates
- Shut down after game completion

#### Configuration

Game parameters are configured in the server code (`GameParams`):

- `turnTimeoutLimit`: Time limit per turn (seconds)
- `foodScarcity`: Likelihood of food spawning each turn (0.0-1.0)
- `mapConfig`: Map dimensions, walls, and base locations

### Client

The client connects to the server, receives game state, executes bot logic, and submits actions.

#### Client Build

```bash
cd client-java
./gradlew build          # Unix/Mac/WSL
# OR
gradlew.bat build        # Windows
```

#### Client Run

```bash
cd client-java
./gradlew run            # Unix/Mac/WSL
# OR
gradlew.bat run          # Windows
```

The client will:

- Connect to the game server
- Wait for game start
- Execute bot strategy on each turn (using pathfinding and action planning)
- Submit pawn movement actions
- Log the complete game history to `game-logs/game_<timestamp>.json`

#### Bot Strategy

The client includes a bot AI that:

- Uses BFS pathfinding to navigate around walls
- Prioritizes collecting food to spawn more pawns
- Employs tactical strategies to attack opponent pawns and base
- Handles multiple pawns simultaneously

### Replay Viewer

The replay viewer is a web application that visualizes game logs.

#### Installation

```bash
cd replay
npm install
```

#### Replay Configuration

Edit `replay/config.json` to customize:

```json
{
  "gameLogsPath": "../client-java/game-logs",
  "cellSize": 5,
  "playbackSpeed": 1000,
  "serverPort": 8081,
  "graphHeight": 150
}
```

- `gameLogsPath`: Directory containing game JSON files (relative or absolute)
- `cellSize`: Canvas pixels per map cell (default: 5x5 pixels)
- `playbackSpeed`: Milliseconds between turns during playback
- `serverPort`: HTTP server port
- `graphHeight`: Height of the units-over-time graph in pixels

#### Replay Run

```bash
cd replay
npm start
```

Then open your browser to: **[http://localhost:8081](http://localhost:8081)**

#### Usage

1. **Select a game** from the list on the left panel
2. **Navigate turns** using the slider or play/pause button
3. **View statistics** in the units-over-time graph

**Controls:**

- **Range Slider**: Navigate to specific turn
- **Play/Pause Button**: Auto-advance through turns
- **Units Graph**: Shows unit count over time

**Visual Legend:**

- 🟦 Blue Square: Player-1 base
- 🟥 Red Square: Player-2 base
- 🔵 Blue Circle: Player-1 pawn
- 🔴 Red Circle: Player-2 pawn
- 🔺 Yellow Triangle: Food
- ⬜ Gray Square: Wall

## Development

### Running Tests

#### Server Tests

```bash
cd server
./gradlew test           # Unix/Mac/WSL
# OR
gradlew.bat test         # Windows
```

#### Client Tests

```bash
cd client-java
./gradlew test           # Unix/Mac/WSL
# OR
gradlew.bat test         # Windows
```

### Building Distributions

#### Building Distributions: Server

```bash
cd server
./gradlew installDist    # Unix/Mac/WSL
# Outputs to: build/install/server/

# OR
gradlew.bat installDist  # Windows
```

#### Building Distributions: Client

```bash
cd client-java
./gradlew installDist    # Unix/Mac/WSL
# Outputs to: build/install/client/

# OR
gradlew.bat installDist  # Windows
```

### Code Structure

#### Server Components

- **GameEngine**: Orchestrates game state, turn management, and player coordination
- **ActionValidator**: Validates player actions before execution
- **GameStateUpdater**: Applies actions and resolves collisions
- **CommManager**: Handles WebSocket connections and message routing
- **MessageCodec**: Serializes/deserializes game messages using Jackson

#### Client Components

- **Orchestrator**: Manages game state and coordinates with bot
- **Bot**: Implements game strategy and action planning
- **HelperTools**: Provides pathfinding and proximity utilities
- **ServerAPI**: Handles WebSocket communication with server

## Architecture

The system uses a client-server architecture with WebSocket-based communication:

1. **Server** is authoritative and manages all game logic
2. **Clients** are thin wrappers around bot AI that respond to server events
3. **Communication** uses JSON-RPC style messages with polymorphic serialization
4. **Game logs** are written by clients in JSON format for replay analysis

### Message Flow

```
Server                          Client
  |                               |
  |<------ join_game --------------|
  |                               |
  |------- start_game ----------->|
  |                               |
  |------- next_turn ------------>|
  |                               |
  |<------ actions ---------------|
  |                               |
  |------- next_turn ------------>|
  |                               |
  |------- end_game ------------->|
```

## Documentation

See the `docs/` directory for detailed design documents:

- [server-design.md](docs/server-design.md) - Server architecture and responsibilities
- [client-design.md](docs/client-design.md) - Client and bot design
- [gameEngine-design.md](docs/gameEngine-design.md) - Game rules and engine logic
- [replay-design.md](docs/replay-design.md) - Replay viewer specifications
- [impelementation-plan.md](docs/impelementation-plan.md) - Sprint-based implementation plan

---

**Happy Gaming!** 🎮
