package ru.truwlf.truecombat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.List;

final class LocaleManager {
    private final TrueCombatPlugin plugin;
    private FileConfiguration locale;

    LocaleManager(TrueCombatPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        String configured = plugin.getConfig().getString("lang", "en_US");
        String name = configured == null ? "en_us" : configured.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        name = switch (name) {
            case "en", "en_us" -> "en_US";
            case "ru", "ru_ru" -> "ru_RU";
            case "de", "de_de" -> "de_DE";
            case "fr", "fr_fr" -> "fr_FR";
            case "it", "it_it" -> "it_IT";
            case "es", "es_es" -> "es_ES";
            case "pt", "pt_br" -> "pt_BR";
            case "zh", "zh_cn" -> "zh_CN";
            case "ja", "ja_jp" -> "ja_JP";
            default -> "en_US";
        };
        File directory = new File(plugin.getDataFolder(), "lang");
        if (!directory.exists() && !directory.mkdirs()) plugin.getLogger().warning("Unable to create lang directory.");
        for (String bundledLocale : List.of("en_US", "ru_RU", "de_DE", "fr_FR", "it_IT", "es_ES", "pt_BR", "zh_CN", "ja_JP")) {
            File bundled = new File(directory, bundledLocale + ".yml");
            if (!bundled.exists()) plugin.saveResource("lang/" + bundledLocale + ".yml", false);
        }
        File requested = new File(directory, name + ".yml");
        if (!requested.exists()) {
            plugin.getLogger().warning("Locale " + name + " is unavailable; using en_US.");
            requested = new File(directory, "en_US.yml");
            if (!requested.exists()) plugin.saveResource("lang/en_US.yml", false);
        }
        locale = YamlConfiguration.loadConfiguration(requested);
        plugin.getLogger().info("Loaded locale: " + name);
    }

    String get(String path) {
        return locale.getString(path, "");
    }
}
