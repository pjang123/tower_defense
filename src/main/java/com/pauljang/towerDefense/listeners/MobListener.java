package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class MobListener implements Listener {

    private final TowerDefense plugin;

    public MobListener(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobBurn(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        
        // If the entity has our custom TD Mob tag
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            NamespacedKey fireImmuneKey = new NamespacedKey(plugin, "td_fire_immune");
            
            // If immune to fire, cancel all combustion
            if (entity.getPersistentDataContainer().has(fireImmuneKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                return;
            }

            // If it's caused by a block (fire/lava) or another entity, we allow it.
            if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
                return;
            }
            // Cancel sunlight burning
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return;

        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (mob.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            // Check for fire-related damage if the mob has fire immunity
            NamespacedKey fireImmuneKey = new NamespacedKey(plugin, "td_fire_immune");
            if (mob.getPersistentDataContainer().has(fireImmuneKey, PersistentDataType.BYTE)) {
                EntityDamageEvent.DamageCause cause = event.getCause();
                if (cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                    cause == EntityDamageEvent.DamageCause.LAVA ||
                    cause == EntityDamageEvent.DamageCause.HOT_FLOOR ||
                    cause == EntityDamageEvent.DamageCause.MELTING) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Update the healthbar 1 tick later to get the updated health value after damage is applied
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (mob.isValid() && !mob.isDead()) {
                        plugin.getMobManager().updateHealthBar(mob);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onMobKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (!entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        NamespacedKey slowImmuneKey = new NamespacedKey(plugin, "td_slow_immune");
        if (entity.getPersistentDataContainer().has(slowImmuneKey, PersistentDataType.BYTE)) {
            org.bukkit.potion.PotionEffect newEffect = event.getNewEffect();
            if (newEffect != null) {
                org.bukkit.potion.PotionEffectType type = newEffect.getType();
                org.bukkit.potion.PotionEffectType slowType = org.bukkit.potion.PotionEffectType.getByName("SLOW");
                org.bukkit.potion.PotionEffectType slownessType = org.bukkit.potion.PotionEffectType.getByName("SLOWNESS");
                
                if ((slowType != null && type.equals(slowType)) || 
                    (slownessType != null && type.equals(slownessType))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onMobDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onMobTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (plugin.getGameManager().getBossBar() != null) {
            plugin.getGameManager().getBossBar().addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(org.bukkit.ChatColor.DARK_RED + "TD Mob Spawner")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            org.bukkit.inventory.ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            int slot = event.getRawSlot();
            com.pauljang.towerDefense.entities.PresetMobType presetType = null;
            switch (slot) {
                case 10 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.DEFAULT_ZOMBIE;
                case 11 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.SPEEDY_ZOMBIE;
                case 12 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.TANK_ZOMBIE;
                case 13 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.FIRE_ZOMBIE;
                case 14 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.PIGLIN;
                case 15 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.HOGLIN;
            }

            if (presetType != null) {
                if (event.isLeftClick()) {
                    plugin.getMobManager().addToQueue(player.getUniqueId(), presetType);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                } else if (event.isRightClick()) {
                    plugin.getMobManager().removeFromQueue(player.getUniqueId(), presetType);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.7f);
                }
                plugin.getMobManager().openMobSpawnerGUI(player);
                return;
            }

            if (slot == 21) { // Clear Queue
                plugin.getMobManager().clearQueue(player.getUniqueId());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
                plugin.getMobManager().openMobSpawnerGUI(player);
                player.sendMessage(org.bukkit.ChatColor.RED + "Cleared the mob spawner queue!");
            } else if (slot == 23) { // Send Wave
                plugin.getMobManager().sendQueue(player.getUniqueId());
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
                player.sendMessage(org.bukkit.ChatColor.GREEN + "Spawning the queued mob wave!");
            }
        } else if (title.startsWith(org.bukkit.ChatColor.DARK_BLUE + "Buy Tower: ")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            org.bukkit.inventory.ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            // Extract plot ID from title
            String plotId = title.substring((org.bukkit.ChatColor.DARK_BLUE + "Buy Tower: ").length());
            int slot = event.getRawSlot();

            switch (slot) {
                case 1 -> { // Archer Tower
                    plugin.getTowerManager().placeTower(plotId, com.pauljang.towerDefense.towers.TowerType.ARCHER);
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "Placed Archer Tower on plot " + plotId);
                }
                case 3 -> { // Mage Tower
                    plugin.getTowerManager().placeTower(plotId, com.pauljang.towerDefense.towers.TowerType.MAGE);
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "Placed Mage Tower on plot " + plotId);
                }
                case 5 -> { // Frost Tower
                    plugin.getTowerManager().placeTower(plotId, com.pauljang.towerDefense.towers.TowerType.FROST);
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "Placed Frost Tower on plot " + plotId);
                }
                case 8 -> { // Demolish Tower
                    plugin.getTowerManager().removeTower(plotId);
                    player.sendMessage(org.bukkit.ChatColor.RED + "Demolished Tower on plot " + plotId);
                }
            }
            player.closeInventory();
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
        }
    }
}


