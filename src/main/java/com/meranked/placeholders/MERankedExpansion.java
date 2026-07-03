package com.meranked.placeholders;

import com.meranked.bootstrap.ServiceRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class MERankedExpansion extends PlaceholderExpansion {

    private final ServiceRegistry services;

    public MERankedExpansion(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "meranked";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MERanked";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) return "";
        return services.placeholders().resolve(player.getPlayer(), params);
    }
}
