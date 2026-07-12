package com.meranked.util;

public final class DurationUtil {

    private DurationUtil() {}

    public static boolean isDuration(String input) {
        return input != null && input.matches("\\d+[smhd]?|permanent");
    }

    /** Parses a duration token into milliseconds. {@code permanent} and {@code 0} mean permanent. */
    public static long parseMillis(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("permanent") || input.equals("0")) {
            return 0;
        }
        if (input.endsWith("d")) return Long.parseLong(input.replace("d", "")) * 86400000L;
        if (input.endsWith("h")) return Long.parseLong(input.replace("h", "")) * 3600000L;
        if (input.endsWith("m")) return Long.parseLong(input.replace("m", "")) * 60000L;
        if (input.endsWith("s")) return Long.parseLong(input.replace("s", "")) * 1000L;
        return Long.parseLong(input) * 1000L;
    }

    public static long parseSeconds(String input) {
        return parseMillis(input) / 1000L;
    }
}
