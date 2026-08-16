package ru.truwlf.truecombat;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CombatListener implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Material MACE = Material.matchMaterial("MACE");
    private static final Material WIND_CHARGE = Material.matchMaterial("WIND_CHARGE");
    private static final org.bukkit.entity.EntityType WIND_CHARGE_ENTITY = org.bukkit.entity.EntityType.fromName("WIND_CHARGE");
    private final TrueCombatPlugin plugin;
    private final Map<UUID, Long> combat = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> trident = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> pearl = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> windCharge = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> mace = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttacker = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> incomingAttackers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> outgoingTargets = new java.util.concurrent.ConcurrentHashMap<>();

    CombatListener(TrueCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onMaceDamage(EntityDamageByEntityEvent event) {
        if (!enabled("mace.enabled") || !(event.getDamager() instanceof Player player)
                || MACE == null || player.getInventory().getItemInMainHand().getType() != MACE) return;
        if (onCooldown(mace, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!enabled("pvp.enabled") || !(event.getEntity() instanceof Player victim)) return;
        if (plugin.getConfig().getStringList("pvp.ignored-damage-causes").stream()
                .anyMatch(cause -> cause.equalsIgnoreCase(event.getDamager().getType().name()))) return;
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (plugin.getConfig().getBoolean("pvp.refresh-on-hit", true) || !active(combat, attacker.getUniqueId())) tag(attacker);
        if (plugin.getConfig().getBoolean("pvp.refresh-on-hit", true) || !active(combat, victim.getUniqueId())) tag(victim);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        incomingAttackers.computeIfAbsent(victim.getUniqueId(), ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(attacker.getUniqueId(), combat.get(victim.getUniqueId()));
        outgoingTargets.computeIfAbsent(attacker.getUniqueId(), ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(victim.getUniqueId(), combat.get(attacker.getUniqueId()));
        if (enabled("mace.enabled") && MACE != null && attacker.getInventory().getItemInMainHand().getType() == MACE) {
            start(mace, attacker, "mace.cooldown-seconds", MACE);
        } else if (enabled("wind-charge.enabled") && WIND_CHARGE_ENTITY != null && event.getDamager().getType() == WIND_CHARGE_ENTITY) {
            start(windCharge, attacker, "wind-charge.cooldown-seconds", WIND_CHARGE);
        } else if (enabled("trident.enabled") && attacker.isRiptiding()) {
            start(trident, attacker, "trident.cooldown-seconds", Material.TRIDENT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) return;
        Player player = event.getPlayer();
        Material type = event.getItem().getType();
        if (type == Material.ENDER_PEARL && !enabled("ender-pearl.enabled") && !player.hasPermission("truecombat.bypass")) {
            event.setCancelled(true);
            actionBar(player, component("ender-pearl.disabled", player, 0));
        } else if (type == WIND_CHARGE && !enabled("wind-charge.enabled") && !player.hasPermission("truecombat.bypass")) {
            event.setCancelled(true);
            actionBar(player, component("wind-charge.disabled", player, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile)) return;
        Projectile projectile = (Projectile) event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player) projectile.getShooter();
        Material type = projectile.getType() == org.bukkit.entity.EntityType.ENDER_PEARL ? Material.ENDER_PEARL
                : projectile.getType() == WIND_CHARGE_ENTITY ? WIND_CHARGE : null;
        if (type == null) return;
        if (type == Material.ENDER_PEARL) {
            if (!enabled("ender-pearl.enabled") && !player.hasPermission("truecombat.bypass")) {
                event.setCancelled(true);
                actionBar(player, component("ender-pearl.disabled", player, 0));
            } else if (!player.hasPermission("truecombat.bypass")) {
                if (onCooldown(pearl, player)) event.setCancelled(true);
            }
            return;
        }
        if (!enabled("wind-charge.enabled") && !player.hasPermission("truecombat.bypass")) {
            event.setCancelled(true);
            actionBar(player, component("wind-charge.disabled", player, 0));
        } else if (active(combat, player.getUniqueId()) && !player.hasPermission("truecombat.bypass") && onCooldown(windCharge, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onSuccessfulProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile)) return;
        Projectile projectile = (Projectile) event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player) projectile.getShooter();
        if (player.hasPermission("truecombat.bypass")) return;
        if (projectile.getType() == org.bukkit.entity.EntityType.ENDER_PEARL) {
            if (enabled("ender-pearl.enabled") && active(combat, player.getUniqueId())) {
                start(pearl, player, "ender-pearl.cooldown-seconds", Material.ENDER_PEARL);
            }
        } else if (projectile.getType() == WIND_CHARGE_ENTITY
                && enabled("wind-charge.enabled")
                && (!plugin.getConfig().getBoolean("wind-charge.only-in-combat", true) || active(combat, player.getUniqueId()))) {
            start(windCharge, player, "wind-charge.cooldown-seconds", WIND_CHARGE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        if (!enabled("trident.enabled") || player.hasPermission("truecombat.bypass")
                || !event.getItem().containsEnchantment(org.bukkit.enchantments.Enchantment.RIPTIDE)) return;
        if (plugin.getConfig().getBoolean("trident.only-in-combat", true) && !active(combat, player.getUniqueId())) return;
        start(trident, player, "trident.cooldown-seconds", Material.TRIDENT);
    }

    @EventHandler(ignoreCancelled = true)
    private void onTotemPickup(EntityPickupItemEvent event) {
        if (!enabled("totems.enabled") || !(event.getEntity() instanceof Player player)
                || event.getItem().getItemStack().getType() != Material.TOTEM_OF_UNDYING) return;
        int total = countTotems(player.getInventory());
        if (total + event.getItem().getItemStack().getAmount() > limit()) {
            event.setCancelled(true);
            warnTotem(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onTotemClick(InventoryClickEvent event) {
        if (!enabled("totems.enabled") || !(event.getWhoClicked() instanceof Player player)) return;
        validateAfterInventoryChange(player);
    }

    @EventHandler(ignoreCancelled = true)
    private void onTotemDrag(InventoryDragEvent event) {
        if (!enabled("totems.enabled") || !(event.getWhoClicked() instanceof Player player) || !isTotem(event.getOldCursor())) return;
        if (countTotems(player.getInventory()) + event.getOldCursor().getAmount() > limit()) {
            event.setCancelled(true);
            warnTotem(player);
        }
    }

    @EventHandler
    private void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        UUID victimId = victim.getUniqueId();
        List<UUID> combatPartners = new ArrayList<>();
        Map<UUID, Long> incoming = incomingAttackers.get(victimId);
        Map<UUID, Long> outgoing = outgoingTargets.get(victimId);
        if (incoming != null) combatPartners.addAll(incoming.keySet());
        if (outgoing != null) combatPartners.addAll(outgoing.keySet());
        if (killer != null) combatPartners.add(killer.getUniqueId());

        boolean wasInCombat = active(combat, victimId) && enabled("combat-log.enabled");
        Player lastPvPAttacker = lastAttacker.containsKey(victimId)
                ? plugin.getServer().getPlayer(lastAttacker.get(victimId)) : null;

        if (wasInCombat && plugin.getConfig().getBoolean("combat-log.reward-commands.enabled")) {
            runRewardCommandsOnDeath(victim, lastPvPAttacker, event);
        }

        removeAttacker(victimId);
        lastAttacker.remove(victimId);
        clearPlayerState(victim);

        combatPartners.stream().distinct()
                .map(plugin.getServer()::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .filter(player -> !hasActiveCombatPartner(player.getUniqueId()))
                .forEach(this::clearCombatTagKeepCooldowns);
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean loggedOutInCombat = plugin.combatLoggers().remove(player.getUniqueId());
        if (loggedOutInCombat && plugin.getConfig().getBoolean("combat-log.notify-on-rejoin")) {
            player.sendMessage(component("combat-log.rejoin-message", player, 0));
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean combatLogged = active(combat, player.getUniqueId()) && enabled("combat-log.enabled");
        Player attacker = lastAttacker.containsKey(player.getUniqueId())
                ? plugin.getServer().getPlayer(lastAttacker.get(player.getUniqueId())) : null;
        clearPlayerState(player);
        if (combatLogged) {
            plugin.combatLoggers().add(player.getUniqueId());
            if (plugin.getConfig().getBoolean("combat-log.reward-commands.enabled")) {
                runRewardCommands(player, attacker);
            }
            if (plugin.getConfig().getBoolean("combat-log.drop-inventory")) dropInventory(player);
            if (plugin.getConfig().getBoolean("combat-log.broadcast", true)) {
                broadcast(text("combat-log.broadcast", player, 0));
            }
            if (plugin.getConfig().getBoolean("combat-log.force-kill") && !player.isDead()) player.setHealth(0.0D);
        }
    }

    void tick() {
        long now = System.currentTimeMillis();
        expireIncomingAttackers(now);
        for (Player player : plugin.getServer().getOnlinePlayers()) plugin.scheduler().run(player, () -> {
            Long end = combat.get(player.getUniqueId());
            if (end != null && end <= System.currentTimeMillis()) {
                combat.remove(player.getUniqueId());
                sendState(player, false, 0L);
                player.sendMessage(component("combat.expired", player, 0));
            }
            sendHud(player, System.currentTimeMillis());
        });
    }

    void clear() {
        combat.clear(); trident.clear(); pearl.clear(); windCharge.clear(); mace.clear(); incomingAttackers.clear(); outgoingTargets.clear(); lastAttacker.clear();
    }

    void tagForAdmin(Player player) {
        tag(player);
    }

    void clearCombatTag(Player player) {
        clearCombatTagKeepCooldowns(player);
        sendState(player, false, 0L);
    }

    void clearPlayerStateForAdmin(Player player) {
        clearPlayerState(player);
    }

    int combatCount() {
        long now = System.currentTimeMillis();
        combat.entrySet().removeIf(entry -> entry.getValue() <= now);
        return combat.size();
    }

    private void tag(Player player) {
        UUID id = player.getUniqueId();
        boolean newTag = !active(combat, id);
        long duration = Math.max(1L, plugin.getConfig().getLong("pvp.duration-seconds", 30L));
        combat.put(id, System.currentTimeMillis() + duration * 1000L);
        sendState(player, true, duration);
        if (newTag) player.sendMessage(component("combat.entered", player, 0));
    }

    private void sendHud(Player player, long now) {
        if (!enabled("actionbar.enabled")) return;
        List<String> parts = new ArrayList<>(5);
        if (enabled("actionbar.show-ender-pearl")) addHud(parts, "actionbar.ender-pearl", pearl, player, now);
        if (enabled("actionbar.show-trident")) addHud(parts, "actionbar.trident", trident, player, now);
        if (enabled("actionbar.show-pvp")) addHud(parts, "actionbar.pvp", combat, player, now);
        if (enabled("actionbar.show-mace")) addHud(parts, "actionbar.mace", mace, player, now);
        if (enabled("actionbar.show-wind-charge")) addHud(parts, "actionbar.wind-charge", windCharge, player, now);
        if (!parts.isEmpty()) actionBar(player, legacy(String.join(plugin.locale().get("actionbar.separator"), parts)));
    }

    private void addHud(List<String> parts, String path, Map<UUID, Long> timer, Player player, long now) {
        long remaining = timer.getOrDefault(player.getUniqueId(), 0L) - now;
        if (remaining > 0) parts.add(plugin.locale().get(path).replace("<time>", Long.toString(roundSeconds(remaining))));
    }

    private boolean onCooldown(Map<UUID, Long> timer, Player player) {
        if (player.hasPermission("truecombat.bypass")) return false;
        long remaining = timer.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
        if (remaining <= 0L) {
            timer.remove(player.getUniqueId());
            return false;
        }
        sendHud(player, System.currentTimeMillis());
        return true;
    }

    private void start(Map<UUID, Long> timer, Player player, String secondsPath, Material overlay) {
        long seconds = seconds(secondsPath);
        if (seconds <= 0) return;
        timer.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.setCooldown(overlay, Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds * 20L)));
    }

    private void validateAfterInventoryChange(Player player) {
        plugin.scheduler().runLater(player, () -> {
            if (player.isValid()) trimTotems(player);
        }, 1L);
    }

    private void trimTotems(Player player) {
        int excess = countTotems(player.getInventory()) - limit();
        if (excess <= 0) return;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize() && excess > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            excess = removeTotems(item, excess, player);
            if (item != null && item.getAmount() <= 0) inventory.setItem(slot, null);
        }
        warnTotem(player);
    }

    private int removeTotems(ItemStack item, int excess, Player player) {
        if (item == null || excess <= 0) return excess;
        if (isTotem(item)) {
            int remove = Math.min(excess, item.getAmount());
            ItemStack dropped = item.clone();
            dropped.setAmount(remove);
            item.setAmount(item.getAmount() - remove);
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            return excess - remove;
        }
        if (!(item.getItemMeta() instanceof BundleMeta meta)) return excess;
        List<ItemStack> contents = new ArrayList<>(meta.getItems());
        for (ItemStack contained : contents) {
            excess = removeTotems(contained, excess, player);
            if (excess == 0) break;
        }
        contents.removeIf(contained -> contained == null || contained.getAmount() <= 0);
        meta.setItems(contents);
        item.setItemMeta(meta);
        return excess;
    }

    private int countTotems(Inventory inventory) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) total += countTotems(item);
        return total;
    }

    private int countTotems(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        if (item.getType() == Material.TOTEM_OF_UNDYING) return item.getAmount();
        if (!(item.getItemMeta() instanceof BundleMeta meta)) return 0;
        int total = 0;
        for (ItemStack contained : meta.getItems()) total += countTotems(contained);
        return total;
    }

    private void dropInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        inventory.clear();
    }

    private Player playerDamager(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        return null;
    }

    private void clearPlayer(UUID id) {
        combat.remove(id); trident.remove(id); pearl.remove(id); windCharge.remove(id); mace.remove(id);
        removeAttacker(id);
        incomingAttackers.remove(id);
        outgoingTargets.remove(id);
        lastAttacker.remove(id);
    }

    private void clearPlayerState(Player player) {
        clearPlayer(player.getUniqueId());
        sendState(player, false, 0L);
        actionBar(player, "");
    }

    private void sendState(Player player, boolean active, long seconds) {
        if (player.isOnline() && plugin.getConfig().getBoolean("proxy.sync-combat-state", true)) {
            player.sendPluginMessage(plugin, plugin.registeredProxyChannel(),
                    CombatProtocol.state(player.getUniqueId(), active, seconds));
        }
    }

    private void clearCombatTagKeepCooldowns(Player player) {
        if (combat.remove(player.getUniqueId()) == null) return;
        long now = System.currentTimeMillis();
        if (hasActiveItemCooldown(player.getUniqueId(), now)) sendHud(player, now);
        else actionBar(player, "");
    }

    private void runRewardCommands(Player logger, Player attacker) {
        for (String command : plugin.getConfig().getStringList("combat-log.reward-commands.commands")) {
            if (command == null || command.isBlank()) continue;
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command
                    .replace("<logger>", logger.getName())
                    .replace("<attacker>", attacker == null ? "" : attacker.getName()));
        }
    }

    private void runRewardCommandsOnDeath(Player logger, Player attacker, PlayerDeathEvent event) {
        ItemStack[] before = cloneContents(logger.getInventory().getContents());
        runRewardCommands(logger, attacker);
        ItemStack[] after = logger.getInventory().getContents();
        boolean[] matched = new boolean[before.length];

        for (int slot = 0; slot < after.length; slot++) {
            ItemStack current = after[slot];
            if (current == null || current.getType().isAir()) continue;

            int remaining = current.getAmount();
            for (int previousSlot = 0; previousSlot < before.length && remaining > 0; previousSlot++) {
                ItemStack previous = before[previousSlot];
                if (matched[previousSlot] || previous == null || previous.getType().isAir()
                        || !previous.isSimilar(current)) continue;
                int used = Math.min(remaining, previous.getAmount());
                matched[previousSlot] = true;
                remaining -= used;
            }

            if (remaining <= 0) continue;
            ItemStack dropped = current.clone();
            dropped.setAmount(remaining);
            event.getDrops().add(dropped);
            logger.getInventory().setItem(slot, slot < before.length ? before[slot] : null);
        }
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            copy[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return copy;
    }

    private boolean hasActiveItemCooldown(UUID playerId, long now) {
        return trident.getOrDefault(playerId, 0L) > now || pearl.getOrDefault(playerId, 0L) > now
                || windCharge.getOrDefault(playerId, 0L) > now || mace.getOrDefault(playerId, 0L) > now;
    }

    private boolean hasActiveCombatPartner(UUID playerId) {
        return hasActivePartner(incomingAttackers, playerId) || hasActivePartner(outgoingTargets, playerId);
    }

    private boolean hasActivePartner(Map<UUID, Map<UUID, Long>> relations, UUID playerId) {
        Map<UUID, Long> partners = relations.get(playerId);
        if (partners == null) return false;
        long now = System.currentTimeMillis();
        partners.values().removeIf(end -> end <= now);
        if (partners.isEmpty()) {
            relations.remove(playerId);
            return false;
        }
        return true;
    }

    private void removeAttacker(UUID attackerId) {
        incomingAttackers.entrySet().removeIf(entry -> {
            Map<UUID, Long> attackers = entry.getValue();
            attackers.remove(attackerId);
            return attackers.isEmpty();
        });
        outgoingTargets.entrySet().removeIf(entry -> {
            Map<UUID, Long> targets = entry.getValue();
            targets.remove(attackerId);
            return targets.isEmpty();
        });
    }

    private void expireIncomingAttackers(long now) {
        incomingAttackers.entrySet().removeIf(entry -> {
            Map<UUID, Long> attackers = entry.getValue();
            attackers.values().removeIf(end -> end <= now);
            return attackers.isEmpty();
        });
        outgoingTargets.entrySet().removeIf(entry -> {
            Map<UUID, Long> targets = entry.getValue();
            targets.values().removeIf(end -> end <= now);
            return targets.isEmpty();
        });
    }

    private boolean active(Map<UUID, Long> map, UUID id) { return map.getOrDefault(id, 0L) > System.currentTimeMillis(); }
    private boolean enabled(String path) { return plugin.getConfig().getBoolean(path, true); }
    private long seconds(String path) { return Math.max(0L, plugin.getConfig().getLong(path, 0L)); }
    private int limit() { return Math.max(0, plugin.getConfig().getInt("totems.maximum", 4)); }
    private boolean isTotem(ItemStack item) { return item != null && item.getType() == Material.TOTEM_OF_UNDYING; }
    private long roundSeconds(long millis) { return Math.max(1L, (millis + 999L) / 1000L); }
    private void warnTotem(Player player) { actionBar(player, component("totems.limit-reached", player, 0)); }
    private String component(String path, Player player, long seconds) { return legacy(text(path, player, seconds)); }
    private String legacy(String text) { return LEGACY.serialize(MINI_MESSAGE.deserialize(text)); }
    private void actionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }
    private void broadcast(String text) { plugin.scheduler().runGlobal(() -> plugin.getServer().broadcastMessage(legacy(text))); }
    private String text(String path, Player player, long seconds) {
        return plugin.locale().get(path).replace("<player>", player.getName()).replace("<maximum>", Integer.toString(limit())).replace("<time>", Long.toString(seconds));
    }
}
