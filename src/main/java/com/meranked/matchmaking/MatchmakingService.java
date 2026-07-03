package com.meranked.matchmaking;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.model.QueueEntry;
import com.meranked.queue.QueueService;
import com.meranked.rating.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MatchmakingService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final QueueService queueService;
    private final ProfileService profileService;
    private ServiceRegistry services;
    private BukkitTask task;
    private final Set<String> recentPairs = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet();

    public MatchmakingService(MERankedPlugin plugin, ConfigService configService,
                              QueueService queueService, ProfileService profileService) {
        this.plugin = plugin;
        this.configService = configService;
        this.queueService = queueService;
        this.profileService = profileService;
    }

    public void bindServices(ServiceRegistry services) {
        this.services = services;
    }

    public void start() {
        FileConfiguration config = configService.get("matchmaking.yml");
        long interval = config.getLong("tick-interval-ms", 500) / 50;
        task = plugin.tasks().runSyncTimer(this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        if (services == null) return;
        for (String gamemode : profileService.enabledGamemodes()) {
            List<QueueEntry> queue = new ArrayList<>(queueService.getQueue(gamemode));
            queue.sort(Comparator.comparingLong(QueueEntry::joinedAt));
            Set<UUID> matched = new HashSet<>();

            for (QueueEntry entry : queue) {
                if (matched.contains(entry.uuid())) continue;
                Player player = Bukkit.getPlayer(entry.uuid());
                if (player == null || !player.isOnline()) {
                    queueService.removeFromQueue(entry.uuid());
                    continue;
                }

                Optional<QueueEntry> opponent = findOpponent(entry, queue, matched);
                if (opponent.isEmpty()) continue;

                matched.add(entry.uuid());
                matched.add(opponent.get().uuid());
                queueService.removeFromQueue(entry.uuid());
                queueService.removeFromQueue(opponent.get().uuid());

                String pairKey = pairKey(entry.uuid(), opponent.get().uuid());
                recentPairs.add(pairKey);

                int range = getRatingRange(entry.queueTimeSeconds());
                services.matches().createMatch(gamemode, entry.uuid(), opponent.get().uuid(), range);
            }
        }
    }

    private Optional<QueueEntry> findOpponent(QueueEntry seeker, List<QueueEntry> queue, Set<UUID> matched) {
        int range = getRatingRange(seeker.queueTimeSeconds());
        FileConfiguration config = configService.get("matchmaking.yml");
        boolean preferConfidence = config.getBoolean("prefer.similar-confidence", true);
        double confidenceWeight = config.getDouble("prefer.confidence-weight", 150.0);

        return queue.stream()
                .filter(e -> !e.uuid().equals(seeker.uuid()))
                .filter(e -> !matched.contains(e.uuid()))
                .filter(e -> Bukkit.getPlayer(e.uuid()) != null)
                .filter(e -> Math.abs(e.rating() - seeker.rating()) <= range)
                .filter(e -> !recentPairs.contains(pairKey(seeker.uuid(), e.uuid())))
                .filter(e -> {
                    if (!config.getBoolean("prefer.different-ip", true)) return true;
                    return seeker.ip() == null || e.ip() == null || !seeker.ip().equals(e.ip());
                })
                .min(Comparator.comparingDouble(e -> matchScore(seeker, e, preferConfidence, confidenceWeight)));
    }

    /** Lower is better: pure rating gap, plus a soft penalty when confidence labels differ. */
    private double matchScore(QueueEntry seeker, QueueEntry candidate, boolean preferConfidence, double confidenceWeight) {
        double score = Math.abs(candidate.rating() - seeker.rating());
        if (preferConfidence && seeker.confidence() != null
                && !seeker.confidence().equalsIgnoreCase(candidate.confidence())) {
            score += confidenceWeight;
        }
        return score;
    }

    public int getRatingRange(long queueSeconds) {
        FileConfiguration config = configService.get("matchmaking.yml");
        var ranges = config.getMapList("ranges");
        int ratingRange = 400;
        for (var map : ranges) {
            long min = ((Number) map.get("min-seconds")).longValue();
            long max = ((Number) map.get("max-seconds")).longValue();
            if (queueSeconds >= min && queueSeconds <= max) {
                ratingRange = ((Number) map.get("rating-range")).intValue();
            }
        }
        return ratingRange;
    }

    private String pairKey(UUID a, UUID b) {
        if (a.compareTo(b) < 0) return a + ":" + b;
        return b + ":" + a;
    }
}
