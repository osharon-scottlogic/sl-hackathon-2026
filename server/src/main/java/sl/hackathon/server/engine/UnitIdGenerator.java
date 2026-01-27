package sl.hackathon.server.engine;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single authority for generating unique sequential unit IDs.
 * Thread-safe implementation using AtomicInteger.
 */
public class UnitIdGenerator {
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Generates and returns the next unique unit ID.
     *
     * @return the next sequential unit ID
     */
    public int nextId() {
        return counter.incrementAndGet();
    }

    /**
     * Resets the counter to 0 (useful for testing).
     */
    public void reset() {
        counter.set(0);
    }

    /**
     * Gets the current counter value without incrementing.
     *
     * @return the current counter value
     */
    public int getCurrentCount() {
        return counter.get();
    }
}
