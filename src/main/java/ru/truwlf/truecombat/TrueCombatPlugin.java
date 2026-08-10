package ru.truwlf.truecombat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrueCombatPlugin extends JavaPlugin implements TabCompleter {
    private CombatListener listener;
    private LocaleManager locale;
    private CombatLoggerStore combatLoggers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locale = new LocaleManager(this);
        combatLoggers = new CombatLoggerStore(this);
        listener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getScheduler().runTaskTimer(this, listener::tick, 20L, 20L);
        getCommand("truecombat").setTabCompleter(this);
        getLogger().info("TrueCombat v2.1.1 enabled. Author: TrueWulf.");
    }

    @Override
    public void onDisable() {
        if (listener != null) listener.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("truecombat.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "reload" -> {
                reloadConfig();
                locale.reload();
                sender.sendMessage("TrueCombat configuration and locale reloaded.");
            }
            case "status" -> sender.sendMessage("TrueCombat v" + getPluginMeta().getVersion() + ": "
                    + getServer().getOnlinePlayers().size() + " players online.");
            case "tag", "untag", "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " " + subcommand + " <player>");
                    return true;
                }
                Player target = getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                    return true;
                }
                if (subcommand.equals("tag")) {
                    listener.tagForAdmin(target);
                    sender.sendMessage("PvP tag applied to " + target.getName() + ".");
                } else if (subcommand.equals("untag")) {
                    listener.clearCombatTag(target);
                    sender.sendMessage("PvP tag removed from " + target.getName() + ".");
                } else {
                    listener.clearPlayerStateForAdmin(target);
                    sender.sendMessage("All TrueCombat timers cleared for " + target.getName() + ".");
                }
            }
            default -> sender.sendMessage("TrueCombat commands: /" + label + " reload, status, tag <player>, untag <player>, clear <player>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("truecombat.admin")) return List.of();
        if (args.length == 1) return matches(args[0], List.of("reload", "status", "tag", "untag", "clear", "help"));
        if (args.length == 2 && List.of("tag", "untag", "clear").contains(args[0].toLowerCase(Locale.ROOT))) {
            return matches(args[1], getServer().getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private List<String> matches(String input, List<String> choices) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String choice : choices) if (choice.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(choice);
        return result;
    }

    LocaleManager locale() {
        return locale;
    }

    CombatLoggerStore combatLoggers() {
        return combatLoggers;
    }
}
