package com.meranked.scoreboard;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.model.QueueEntry;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedPlayer;
import com.meranked.model.RankedProfile;
import com.meranked.rating.RatingService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardService {

    private static final String LOADING_TIER = "Loading...";

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final ServiceRegistry services;
    private BukkitTask task;
    private final Set<UUID> profileLoadRequested = ConcurrentHashMap.newKeySet();

    public ScoreboardService(MERankedPlugin plugin, ConfigService configService, ServiceRegistry services) {
        this.plugin = plugin;
        this.configService = configService;
        this.services = services;
    }

    public void start() {
        FileConfiguration config = configService.get("scoreboards.yml");
        if (!config.getBoolean("enabled", true)) return;
        task = plugin.tasks().runSyncTimer(this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        profileLoadRequested.clear();
    }

    public void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        if (services.kitEditor().isEditing(player.getUniqueId())) return;
        Optional<RankedMatch> match = services.matches().getMatch(player.getUniqueId());
        if (match.isPresent()) {
            applyMatchBoard(player, match.get());
        } else if (services.queue().isQueued(player.getUniqueId())) {
            applyQueueBoard(player);
        } else {
            applySpawnBoard(player);
        }
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    private void applySpawnBoard(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = configService.get("scoreboards.yml");
        List<String> lines = config.getStringList("spawn.lines");

        RankedPlayer rp = services.profiles().getCachedPlayer(uuid);
        RankedProfile mace = services.profiles().getCachedProfile(uuid, "Mace");
        RankedProfile crystal = services.profiles().getCachedProfile(uuid, "Crystal");

        if (rp == null || mace == null || crystal == null) {
            requestProfileLoad(player);
            renderLoadingSpawnBoard(player, config, lines, rp);
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("region", rp.regionHidden() ? "Hidden" : rp.region());
        ph.put("season", String.valueOf(services.seasons().currentSeasonId()));
        ph.put("mace_tier", services.placements().displayTier(mace));
        ph.put("crystal_tier", services.placements().displayTier(crystal));
        boolean needsProgress = lines.stream().anyMatch(l -> l.contains("%mace_progress%") || l.contains("%crystal_progress%"));
        if (needsProgress) {
            ph.put("mace_progress", mace.ranked() ? com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(mace)) : "");
            ph.put("crystal_progress", crystal.ranked() ? com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(crystal)) : "");
        }
        ph.put("queue_status", services.queue().isQueued(uuid) ? "In Queue" : "Idle");
        setBoard(player, config.getString("spawn.title", "MERanked"), lines, ph);
    }

    private void renderLoadingSpawnBoard(Player player, FileConfiguration config, List<String> lines, RankedPlayer rp) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("region", rp == null ? "Loading..." : (rp.regionHidden() ? "Hidden" : rp.region()));
        ph.put("season", String.valueOf(services.seasons().currentSeasonId()));
        ph.put("mace_tier", LOADING_TIER);
        ph.put("crystal_tier", LOADING_TIER);
        ph.put("mace_progress", "");
        ph.put("crystal_progress", "");
        ph.put("queue_status", services.queue().isQueued(player.getUniqueId()) ? "In Queue" : "Idle");
        setBoard(player, config.getString("spawn.title", "MERanked"), lines, ph);
    }

    private void requestProfileLoad(Player player) {
        UUID uuid = player.getUniqueId();
        if (!profileLoadRequested.add(uuid)) return;
        services.profiles().preloadAsync(uuid, player.getName());
        services.profiles().loadProfileAsync(uuid, "Mace");
        services.profiles().loadProfileAsync(uuid, "Crystal");
        services.profiles().loadPlayerAsync(uuid).whenComplete((ignored, error) ->
                plugin.tasks().runSync(() -> {
                    profileLoadRequested.remove(uuid);
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && online.isOnline()) {
                        refreshPlayer(online);
                    }
                }));
    }

    private void applyQueueBoard(Player player) {
        FileConfiguration config = configService.get("scoreboards.yml");
        Optional<QueueEntry> entry = services.queue().getEntry(player.getUniqueId());
        if (entry.isEmpty()) return;
        QueueEntry e = entry.get();
        Map<String, String> ph = Map.of(
                "gamemode", e.gamemode(),
                "queue_time", String.valueOf(e.queueTimeSeconds()),
                "range", String.valueOf(services.matchmaking().getRatingRange(e.queueTimeSeconds()))
        );
        setBoard(player, config.getString("queue.title", "IN QUEUE"), config.getStringList("queue.lines"), ph);
    }

    private void applyMatchBoard(Player player, RankedMatch match) {
        FileConfiguration config = configService.get("scoreboards.yml");
        UUID uuid = player.getUniqueId();
        UUID opponent = match.opponent(uuid);

        RankedProfile self = services.profiles().getCachedProfile(uuid, match.gamemode());
        RankedProfile opp = services.profiles().getCachedProfile(opponent, match.gamemode());

        if (self == null || opp == null) {
            services.profiles().loadProfileAsync(uuid, match.gamemode());
            services.profiles().loadProfileAsync(opponent, match.gamemode()).whenComplete((ignored, error) ->
                    plugin.tasks().runSync(() -> {
                        Player online = Bukkit.getPlayer(uuid);
                        if (online != null && online.isOnline()) refreshPlayer(online);
                    }));
            Map<String, String> ph = new HashMap<>();
            ph.put("gamemode", match.gamemode());
            ph.put("opponent", Bukkit.getOfflinePlayer(opponent).getName());
            ph.put("tier", LOADING_TIER);
            ph.put("opponent_tier", LOADING_TIER);
            ph.put("match_time", com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()));
            ph.put("ping", String.valueOf(player.getPing()));
            ph.put("rating", "Loading...");
            ph.put("win_gain", "0");
            ph.put("loss_loss", "0");
            ph.put("player_rounds", String.valueOf(match.roundWins(uuid)));
            ph.put("opponent_rounds", String.valueOf(match.roundWins(opponent)));
            ph.put("round", String.valueOf(match.currentRound()));
            ph.put("bo3", match.bestOfThree() ? "Bo3" : "Bo1");
            setBoard(player, config.getString("match.title", "RANKED DUEL"), config.getStringList("match.lines"), ph);
            return;
        }

        RatingService.PotentialChange potential = services.rating().calculatePotential(self, opp);

        Map<String, String> ph = new HashMap<>();
        ph.put("gamemode", match.gamemode());
        ph.put("opponent", Bukkit.getOfflinePlayer(opponent).getName());
        ph.put("tier", services.placements().displayTier(self));
        ph.put("opponent_tier", services.placements().displayTier(opp));
        ph.put("match_time", com.meranked.util.TextUtil.formatMatchTime(match.durationMillis()));
        ph.put("ping", String.valueOf(player.getPing()));
        ph.put("rating", self.inPlacements(services.profiles().placementRequired(match.gamemode())) ? "Hidden" : String.valueOf(Math.round(self.rating())));
        ph.put("win_gain", String.valueOf(potential.winGain()));
        ph.put("loss_loss", String.valueOf(potential.lossLoss()));
        ph.put("player_rounds", String.valueOf(match.roundWins(uuid)));
        ph.put("opponent_rounds", String.valueOf(match.roundWins(opponent)));
        ph.put("round", String.valueOf(match.currentRound()));
        ph.put("bo3", match.bestOfThree() ? "Bo3" : "Bo1");

        setBoard(player, config.getString("match.title", "RANKED DUEL"), config.getStringList("match.lines"), ph);
    }

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private static final String[] ENTRIES = buildEntries();

    private static String[] buildEntries() {
        org.bukkit.ChatColor[] colors = org.bukkit.ChatColor.values();
        String[] entries = new String[Math.min(15, colors.length)];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = colors[i].toString();
        }
        return entries;
    }

    /**
     * Renders the sidebar for a player. Reuses a single {@link Scoreboard} per player and updates lines
     * through teams so updates are flicker-free and allocation-free (safe for 100+ concurrent players).
     */
    private void setBoard(Player player, String title, List<String> lines, Map<String, String> placeholders) {
        Scoreboard board = boards.get(player.getUniqueId());
        Objective obj;
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            obj = board.registerNewObjective("meranked", Criteria.DUMMY, com.meranked.util.TextUtil.parse(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            boards.put(player.getUniqueId(), board);
        } else {
            obj = board.getObjective("meranked");
            if (obj == null) {
                obj = board.registerNewObjective("meranked", Criteria.DUMMY, com.meranked.util.TextUtil.parse(title));
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else {
                obj.displayName(com.meranked.util.TextUtil.parse(title));
            }
        }
        if (player.getScoreboard() != board) player.setScoreboard(board);

        int count = Math.min(lines.size(), ENTRIES.length);
        for (int i = count; i < ENTRIES.length; i++) {
            String entry = ENTRIES[i];
            board.resetScores(entry);
            Team stale = board.getTeam("l" + i);
            if (stale != null) stale.unregister();
        }
        for (int i = 0; i < count; i++) {
            String resolved = lines.get(i);
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                resolved = resolved.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
            String entry = ENTRIES[i];
            Team team = board.getTeam("l" + i);
            if (team == null) {
                team = board.registerNewTeam("l" + i);
                team.addEntry(entry);
            }
            team.prefix(com.meranked.util.TextUtil.parse(resolved));
            Score s = obj.getScore(entry);
            if (s.getScore() != count - i) s.setScore(count - i);
        }
    }

    /** Releases the cached scoreboard for a player who has left the server. */
    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        profileLoadRequested.remove(player.getUniqueId());
    }
}
