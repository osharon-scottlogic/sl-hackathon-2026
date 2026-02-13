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
    
    private MapConfig validMapConfig;
    private static final int SERVER_VERSION = 1;
    
    @BeforeEach
    void setUp() {
        Dimension dimension = new Dimension(10, 10);
        Position[] walls = new Position[]{new Position(5, 5)};
        Position[] baseLocations = new Position[]{new Position(1, 1), new Position(9, 9)};
        validMapConfig = new MapConfig(dimension, walls, baseLocations);
    }
    
    @Test
    void constructor_WithValidParameters_CreatesConfig() {
        // Act
        ServerConfig config = new ServerConfig(8080, validMapConfig, 15000, SERVER_VERSION);
        
        // Assert
        assertEquals(8080, config.port());
        assertEquals(validMapConfig, config.mapConfig());
        assertEquals(15000, config.turnTimeLimit());
        assertEquals(SERVER_VERSION, config.serverVersion());
    }
    
    @Test
    void constructor_WithDefaultPortAndTimeLimit_UsesDefaults() {
        // Act
        ServerConfig config = new ServerConfig(validMapConfig);
        
        // Assert
        assertEquals(8080, config.port()); // Default port
        assertEquals(15000, config.turnTimeLimit()); // Default turn time limit
        assertEquals(validMapConfig, config.mapConfig());
    }
    
    @Test
    void validate_WithPortBelowMinimum_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(1023, validMapConfig, 15000, SERVER_VERSION));
    }
    
    @Test
    void validate_WithPortAboveMaximum_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(65536, validMapConfig, 15000, SERVER_VERSION));
    }
    
    @Test
    void validate_WithMinimumValidPort_Succeeds() {
        // Act
        ServerConfig config = new ServerConfig(1024, validMapConfig, 15000, SERVER_VERSION);
        
        // Assert
        assertEquals(1024, config.port());
    }
    
    @Test
    void validate_WithMaximumValidPort_Succeeds() {
        // Act
        ServerConfig config = new ServerConfig(65535, validMapConfig, 15000, SERVER_VERSION);
        
        // Assert
        assertEquals(65535, config.port());
    }
    
    @Test
    void validate_WithNullMapConfig_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(8080, null, 15000, SERVER_VERSION));
    }
    
    @Test
    void validate_WithZeroTurnTimeLimit_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(8080, validMapConfig, 0, SERVER_VERSION));
    }
    
    @Test
    void validate_WithNegativeTurnTimeLimit_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            new ServerConfig(8080, validMapConfig, -1000, SERVER_VERSION));
    }
    
    @Test
    void toString_ReturnsConfigDetails() {
        // Arrange
        ServerConfig config = new ServerConfig(8080, validMapConfig, 15000, SERVER_VERSION);
        
        // Act
        String result = config.toString();
        
        // Assert
        assertTrue(result.contains("port=8080"));
        assertTrue(result.contains("turnTimeLimit=15000"));
    }
}
