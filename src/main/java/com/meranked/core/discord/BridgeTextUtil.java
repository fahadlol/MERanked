package com.meranked.core.discord;

import java.util.regex.Pattern;

public final class BridgeTextUtil {

    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b|\\b[0-9a-fA-F:]+:[0-9a-fA-F:]+\\b");
    private static final int MAX_LENGTH = 2000;

    private BridgeTextUtil() {}

    public static String sanitize(String input) {
        if (input == null) return "";
        String cleaned = input.replace('\n', ' ').replace('\r', ' ').trim();
        cleaned = IP_PATTERN.matcher(cleaned).replaceAll("[redacted-ip]");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH - 3) + "...";
        }
        return cleaned;
    }
}
