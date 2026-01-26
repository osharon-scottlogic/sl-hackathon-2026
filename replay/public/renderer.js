let canvas = null;
let ctx = null;
let cellSize = 5;
let gameData = null;
let isPlaying = false;
let playbackInterval = null;

// Initialize renderer
function initializeRenderer(game, cfg) {
    gameData = game;
    cellSize = cfg.cellSize;
    
    canvas = document.getElementById('gameCanvas');
    ctx = canvas.getContext('2d');
    
    // Set canvas size
    canvas.width = game.mapDimensions.width * cellSize;
    canvas.height = game.mapDimensions.height * cellSize;
    
    // Setup event listeners
    setupControls(cfg.playbackSpeed);
    
    // Render initial state with walls
    renderWalls();
}

// Setup playback controls
function setupControls(playbackSpeed) {
    const slider = document.getElementById('turnSlider');
    const playButton = document.getElementById('playButton');
    
    slider.addEventListener('input', (e) => {
        const turnIndex = parseInt(e.target.value);
        renderTurn(turnIndex);
        updateTurnCounter(turnIndex, gameData.turns.length);
        updateGraphIndicator(turnIndex);
    });
    
    playButton.addEventListener('click', () => {
        if (isPlaying) {
            stopPlayback();
        } else {
            startPlayback(playbackSpeed);
        }
    });
}

// Start automatic playback
function startPlayback(speed) {
    isPlaying = true;
    document.getElementById('playButton').textContent = '⏸ Pause';
    
    playbackInterval = setInterval(() => {
        const slider = document.getElementById('turnSlider');
        let currentTurn = parseInt(slider.value);
        
        if (currentTurn >= gameData.turns.length - 1) {
            stopPlayback();
            return;
        }
        
        currentTurn++;
        slider.value = currentTurn;
        renderTurn(currentTurn);
        updateTurnCounter(currentTurn, gameData.turns.length);
        updateGraphIndicator(currentTurn);
    }, speed);
}

// Stop playback
function stopPlayback() {
    isPlaying = false;
    document.getElementById('playButton').textContent = '▶ Play';
    
    if (playbackInterval) {
        clearInterval(playbackInterval);
        playbackInterval = null;
    }
}

// Render walls (static elements)
function renderWalls() {
    if (!gameData || !gameData.walls) return;
    
    gameData.walls.forEach(wall => {
        ctx.fillStyle = '#808080';
        ctx.fillRect(
            wall.x * cellSize,
            wall.y * cellSize,
            cellSize,
            cellSize
        );
    });
}

// Render specific turn
function renderTurn(turnIndex) {
    if (!gameData || !gameData.turns[turnIndex]) return;
    
    const turn = gameData.turns[turnIndex];
    
    // Clear canvas (except walls)
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Redraw walls
    renderWalls();
    
    // Render units
    turn.units.forEach(unit => {
        renderUnit(unit);
    });
}

// Render individual unit
function renderUnit(unit) {
    const x = unit.position.x * cellSize;
    const y = unit.position.y * cellSize;
    const centerX = x + cellSize / 2;
    const centerY = y + cellSize / 2;
    
    // Determine color based on owner
    let color;
    if (unit.type === 'FOOD') {
        color = '#FFFF00'; // Yellow
    } else if (unit.owner === 'player-1') {
        color = '#0000FF'; // Blue
    } else if (unit.owner === 'player-2') {
        color = '#FF0000'; // Red
    }
    
    ctx.fillStyle = color;
    
    // Render based on type
    if (unit.type === 'BASE') {
        // Square (80% of cell)
        const size = cellSize * 0.8;
        const offset = (cellSize - size) / 2;
        ctx.fillRect(x + offset, y + offset, size, size);
        
    } else if (unit.type === 'PAWN') {
        // Circle (60% of cell)
        const radius = (cellSize * 0.6) / 2;
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, 0, 2 * Math.PI);
        ctx.fill();
        
    } else if (unit.type === 'FOOD') {
        // Triangle (50% of cell)
        const size = cellSize * 0.5;
        const offset = (cellSize - size) / 2;
        
        ctx.beginPath();
        ctx.moveTo(centerX, y + offset); // Top point
        ctx.lineTo(x + offset, y + cellSize - offset); // Bottom left
        ctx.lineTo(x + cellSize - offset, y + cellSize - offset); // Bottom right
        ctx.closePath();
        ctx.fill();
    }
}
