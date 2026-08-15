package ru.truwlf.truecombat.waterfall;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class WaterfallTrueCombat extends Plugin implements Listener {
    private static final String CHANNEL = "truecombat:state";
    private final Map<UUID, Long> combat = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getProxy().registerChannel(CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new AdminCommand());
        getLogger().info("TrueCombat Waterfall guard enabled");
    }

    @Override
    public void onDisable() {
        getProxy().getPluginManager().unregisterCommands(this);
        getProxy().getPluginManager().unregisterListeners(this);
        getProxy().unregisterChannel(CHANNEL);
        combat.clear();
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getTag()) || !(event.getSender() instanceof net.md_5.bungee.api.connection.Server server) || !(event.getReceiver() instanceof ProxiedPlayer player)) return;
        byte[] data = event.getData();
        if (data.length < 25) return;
        UUID id = player.getUniqueId();
        ByteBuffer identity = ByteBuffer.wrap(data);
        if (identity.getLong() != id.getMostSignificantBits() || identity.getLong() != id.getLeastSignificantBits()) return;
        if (data[16] != 0 && ByteBuffer.wrap(data, 17, 8).getLong() > 0L) {
            long seconds = ByteBuffer.wrap(data, 17, 8).getLong();
            long end = System.currentTimeMillis() + seconds * 1000L;
            combat.put(id, end);
            getProxy().getScheduler().schedule(this, () -> combat.remove(id, end), seconds, TimeUnit.SECONDS);
        } else combat.remove(id);
        event.setCancelled(true);
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        if (active(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(new TextComponent("You cannot switch servers while in combat."));
        }
    }

    private boolean active(UUID id) {
        Long end = combat.get(id);
        if (end == null || end <= System.currentTimeMillis()) { combat.remove(id); return false; }
        return true;
    }

    private final class AdminCommand extends Command implements TabExecutor {
        private AdminCommand() { super("truecombat", "truecombat.admin", "tc"); }
        @Override public void execute(CommandSender sender, String[] args) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sender.sendMessage(new TextComponent("/truecombat status [player], tag <player>, untag <player>, clear <player>")); return; }
            if (args[0].equalsIgnoreCase("status")) {
                if (args.length > 2) { sender.sendMessage(new TextComponent("Usage: /truecombat status [player]")); return; }
                if (args.length == 1) sender.sendMessage(new TextComponent("TrueCombat: " + combat.keySet().stream().filter(WaterfallTrueCombat.this::active).count() + " players in combat."));
                else { ProxiedPlayer player = getProxy().getPlayer(args[1]); sender.sendMessage(new TextComponent(player == null ? "Player not found." : player.getName() + ": " + active(player.getUniqueId()))); }
                return;
            }
            if (!List.of("tag", "untag", "clear").contains(args[0].toLowerCase(java.util.Locale.ROOT))) {
                sender.sendMessage(new TextComponent("Unknown action."));
                return;
            }
            if (args.length != 2) { sender.sendMessage(new TextComponent("Usage: /truecombat " + args[0] + " <player>")); return; }
            ProxiedPlayer player = getProxy().getPlayer(args[1]);
            if (player == null) { sender.sendMessage(new TextComponent("Player not found.")); return; }
            if (args[0].equalsIgnoreCase("tag")) combat.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
            else if (args[0].equalsIgnoreCase("untag") || args[0].equalsIgnoreCase("clear")) combat.remove(player.getUniqueId());
            else { sender.sendMessage(new TextComponent("Unknown action.")); return; }
            sender.sendMessage(new TextComponent("TrueCombat state updated for " + player.getName() + "."));
        }
        @Override public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
                return List.of("help", "status", "tag", "untag", "clear").stream()
                        .filter(choice -> choice.startsWith(prefix)).toList();
            }
            if (args.length == 2 && List.of("status", "tag", "untag", "clear")
                    .contains(args[0].toLowerCase(java.util.Locale.ROOT))) {
                String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
                return new ArrayList<>(getProxy().getPlayers().stream().map(ProxiedPlayer::getName)
                        .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)).toList());
            }
            return List.of();
        }
    }
}
