package com.meranked.config;

import com.meranked.MERankedPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class ConfigService {

    private static final List<String> CONFIG_FILES = Arrays.asList(
            "config.yml", "messages.yml", "guis.yml", "tiers.yml", "gamemodes.yml",
            "seasons.yml", "database.yml", "anti-boost.yml", "anti-dodge.yml",
            "kits.yml", "kit-editor.yml", "arenas.yml", "arena-voting.yml",
            "cinematic.yml", "scoreboards.yml", "regions.yml", "spectating.yml",
            "replays.yml", "website.yml", "staff-alerts.yml", "suspicion.yml",
            "utility.yml", "matchmaking.yml", "settings.yml",
            "punishments.yml", "rank-progress.yml", "match-quality.yml",
            "placement-scaling.yml", "rollback.yml", "evidence.yml",
            "staff-center.yml", "restart-protection.yml", "best-of-three.yml",
            "streak-pressure.yml", "friend-farming.yml", "queue-ghosting.yml",
            "alt-lock.yml", "kit-checksum.yml", "identity-card.yml", "demotion.yml"
    );

    private final MERankedPlugin plugin;
    private final File dataFolder;

    public ConfigService(MERankedPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
    }

    public void loadAll() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        for (String fileName : CONFIG_FILES) {
            load(fileName);
        }
    }

    public FileConfiguration load(String fileName) {
        File file = new File(dataFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration get(String fileName) {
        return YamlConfiguration.loadConfiguration(new File(dataFolder, fileName));
    }

    public void save(String fileName, FileConfiguration config) {
        try {
            config.save(new File(dataFolder, fileName));
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save " + fileName + ": " + ex.getMessage());
        }
    }

    public void reloadAll() {
        loadAll();
    }

    public File getDataFolder() {
        return dataFolder;
    }
}
