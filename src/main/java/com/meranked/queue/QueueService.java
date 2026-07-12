package com.meranked.queue;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.model.QueueEntry;
import com.meranked.rating.PlacementScalingService;
import com.meranked.rating.ProfileService;
import com.meranked.rating.TierService;
import com.meranked.staff.BanService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final ProfileService profileService;
    private final AntiDodgeService antiDodgeService;
    private final MessageService messages;
    private final BanService banService;
    private final PlacementScalingService placementScaling;
    private final Map<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();
    private QueueLockHolder lockHolder;

    public QueueService(MERankedPlugin plugin, ConfigService configService, ProfileService profileService,
                        AntiDodgeService antiDodgeService, MessageService messages, BanService banService,
                        PlacementScalingService placementScaling) {
        this.plugin = plugin;
        this.configService = configService;
        this.profileService = profileService;
        this.antiDodgeService = antiDodgeService;
        this.messages = messages;
        this.banService = banService;
        this.placementScaling = placementScaling;
    }

    public void setLockHolder(QueueLockHolder holder) {
        this.lockHolder = holder;
    }

    /** Supplies a queue-lock reason if the queue is currently locked (restart protection / manual). */
    public interface QueueLockHolder {
        String lockReason();
    }

    public boolean joinQueue(Player player, String gamemode) {
        if (!profileService.enabledGamemodes().contains(gamemode)) {
            messages.sendPrefixed(player, "queue.invalid-gamemode", Map.of("gamemode", gamemode));
            return false;
        }
        if (lockHolder != null && !player.hasPermission("meranked.bypass.queue")) {
            String reason = lockHolder.lockReason();
            if (reason != null) {
                messages.sendPrefixed(player, "queue.locked", Map.of("reason", reason));
                return false;
            }
        }
        if (banService.isBanned(player.getUniqueId())) {
            messages.sendPrefixed(player, "queue.banned",
                    Map.of("reason", banService.banReason(player.getUniqueId())));
            return false;
        }
        if (plugin.services().punishments().isQueueBanned(player.getUniqueId())) {
            messages.sendPrefixed(player, "queue.banned", Map.of("reason", "Queue ban"));
            return false;
        }
        if (isQueued(player.getUniqueId())) {
            messages.send(player, "queue.already-queued");
            return false;
        }
        if (!antiDodgeService.canQueue(player.getUniqueId()) && !player.hasPermission("meranked.bypass.dodge")) {
            antiDodgeService.queueBlockReason(player.getUniqueId()).ifPresent(reason -> {
                if (reason.startsWith("hidden:")) {
                    long sec = Long.parseLong(reason.split(":")[1]) / 1000;
                    messages.sendPrefixed(player, "queue.hidden", Map.of("time", com.meranked.util.TextUtil.formatDuration(sec)));
                } else if (reason.startsWith("cooldown:")) {
                    long sec = Long.parseLong(reason.split(":")[1]) / 1000;
                    messages.sendPrefixed(player, "queue.cooldown", Map.of("time", com.meranked.util.TextUtil.formatDuration(sec)));
                }
            });
            return false;
        }

        profileService.ensurePlayer(player);
        var profile = profileService.getProfile(player.getUniqueId(), gamemode);
        TierService tierService = profileService.tierService();
        String confidence = tierService.getConfidenceLabel(profile.ratingDeviation());

        // Alt confidence lock: classify trust and possibly lower placement cap on first placement queue.
        if (profile.inPlacements(profileService.placementRequired(gamemode)) && profile.placementCapOverride() == null) {
            plugin.services().altLock().evaluate(player, profile);
        }

        // Placement players: hidden difficulty rating + behavioral bias; ranked: hidden MMR when enabled.
        int required = profileService.placementRequired(gamemode);
        boolean inPlacements = profile.inPlacements(required);
        double matchmakingRating;
        if (inPlacements) {
            matchmakingRating = plugin.services().placementBehavior().adjustedHiddenRating(profile, placementScaling);
        } else {
            matchmakingRating = plugin.services().hiddenMmr().matchmakingRating(profile, false, profile.rating());
        }

        var rp = profileService.getPlayer(player.getUniqueId());
        String region = rp.regionHidden() ? "Hidden" : rp.region();
        int ping = player.getPing();

        QueueEntry entry = new QueueEntry(
                player.getUniqueId(),
                gamemode,
                matchmakingRating,
                profile.ratingDeviation(),
                confidence,
                System.currentTimeMillis(),
                player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress(),
                region,
                ping
        );

        queues.computeIfAbsent(gamemode, g -> new ArrayList<>()).add(entry);
        messages.sendPrefixed(player, "queue.joined", Map.of("gamemode", gamemode));
        if (!plugin.services().kits().hasKit(player.getUniqueId(), gamemode)) {
            messages.sendPrefixed(player, "kit-editor.empty", Map.of("gamemode", gamemode));
            plugin.tasks().runSyncLater(() -> plugin.services().kitEditor().enterEditor(player, gamemode), 5L);
        }
        return true;
    }

    public void leaveQueue(UUID uuid) {
        String gamemode = getGamemode(uuid).orElse(null);
        for (List<QueueEntry> list : queues.values()) {
            list.removeIf(e -> e.uuid().equals(uuid));
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) messages.send(player, "queue.left");
        if (gamemode != null) {
            plugin.services().detection().recordQueueLeave(uuid, gamemode);
        }
    }

    public boolean isQueued(UUID uuid) {
        return queues.values().stream().anyMatch(list -> list.stream().anyMatch(e -> e.uuid().equals(uuid)));
    }

    public Optional<QueueEntry> getEntry(UUID uuid) {
        for (List<QueueEntry> list : queues.values()) {
            for (QueueEntry entry : list) {
                if (entry.uuid().equals(uuid)) return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<String> getGamemode(UUID uuid) {
        return getEntry(uuid).map(QueueEntry::gamemode);
    }

    public List<QueueEntry> getQueue(String gamemode) {
        return new ArrayList<>(queues.getOrDefault(gamemode, List.of()));
    }

    public void removeFromQueue(UUID uuid) {
        for (List<QueueEntry> list : queues.values()) {
            list.removeIf(e -> e.uuid().equals(uuid));
        }
    }

    public Map<String, List<QueueEntry>> allQueues() {
        return queues;
    }
}
