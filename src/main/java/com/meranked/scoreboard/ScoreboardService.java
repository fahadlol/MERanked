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
        RankedProfile mace = services.profiles().getProfile(player.getUniqueId(), "Mace");
        RankedProfile crystal = services.profiles().getProfile(player.getUniqueId(), "Crystal");
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("region", rp.regionHidden() ? "Hidden" : rp.region());
        ph.put("season", String.valueOf(services.seasons().currentSeasonId()));
        ph.put("mace_tier", services.placements().displayTier(mace));
        ph.put("crystal_tier", services.placements().displayTier(crystal));
        ph.put("mace_progress", mace.ranked() ? com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(mace)) : "");
        ph.put("crystal_progress", crystal.ranked() ? com.meranked.util.TextUtil.stripToLegacy(services.rankProgress().buildBar(crystal)) : "");
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

    private void setBoard(Player player, String title, List<String> lines, Map<String, String> placeholders) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("meranked", Criteria.DUMMY, com.meranked.util.TextUtil.parse(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = lines.size();
        for (String line : lines) {
            String resolved = line;
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                resolved = resolved.replace("%" + e.getKey() + "%", e.getValue());
            }
            Score s = obj.getScore(resolved.length() > 40 ? resolved.substring(0, 40) : resolved);
            s.setScore(score--);
        }
        player.setScoreboard(board);
    }
}
