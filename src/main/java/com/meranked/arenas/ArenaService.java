package com.meranked.arenas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.database.DatabaseService;
import com.meranked.model.Arena;
import com.meranked.model.AlertSeverity;
import com.meranked.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final DatabaseService database;
    private final MessageService messages;
    private final Gson gson = new GsonBuilder().create();
    private final Map<String, Arena> arenaCache = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> inUseClones = new ConcurrentHashMap<>();

    private final ArenaRegenService arenaRegen;

    public ArenaService(MERankedPlugin plugin, ConfigService configService,
                        DatabaseService database, MessageService messages, ArenaRegenService arenaRegen) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.messages = messages;
        this.arenaRegen = arenaRegen;
        loadArenasAsync();
    }

    public Collection<Arena> allArenas() {
        return arenaCache.values();
    }

    public Optional<Arena> getArena(String name) {
        return Optional.ofNullable(arenaCache.get(name.toLowerCase()));
    }

    public Arena createArena(String name) {
        Arena arena = new Arena(name);
        arenaCache.put(name.toLowerCase(), arena);
        saveArenaAsync(arena);
        return arena;
    }

    public void deleteArena(String name) {
        arenaCache.remove(name.toLowerCase());
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ranked_arenas WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        });
    }

    public List<Arena> getAvailableArenas(String gamemode) {
        return arenaCache.values().stream()
                .filter(Arena::enabled)
                .filter(a -> !a.broken())
                .filter(a -> a.isValid())
                .filter(a -> a.supportsGamemode(gamemode))
                .filter(a -> hasAvailableClone(a.name()))
                .toList();
    }

    public List<Arena> pickRandomArenas(String gamemode, int count) {
        List<Arena> available = new ArrayList<>(getAvailableArenas(gamemode));
        Collections.shuffle(available);
        return available.subList(0, Math.min(count, available.size()));
    }

    public Optional<Integer> reserveClone(String arenaName) {
        FileConfiguration config = configService.get("arenas.yml");
        int copies = config.getInt("cloning.copies-per-arena", 3);
        Set<Integer> inUse = inUseClones.computeIfAbsent(arenaName.toLowerCase(), k -> ConcurrentHashMap.newKeySet());
        for (int i = 0; i < copies; i++) {
            if (!inUse.contains(i)) {
                inUse.add(i);
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public void releaseClone(String arenaName, int cloneIndex) {
        Set<Integer> inUse = inUseClones.get(arenaName.toLowerCase());
        if (inUse != null) inUse.remove(cloneIndex);
    }

    private boolean hasAvailableClone(String arenaName) {
        return reserveClone(arenaName).map(i -> {
            releaseClone(arenaName, i);
            return true;
        }).orElse(false);
    }

    public Location offsetLocation(Location base, String arenaName, int cloneIndex) {
        FileConfiguration config = configService.get("arenas.yml");
        int spacing = config.getInt("cloning.spacing", 300);
        Location loc = base.clone();
        loc.add(cloneIndex * spacing, 0, cloneIndex * spacing);
        return loc;
    }

    public void regenerateArenaAsync(Arena arena, int cloneIndex, Runnable onComplete) {
        arenaRegen.regenerate(arena, cloneIndex, this).thenAccept(ok -> {
            if (ok) arena.setLastRegenResult("OK");
            else {
                arena.setLastRegenResult("FAILED");
                autoDisableArena(arena, "Regeneration failed");
            }
            plugin.tasks().runSync(onComplete);
        });
    }

    public void saveCloneAsync(Arena arena, java.util.function.Consumer<Boolean> callback) {
        arenaRegen.saveClone(arena).thenAccept(ok -> plugin.tasks().runSync(() -> callback.accept(ok)));
    }

    public ArenaRegenService.TestResult testArena(Arena arena) {
        return arenaRegen.testArena(arena, this);
    }

    public boolean fixArena(Arena arena) {
        boolean ok = arenaRegen.fixArena(arena);
        if (ok) saveArenaAsync(arena);
        return ok;
    }

    public void autoDisableArena(Arena arena, String reason) {
        arena.setEnabled(false);
        arena.setBroken(true);
        arena.setBrokenReason(reason);
        saveArenaAsync(arena);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("meranked.staff")) {
                messages.sendPrefixed(staff, "arena.auto-disabled", Map.of("arena", arena.name(), "reason", reason));
            }
        }
    }

    public void saveArenaAsync(Arena arena) {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO ranked_arenas (name, data, enabled, broken, usage_count)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                ps.setString(1, arena.name());
                ps.setString(2, serializeArena(arena));
                ps.setBoolean(3, arena.enabled());
                ps.setBoolean(4, arena.broken());
                ps.setInt(5, arena.usageCount());
                ps.executeUpdate();
            }
        });
        arenaCache.put(arena.name().toLowerCase(), arena);
    }

    private void loadArenasAsync() {
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT name, data, enabled, broken, usage_count FROM ranked_arenas");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Arena arena = deserializeArena(rs.getString("name"), rs.getString("data"));
                    arena.setEnabled(rs.getBoolean("enabled"));
                    arena.setBroken(rs.getBoolean("broken"));
                    for (int i = 0; i < rs.getInt("usage_count") - arena.usageCount(); i++) arena.incrementUsage();
                    arenaCache.put(arena.name().toLowerCase(), arena);
                }
            }
        });
    }

    private String serializeArena(Arena arena) {
        Map<String, Object> data = new HashMap<>();
        data.put("displayItem", arena.displayItem().name());
        data.put("allowed", new ArrayList<>(arena.allowedGamemodes()));
        data.put("blocked", new ArrayList<>(arena.blockedGamemodes()));
        data.put("spawn1", locToMap(arena.spawn1()));
        data.put("spawn2", locToMap(arena.spawn2()));
        data.put("spectator", locToMap(arena.spectatorSpawn()));
        data.put("intro1", locToMap(arena.intro1()));
        data.put("intro2", locToMap(arena.intro2()));
        data.put("introCamera", locToMap(arena.introCamera()));
        data.put("pos1", locToMap(arena.pos1()));
        data.put("pos2", locToMap(arena.pos2()));
        data.put("cloneSource", locToMap(arena.cloneSource()));
        data.put("regenMethod", arena.regenMethod());
        data.put("brokenReason", arena.brokenReason());
        return gson.toJson(data);
    }

    @SuppressWarnings("unchecked")
    private Arena deserializeArena(String name, String json) {
        Arena arena = new Arena(name);
        if (json == null || json.isEmpty()) return arena;
        Map<String, Object> data = gson.fromJson(json, Map.class);
        if (data == null) return arena;
        if (data.containsKey("displayItem")) {
            arena.setDisplayItem(Material.valueOf(String.valueOf(data.get("displayItem"))));
        }
        if (data.get("allowed") instanceof List<?> list) {
            arena.setAllowedGamemodes(new HashSet<>(list.stream().map(String::valueOf).toList()));
        }
        if (data.get("blocked") instanceof List<?> list) {
            arena.setBlockedGamemodes(new HashSet<>(list.stream().map(String::valueOf).toList()));
        }
        arena.setSpawn1(mapToLoc((Map<String, Object>) data.get("spawn1")));
        arena.setSpawn2(mapToLoc((Map<String, Object>) data.get("spawn2")));
        arena.setSpectatorSpawn(mapToLoc((Map<String, Object>) data.get("spectator")));
        arena.setIntro1(mapToLoc((Map<String, Object>) data.get("intro1")));
        arena.setIntro2(mapToLoc((Map<String, Object>) data.get("intro2")));
        arena.setIntroCamera(mapToLoc((Map<String, Object>) data.get("introCamera")));
        arena.setPos1(mapToLoc((Map<String, Object>) data.get("pos1")));
        arena.setPos2(mapToLoc((Map<String, Object>) data.get("pos2")));
        arena.setCloneSource(mapToLoc((Map<String, Object>) data.get("cloneSource")));
        if (data.get("regenMethod") != null) arena.setRegenMethod(String.valueOf(data.get("regenMethod")));
        if (data.get("brokenReason") != null) arena.setBrokenReason(String.valueOf(data.get("brokenReason")));
        return arena;
    }

    private Map<String, Object> locToMap(Location loc) {
        if (loc == null) return null;
        Map<String, Object> map = new HashMap<>();
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        map.put("yaw", loc.getYaw());
        map.put("pitch", loc.getPitch());
        return map;
    }

    private Location mapToLoc(Map<String, Object> map) {
        if (map == null) return null;
        World world = Bukkit.getWorld(String.valueOf(map.get("world")));
        if (world == null) return null;
        return new Location(world,
                ((Number) map.get("x")).doubleValue(),
                ((Number) map.get("y")).doubleValue(),
                ((Number) map.get("z")).doubleValue(),
                ((Number) map.get("yaw")).floatValue(),
                ((Number) map.get("pitch")).floatValue());
    }
}
