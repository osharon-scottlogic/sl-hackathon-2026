package sl.hackathon.server.integration;

import jakarta.websocket.*;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sl.hackathon.server.dtos.EndGameMessage;
import sl.hackathon.server.dtos.Message;
import sl.hackathon.server.dtos.MessageCodec;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket client endpoint for testing.
 * Collects messages and provides synchronization primitives for test assertions.
 */
@ClientEndpoint
public class TestWebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(TestWebSocketClient.class);

    private Session session;
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final List<Message> allMessages = new CopyOnWriteArrayList<>();
    private volatile boolean endGameReceived = false;

    public Session connect(String url) throws Exception {
        ClientManager client = ClientManager.createClient();
        URI uri = new URI(url);
        session = client.connectToServer(this, uri);

        logger.info("Connected to {}", url);
        return session;
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("WebSocket opened: {}", session.getId());
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            logger.debug("Received message: {}", message);
            Message msg = MessageCodec.deserialize(message);
            allMessages.add(msg);
            messageQueue.offer(msg);

            if (msg instanceof EndGameMessage) {
                endGameReceived = true;
            }
        } catch (Exception e) {
            logger.error("Failed to deserialize message: {}", message, e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("WebSocket closed: {} - {}", session.getId(), closeReason);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.error("WebSocket error: {}", session.getId(), throwable);
    }

    public Message waitForMessage(long timeoutMs) throws InterruptedException {
        return messageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void sendMessage(Message message) {
        if (session != null && session.isOpen()) {
            try {
                String json = MessageCodec.serialize(message);
                session.getBasicRemote().sendText(json);
                logger.debug("Sent message: {}", json);
            } catch (IOException e) {
                logger.error("Failed to send message", e);
            }
        }
    }

    public boolean hasReceivedEndGame() {
        return endGameReceived;
    }

    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                logger.error("Failed to close session", e);
            }
        }
    }
}
