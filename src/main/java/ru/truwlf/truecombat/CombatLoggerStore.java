package ru.truwlf.truecombat;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class CombatLoggerStore {
    private final File file;
    private final Set<UUID> loggers = new HashSet<>();
    private final ExecutorService saver = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TrueCombat-logger-store");
        thread.setDaemon(true);
        return thread;
    });
    private final TrueCombatPlugin plugin;

    CombatLoggerStore(TrueCombatPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "combat-loggers.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String raw : data.getStringList("players")) {
            try {
                loggers.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in combat-loggers.yml: " + raw);
            }
        }
    }

    synchronized boolean remove(UUID playerId) {
        boolean removed = loggers.remove(playerId);
        if (removed) save();
        return removed;
    }

    synchronized void add(UUID playerId) {
        if (loggers.add(playerId)) save();
    }

    private void save() {
        Set<UUID> snapshot = Set.copyOf(loggers);
        saver.execute(() -> saveSnapshot(snapshot));
    }

    private void saveSnapshot(Set<UUID> snapshot) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("players", snapshot.stream().map(UUID::toString).toList());
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to save combat logger data.", exception);
        }
    }

    void close() {
        saver.shutdown();
        try {
            if (!saver.awaitTermination(5, TimeUnit.SECONDS)) saver.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saver.shutdownNow();
        }
    }
}
