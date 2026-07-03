package com.meranked.redis;

import com.google.gson.Gson;
import com.meranked.MERankedPlugin;
import com.meranked.database.RedisService;
import com.meranked.model.RankedMatch;

import java.util.Collection;
import java.util.Map;

/** Publishes live queue/match data to Redis for website/scaling. */
public final class RedisLiveCache {

    private final MERankedPlugin plugin;
    private final RedisService redis;
    private final Gson gson = new Gson();

    public RedisLiveCache(MERankedPlugin plugin, RedisService redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    public void publishStatus(int online, int liveMatches, Map<String, Integer> queues) {
        if (!redis.enabled()) return;
        redis.set("live:status", gson.toJson(Map.of(
                "online", online,
                "liveMatches", liveMatches,
                "queues", queues,
                "updatedAt", System.currentTimeMillis()
        )));
    }

    public void publishMatches(Collection<RankedMatch> matches) {
        if (!redis.enabled()) return;
        redis.set("live:matches", gson.toJson(matches.stream().map(m -> Map.of(
                "matchId", m.matchId(),
                "gamemode", m.gamemode(),
                "arena", m.arenaName() == null ? "" : m.arenaName(),
                "state", m.state().name()
        )).toList()));
    }

    public String getCachedStatus() {
        return redis.get("live:status");
    }
}
