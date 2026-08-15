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
    private PlatformScheduler scheduler;
    private PlatformScheduler.TaskHandle ticker;
    private String registeredProxyChannel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registeredProxyChannel = proxyChannel();
        getServer().getMessenger().registerOutgoingPluginChannel(this, registeredProxyChannel);
        locale = new LocaleManager(this);
        combatLoggers = new CombatLoggerStore(this);
        scheduler = new PlatformScheduler(this);
        listener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        ticker = scheduler.runTimer(listener::tick, 20L, 20L);
        if (getCommand("truecombat") != null) getCommand("truecombat").setTabCompleter(this);
        getLogger().info("TrueCombat v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (listener != null) listener.clear();
        if (combatLoggers != null) combatLoggers.close();
        if (registeredProxyChannel != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, registeredProxyChannel);
            registeredProxyChannel = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("truecombat.admin")
                && !sender.hasPermission("truecombat.admin.reload")
                && !sender.hasPermission("truecombat.admin.status")
                && !sender.hasPermission("truecombat.admin.combat")
                && !sender.hasPermission("truecombat.admin.debug")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("reload") && !adminPermission(sender, "truecombat.admin.reload")) {
            sender.sendMessage("You do not have permission to reload TrueCombat.");
            return true;
        }
        if (subcommand.equals("status") && !adminPermission(sender, "truecombat.admin.status")) {
            sender.sendMessage("You do not have permission to view TrueCombat status.");
            return true;
        }
        if (subcommand.equals("debug") && !adminPermission(sender, "truecombat.admin.debug")) {
            sender.sendMessage("You do not have permission to view TrueCombat debug output.");
            return true;
        }
        if (List.of("tag", "untag", "clear").contains(subcommand) && !adminPermission(sender, "truecombat.admin.combat")) {
            sender.sendMessage("You do not have permission to manage combat state.");
            return true;
        }
        switch (subcommand) {
            case "reload" -> {
                if (args.length != 1) {
                    sender.sendMessage("Usage: /" + label + " reload");
                    return true;
                }
                String oldChannel = registeredProxyChannel;
                reloadConfig();
                locale.reload();
                String newChannel = proxyChannel();
                if (!newChannel.equals(oldChannel)) {
                    getServer().getMessenger().unregisterOutgoingPluginChannel(this, oldChannel);
                    getServer().getMessenger().registerOutgoingPluginChannel(this, newChannel);
                    registeredProxyChannel = newChannel;
                }
                sender.sendMessage("TrueCombat configuration and locale reloaded.");
            }
            case "status" -> {
                if (args.length != 1) {
                    sender.sendMessage("Usage: /" + label + " status");
                    return true;
                }
                sender.sendMessage(message("TrueCombat v" + getDescription().getVersion() + ": "
                        + listener.combatCount() + " players in combat."));
            }
            case "debug" -> {
                if (args.length != 1) {
                    sender.sendMessage("Usage: /" + label + " debug");
                    return true;
                }
                if (!getConfig().getBoolean("admin.debug-enabled", false)) {
                    sender.sendMessage(message("Debug output is disabled in config."));
                } else {
                    sender.sendMessage(message("Online: " + getServer().getOnlinePlayers().size() + ", combat: " + listener.combatCount()));
                }
            }
            case "tag", "untag", "clear" -> {
                if (args.length != 2) {
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
            default -> sender.sendMessage("TrueCombat commands: /" + label + " help, reload, status, debug, tag <player>, untag <player>, clear <player>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("truecombat.admin")
                && !sender.hasPermission("truecombat.admin.reload")
                && !sender.hasPermission("truecombat.admin.status")
                && !sender.hasPermission("truecombat.admin.combat")
                && !sender.hasPermission("truecombat.admin.debug")) return List.of();
        if (args.length == 1) {
            List<String> choices = new ArrayList<>();
            if (adminPermission(sender, "truecombat.admin.reload")) choices.add("reload");
            if (adminPermission(sender, "truecombat.admin.status")) choices.add("status");
            if (adminPermission(sender, "truecombat.admin.debug")) choices.add("debug");
            if (adminPermission(sender, "truecombat.admin.combat")) choices.addAll(List.of("tag", "untag", "clear"));
            choices.add("help");
            return matches(args[0], choices);
        }
        if (args.length == 2 && List.of("tag", "untag", "clear").contains(args[0].toLowerCase(Locale.ROOT))) {
            return matches(args[1], getServer().getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private String message(String text) {
        return getConfig().getString("messages.prefix", "[TrueCombat] ") + text;
    }

    private boolean adminPermission(CommandSender sender, String permission) {
        return sender.hasPermission("truecombat.admin") || sender.hasPermission(permission);
    }

    private String proxyChannel() {
        return getConfig().getString("proxy.channel", CombatProtocol.CHANNEL);
    }

    String registeredProxyChannel() {
        return registeredProxyChannel == null ? CombatProtocol.CHANNEL : registeredProxyChannel;
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

    PlatformScheduler scheduler() {
        return scheduler;
    }
}
