package ru.truwlf.truecombat.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "truecombat", name = "TrueCombat", version = "2.3.0", description = "Combat-state protection for Velocity networks.", authors = {"TrueWulf"})
public final class VelocityTrueCombat {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("truecombat", "state");
    private final ProxyServer proxy;
    private final Logger logger;
    private final Map<UUID, Long> combat = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> expirations = new ConcurrentHashMap<>();

    @Inject
    public VelocityTrueCombat(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("truecombat").aliases("tc").build(), new AdminCommand(this));
        logger.info("TrueCombat Velocity guard enabled");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier()) || !(event.getSource() instanceof ServerConnection connection)) return;
        if (event.getData().length < 25) return;
        Player player = connection.getPlayer();
        java.nio.ByteBuffer identity = java.nio.ByteBuffer.wrap(event.getData());
        if (identity.getLong() != player.getUniqueId().getMostSignificantBits()
                || identity.getLong() != player.getUniqueId().getLeastSignificantBits()) return;
        UUID id = player.getUniqueId();
        boolean active = event.getData()[16] != 0;
        long seconds = java.nio.ByteBuffer.wrap(event.getData(), 17, 8).getLong();
        if (active && seconds > 0L) {
            combat.put(id, System.currentTimeMillis() + seconds * 1000L);
            ScheduledTask old = expirations.remove(id);
            if (old != null) old.cancel();
            expirations.put(id, proxy.getScheduler().buildTask(this, () -> clear(id)).delay(Duration.ofSeconds(seconds)).schedule());
        } else clear(id);
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (!active(event.getPlayer().getUniqueId())) return;
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        event.getPlayer().sendMessage(Component.text("You cannot switch servers while in combat."));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        expirations.values().forEach(ScheduledTask::cancel);
        expirations.clear();
        proxy.getChannelRegistrar().unregister(CHANNEL);
    }

    boolean active(UUID id) {
        Long end = combat.get(id);
        if (end == null || end <= System.currentTimeMillis()) {
            clear(id);
            return false;
        }
        return true;
    }

    void admin(CommandSource source, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            source.send("/truecombat status [player], tag <player>, untag <player>, clear <player>");
            return;
        }
        if (args[0].equalsIgnoreCase("status")) {
            if (args.length > 2) { source.send("Usage: /truecombat status [player]"); return; }
            if (args.length == 1) source.send("TrueCombat: " + combat.keySet().stream().filter(this::active).count() + " players in combat.");
            else proxy.getPlayer(args[1]).ifPresentOrElse(player -> source.send(player.getUsername() + ": " + active(player.getUniqueId())), () -> source.send("Player not found."));
            return;
        }
        if (!List.of("tag", "untag", "clear").contains(args[0].toLowerCase(Locale.ROOT))) {
            source.send("Unknown action.");
            return;
        }
        if (args.length != 2) { source.send("Usage: /truecombat " + args[0] + " <player>"); return; }
        proxy.getPlayer(args[1]).ifPresentOrElse(player -> {
            if (args[0].equalsIgnoreCase("tag")) combat.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
            else if (args[0].equalsIgnoreCase("untag") || args[0].equalsIgnoreCase("clear")) clear(player.getUniqueId());
            else { source.send("Unknown action."); return; }
            source.send("TrueCombat state updated for " + player.getUsername() + ".");
        }, () -> source.send("Player not found."));
    }

    private void clear(UUID id) {
        combat.remove(id);
        ScheduledTask task = expirations.remove(id);
        if (task != null) task.cancel();
    }

    interface CommandSource {
        void send(String message);
    }

    private static final class AdminCommand implements SimpleCommand {
        private final VelocityTrueCombat plugin;
        private AdminCommand(VelocityTrueCombat plugin) { this.plugin = plugin; }
        @Override public void execute(Invocation invocation) {
            if (!invocation.source().hasPermission("truecombat.admin")) { invocation.source().sendMessage(Component.text("You do not have permission.")); return; }
            plugin.admin(message -> invocation.source().sendMessage(Component.text(message)), invocation.arguments());
        }
        @Override public List<String> suggest(Invocation invocation) {
            if (invocation.arguments().length == 1) {
                String prefix = invocation.arguments()[0].toLowerCase(Locale.ROOT);
                return List.of("help", "status", "tag", "untag", "clear").stream()
                        .filter(choice -> choice.startsWith(prefix)).toList();
            }
            if (invocation.arguments().length == 2 && List.of("status", "tag", "untag", "clear")
                    .contains(invocation.arguments()[0].toLowerCase(Locale.ROOT))) {
                String prefix = invocation.arguments()[1].toLowerCase(Locale.ROOT);
                return new ArrayList<>(plugin.proxy.getAllPlayers().stream().map(Player::getUsername)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList());
            }
            return List.of();
        }
    }
}
