package com.meranked.util;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerFreezeUtil {

    private static final Set<UUID> FROZEN = ConcurrentHashMap.newKeySet();

    private PlayerFreezeUtil() {}

    public static void setFrozen(Player player, boolean frozen) {
        if (frozen) {
            FROZEN.add(player.getUniqueId());
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
        } else {
            FROZEN.remove(player.getUniqueId());
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
    }

    public static boolean isFrozen(UUID uuid) {
        return FROZEN.contains(uuid);
    }

    public static void clear(UUID uuid) {
        FROZEN.remove(uuid);
    }
}
