package com.meranked.arenas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.model.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Arena regeneration: FAWE/WorldEdit schematics first, block snapshot fallback.
 */
public final class ArenaRegenService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final WorldEditBridge worldEdit;
    private final Gson gson = new GsonBuilder().create();
    private final File arenaDataFolder;

    public ArenaRegenService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.worldEdit = new WorldEditBridge(plugin);
        this.arenaDataFolder = new File(configService.getDataFolder(), "arena-data");
        arenaDataFolder.mkdirs();
    }

    public CompletableFuture<Boolean> saveClone(Arena arena) {
        return CompletableFuture.supplyAsync(() -> {
            if (arena.pos1() == null || arena.pos2() == null) return false;
            FileConfiguration config = configService.get("arenas.yml");
            String method = config.getString("arenas.regeneration.method", "FAWE").toUpperCase();

            if (worldEdit.available() && (method.equals("FAWE") || method.equals("WORLDEDIT"))) {
                File schematic = schematicFile(arena.name());
                boolean saved = worldEdit.saveSchematic(arena.pos1(), arena.pos2(), schematic);
                if (saved) return true;
            }
            return saveSnapshot(arena);
        });
    }

    public CompletableFuture<Boolean> regenerate(Arena arena, int cloneIndex, ArenaService arenaService) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        FileConfiguration config = configService.get("arenas.yml");
        String method = config.getString("arenas.regeneration.method", "FAWE").toUpperCase();
        String fallback = config.getString("arenas.regeneration.fallback-method", "SNAPSHOT").toUpperCase();

        Location targetMin = arenaService.offsetLocation(arena.pos1(), arena.name(), cloneIndex);
        Location targetMax = arenaService.offsetLocation(arena.pos2(), arena.name(), cloneIndex);

        plugin.tasks().runAsync(() -> {
            boolean ok = false;
            try {
                if (worldEdit.available() && method.equals("FAWE")) {
                    ok = worldEdit.pasteSchematic(schematicFile(arena.name()), targetMin);
                } else if (worldEdit.available() && method.equals("WORLDEDIT")) {
                    ok = worldEdit.pasteSchematic(schematicFile(arena.name()), targetMin);
                }
                if (!ok && fallback.equals("SNAPSHOT")) {
                    ok = pasteSnapshot(arena, targetMin, targetMax);
                }
                if (ok) {
                    plugin.tasks().runSync(() -> cleanupEntities(targetMin, targetMax, config));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Arena regen failed for " + arena.name() + ": " + ex.getMessage());
            }
            future.complete(ok);
        });
        return future;
    }

    public CompletableFuture<Boolean> ensureCloneReady(Arena arena, int cloneIndex, ArenaService arenaService) {
        if (cloneIndex == 0) {
            return regenerate(arena, 0, arenaService).thenApply(ok -> ok);
        }
        // For clone copies, paste from source schematic/snapshot to offset
        return regenerate(arena, cloneIndex, arenaService);
    }

    public TestResult testArena(Arena arena, ArenaService arenaService) {
        List<String> issues = new ArrayList<>();
        if (arena.spawn1() == null) issues.add("Missing spawn1");
        if (arena.spawn2() == null) issues.add("Missing spawn2");
        if (arena.pos1() == null || arena.pos2() == null) issues.add("Missing region bounds");
        if (arena.spawn1() != null && arena.pos1() != null && arena.pos2() != null) {
            if (!isInside(arena.spawn1(), arena.pos1(), arena.pos2())) {
                issues.add("Spawn1 outside bounds");
            }
        }
        File schematic = schematicFile(arena.name());
        File snapshot = snapshotFile(arena.name());
        if (!schematic.exists() && !snapshot.exists()) {
            issues.add("No schematic or snapshot saved — run /arena saveclone");
        }
        if (arena.spawn1() != null && arena.spawn1().getWorld() == null) issues.add("World unloaded");
        return new TestResult(issues.isEmpty(), issues);
    }

    public boolean fixArena(Arena arena) {
        arena.setBroken(false);
        arena.setBrokenReason(null);
        arena.setEnabled(true);
        return testArena(arena, null).passed();
    }

    private boolean saveSnapshot(Arena arena) {
        Location pos1 = arena.pos1();
        Location pos2 = arena.pos2();
        World world = pos1.getWorld();
        if (world == null) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        List<BlockData> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new BlockData(
                            x - minX, y - minY, z - minZ,
                            block.getBlockData().getAsString(),
                            block.getType() == Material.AIR ? null : null
                    ));
                }
            }
        }
        Snapshot snap = new Snapshot(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, blocks);
        try (OutputStream out = new GZIPOutputStream(new FileOutputStream(snapshotFile(arena.name())));
             Writer writer = new OutputStreamWriter(out)) {
            gson.toJson(snap, writer);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save snapshot: " + ex.getMessage());
            return false;
        }
    }

    private boolean pasteSnapshot(Arena arena, Location targetMin, Location targetMax) {
        File file = snapshotFile(arena.name());
        if (!file.exists()) return false;
        try (InputStream in = new GZIPInputStream(new FileInputStream(file));
             Reader reader = new InputStreamReader(in)) {
            Snapshot snap = gson.fromJson(reader, Snapshot.class);
            if (snap == null) return false;
            World world = targetMin.getWorld();
            if (world == null) return false;

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean(false);
            plugin.tasks().runSync(() -> {
                try {
                    for (BlockData bd : snap.blocks()) {
                        Block block = world.getBlockAt(
                                targetMin.getBlockX() + bd.rx(),
                                targetMin.getBlockY() + bd.ry(),
                                targetMin.getBlockZ() + bd.rz());
                        block.setBlockData(Bukkit.createBlockData(bd.data()));
                    }
                    ok.set(true);
                } catch (Exception ignored) {
                    ok.set(false);
                } finally {
                    latch.countDown();
                }
            });
            latch.await();
            return ok.get();
        } catch (Exception ex) {
            plugin.getLogger().warning("Snapshot paste failed: " + ex.getMessage());
            return false;
        }
    }

    private void cleanupEntities(Location min, Location max, FileConfiguration config) {
        World world = min.getWorld();
        if (world == null) return;
        world.getEntities().stream()
                .filter(e -> isInside(e.getLocation(), min, max))
                .filter(e -> {
                    if (config.getBoolean("arenas.regeneration.remove-drops", true)
                            && (e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.ExperienceOrb)) return true;
                    if (config.getBoolean("arenas.regeneration.remove-projectiles", true)
                            && e instanceof org.bukkit.entity.Projectile) return true;
                    if (config.getBoolean("arenas.regeneration.remove-crystals", true)
                            && e.getType().name().contains("CRYSTAL")) return true;
                    return false;
                })
                .forEach(org.bukkit.entity.Entity::remove);
    }

    private boolean isInside(Location loc, Location a, Location b) {
        double x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
        double y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
        double z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());
        return loc.getX() >= x1 && loc.getX() <= x2 && loc.getY() >= y1 && loc.getY() <= y2 && loc.getZ() >= z1 && loc.getZ() <= z2;
    }

    private File schematicFile(String arena) {
        return new File(arenaDataFolder, arena.toLowerCase() + ".schem");
    }

    private File snapshotFile(String arena) {
        return new File(arenaDataFolder, arena.toLowerCase() + ".snapshot.json.gz");
    }

    public record TestResult(boolean passed, List<String> issues) {}
    private record Snapshot(int sizeX, int sizeY, int sizeZ, List<BlockData> blocks) {}
    private record BlockData(int rx, int ry, int rz, String data, String container) {}
}
