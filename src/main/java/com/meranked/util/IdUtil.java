package com.meranked.util;

import java.util.UUID;

public final class IdUtil {

    private IdUtil() {}

    public static String newMatchId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String newAlertId() {
        return "A-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
