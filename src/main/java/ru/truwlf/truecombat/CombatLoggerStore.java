package ru.truwlf.truecombat;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class CombatLoggerStore {
    private final File file;
    private final Set<UUID> loggers = new HashSet<>();

    CombatLoggerStore(TrueCombatPlugin plugin) {
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

    boolean remove(UUID playerId) {
        boolean removed = loggers.remove(playerId);
        if (removed) save();
        return removed;
    }

    void add(UUID playerId) {
        if (loggers.add(playerId)) save();
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("players", loggers.stream().map(UUID::toString).toList());
        try {
            data.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save combat logger data.", exception);
        }
    }
}
