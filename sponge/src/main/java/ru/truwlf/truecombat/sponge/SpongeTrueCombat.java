package ru.truwlf.truecombat.sponge;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.event.message.PlayerChatEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.event.cause.entity.damage.source.EntityDamageSource;
import org.spongepowered.api.event.cause.entity.damage.source.IndirectEntityDamageSource;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.network.channel.raw.RawDataChannel;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import org.spongepowered.api.config.ConfigDir;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin("truecombat")
public final class SpongeTrueCombat {
    private final PluginContainer plugin;
    private final Path configDirectory;
    private final Map<UUID, Long> combat = new ConcurrentHashMap<>();
    private Properties config;
    private RawDataChannel stateChannel;
    private ScheduledTask ticker;

    @Inject
    public SpongeTrueCombat(PluginContainer plugin, @ConfigDir(sharedRoot = false) Path configDirectory) {
        this.plugin = plugin;
        this.configDirectory = configDirectory;
    }

    @Listener
    public void onRegisterCommands(RegisterCommandEvent<Command.Parameterized> event) {
        Parameter.Value<String> action = Parameter.choices("help", "status", "tag", "untag", "clear", "reload")
                .key("action").build();
        Parameter.Value<String> arguments = Parameter.remainingJoinedStrings().key("arguments").optional().build();
        Command.Parameterized command = Command.builder()
                .addParameter(action)
                .addParameter(arguments)
                .permission("truecombat.admin")
                .shortDescription(Component.text("Manage TrueCombat combat protection"))
                .executor(context -> execute(context, action, arguments))
                .build();
        event.register(plugin, command, "truecombat", "tc");
    }

    @Listener
    public void onJoin(ServerSideConnectionEvent.Join event) {
        if (config == null) loadConfig();
    }

    @Listener
    public void onDisconnect(ServerSideConnectionEvent.Disconnect event) {
        UUID id = event.player().uniqueId();
        Long end = combat.remove(id);
        if (end != null && end > System.currentTimeMillis() && bool("combat-log.enabled", true)) {
            Sponge.server().broadcastAudience().sendMessage(Component.text(event.player().name() + " disconnected during combat."));
        }
    }

    @Listener
    public void onStopping(StoppingEngineEvent<?> event) {
        if (ticker != null) ticker.cancel();
    }

    @Listener
    public void onChat(PlayerChatEvent event) {
        event.cause().first(ServerPlayer.class).ifPresent(player -> event.setCancelled(false));
    }

    @Listener
    public void onDamage(DamageEntityEvent event) {
        if (!bool("pvp.enabled", true) || !(event.entity() instanceof ServerPlayer victim) || event.isCancelled()) return;
        EntityDamageSource source = event.cause().first(EntityDamageSource.class).orElse(null);
        if (source == null) return;
        ServerPlayer attacker = source.source() instanceof ServerPlayer direct ? direct : null;
        if (attacker == null && source instanceof IndirectEntityDamageSource indirect
                && indirect.indirectSource() instanceof ServerPlayer indirectPlayer) attacker = indirectPlayer;
        if (attacker == null) return;
        if (attacker.uniqueId().equals(victim.uniqueId())) return;
        tag(attacker);
        tag(victim);
    }

