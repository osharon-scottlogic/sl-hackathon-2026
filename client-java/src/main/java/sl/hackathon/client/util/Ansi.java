package sl.hackathon.client.util;

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

    public static final String BLACK_BG = "\u001B[40m";
    public static final String RED_BG = "\u001B[41m";
    public static final String GREEN_BG = "\u001B[42m";
    public static final String YELLOW_BG = "\u001B[43m";
    public static final String BLUE_BG = "\u001B[44m";
    public static final String MAGENTA_BG = "\u001B[45m";
    public static final String CYAN_BG = "\u001B[46m";
    public static final String WHITE_BG = "\u001B[47m";

    // Prevent instantiation
    private Ansi() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String green(String message) {
        return message.replaceAll("\\{}", GREEN + "{}" + RESET );
    }

    public static String yellow(String message) {
        return message.replaceAll("\\{}", YELLOW + "{}" + RESET );
    }

    public static String red(String message) {
        return message.replaceAll("\\{}", RED + "{}" + RESET );
    }

    public static String redBg(String message) {
        return RED_BG + message + RESET;
    }
}
