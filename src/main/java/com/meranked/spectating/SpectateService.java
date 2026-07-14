package com.meranked.spectating;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.matches.MatchService;
import com.meranked.model.RankedMatch;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectateService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final MatchService matchService;
    private final MessageService messages;
    private final Map<UUID, SpectateSession> sessions = new ConcurrentHashMap<>();

    public SpectateService(MERankedPlugin plugin, ConfigService configService,
                           MatchService matchService, MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.matchService = matchService;
        this.messages = messages;
    }

    public boolean spectateMatch(Player spectator, String matchId, boolean staff) {
        FileConfiguration config = configService.get("spectating.yml");
        if (staff && !config.getBoolean("staff-silent.enabled", true)) return false;
        if (!staff && !config.getBoolean("player-spectate.enabled", true)) return false;
        if (!staff && plugin.services().punishments().isSpectateBanned(spectator.getUniqueId())) {
            messages.send(spectator, "spectate.not-available");
            return false;
        }

        Optional<RankedMatch> matchOpt = matchService.getMatchById(matchId);
        if (matchOpt.isEmpty()) {
            messages.send(spectator, "spectate.not-available");
            return false;
        }
        RankedMatch match = matchOpt.get();

        Location loc = spectator.getLocation();
        spectator.setGameMode(GameMode.SPECTATOR);
        if (staff) {
            spectator.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
            match.staffSpectators().add(spectator.getUniqueId());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(match.player1()) || p.getUniqueId().equals(match.player2())) {
                    p.hidePlayer(plugin, spectator);
                }
            }
        } else {
            match.spectators().add(spectator.getUniqueId());
        }

        Player target = Bukkit.getPlayer(match.player1());
        if (target != null) spectator.teleport(target);
        sessions.put(spectator.getUniqueId(), new SpectateSession(matchId, loc, staff));
        messages.sendPrefixed(spectator, "spectate.joined", Map.of("match_id", matchId));
        return true;
    }

    public void unspectate(Player player) {
        SpectateSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;

        matchService.getMatchById(session.matchId()).ifPresent(match -> {
            match.spectators().remove(player.getUniqueId());
            match.staffSpectators().remove(player.getUniqueId());
        });

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(session.returnLocation());
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
        plugin.services().lobbyItems().giveLobbyItems(player);
        messages.send(player, "spectate.left");
    }

    public void removeAllSpectators(RankedMatch match) {
        for (UUID uuid : match.spectators()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) unspectate(p);
        }
        for (UUID uuid : match.staffSpectators()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) unspectate(p);
        }
    }

    public boolean isSpectating(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    private record SpectateSession(String matchId, Location returnLocation, boolean staff) {}
}
