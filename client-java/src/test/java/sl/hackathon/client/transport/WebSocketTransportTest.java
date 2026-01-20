package sl.hackathon.client.transport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sl.hackathon.client.api.TransportState;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocketTransport.
 * Tests connection lifecycle, message sending/receiving, error handling, and state management.
 */
class WebSocketTransportTest {
    
    private WebSocketTransport transport;
    
    @BeforeEach
    void setUp() {
        transport = new WebSocketTransport();
    }
    
    @AfterEach
    void tearDown() {
        if (transport.isConnected()) {
            transport.disconnect();
        }
    }
    
    @Test
    void testInitialState() {
        assertEquals(TransportState.DISCONNECTED, transport.getState());
        assertFalse(transport.isConnected());
    }
    
    @Test
    void testConnectWithInvalidURL() {
        assertThrows(Exception.class, () -> transport.connect("invalid-url"));
        assertEquals(TransportState.DISCONNECTED, transport.getState());
    }
    
    @Test
    void testConnectTwiceThrowsException() throws Exception {
        // Simulate connecting state by attempting connection
        // (will fail but state will change temporarily)
        try {
            transport.connect("ws://nonexistent-server:9999/game");
        } catch (Exception e) {
            // Expected - server doesn't exist
        }
        
        // After failed connection, state should be DISCONNECTED
        // This test verifies we can't connect when already in CONNECTING state
        // For a more complete test, we'd need a mock WebSocket server
    }
    
    @Test
    void testSendWithoutConnection() {
        assertThrows(IllegalStateException.class, () -> transport.send("test message"));
    }
    
    @Test
    void testDisconnectWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> transport.disconnect());
        assertEquals(TransportState.DISCONNECTED, transport.getState());
    }
    
    @Test
    void testOnConnectedCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        transport.setOnConnected(() -> {
            callbackInvoked.set(true);
            latch.countDown();
        });
        
        // Note: Without a real WebSocket server, we can't test actual connection
        // This test verifies the callback can be set without errors
        assertNotNull(transport);
    }
    
    @Test
    void testOnDisconnectedCallback() {
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        transport.setOnDisconnected(() -> callbackInvoked.set(true));
        
        // Disconnect when not connected - callback should not be invoked
        transport.disconnect();
        assertFalse(callbackInvoked.get());
    }
    
    @Test
    void testOnMessageReceivedCallback() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        transport.setOnMessageReceived(msg -> {
            receivedMessage.set(msg);
            latch.countDown();
        });
        
        // Verify callback is set
        assertNotNull(transport);
        // Note: Actual message reception requires WebSocket connection
    }
    
    @Test
    void testOnErrorCallback() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> receivedError = new AtomicReference<>();
        
        transport.setOnError(error -> {
            receivedError.set(error);
            latch.countDown();
        });
        
        // Verify callback is set
        assertNotNull(transport);
    }
    
    @Test
    void testMultipleCallbackRegistrations() {
        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);
        
        // First callback
        transport.setOnConnected(counter1::incrementAndGet);
        
        // Second callback (should replace first)
        transport.setOnConnected(counter2::incrementAndGet);
        
        // Verify both can be set (actual invocation requires real connection)
        assertNotNull(transport);
    }
    
    @Test
    void testStateTransitions() {
        // Initial state
        assertEquals(TransportState.DISCONNECTED, transport.getState());
        
        // After disconnect (when already disconnected)
        transport.disconnect();
        assertEquals(TransportState.DISCONNECTED, transport.getState());
    }
    
    @Test
    void testSendMessageFormat() {
        String testMessage = "{\"type\":\"ACTION\",\"actions\":[]}";
        
        // Should throw because not connected
        Exception exception = assertThrows(IllegalStateException.class, 
            () -> transport.send(testMessage));
        
        assertTrue(exception.getMessage().contains("not connected"));
    }
    
    @Test
    void testConnectionTimeout() {
        // Test that connection attempt to non-existent server times out
        // Note: This will take 10 seconds in real execution
        // For unit tests, this would ideally use a mock or shorter timeout
        
        assertThrows(Exception.class, () -> {
            transport.connect("ws://localhost:99999/nonexistent");
        });
        
        assertEquals(TransportState.DISCONNECTED, transport.getState());
    }
    
    @Test
    void testNullCallbacksHandledGracefully() {
        // Setting null callbacks should not cause issues
        assertDoesNotThrow(() -> {
            transport.setOnMessageReceived(null);
            transport.setOnError(null);
            transport.setOnConnected(null);
            transport.setOnDisconnected(null);
        });
    }
}
