const express = require('express');
const cors = require('cors');
const fs = require('fs').promises;
const path = require('path');

const config = require('./config.json');

const app = express();
app.use(cors());
app.use(express.static('public'));

// API endpoint: List all game files
app.get('/api/games', async (req, res) => {
    try {
        const gamesDir = path.resolve(__dirname, config.gameLogsPath);
        const files = await fs.readdir(gamesDir);
        
        const gameFiles = files
            .filter(file => file.endsWith('.json'))
            .map(file => {
                const timestamp = file.match(/game_(\d+)\.json/)?.[1];
                return {
                    filename: file,
                    timestamp: timestamp ? parseInt(timestamp) : 0
                };
            })
            .sort((a, b) => b.timestamp - a.timestamp);
        
        res.json(gameFiles);
    } catch (error) {
        console.error('Error reading game logs:', error);
        res.status(500).json({ error: 'Failed to read game logs' });
    }
});

// API endpoint: Get specific game data
app.get('/api/games/:filename', async (req, res) => {
    try {
        const filename = req.params.filename;
        if (!filename.endsWith('.json') || filename.includes('..')) {
            return res.status(400).json({ error: 'Invalid filename' });
        }
        
        const filePath = path.resolve(__dirname, config.gameLogsPath, filename);
        const data = await fs.readFile(filePath, 'utf8');
        const gameData = JSON.parse(data);
        
        res.json(gameData);
    } catch (error) {
        console.error('Error reading game file:', error);
        res.status(404).json({ error: 'Game file not found' });
    }
});

// API endpoint: Get configuration
app.get('/api/config', (req, res) => {
    res.json({
        cellSize: config.cellSize,
        playbackSpeed: config.playbackSpeed,
        graphHeight: config.graphHeight
    });
});

const PORT = config.serverPort || 8081;
app.listen(PORT, () => {
    console.log(`Game Replay Viewer running on http://localhost:${PORT}`);
    console.log(`Game logs directory: ${path.resolve(__dirname, config.gameLogsPath)}`);
});
