package com.meranked.matchmaking;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.matches.MatchQualityService;
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
    private final java.util.Map<String, Long> recentPairs = new java.util.concurrent.ConcurrentHashMap<>();

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
        long now = System.currentTimeMillis();
        recentPairs.values().removeIf(expiry -> expiry <= now);
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

                Optional<Candidate> opponent = findOpponent(entry, queue, matched);
                if (opponent.isEmpty()) continue;

                matched.add(entry.uuid());
                matched.add(opponent.get().entry().uuid());
                queueService.removeFromQueue(entry.uuid());
                queueService.removeFromQueue(opponent.get().entry().uuid());

                long cooldownMs = configService.get("matchmaking.yml")
                        .getLong("rematch-cooldown-seconds", 30) * 1000L;
                recentPairs.put(pairKey(entry.uuid(), opponent.get().entry().uuid()),
                        System.currentTimeMillis() + cooldownMs);

                int range = getRatingRange(entry.queueTimeSeconds());
                services.matches().createMatch(gamemode, entry.uuid(), opponent.get().entry().uuid(),
                        range, opponent.get().matchmakingReason());
            }
        }
    }

    private Optional<Candidate> findOpponent(QueueEntry seeker, List<QueueEntry> queue, Set<UUID> matched) {
        int range = getRatingRange(seeker.queueTimeSeconds());
        FileConfiguration config = configService.get("matchmaking.yml");
        boolean qualityMax = config.getBoolean("quality-maximizing.enabled", true);
        int minQuality = config.getInt("quality-maximizing.min-quality-percent", 40);

        List<QueueEntry> candidates = queue.stream()
                .filter(e -> !e.uuid().equals(seeker.uuid()))
                .filter(e -> !matched.contains(e.uuid()))
                .filter(e -> Bukkit.getPlayer(e.uuid()) != null)
                .filter(e -> Math.abs(e.rating() - seeker.rating()) <= range)
                .filter(e -> !recentPairs.containsKey(pairKey(seeker.uuid(), e.uuid())))
                .filter(e -> {
                    if (!config.getBoolean("prefer.different-ip", true)) return true;
                    return seeker.ip() == null || e.ip() == null || !seeker.ip().equals(e.ip());
                })
                .filter(e -> regionCompatible(seeker, e))
                .filter(e -> pingCompatible(seeker, e))
                .toList();

        if (candidates.isEmpty()) return Optional.empty();

        if (qualityMax && services.matchQuality().enabled()) {
            Candidate best = null;
            for (QueueEntry c : candidates) {
                int recent = services.antiBoost().recentOpponentCount(seeker.uuid(), c.uuid());
                MatchQualityService.QualityResult q = services.matchQuality()
                        .evaluateForQueue(seeker, c, range, recent);
                if (q.quality() < minQuality) continue;
                String reason = services.matchQuality().buildMatchmakingReason(q, seeker, c);
                if (best == null || q.quality() > best.quality()) {
                    best = new Candidate(c, q.quality(), reason);
                }
            }
            if (best != null) return Optional.of(best);
        }

        // Fallback: closest rating + confidence preference
        boolean preferConfidence = config.getBoolean("prefer.similar-confidence", true);
        double confidenceWeight = config.getDouble("prefer.confidence-weight", 150.0);
        QueueEntry pick = candidates.stream()
                .min(Comparator.comparingDouble(e -> matchScore(seeker, e, preferConfidence, confidenceWeight)))
                .orElse(null);
        if (pick == null) return Optional.empty();
        int recent = services.antiBoost().recentOpponentCount(seeker.uuid(), pick.uuid());
        MatchQualityService.QualityResult q = services.matchQuality().evaluateForQueue(seeker, pick, range, recent);
        return Optional.of(new Candidate(pick, q.quality(),
                services.matchQuality().buildMatchmakingReason(q, seeker, pick)));
    }

    private boolean regionCompatible(QueueEntry a, QueueEntry b) {
        FileConfiguration cfg = configService.get("matchmaking.yml");
        if (!cfg.getBoolean("region.enabled", true)) return true;
        long relax = cfg.getLong("region.relax-after-seconds", 45);
        if (a.queueTimeSeconds() >= relax || b.queueTimeSeconds() >= relax) return true;
        if (!cfg.getBoolean("region.prefer-same-region", true)) return true;
        if (a.region() == null || b.region() == null) return true;
        if ("Hidden".equalsIgnoreCase(a.region()) || "Hidden".equalsIgnoreCase(b.region())) return true;
        return a.region().equalsIgnoreCase(b.region());
    }

    private boolean pingCompatible(QueueEntry a, QueueEntry b) {
        FileConfiguration cfg = configService.get("matchmaking.yml");
        if (!cfg.getBoolean("region.enabled", true)) return true;
        int maxCross = cfg.getInt("region.max-cross-region-ping", 150);
        int hardCap = cfg.getInt("region.hard-ping-cap", 250);
        int pingDiff = Math.abs(a.ping() - b.ping());
        if (a.ping() > hardCap || b.ping() > hardCap) return false;
        long relax = cfg.getLong("region.relax-after-seconds", 45);
        if (a.queueTimeSeconds() >= relax && b.queueTimeSeconds() >= relax) return true;
        if (a.region() != null && a.region().equalsIgnoreCase(b.region())) return pingDiff <= maxCross * 2;
        return pingDiff <= maxCross;
    }

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

    private record Candidate(QueueEntry entry, int quality, String matchmakingReason) {}
}