    private CommandResult execute(CommandContext context, Parameter.Value<String> action, Parameter.Value<String> arguments) {
        if (config == null) loadConfig();
        String actionName = context.requireOne(action).toLowerCase(Locale.ROOT);
        String raw = context.one(arguments).orElse("").trim();
        if (actionName.equals("help")) {
            if (!raw.isEmpty()) return CommandResult.error(Component.text("Usage: /truecombat help"));
            context.cause().audience().sendMessage(Component.text("/truecombat status | tag <player> | untag <player> | clear <player> | reload"));
            return CommandResult.success();
        }
        if (actionName.equals("reload")) {
            if (!raw.isEmpty()) return CommandResult.error(Component.text("Usage: /truecombat reload"));
            loadConfig();
            context.cause().audience().sendMessage(Component.text("TrueCombat configuration reloaded."));
            return CommandResult.success();
        }
        if (actionName.equals("status")) {
            if (!raw.isEmpty()) return CommandResult.error(Component.text("Usage: /truecombat status"));
            context.cause().audience().sendMessage(Component.text("TrueCombat 2.3.0: " + combat.size() + " players in combat."));
            return CommandResult.success();
        }
        if (raw.isEmpty() || raw.contains(" ")) {
            return CommandResult.error(Component.text("Usage: /truecombat " + actionName + " <player>"));
        }
        ServerPlayer target = Sponge.server().player(raw).orElse(null);
        if (target == null) return CommandResult.error(Component.text("Player not found: " + raw));
        if (actionName.equals("tag")) tag(target);
        else if (actionName.equals("untag") || actionName.equals("clear")) {
            combat.remove(target.uniqueId());
            sendState(target, false, 0L);
        }
        else return CommandResult.error(Component.text("Unknown action."));
        context.cause().audience().sendMessage(Component.text("TrueCombat state updated for " + raw + "."));
        return CommandResult.success();
    }

    private void tag(ServerPlayer player) {
        long seconds = Math.max(0L, number("pvp.duration-seconds", 30L));
        if (seconds == 0L) {
            combat.remove(player.uniqueId());
            sendState(player, false, 0L);
            return;
        }
        combat.put(player.uniqueId(), System.currentTimeMillis() + seconds * 1000L);
        sendState(player, true, seconds);
    }

    private boolean active(UUID id) {
        Long end = combat.get(id);
        if (end == null) return false;
        if (end <= System.currentTimeMillis()) {
            combat.remove(id, end);
            Sponge.server().player(id).ifPresent(player -> sendState(player, false, 0L));
            return false;
        }
        return true;
    }

    private void loadConfig() {
        config = new Properties();
        Path file = configDirectory.resolve("config.properties");
        try {
            Files.createDirectories(configDirectory);
            if (Files.notExists(file)) {
                config.setProperty("pvp.enabled", "true");
                config.setProperty("pvp.duration-seconds", "30");
                config.setProperty("combat-log.enabled", "true");
                config.setProperty("proxy.sync-combat-state", "true");
                config.setProperty("proxy.channel", "truecombat:state");
                try (OutputStream output = Files.newOutputStream(file)) { config.store(output, "TrueCombat Sponge configuration"); }
            } else try (InputStream input = Files.newInputStream(file)) { config.load(input); }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load TrueCombat configuration", exception);
        }
        String[] channelParts = config.getProperty("proxy.channel", "truecombat:state").split(":", 2);
        if (channelParts.length == 2) {
            stateChannel = Sponge.channelManager().ofType(ResourceKey.of(channelParts[0], channelParts[1]), RawDataChannel.class);
        }
        if (ticker == null) ticker = Sponge.server().scheduler().submit(Task.builder().plugin(plugin).delay(Duration.ofSeconds(1)).interval(Duration.ofSeconds(1)).execute(task -> combat.keySet().removeIf(id -> !active(id))).build());
    }

    private void sendState(ServerPlayer player, boolean active, long seconds) {
        if (!bool("proxy.sync-combat-state", true) || stateChannel == null) return;
        stateChannel.play().sendTo(player, buffer -> buffer.writeBytes(state(player.uniqueId(), active, seconds)));
    }

    private byte[] state(UUID id, boolean active, long seconds) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(25);
        buffer.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .put((byte) (active ? 1 : 0)).putLong(seconds);
        return buffer.array();
    }

    private boolean bool(String key, boolean fallback) { return Boolean.parseBoolean(config.getProperty(key, Boolean.toString(fallback))); }
    private long number(String key, long fallback) {
        try { return Math.max(0L, Long.parseLong(config.getProperty(key, Long.toString(fallback)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
