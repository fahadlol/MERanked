package com.meranked.database;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class RedisService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private JedisPool pool;
    private String prefix;
    private boolean enabled;

    public RedisService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            FileConfiguration config = configService.get("database.yml");
            enabled = config.getBoolean("redis.enabled", false);
            if (!enabled) {
                plugin.getLogger().info("Redis disabled (set redis.enabled in database.yml to enable).");
                return;
            }

            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(16);
            String host = config.getString("redis.host", "localhost");
            int port = config.getInt("redis.port", 6379);
            String password = config.getString("redis.password", "");
            int database = config.getInt("redis.database", 0);
            prefix = config.getString("redis.prefix", "meranked:");

            if (password == null || password.isEmpty()) {
                pool = new JedisPool(poolConfig, host, port, 2000, null, database);
            } else {
                pool = new JedisPool(poolConfig, host, port, 2000, password, database);
            }
            plugin.getLogger().info("Redis connected (" + host + ":" + port + ", db " + database + ").");
        });
    }

    public boolean enabled() {
        return enabled && pool != null;
    }

    public <T> T withJedis(Function<redis.clients.jedis.Jedis, T> function) {
        if (!enabled()) return null;
        try (var jedis = pool.getResource()) {
            return function.apply(jedis);
        }
    }

    public void set(String key, String value) {
        withJedis(j -> {
            j.set(prefix + key, value);
            return null;
        });
    }

    public String get(String key) {
        return withJedis(j -> j.get(prefix + key));
    }

    public void shutdown() {
        if (pool != null) pool.close();
    }
}
