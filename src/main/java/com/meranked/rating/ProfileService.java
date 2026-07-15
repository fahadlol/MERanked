package com.meranked.rating;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import com.meranked.model.RankedPlayer;
import com.meranked.model.RankedProfile;
import com.meranked.util.DatabaseThreading;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileService {

    private final MERankedPlugin plugin;
    private final DatabaseService database;
    private final ConfigService configService;
    private final TierService tierService;
    private final RatingService ratingService;
    private final SeasonService seasonService;
    private final DatabaseThreading threading;

    private final Map<UUID, RankedPlayer> playerCache = new ConcurrentHashMap<>();
    private final Map<String, RankedProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<RankedPlayer>> playerLoads = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<RankedProfile>> profileLoads = new ConcurrentHashMap<>();
    private final List<RankedProfile> pendingWrites = new java.util.concurrent.CopyOnWriteArrayList<>();

    public ProfileService(MERankedPlugin plugin, DatabaseService database, ConfigService configService,
                          TierService tierService, RatingService ratingService, SeasonService seasonService) {
        this.plugin = plugin;
        this.database = database;
        this.configService = configService;
        this.tierService = tierService;
        this.ratingService = ratingService;
        this.seasonService = seasonService;
        this.threading = new DatabaseThreading(plugin);
    }

    public RankedProfile getCachedProfile(UUID uuid, String gamemode) {
        return profileCache.get(cacheKey(uuid, gamemode));
    }

    public RankedPlayer getCachedPlayer(UUID uuid) {
        return playerCache.get(uuid);
    }

    /**
     * Returns a cached profile when available. When not cached, triggers a deduplicated async load and
     * returns a non-cached default placeholder (safe for display only — never persisted).
     */
    public RankedProfile getProfile(UUID uuid, String gamemode) {
        RankedProfile cached = getCachedProfile(uuid, gamemode);
        if (cached != null) return cached;
        loadProfileAsync(uuid, gamemode);
        return defaultProfile(uuid, gamemode);
    }

    public RankedPlayer getPlayer(UUID uuid) {
        RankedPlayer cached = getCachedPlayer(uuid);
        if (cached != null) return cached;
        loadPlayerAsync(uuid);
        return defaultPlayer(uuid);
    }

    public CompletableFuture<RankedProfile> getProfileAsync(UUID uuid, String gamemode) {
        RankedProfile cached = getCachedProfile(uuid, gamemode);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return loadProfileAsync(uuid, gamemode);
    }

    public CompletableFuture<RankedPlayer> getPlayerAsync(UUID uuid) {
        RankedPlayer cached = getCachedPlayer(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return loadPlayerAsync(uuid);
    }

    public CompletableFuture<RankedProfile> loadProfileAsync(UUID uuid, String gamemode) {
        String key = cacheKey(uuid, gamemode);
        RankedProfile cached = profileCache.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        CompletableFuture<RankedProfile> existing = profileLoads.get(key);
        if (existing != null) return existing;

        CompletableFuture<RankedProfile> created = new CompletableFuture<>();
        CompletableFuture<RankedProfile> prior = profileLoads.putIfAbsent(key, created);
        if (prior != null) return prior;

        database.queryAsync("loadProfile", conn -> queryProfile(conn, uuid, gamemode))
                .whenComplete((profile, error) -> {
                    profileLoads.remove(key);
                    if (error != null) {
                        plugin.getLogger().warning("Failed to load profile for " + uuid + " (" + gamemode + "): "
                                + error.getMessage());
                        RankedProfile fallback = defaultProfile(uuid, gamemode);
                        profileCache.put(key, fallback);
                        created.complete(fallback);
                        return;
                    }
                    if (profile == null) {
                        RankedProfile createdProfile = defaultProfile(uuid, gamemode);
                        createdProfile.setSeasonId(seasonService.currentSeasonId());
                        profileCache.put(key, createdProfile);
                        queueSave(createdProfile);
                        created.complete(createdProfile);
                    } else {
                        profileCache.put(key, profile);
                        created.complete(profile);
                    }
                });
        return created;
    }

    public CompletableFuture<RankedPlayer> loadPlayerAsync(UUID uuid) {
        RankedPlayer cached = playerCache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        CompletableFuture<RankedPlayer> existing = playerLoads.get(uuid);
        if (existing != null) return existing;

        CompletableFuture<RankedPlayer> created = new CompletableFuture<>();
        CompletableFuture<RankedPlayer> prior = playerLoads.putIfAbsent(uuid, created);
        if (prior != null) return prior;

        database.queryAsync("loadPlayer", conn -> queryPlayer(conn, uuid))
                .whenComplete((player, error) -> {
                    playerLoads.remove(uuid);
                    if (error != null) {
                        plugin.getLogger().warning("Failed to load player record for " + uuid + ": " + error.getMessage());
                        RankedPlayer fallback = defaultPlayer(uuid);
                        playerCache.put(uuid, fallback);
                        created.complete(fallback);
                        return;
                    }
                    if (player == null) {
                        RankedPlayer newPlayer = defaultPlayer(uuid);
                        playerCache.put(uuid, newPlayer);
                        savePlayerAsync(newPlayer);
                        created.complete(newPlayer);
                    } else {
                        playerCache.put(uuid, player);
                        created.complete(player);
                    }
                });
        return created;
    }

    public void ensurePlayer(Player player) {
        preloadAsync(player.getUniqueId(), player.getName());
    }

    /**
     * Starts asynchronous loading for the player record and all gamemode profiles without blocking login.
     */
    public void preloadAsync(UUID uuid, String name) {
        loadPlayerAsync(uuid).thenCompose(player -> {
            if ("Unknown".equals(player.username()) && name != null) {
                RankedPlayer named = new RankedPlayer(player.uuid(), name, player.region(),
                        player.regionHidden(), player.suspicionScore(), player.createdAt(), player.lastSeen());
                playerCache.put(uuid, named);
                savePlayerAsync(named);
            }
            List<CompletableFuture<RankedProfile>> futures = new ArrayList<>();
            for (String mode : enabledGamemodes()) {
                futures.add(loadProfileAsync(uuid, mode));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Profile preload failed for " + uuid + ": " + error.getMessage());
            }
            threading.runSync(() -> {
                if (!plugin.isEnabled()) return;
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) return;
                plugin.services().scoreboards().refreshPlayer(online);
            });
        });
    }

    /** Frees in-flight load state for a player who left. Cached data is retained until after flush. */
    public void clearInflight(UUID uuid) {
        playerLoads.remove(uuid);
        profileLoads.keySet().removeIf(k -> k.startsWith(uuid + ":"));
    }

    /** Frees cached data for a player who is no longer online (called on quit, after flush). */
    public void unloadPlayer(UUID uuid) {
        clearInflight(uuid);
        playerCache.remove(uuid);
        profileCache.keySet().removeIf(k -> k.startsWith(uuid + ":"));
    }

    public List<String> enabledGamemodes() {
        FileConfiguration gamemodes = configService.get("gamemodes.yml");
        var section = gamemodes.getConfigurationSection("gamemodes");
        List<String> modes = new ArrayList<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.getBoolean(key + ".enabled", true) ||
                        section.getConfigurationSection(key) != null &&
                        gamemodes.getBoolean("gamemodes." + key + ".enabled", true)) {
                    modes.add(key);
                }
            }
        }
        if (modes.isEmpty()) modes = List.of("Mace", "Crystal", "Sword", "Axe", "SMP", "UHC");
        return modes;
    }

    public int placementRequired(String gamemode) {
        FileConfiguration config = configService.get("config.yml");
        FileConfiguration gamemodes = configService.get("gamemodes.yml");
        int perMode = gamemodes.getInt("gamemodes." + gamemode + ".placement-matches", -1);
        return perMode > 0 ? perMode : config.getInt("placement.matches-required", 7);
    }

    public void queueSave(RankedProfile profile) {
        pendingWrites.add(profile);
        profileCache.put(cacheKey(profile.uuid(), profile.gamemode()), profile);
    }

    public void startCacheFlushTask() {
        plugin.tasks().runAsyncTimer(this::flushPendingWrites, 40L, 40L);
    }

    public void flushNow() {
        flushPendingWrites();
    }

    public void shutdown() {
        flushPendingWrites();
    }

    public int cachedProfileCount() {
        return profileCache.size();
    }

    public int cachedPlayerCount() {
        return playerCache.size();
    }

    public int inflightProfileLoads() {
        return profileLoads.size();
    }

    public int inflightPlayerLoads() {
        return playerLoads.size();
    }

    private void flushPendingWrites() {
        if (pendingWrites.isEmpty()) return;
        List<RankedProfile> batch = new ArrayList<>(pendingWrites);
        pendingWrites.clear();
        database.executeAsync("flushProfiles", conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_profiles
                (uuid, gamemode, rating, rating_deviation, volatility, tier, peak_rating, peak_tier,
                 peak_date, season_peak_rating, season_peak_tier, ranked, placement_played, placement_wins,
                 placement_losses, wins, losses, win_streak, best_win_opponent, best_win_tier, last_played,
                 decay_active, rank_protection, season_id, hidden_mmr)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
                for (RankedProfile p : batch) {
                    bindProfile(ps, p);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private void bindProfile(PreparedStatement ps, RankedProfile p) throws java.sql.SQLException {
        ps.setString(1, p.uuid().toString());
        ps.setString(2, p.gamemode());
        ps.setDouble(3, p.rating());
        ps.setDouble(4, p.ratingDeviation());
        ps.setDouble(5, p.volatility());
        ps.setString(6, p.tier());
        ps.setDouble(7, p.peakRating());
        ps.setString(8, p.peakTier());
        ps.setLong(9, p.peakDate());
        ps.setDouble(10, p.seasonPeakRating());
        ps.setString(11, p.seasonPeakTier());
        ps.setBoolean(12, p.ranked());
        ps.setInt(13, p.placementPlayed());
        ps.setInt(14, p.placementWins());
        ps.setInt(15, p.placementLosses());
        ps.setInt(16, p.wins());
        ps.setInt(17, p.losses());
        ps.setInt(18, p.winStreak());
        ps.setString(19, p.bestWinOpponent());
        ps.setString(20, p.bestWinTier());
        ps.setLong(21, p.lastPlayed());
        ps.setBoolean(22, p.decayActive());
        ps.setInt(23, p.rankProtection());
        ps.setInt(24, p.seasonId());
        ps.setDouble(25, p.hiddenMmr());
    }

    private RankedProfile queryProfile(java.sql.Connection conn, UUID uuid, String gamemode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM ranked_profiles WHERE uuid = ? AND gamemode = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, gamemode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapProfile(rs);
            }
        }
        return null;
    }

    private RankedPlayer queryPlayer(java.sql.Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ranked_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RankedPlayer(
                            uuid,
                            rs.getString("username"),
                            rs.getString("region"),
                            rs.getBoolean("region_hidden"),
                            rs.getInt("suspicion_score"),
                            rs.getLong("created_at"),
                            rs.getLong("last_seen")
                    );
                }
            }
        }
        return null;
    }

    private RankedProfile defaultProfile(UUID uuid, String gamemode) {
        RankedProfile profile = new RankedProfile(uuid, gamemode);
        profile.setSeasonId(seasonService.currentSeasonId());
        return profile;
    }

    private RankedPlayer defaultPlayer(UUID uuid) {
        return RankedPlayer.create(uuid, "Unknown");
    }

    private RankedProfile mapProfile(ResultSet rs) throws java.sql.SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String gamemode = rs.getString("gamemode");
        RankedProfile p = new RankedProfile(uuid, gamemode);
        p.setRating(rs.getDouble("rating"));
        p.setRatingDeviation(rs.getDouble("rating_deviation"));
        p.setVolatility(rs.getDouble("volatility"));
        p.setTier(rs.getString("tier"));
        p.setPeakRating(rs.getDouble("peak_rating"));
        p.setPeakTier(rs.getString("peak_tier"));
        p.setPeakDate(rs.getLong("peak_date"));
        p.setSeasonPeakRating(rs.getDouble("season_peak_rating"));
        p.setSeasonPeakTier(rs.getString("season_peak_tier"));
        p.setRanked(rs.getBoolean("ranked"));
        p.setPlacementPlayed(rs.getInt("placement_played"));
        p.setPlacementWins(rs.getInt("placement_wins"));
        p.setPlacementLosses(rs.getInt("placement_losses"));
        p.setWins(rs.getInt("wins"));
        p.setLosses(rs.getInt("losses"));
        p.setWinStreak(rs.getInt("win_streak"));
        p.setBestWinOpponent(rs.getString("best_win_opponent"));
        p.setBestWinTier(rs.getString("best_win_tier"));
        p.setLastPlayed(rs.getLong("last_played"));
        p.setDecayActive(rs.getBoolean("decay_active"));
        p.setRankProtection(rs.getInt("rank_protection"));
        p.setSeasonId(rs.getInt("season_id"));
        p.setHiddenMmr(rs.getDouble("hidden_mmr"));
        return p;
    }

    public void savePlayerAsync(RankedPlayer player) {
        playerCache.put(player.uuid(), player);
        database.executeAsync("savePlayer", conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_players
                (uuid, username, region, region_hidden, suspicion_score, created_at, last_seen)
                VALUES (?,?,?,?,?,?,?)
                """)) {
                ps.setString(1, player.uuid().toString());
                ps.setString(2, player.username());
                ps.setString(3, player.region());
                ps.setBoolean(4, player.regionHidden());
                ps.setInt(5, player.suspicionScore());
                ps.setLong(6, player.createdAt());
                ps.setLong(7, player.lastSeen());
                ps.executeUpdate();
            }
        });
    }

    public void updatePeak(RankedProfile profile) {
        if (profile.rating() > profile.peakRating()) {
            profile.setPeakRating(profile.rating());
            profile.setPeakTier(profile.tier());
            profile.setPeakDate(System.currentTimeMillis());
        }
        if (profile.rating() > profile.seasonPeakRating()) {
            profile.setSeasonPeakRating(profile.rating());
            profile.setSeasonPeakTier(profile.tier());
        }
    }

    public void applyRankProtection(RankedProfile profile, String oldTier, String newTier, FileConfiguration config) {
        if (!config.getBoolean("rank-protection.enabled", true)) return;
        if (tierIndex(newTier) > tierIndex(oldTier)) {
            profile.setRankProtection(config.getInt("rank-protection.matches-after-promotion", 2));
        }
    }

    public boolean canDemote(RankedProfile profile, String newTier, FileConfiguration config) {
        if (profile.rankProtection() > 0 && config.getBoolean("rank-protection.protect-from-demotion-only", true)) {
            return false;
        }
        return true;
    }

    public void decreaseRankProtection(RankedProfile profile, boolean lost, FileConfiguration config) {
        if (profile.rankProtection() <= 0) return;
        boolean decrease = (lost && config.getBoolean("rank-protection.decrease-on-loss", true))
                || config.getBoolean("rank-protection.decrease-on-match", false);
        if (decrease) profile.setRankProtection(profile.rankProtection() - 1);
    }

    private int tierIndex(String tier) {
        return tierService.allTiers().stream().map(TierService.TierDefinition::id).toList().indexOf(tier);
    }

    private static String cacheKey(UUID uuid, String gamemode) {
        return uuid + ":" + gamemode;
    }

    public TierService tierService() { return tierService; }
    public RatingService ratingService() { return ratingService; }
}
