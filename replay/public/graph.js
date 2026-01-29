let graphCanvas = null;
let graphCtx = null;
let graphData = null;
let graphHeight = 150;
let currentTurnIndex = 0;

// Initialize graph
function initializeGraph(game, cfg) {
    graphCanvas = document.getElementById('graphCanvas');
    graphCtx = graphCanvas.getContext('2d');
    graphHeight = cfg.graphHeight;
    
    // Calculate unit counts per turn
    graphData = calculateGraphData(game);
    
    // Set canvas size
    const canvasWidth = document.getElementById('gameCanvas').width;
    graphCanvas.width = canvasWidth;
    graphCanvas.height = graphHeight;
    
    // Render graph
    renderGraph();
    updateGraphIndicator(0);
}

// Calculate unit counts for each turn
function calculateGraphData(game) {
    const data = [];
    let maxUnits = 0;
    
    game.turns.forEach(turn => {
        const counts = {
            player1: 0,
            player2: 0,
            food: 0
        };

        const units = Array.isArray(turn.units) ? turn.units : [];
        units.forEach(unit => {
            if (unit.type === 'FOOD') {
                counts.food++;
            } else if (unit.owner === 'player-1') {
                counts.player1++;
            } else if (unit.owner === 'player-2') {
                counts.player2++;
            }
        });
        
        const total = counts.player1 + counts.player2 + counts.food;
        if (total > maxUnits) maxUnits = total;
        
        data.push(counts);
    });
    
    return { counts: data, maxUnits };
}

// Render the graph
function renderGraph() {
    if (!graphData || !graphCanvas) return;
    
    const width = graphCanvas.width;
    const height = graphCanvas.height;
    const barWidth = width / graphData.counts.length;
    const padding = 20;
    const graphAreaHeight = height - padding;
    
    // Clear canvas
    graphCtx.fillStyle = '#FFFFFF';
    graphCtx.fillRect(0, 0, width, height);
    
    // Draw bars
    graphData.counts.forEach((counts, index) => {
        const x = index * barWidth;
        const total = counts.player1 + counts.player2 + counts.food;
        
        if (total === 0) return;
        
        const scale = graphAreaHeight / graphData.maxUnits;
        
        let currentY = graphAreaHeight;
        
        // Draw player-1 (blue) - bottom section
        if (counts.player1 > 0) {
            const barHeight = counts.player1 * scale;
            graphCtx.fillStyle = '#0000FF';
            graphCtx.fillRect(x, currentY - barHeight, barWidth - 1, barHeight);
            currentY -= barHeight;
        }
        
        // Draw player-2 (red) - middle section
        if (counts.player2 > 0) {
            const barHeight = counts.player2 * scale;
            graphCtx.fillStyle = '#FF0000';
            graphCtx.fillRect(x, currentY - barHeight, barWidth - 1, barHeight);
            currentY -= barHeight;
        }
        
        // Draw food (yellow) - top section
        if (counts.food > 0) {
            const barHeight = counts.food * scale;
            graphCtx.fillStyle = '#FFFF00';
            graphCtx.fillRect(x, currentY - barHeight, barWidth - 1, barHeight);
        }
    });
    
    // Draw baseline
    graphCtx.strokeStyle = '#333';
    graphCtx.lineWidth = 1;
    graphCtx.beginPath();
    graphCtx.moveTo(0, graphAreaHeight);
    graphCtx.lineTo(width, graphAreaHeight);
    graphCtx.stroke();
}

// Update current turn indicator on graph
function updateGraphIndicator(turnIndex) {
    if (!graphData || !graphCanvas) return;
    
    currentTurnIndex = turnIndex;
    
    // Redraw entire graph (to clear previous indicator)
    renderGraph();
    
    // Draw indicator line
    const width = graphCanvas.width;
    const barWidth = width / graphData.counts.length;
    const x = (turnIndex + 0.5) * barWidth;
    
    graphCtx.strokeStyle = '#000000';
    graphCtx.lineWidth = 2;
    graphCtx.beginPath();
    graphCtx.moveTo(x, 0);
    graphCtx.lineTo(x, graphCanvas.height - 20);
    graphCtx.stroke();
    
    // Draw indicator circle
    graphCtx.fillStyle = '#000000';
    graphCtx.beginPath();
    graphCtx.arc(x, graphCanvas.height - 20, 4, 0, 2 * Math.PI);
    graphCtx.fill();
}
