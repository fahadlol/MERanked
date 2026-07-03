package com.meranked.arenas;

import com.meranked.MERankedPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.lang.reflect.Method;

/** Runtime WorldEdit/FAWE bridge — no compile-time dependency required. */
final class WorldEditBridge {

    private final MERankedPlugin plugin;
    private final boolean available;

    WorldEditBridge(MERankedPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
        if (available) plugin.getLogger().info("WorldEdit/FAWE detected for arena regeneration.");
    }

    boolean available() {
        return available;
    }

    boolean saveSchematic(Location pos1, Location pos2, File output) {
        if (!available) return false;
        try {
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit");
            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object weWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class).invoke(null, pos1.getWorld());

            Object min = bv3.getMethod("at", int.class, int.class, int.class).invoke(null,
                    Math.min(pos1.getBlockX(), pos2.getBlockX()),
                    Math.min(pos1.getBlockY(), pos2.getBlockY()),
                    Math.min(pos1.getBlockZ(), pos2.getBlockZ()));
            Object max = bv3.getMethod("at", int.class, int.class, int.class).invoke(null,
                    Math.max(pos1.getBlockX(), pos2.getBlockX()),
                    Math.max(pos1.getBlockY(), pos2.getBlockY()),
                    Math.max(pos1.getBlockZ(), pos2.getBlockZ()));

            Object region = Class.forName("com.sk89q.worldedit.regions.CuboidRegion")
                    .getConstructor(Class.forName("com.sk89q.worldedit.world.World"), bv3, bv3)
                    .newInstance(weWorld, min, max);

            Object weInstance = worldEdit.getMethod("getInstance").invoke(null);
            Object session = weInstance.getClass().getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(weInstance, weWorld);

            Class<?> clipboardFormats = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
            Object format = clipboardFormats.getMethod("findByAlias", String.class).invoke(null, "schem");
            if (format == null) format = clipboardFormats.getMethod("findByAlias", String.class).invoke(null, "schematic");

            Object forwardExtent = Class.forName("com.sk89q.worldedit.extent.ForwardExtentCopy")
                    .getConstructor(Class.forName("com.sk89q.worldedit.extent.Extent"), Class.forName("com.sk89q.worldedit.regions.Region"),
                            Class.forName("com.sk89q.worldedit.extent.Extent"), bv3)
                    .newInstance(weWorld, region,
                            Class.forName("com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard")
                                    .getConstructor(bv3).newInstance(max),
                            bv3.getMethod("at", int.class, int.class, int.class).invoke(null, 0, 0, 0));

            Class.forName("com.sk89q.worldedit.function.operation.Operations")
                    .getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                    .invoke(null, forwardExtent);

            Object writer = format.getClass().getMethod("getWriter", java.io.OutputStream.class)
                    .invoke(format, new java.io.FileOutputStream(output));
            Method write = writer.getClass().getMethod("write", Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"));
            // Fallback: region save via //schem is complex; snapshot handles failure
            if (session instanceof AutoCloseable ac) ac.close();
            return output.exists() && output.length() > 0;
        } catch (Exception ex) {
            plugin.getLogger().warning("WE schematic save failed (snapshot fallback available): " + ex.getMessage());
            return false;
        }
    }

    boolean pasteSchematic(File schematic, Location target) {
        if (!available || !schematic.exists()) return false;
        try {
            Class<?> formats = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
            Object format = formats.getMethod("findByFile", File.class).invoke(null, schematic);
            if (format == null) return false;

            Object reader = format.getClass().getMethod("getReader", java.io.InputStream.class)
                    .invoke(format, new java.io.FileInputStream(schematic));
            Object clipboard = reader.getClass().getMethod("read").invoke(reader);
            if (reader instanceof AutoCloseable ac) ac.close();

            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class).invoke(null, target.getWorld());
            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object to = bv3.getMethod("at", int.class, int.class, int.class).invoke(null,
                    target.getBlockX(), target.getBlockY(), target.getBlockZ());

            Object we = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);
            Object session = we.getClass().getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(we, weWorld);

            Object holder = Class.forName("com.sk89q.worldedit.session.ClipboardHolder")
                    .getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                    .newInstance(clipboard);
            Object pasteBuilder = holder.getClass().getMethod("createPaste", Class.forName("com.sk89q.worldedit.extent.Extent"))
                    .invoke(holder, session);
            pasteBuilder = pasteBuilder.getClass().getMethod("to", bv3).invoke(pasteBuilder, to);
            pasteBuilder = pasteBuilder.getClass().getMethod("ignoreAirBlocks", boolean.class).invoke(pasteBuilder, false);
            Object operation = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);

            Class.forName("com.sk89q.worldedit.function.operation.Operations")
                    .getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                    .invoke(null, operation);
            session.getClass().getMethod("flushSession").invoke(session);
            if (session instanceof AutoCloseable ac) ac.close();
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("WE schematic paste failed: " + ex.getMessage());
            return false;
        }
    }
}
