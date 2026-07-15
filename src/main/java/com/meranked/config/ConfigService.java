package com.meranked.config;

import com.meranked.MERankedPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigService {

    private static final List<String> CONFIG_FILES = Arrays.asList(
            "config.yml", "messages.yml", "guis.yml", "tiers.yml", "gamemodes.yml",
            "seasons.yml", "database.yml", "anti-boost.yml", "anti-dodge.yml",
            "kits.yml", "kit-editor.yml", "lobby-items.yml", "arenas.yml", "arena-voting.yml",
            "cinematic.yml", "scoreboards.yml", "regions.yml", "spectating.yml",
            "replays.yml", "website.yml", "staff-alerts.yml", "suspicion.yml",
            "utility.yml", "matchmaking.yml", "settings.yml",
            "punishments.yml", "rank-progress.yml", "match-quality.yml",
            "placement-scaling.yml", "rollback.yml", "evidence.yml",
            "staff-center.yml", "restart-protection.yml", "best-of-three.yml",
            "streak-pressure.yml", "friend-farming.yml", "queue-ghosting.yml",
            "alt-lock.yml", "kit-checksum.yml", "identity-card.yml", "demotion.yml",
            "placement-behavior.yml", "hidden-mmr.yml", "knockback-sync.yml",
            "behavior-fingerprint.yml", "fairness-dashboard.yml", "coaching-insights.yml"
    );

    private final MERankedPlugin plugin;
    private final File dataFolder;
    // In-memory cache: parsing YAML from disk on every access would be catastrophic under load.
    private final Map<String, FileConfiguration> cache = new ConcurrentHashMap<>();

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
        plugin.getLogger().info("Loaded " + CONFIG_FILES.size() + " configuration files.");
    }

    public int configFileCount() {
        return CONFIG_FILES.size();
    }

    public FileConfiguration load(String fileName) {
        File file = new File(dataFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        cache.put(fileName, config);
        return config;
    }

    public FileConfiguration get(String fileName) {
        return cache.computeIfAbsent(fileName,
                k -> YamlConfiguration.loadConfiguration(new File(dataFolder, k)));
    }

    public void save(String fileName, FileConfiguration config) {
        try {
            config.save(new File(dataFolder, fileName));
            cache.put(fileName, config);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save " + fileName + ": " + ex.getMessage());
        }
    }

    public void reloadAll() {
        cache.clear();
        loadAll();
    }

    public File getDataFolder() {
        return dataFolder;
    }
}
