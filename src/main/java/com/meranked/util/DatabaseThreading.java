package com.meranked.util;

import com.meranked.MERankedPlugin;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Helpers for keeping database work off Paper's main thread and Bukkit API work on it.
 */
public final class DatabaseThreading {

    private final MERankedPlugin plugin;

    public DatabaseThreading(MERankedPlugin plugin) {
        this.plugin = plugin;
    }

    public void runSync(Runnable task) {
        plugin.tasks().runSync(task);
    }

    public void assertNotOnMainThread(String operation) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Blocking database load attempted on the main thread: " + operation);
        }
    }

    public <T> CompletableFuture<T> supplyDatabase(Supplier<T> task) {
        return plugin.services().database().supplyAsync(task);
    }

    public CompletableFuture<Void> runDatabase(Runnable task) {
        return supplyDatabase(() -> {
            task.run();
            return null;
        });
    }
}
