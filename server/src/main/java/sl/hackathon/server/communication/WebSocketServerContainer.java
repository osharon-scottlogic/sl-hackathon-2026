package sl.hackathon.server.communication;

import lombok.Getter;
import org.glassfish.tyrus.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static sl.hackathon.server.util.Ansi.*;

/**
 * Manages the WebSocket server lifecycle using Tyrus embedded server.
 * Responsibilities:
 * - Start and stop the WebSocket server
 * - Register WebSocket endpoints
 * - Configure server port and host
 * - Handle graceful shutdown
 * - Manage server state
 */
public class WebSocketServerContainer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerContainer.class);
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_CONTEXT_PATH = "/";

    @Getter
    private final String host;
    @Getter
    private final int port;
    @Getter
    private final String contextPath;
    @Getter
    private volatile boolean running = false;
    private Server server;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    /**
     * Creates a WebSocket server container with default configuration.
     * Host: localhost, Port: 8080, Context: /
     */
    public WebSocketServerContainer() {
        this(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_CONTEXT_PATH);
    }
    
    /**
     * Creates a WebSocket server container with custom port.
     * Host: localhost, Context: /
     * 
     * @param port the port to listen on (1024-65535)
     * @throws IllegalArgumentException if port is invalid
     */
    public WebSocketServerContainer(int port) {
        this(DEFAULT_HOST, port, DEFAULT_CONTEXT_PATH);
    }
    
    /**
     * Creates a WebSocket server container with custom configuration.
     * 
     * @param host the host to bind to (must not be null or empty)
     * @param port the port to listen on (1024-65535)
     * @param contextPath the context path (must start with /)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public WebSocketServerContainer(String host, int port, String contextPath) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1024 and 65535");
        }
        if (contextPath == null || !contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Context path must start with /");
        }
        
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
        
        logger.info(green("WebSocket server container created: {}:{}{}"), host, port, contextPath);
    }
    
    /**
     * Starts the WebSocket server.
     * Registers the WebSocketAdapter endpoint at /game.
     * Blocks until the server is successfully started.
     * 
     * @throws IllegalStateException if server is already running
     * @throws RuntimeException if server fails to start
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }
        
        try {
            logger.info("Starting WebSocket server on {}:{}{}", host, port, contextPath);
            
            // Create Tyrus server with configuration
            // Note: Using empty map for config and passing the endpoint class directly
            // The contextPath parameter should be the base path (e.g., "/")
            // The endpoint's @ServerEndpoint("/game") annotation specifies the full path
            server = new Server(host, port, contextPath, null, WebSocketAdapter.class);
            
            // Start the server
            server.start();
            running = true;
            
            logger.info(green("WebSocket server started successfully at ws://{}:{}{}"), host, port, contextPath);
            
            // Register shutdown hook for graceful shutdown on Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                stop();
            }));
            
        } catch (Exception e) {
            running = false;
            logger.error(redBg(yellow("Failed to start WebSocket server: {}")), e.getMessage(), e);
            throw new RuntimeException("Failed to start WebSocket server", e);
        }
    }
    
    /**
     * Stops the WebSocket server gracefully.
     * Waits for active connections to close (with timeout).
     * Safe to call multiple times.
     */
    public void stop() {
        if (!running) {
            logger.debug("Server is not running, nothing to stop");
            return;
        }
        
        try {
            logger.info("Stopping WebSocket server...");
            
            if (server != null) {
                server.stop();
            }
            
            running = false;
            shutdownLatch.countDown();
            
            logger.info("WebSocket server stopped");
            
        } catch (Exception e) {
            logger.error(redBg(yellow("Error stopping WebSocket server: {}")), e.getMessage(), e);
        }
    }

    /**
     * Gets the WebSocket URL for clients to connect to.
     * 
     * @return the WebSocket URL (e.g., ws://localhost:8080/game)
     */
    public String getWebSocketUrl() {
        return String.format("ws://%s:%d%sgame", host, port, 
            contextPath.endsWith("/") ? contextPath : contextPath + "/");
    }
    
    /**
     * Blocks the calling thread until the server is stopped.
     * Useful for keeping the main thread alive in standalone applications.
     * 
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }
    
    /**
     * Blocks the calling thread until the server is stopped or timeout expires.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return true if the server stopped, false if timeout elapsed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        return shutdownLatch.await(timeout, unit);
    }
}
