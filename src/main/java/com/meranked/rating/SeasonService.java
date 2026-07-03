package com.meranked.rating;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.database.DatabaseService;
import org.bukkit.configuration.file.FileConfiguration;

public final class SeasonService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private int currentSeasonId = 1;
    private String currentSeasonName = "Season 1";

    public SeasonService(MERankedPlugin plugin, ConfigService configService, DatabaseService database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        reload();
    }

    public void reload() {
        FileConfiguration config = configService.get("seasons.yml");
        currentSeasonId = config.getInt("current-season", 1);
        var seasonSec = config.getConfigurationSection("seasons." + currentSeasonId);
        if (seasonSec != null) {
            currentSeasonName = seasonSec.getString("name", "Season " + currentSeasonId);
        }
    }

    public int currentSeasonId() {
        return currentSeasonId;
    }

    public String currentSeasonName() {
        return currentSeasonName;
    }

    public void startSeason(String name) {
        currentSeasonId++;
        currentSeasonName = name;
        FileConfiguration config = configService.get("seasons.yml");
        config.set("current-season", currentSeasonId);
        config.set("seasons." + currentSeasonId + ".name", name);
        config.set("seasons." + currentSeasonId + ".active", true);
        configService.save("seasons.yml", config);
        database.executeAsync(conn -> {
            try (var ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO ranked_seasons (season_id, name, start_date, active) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, currentSeasonId);
                ps.setString(2, name);
                ps.setLong(3, System.currentTimeMillis());
                ps.setBoolean(4, true);
                ps.executeUpdate();
            }
        });
    }

    public void endSeason() {
        FileConfiguration config = configService.get("seasons.yml");
        config.set("seasons." + currentSeasonId + ".active", false);
        config.set("seasons." + currentSeasonId + ".end-date", System.currentTimeMillis());
        configService.save("seasons.yml", config);
    }
}
