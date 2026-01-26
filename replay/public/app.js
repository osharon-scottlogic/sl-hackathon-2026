// Global state
let config = null;
let currentGame = null;
let games = [];

// Initialize application
async function init() {
    try {
        // Load configuration
        const configResponse = await fetch('/api/config');
        config = await configResponse.json();
        
        // Load game list
        await loadGameList();
    } catch (error) {
        console.error('Initialization failed:', error);
        alert('Failed to initialize application');
    }
}

// Load list of available games
async function loadGameList() {
    const gameListElement = document.getElementById('gameList');
    
    try {
        const response = await fetch('/api/games');
        games = await response.json();
        
        if (games.length === 0) {
            gameListElement.innerHTML = '<p class="loading">No games found</p>';
            return;
        }
        
        gameListElement.innerHTML = '';
        games.forEach((game, index) => {
            const gameItem = document.createElement('div');
            gameItem.className = 'game-item';
            gameItem.dataset.filename = game.filename;
            
            const date = new Date(game.timestamp);
            const dateStr = date.toLocaleString();
            
            gameItem.innerHTML = `
                <div class="filename">${game.filename}</div>
                <div class="timestamp">${dateStr}</div>
            `;
            
            gameItem.addEventListener('click', () => loadGame(game.filename));
            gameListElement.appendChild(gameItem);
        });
    } catch (error) {
        console.error('Failed to load game list:', error);
        gameListElement.innerHTML = '<p class="loading">Failed to load games</p>';
    }
}

// Load specific game
async function loadGame(filename) {
    try {
        const response = await fetch(`/api/games/${filename}`);
        currentGame = await response.json();
        
        // Update UI
        document.querySelectorAll('.game-item').forEach(item => {
            item.classList.toggle('active', item.dataset.filename === filename);
        });
        
        // Initialize renderer and graph
        initializeRenderer(currentGame, config);
        initializeGraph(currentGame, config);
        
        // Enable controls
        const slider = document.getElementById('turnSlider');
        slider.max = currentGame.turns.length - 1;
        slider.value = 0;
        slider.disabled = false;
        
        const playButton = document.getElementById('playButton');
        playButton.disabled = false;
        
        updateTurnCounter(0, currentGame.turns.length);
        
        // Render first turn
        renderTurn(0);
    } catch (error) {
        console.error('Failed to load game:', error);
        alert('Failed to load game');
    }
}

// Update turn counter display
function updateTurnCounter(current, total) {
    document.getElementById('turnCounter').textContent = `Turn: ${current}/${total - 1}`;
}

// Start application when page loads
document.addEventListener('DOMContentLoaded', init);
