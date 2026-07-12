package com.meranked.scoreboard;

import com.meranked.MERankedPlugin;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.config.ConfigService;
import com.meranked.model.QueueEntry;
import com.meranked.model.RankedMatch;
import com.meranked.model.RankedProfile;
import com.meranked.rating.RatingService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class ScoreboardService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final ServiceRegistry services;
    private BukkitTask task;

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
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (services.kitEditor().isEditing(player.getUniqueId())) continue;
            Optional<RankedMatch> match = services.matches().getMatch(player.getUniqueId());
            if (match.isPresent()) {
                applyMatchBoard(player, match.get());
            } else if (services.queue().isQueued(player.getUniqueId())) {
                applyQueueBoard(player);
            } else {
                applySpawnBoard(player);
            }
        }
    }

    private void applySpawnBoard(Player player) {
        FileConfiguration config = configService.get("scoreboards.yml");
        List<String> lines = config.getStringList("spawn.lines");
        var rp = services.profiles().getPlayer(player.getUniqueId());
        var modes = services.profiles().enabledGamemodes();
        String mode1 = modes.size() > 0 ? modes.get(0) : "Mace";
        String mode2 = modes.size() > 1 ? modes.get(1) : mode1;
        RankedProfile first = services.profiles().getProfile(player.getUniqueId(), mode1);
        RankedProfile second = services.profiles().getProfile(player.getUniqueId(), mode2);
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("region", rp.regionHidden() ? "Hidden" : rp.region());
        ph.put("season", String.valueOf(services.seasons().currentSeasonId()));
        ph.put("mace_tier", services.placements().displayTier(first));
        ph.put("crystal_tier", services.placements().displayTier(second));
        ph.put("mode1_tier", services.placements().displayTier(first));
        ph.put("mode2_tier", services.placements().displayTier(second));
        ph.put("mode1", mode1);
        ph.put("mode2", mode2);
        boolean needsProgress = lines.stream().anyMatch(l -> l.contains("%mace_progress%") || l.contains("%crystal_progress%"));
        if (needsProgress) {
            ph.put("mace_progress", first.ranked() ? com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(first)) : "");
            ph.put("crystal_progress", second.ranked() ? com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(second)) : "");
        }
        ph.put("queue_status", services.queue().isQueued(player.getUniqueId()) ? "In Queue" : "Idle");
        setBoard(player, config.getString("spawn.title", "MERanked"), lines, ph);
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
        UUID opponent = match.opponent(player.getUniqueId());
        RankedProfile self = services.profiles().getProfile(player.getUniqueId(), match.gamemode());
        RankedProfile opp = services.profiles().getProfile(opponent, match.gamemode());
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
        ph.put("player_rounds", String.valueOf(match.roundWins(player.getUniqueId())));
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
    }
}
