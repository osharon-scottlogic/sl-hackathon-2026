package sl.hackathon.server.util;

/**
 * ANSI color codes for terminal output.
 * Provides constants for coloring log messages.
 */
public class Ansi {
    // Color codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String YELLOW = "\u001B[33m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String MAGENTA = "\u001B[35m";
    
    // Prevent instantiation
    private Ansi() {
        throw new UnsupportedOperationException("Utility class");
    }
}
