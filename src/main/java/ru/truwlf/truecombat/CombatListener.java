package ru.truwlf.truecombat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CombatListener implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final TrueCombatPlugin plugin;
    private final Map<UUID, Long> combat = new HashMap<>();
    private final Map<UUID, Long> trident = new HashMap<>();
    private final Map<UUID, Long> pearl = new HashMap<>();
    private final Map<UUID, Long> windCharge = new HashMap<>();
    private final Map<UUID, Long> mace = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> incomingAttackers = new HashMap<>();

    CombatListener(TrueCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onMaceDamage(EntityDamageByEntityEvent event) {
        if (!enabled("mace.enabled") || !(event.getDamager() instanceof Player player)
                || player.getInventory().getItemInMainHand().getType() != Material.MACE) return;
        if (onCooldown(mace, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!enabled("pvp.enabled") || !(event.getEntity() instanceof Player victim)) return;
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (plugin.getConfig().getBoolean("pvp.refresh-on-hit", true) || !active(combat, attacker.getUniqueId())) tag(attacker);
        if (plugin.getConfig().getBoolean("pvp.refresh-on-hit", true) || !active(combat, victim.getUniqueId())) tag(victim);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        incomingAttackers.computeIfAbsent(victim.getUniqueId(), ignored -> new HashMap<>())
                .put(attacker.getUniqueId(), combat.get(victim.getUniqueId()));
        if (enabled("mace.enabled") && attacker.getInventory().getItemInMainHand().getType() == Material.MACE) {
            start(mace, attacker, "mace.cooldown-seconds", Material.MACE);
        } else if (enabled("wind-charge.enabled") && event.getDamager().getType() == org.bukkit.entity.EntityType.WIND_CHARGE) {
            start(windCharge, attacker, "wind-charge.cooldown-seconds", Material.WIND_CHARGE);
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
            player.sendActionBar(component("ender-pearl.disabled", player, 0));
        } else if (type == Material.WIND_CHARGE && !enabled("wind-charge.enabled") && !player.hasPermission("truecombat.bypass")) {
            event.setCancelled(true);
            player.sendActionBar(component("wind-charge.disabled", player, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player player)) return;
        Material type = projectile.getType() == org.bukkit.entity.EntityType.ENDER_PEARL ? Material.ENDER_PEARL
                : projectile.getType() == org.bukkit.entity.EntityType.WIND_CHARGE ? Material.WIND_CHARGE : null;
        if (type == null) return;
        if (type == Material.ENDER_PEARL) {
            if (!enabled("ender-pearl.enabled") && !player.hasPermission("truecombat.bypass")) {
                event.setCancelled(true);
                player.sendActionBar(component("ender-pearl.disabled", player, 0));
            } else if (!player.hasPermission("truecombat.bypass")) {
                if (onCooldown(pearl, player)) event.setCancelled(true);
            }
            return;
        }
        if (!enabled("wind-charge.enabled") && !player.hasPermission("truecombat.bypass")) {
            event.setCancelled(true);
            player.sendActionBar(component("wind-charge.disabled", player, 0));
        } else if (active(combat, player.getUniqueId()) && !player.hasPermission("truecombat.bypass") && onCooldown(windCharge, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onSuccessfulProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player player)
                || player.hasPermission("truecombat.bypass")) return;
        if (projectile.getType() == org.bukkit.entity.EntityType.ENDER_PEARL) {
            if (enabled("ender-pearl.enabled") && active(combat, player.getUniqueId())) {
                start(pearl, player, "ender-pearl.cooldown-seconds", Material.ENDER_PEARL);
            }
        } else if (projectile.getType() == org.bukkit.entity.EntityType.WIND_CHARGE
                && enabled("wind-charge.enabled")
                && (!plugin.getConfig().getBoolean("wind-charge.only-in-combat", true) || active(combat, player.getUniqueId()))) {
            start(windCharge, player, "wind-charge.cooldown-seconds", Material.WIND_CHARGE);
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
        removeAttacker(victim.getUniqueId());
        lastAttacker.remove(victim.getUniqueId());
        clearPlayerState(victim);
        if (killer != null && !hasActiveIncomingAttacker(killer.getUniqueId())) {
            clearCombatTagKeepCooldowns(killer);
        }
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
                for (String command : plugin.getConfig().getStringList("combat-log.reward-commands.commands")) {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command
                            .replace("<logger>", player.getName())
                            .replace("<attacker>", attacker == null ? "" : attacker.getName()));
                }
            }
            if (plugin.getConfig().getBoolean("combat-log.drop-inventory")) dropInventory(player);
            if (plugin.getConfig().getBoolean("combat-log.broadcast", true)) {
                plugin.getServer().broadcast(MINI_MESSAGE.deserialize(text("combat-log.broadcast", player, 0)));
            }
            if (plugin.getConfig().getBoolean("combat-log.force-kill") && !player.isDead()) player.setHealth(0.0D);
        }
    }

    void tick() {
        long now = System.currentTimeMillis();
        expireIncomingAttackers(now);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Long end = combat.get(player.getUniqueId());
            if (end != null && end <= now) {
                combat.remove(player.getUniqueId());
                player.sendMessage(component("combat.expired", player, 0));
            }
            sendHud(player, now);
        }
    }

    void clear() {
        combat.clear(); trident.clear(); pearl.clear(); windCharge.clear(); mace.clear(); incomingAttackers.clear(); lastAttacker.clear();
    }

    void tagForAdmin(Player player) {
        tag(player);
    }

    void clearCombatTag(Player player) {
        clearCombatTagKeepCooldowns(player);
    }

    void clearPlayerStateForAdmin(Player player) {
        clearPlayerState(player);
    }

    private void tag(Player player) {
        UUID id = player.getUniqueId();
        boolean newTag = !active(combat, id);
        combat.put(id, System.currentTimeMillis() + seconds("pvp.duration-seconds") * 1000L);
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
        if (!parts.isEmpty()) player.sendActionBar(MINI_MESSAGE.deserialize(String.join(plugin.locale().get("actionbar.separator"), parts)));
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
        plugin.getServer().getScheduler().runTask(plugin, () -> trimTotems(player));
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
        lastAttacker.remove(id);
    }

    private void clearPlayerState(Player player) {
        clearPlayer(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    private void clearCombatTagKeepCooldowns(Player player) {
        if (combat.remove(player.getUniqueId()) == null) return;
        long now = System.currentTimeMillis();
        if (hasActiveItemCooldown(player.getUniqueId(), now)) sendHud(player, now);
        else player.sendActionBar(Component.empty());
    }

    private boolean hasActiveItemCooldown(UUID playerId, long now) {
        return trident.getOrDefault(playerId, 0L) > now || pearl.getOrDefault(playerId, 0L) > now
                || windCharge.getOrDefault(playerId, 0L) > now || mace.getOrDefault(playerId, 0L) > now;
    }

    private boolean hasActiveIncomingAttacker(UUID playerId) {
        Map<UUID, Long> attackers = incomingAttackers.get(playerId);
        if (attackers == null) return false;
        long now = System.currentTimeMillis();
        attackers.values().removeIf(end -> end <= now);
        if (attackers.isEmpty()) {
            incomingAttackers.remove(playerId);
            return false;
        }
        return true;
    }

    private void removeAttacker(UUID attackerId) {
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> iterator = incomingAttackers.entrySet().iterator(); iterator.hasNext();) {
            Map<UUID, Long> attackers = iterator.next().getValue();
            attackers.remove(attackerId);
            if (attackers.isEmpty()) iterator.remove();
        }
    }

    private void expireIncomingAttackers(long now) {
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> iterator = incomingAttackers.entrySet().iterator(); iterator.hasNext();) {
            Map<UUID, Long> attackers = iterator.next().getValue();
            attackers.values().removeIf(end -> end <= now);
            if (attackers.isEmpty()) iterator.remove();
        }
    }

    private boolean active(Map<UUID, Long> map, UUID id) { return map.getOrDefault(id, 0L) > System.currentTimeMillis(); }
    private boolean enabled(String path) { return plugin.getConfig().getBoolean(path, true); }
    private long seconds(String path) { return Math.max(0L, plugin.getConfig().getLong(path, 0L)); }
    private int limit() { return Math.max(0, plugin.getConfig().getInt("totems.maximum", 4)); }
    private boolean isTotem(ItemStack item) { return item != null && item.getType() == Material.TOTEM_OF_UNDYING; }
    private long roundSeconds(long millis) { return Math.max(1L, (millis + 999L) / 1000L); }
    private void warnTotem(Player player) { player.sendActionBar(component("totems.limit-reached", player, 0)); }
    private Component component(String path, Player player, long seconds) { return MINI_MESSAGE.deserialize(text(path, player, seconds)); }
    private String text(String path, Player player, long seconds) {
        return plugin.locale().get(path).replace("<player>", player.getName()).replace("<maximum>", Integer.toString(limit())).replace("<time>", Long.toString(seconds));
    }
}
