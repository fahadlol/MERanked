package com.meranked.matches;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.model.RankedMatch;
import com.meranked.rating.ProfileService;
import com.meranked.util.PlayerFreezeUtil;
import com.meranked.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CinematicService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final MessageService messages;
    private final Map<String, CinematicSession> sessions = new ConcurrentHashMap<>();

    public CinematicService(MERankedPlugin plugin, ConfigService configService, MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
    }

    public void playIntro(RankedMatch match, ProfileService profiles, com.meranked.rating.PlacementService placements, Runnable onComplete) {
        FileConfiguration config = configService.get("cinematic.yml");
        if (!config.getBoolean("enabled", true)) {
            onComplete.run();
            return;
        }

        Player p1 = Bukkit.getPlayer(match.player1());
        Player p2 = Bukkit.getPlayer(match.player2());
        if (p1 == null || p2 == null) {
            onComplete.run();
            return;
        }

        CinematicSession session = new CinematicSession(match.matchId(), Set.of(match.player1(), match.player2()), onComplete);
        sessions.put(match.matchId(), session);

        if (config.getBoolean("freeze-players", true)) {
            PlayerFreezeUtil.setFrozen(p1, true);
            PlayerFreezeUtil.setFrozen(p2, true);
        }

        var prof1 = profiles.getProfile(match.player1(), match.gamemode());
        var prof2 = profiles.getProfile(match.player2(), match.gamemode());

        String title = config.getString("title", messages.raw("cinematic.title"));
        String subtitle = config.getString("subtitle", messages.raw("cinematic.subtitle"))
                .replace("%player1%", p1.getName())
                .replace("%player2%", p2.getName())
                .replace("%tier1%", placements.displayTier(prof1))
                .replace("%tier2%", placements.displayTier(prof2))
                .replace("%streak1%", prof1.streakLabel())
                .replace("%streak2%", prof2.streakLabel());

        TextUtil.title(p1, title, subtitle, 10, 60, 10);
        TextUtil.title(p2, title, subtitle, 10, 60, 10);

        FileConfiguration streakCfg = configService.get("streak-pressure.yml");
        if (streakCfg.getBoolean("streak-pressure.show-in-intro", true)) {
            String line = messages.raw("cinematic.streak-line");
            p1.sendMessage(TextUtil.parse(line.replace("%player%", p1.getName())
                    .replace("%tier%", placements.displayTier(prof1)).replace("%streak%", prof1.streakLabel())));
            p1.sendMessage(TextUtil.parse(line.replace("%player%", p2.getName())
                    .replace("%tier%", placements.displayTier(prof2)).replace("%streak%", prof2.streakLabel())));
            p2.sendMessage(TextUtil.parse(line.replace("%player%", p1.getName())
                    .replace("%tier%", placements.displayTier(prof1)).replace("%streak%", prof1.streakLabel())));
            p2.sendMessage(TextUtil.parse(line.replace("%player%", p2.getName())
                    .replace("%tier%", placements.displayTier(prof2)).replace("%streak%", prof2.streakLabel())));
        }

        if (config.getBoolean("shift-to-skip", true)) {
            String actionbar = config.getString("skip-actionbar", messages.raw("cinematic.skip-actionbar"));
            p1.sendActionBar(TextUtil.parse(actionbar));
            p2.sendActionBar(TextUtil.parse(actionbar));
        }

        long durationTicks = config.getLong("duration-seconds", 6) * 20;
        BukkitTask task = plugin.tasks().runSyncLater(() -> finish(match.matchId(), onComplete), durationTicks);
        session.task = task;
    }

    public void trySkip(Player player, RankedMatch match) {
        FileConfiguration config = configService.get("cinematic.yml");
        if (!config.getBoolean("shift-to-skip", true)) return;
        CinematicSession session = sessions.get(match.matchId());
        if (session == null) return;
        if (!session.skipped.add(player.getUniqueId())) return;

        boolean requireBoth = config.getBoolean("require-both-skip", false);
        if (requireBoth && session.skipped.size() < 2) {
            // Notify both players that one voted to skip
            String msg = messages.raw("cinematic.skip-vote").replace("%player%", player.getName());
            for (UUID uuid : session.players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(messages.format(msg));
            }
            return;
        }
        finish(match.matchId(), session.onComplete);
    }

    private void finish(String matchId, Runnable onComplete) {
        CinematicSession session = sessions.remove(matchId);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        for (UUID uuid : session.players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerFreezeUtil.setFrozen(player, false);
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        if (onComplete != null) onComplete.run();
    }

    public boolean isInCinematic(UUID uuid) {
        return sessions.values().stream().anyMatch(s -> s.players.contains(uuid));
    }

    private static final class CinematicSession {
        private final String matchId;
        private final Set<UUID> players;
        private final Set<UUID> skipped = ConcurrentHashMap.newKeySet();
        private final Runnable onComplete;
        private BukkitTask task;

        private CinematicSession(String matchId, Set<UUID> players, Runnable onComplete) {
            this.matchId = matchId;
            this.players = players;
            this.onComplete = onComplete;
        }
    }
}
