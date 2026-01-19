package sl.hackathon.server.communication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocketServerContainer.
 * Tests server lifecycle, configuration, and error handling.
 */
class WebSocketServerContainerTest {

    private WebSocketServerContainer container;

    @BeforeEach
    void setUp() {
        // Use a non-standard port to avoid conflicts with running servers
        container = new WebSocketServerContainer(18080);
    }

    @AfterEach
    void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    // ===== CONSTRUCTOR TESTS =====

    @Test
    void testDefaultConstructor() {
        WebSocketServerContainer defaultContainer = new WebSocketServerContainer();
        
        assertEquals("localhost", defaultContainer.getHost());
        assertEquals(8080, defaultContainer.getPort());
        assertEquals("/", defaultContainer.getContextPath());
        assertFalse(defaultContainer.isRunning());
    }

    @Test
    void testConstructorWithPort() {
        WebSocketServerContainer customContainer = new WebSocketServerContainer(9090);
        
        assertEquals("localhost", customContainer.getHost());
        assertEquals(9090, customContainer.getPort());
        assertEquals("/", customContainer.getContextPath());
    }

    @Test
    void testConstructorWithAllParameters() {
        WebSocketServerContainer customContainer = new WebSocketServerContainer("0.0.0.0", 9090, "/api");
        
        assertEquals("0.0.0.0", customContainer.getHost());
        assertEquals(9090, customContainer.getPort());
        assertEquals("/api", customContainer.getContextPath());
    }

    @Test
    void testConstructorWithNullHostThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebSocketServerContainer(null, 8080, "/");
        });
        assertEquals("Host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConstructorWithEmptyHostThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebSocketServerContainer("", 8080, "/");
        });
        assertEquals("Host cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConstructorWithInvalidPortTooLowThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebSocketServerContainer("localhost", 1023, "/");
        });
        assertTrue(exception.getMessage().contains("Port must be between 1024 and 65535"));
    }

    @Test
    void testConstructorWithInvalidPortTooHighThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebSocketServerContainer("localhost", 65536, "/");
        });
        assertTrue(exception.getMessage().contains("Port must be between 1024 and 65535"));
    }

    @Test
    void testConstructorWithNullContextPathThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebSocketServerContainer("localhost", 8080, null);
        });
        assertTrue(exception.getMessage().contains("Context path must start with /"));
    }

    @Test
    void testConstructorWithInvalidContextPathThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new WebSocketServerContainer("localhost", 8080, "api");
        });
        assertTrue(exception.getMessage().contains("Context path must start with /"));
    }

    // ===== LIFECYCLE TESTS =====

    @Test
    void testIsRunningInitiallyFalse() {
        assertFalse(container.isRunning());
    }

    @Test
    void testStartSetsRunningToTrue() {
        container.start();
        assertTrue(container.isRunning());
    }

    @Test
    void testStopSetsRunningToFalse() {
        container.start();
        assertTrue(container.isRunning());
        
        container.stop();
        assertFalse(container.isRunning());
    }

    @Test
    void testStartWhenAlreadyRunningThrowsException() {
        container.start();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            container.start();
        });
        assertEquals("Server is already running", exception.getMessage());
    }

    @Test
    void testStopWhenNotRunningDoesNotThrow() {
        assertFalse(container.isRunning());
        
        // Should not throw exception
        assertDoesNotThrow(() -> container.stop());
    }

    @Test
    void testMultipleStopsDoNotThrow() {
        container.start();
        container.stop();
        
        // Second stop should not throw
        assertDoesNotThrow(() -> container.stop());
    }

    // ===== URL GENERATION TESTS =====

    @Test
    void testGetWebSocketUrlWithDefaultContextPath() {
        assertEquals("ws://localhost:18080/game", container.getWebSocketUrl());
    }

    @Test
    void testGetWebSocketUrlWithCustomContextPath() {
        WebSocketServerContainer customContainer = new WebSocketServerContainer("localhost", 9090, "/api");
        assertEquals("ws://localhost:9090/api/game", customContainer.getWebSocketUrl());
    }

    @Test
    void testGetWebSocketUrlWithTrailingSlashInContextPath() {
        WebSocketServerContainer customContainer = new WebSocketServerContainer("localhost", 9090, "/api/");
        assertEquals("ws://localhost:9090/api/game", customContainer.getWebSocketUrl());
    }

    @Test
    void testGetWebSocketUrlWithCustomHost() {
        WebSocketServerContainer customContainer = new WebSocketServerContainer("0.0.0.0", 9090, "/");
        assertEquals("ws://0.0.0.0:9090/game", customContainer.getWebSocketUrl());
    }

    // ===== AWAIT SHUTDOWN TESTS =====

    @Test
    void testAwaitShutdownBlocksUntilStop() throws InterruptedException {
        container.start();
        
        // Start a thread that stops the server after 100ms
        Thread stopThread = new Thread(() -> {
            try {
                Thread.sleep(100);
                container.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        stopThread.start();
        
        // This should block until stop is called
        long startTime = System.currentTimeMillis();
        container.awaitShutdown();
        long duration = System.currentTimeMillis() - startTime;
        
        // Should have waited at least ~100ms
        assertTrue(duration >= 90, "Should have waited for stop");
        assertFalse(container.isRunning());
    }

    @Test
    void testAwaitShutdownWithTimeoutReturnsTrue() throws InterruptedException {
        container.start();
        
        // Start a thread that stops the server after 50ms
        Thread stopThread = new Thread(() -> {
            try {
                Thread.sleep(50);
                container.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        stopThread.start();
        
        // This should return true before timeout
        boolean stopped = container.awaitShutdown(1, TimeUnit.SECONDS);
        
        assertTrue(stopped, "Should have stopped before timeout");
        assertFalse(container.isRunning());
    }

    @Test
    void testAwaitShutdownWithTimeoutReturnsFalseOnTimeout() throws InterruptedException {
        container.start();
        
        // Don't stop the server - let it timeout
        boolean stopped = container.awaitShutdown(100, TimeUnit.MILLISECONDS);
        
        assertFalse(stopped, "Should have timed out");
        assertTrue(container.isRunning());
    }

    // ===== CONFIGURATION GETTER TESTS =====

    @Test
    void testGetHost() {
        assertEquals("localhost", container.getHost());
    }

    @Test
    void testGetPort() {
        assertEquals(18080, container.getPort());
    }

    @Test
    void testGetContextPath() {
        assertEquals("/", container.getContextPath());
    }

    // ===== EDGE CASE TESTS =====

    @Test
    void testStartAndStopMultipleTimes() {
        // First cycle
        container.start();
        assertTrue(container.isRunning());
        container.stop();
        assertFalse(container.isRunning());
        
        // Create new container for second cycle (can't restart same instance)
        WebSocketServerContainer container2 = new WebSocketServerContainer(18081);
        container2.start();
        assertTrue(container2.isRunning());
        container2.stop();
        assertFalse(container2.isRunning());
    }

    @Test
    void testValidPortBoundaries() {
        // Test minimum valid port
        WebSocketServerContainer minContainer = new WebSocketServerContainer("localhost", 1024, "/");
        assertEquals(1024, minContainer.getPort());
        
        // Test maximum valid port
        WebSocketServerContainer maxContainer = new WebSocketServerContainer("localhost", 65535, "/");
        assertEquals(65535, maxContainer.getPort());
    }
}
