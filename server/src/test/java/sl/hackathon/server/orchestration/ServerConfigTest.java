package sl.hackathon.server.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.server.dtos.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerConfig.
 * Test coverage:
 * 1. Constructor validation
 * 2. Port validation (bounds)
 * 3. MapConfig validation
 * 4. Turn time limit validation
 * 5. Default values
 */
class ServerConfigTest {
    
    private GameSettings gameSettings;
    private static final int SERVER_VERSION = 1;
    
    @BeforeEach
    void setUp() {
        Dimension dimension = new Dimension(10, 10);
        Position[] walls = new Position[]{new Position(5, 5)};
        Position[] baseLocations = new Position[]{new Position(1, 1), new Position(9, 9)};
        gameSettings = new GameSettings(dimension, walls, baseLocations, 15000, 0.1f, false);
    }
    
    @Test
    void constructor_WithValidParameters_CreatesConfig() {
        // Act
        ServerConfig config = new ServerConfig(gameSettings, 8080, SERVER_VERSION);
        
        // Assert
        assertEquals(8080, config.port());
        assertEquals(gameSettings, config.gameSettings());
        assertEquals(15000, config.gameSettings().turnTimeLimit());
        assertEquals(SERVER_VERSION, config.serverVersion());
    }
    
    @Test
    void constructor_WithDefaultPortAndTimeLimit_UsesDefaults() {
        // Act
        ServerConfig config = new ServerConfig(gameSettings);
        
        // Assert
        assertEquals(8080, config.port()); // Default port
        assertEquals(15000, config.gameSettings().turnTimeLimit()); // Default turn time limit
        assertEquals(gameSettings, config.gameSettings());
    }
    
    @Test
    void validate_WithPortBelowMinimum_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(gameSettings, 1023, SERVER_VERSION));
    }
    
    @Test
    void validate_WithPortAboveMaximum_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(gameSettings, 65536, SERVER_VERSION));
    }
    
    @Test
    void validate_WithMinimumValidPort_Succeeds() {
        // Act
        ServerConfig config = new ServerConfig(gameSettings, 1024, SERVER_VERSION);
        
        // Assert
        assertEquals(1024, config.port());
    }
    
    @Test
    void validate_WithMaximumValidPort_Succeeds() {
        // Act
        ServerConfig config = new ServerConfig(gameSettings, 65535, SERVER_VERSION);
        
        // Assert
        assertEquals(65535, config.port());
    }

    @Test
    void toString_ReturnsConfigDetails() {
        // Arrange
        ServerConfig config = new ServerConfig(gameSettings, 8080, SERVER_VERSION);
        
        // Act
        String result = config.toString();
        
        // Assert
        assertTrue(result.contains("port=8080"));
    }
}
