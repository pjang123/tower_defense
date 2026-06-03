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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class MobListener implements Listener {

    private final TowerDefense plugin;

    public MobListener(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof org.bukkit.entity.Ageable ageable) {
            ageable.setAdult();
        }
        if (entity instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setBaby(false);
        }
        if (entity instanceof org.bukkit.entity.Piglin piglin) {
            piglin.setBaby(false);
        }
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

            // Get the arena this mob was walking on
            String mobArena = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, "td_arena"), PersistentDataType.STRING);
            if (mobArena == null) mobArena = "1";

            // Award bounty gold only to active players on that track
            NamespacedKey rewardKey = new NamespacedKey(plugin, "td_gold_reward");
            if (entity.getPersistentDataContainer().has(rewardKey, PersistentDataType.INTEGER)) {
                int reward = entity.getPersistentDataContainer().get(rewardKey, PersistentDataType.INTEGER);
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameManager().getPlayerArena(player.getUniqueId()).equals(mobArena)) {
                        plugin.getGameManager().addGold(player.getUniqueId(), reward);
                    }
                }
            }

            // Award experience only to active players on that track
            NamespacedKey xpRewardKey = new NamespacedKey(plugin, "td_xp_reward");
            if (entity.getPersistentDataContainer().has(xpRewardKey, PersistentDataType.INTEGER)) {
                int xpReward = entity.getPersistentDataContainer().get(xpRewardKey, PersistentDataType.INTEGER);
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameManager().getPlayerArena(player.getUniqueId()).equals(mobArena)) {
                        plugin.getGameManager().addExp(player.getUniqueId(), xpReward);
                    }
                }
            }
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
        if (plugin.getGameManager().getCurrentState() == com.pauljang.towerDefense.core.GameState.ACTIVE) {
            plugin.getGameManager().giveStarterWeapons(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        org.bukkit.inventory.ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                if (name.equals(org.bukkit.ChatColor.LIGHT_PURPLE + "" + org.bukkit.ChatColor.BOLD + "Mob Spawner Menu") ||
                    name.equals(org.bukkit.ChatColor.GOLD + "" + org.bukkit.ChatColor.BOLD + "Player Upgrades Menu")) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        String title = event.getView().getTitle();
        if (title.equals(org.bukkit.ChatColor.DARK_RED + "TD Mob Spawner")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            int slot = event.getRawSlot();
            com.pauljang.towerDefense.entities.PresetMobType presetType = null;
            switch (slot) {
                case 10 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.DEFAULT_ZOMBIE;
                case 11 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.GIANT;
                case 12 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.FIRE_ZOMBIE;
                case 13 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.PIGLIN;
                case 14 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.HOGLIN;
            }

            if (presetType != null) {
                if (event.isLeftClick()) {
                    int cost = presetType.getSpawnCost();
                    if (plugin.getGameManager().removeGold(player.getUniqueId(), cost)) {
                        plugin.getMobManager().addToQueue(player.getUniqueId(), presetType);
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    }
                } else if (event.isRightClick()) {
                    java.util.Map<com.pauljang.towerDefense.entities.PresetMobType, Integer> queue = plugin.getMobManager().getQueue(player.getUniqueId());
                    int count = queue.getOrDefault(presetType, 0);
                    if (count > 0) {
                        plugin.getMobManager().removeFromQueue(player.getUniqueId(), presetType);
                        plugin.getGameManager().addGold(player.getUniqueId(), presetType.getSpawnCost(), true); // Refund Gold
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.7f);
                    }
                }
                plugin.getMobManager().openMobSpawnerGUI(player);
                return;
            }

            if (slot == 21) { // Clear Queue and refund
                java.util.Map<com.pauljang.towerDefense.entities.PresetMobType, Integer> queue = plugin.getMobManager().getQueue(player.getUniqueId());
                int totalRefund = 0;
                for (java.util.Map.Entry<com.pauljang.towerDefense.entities.PresetMobType, Integer> entry : queue.entrySet()) {
                    totalRefund += entry.getKey().getSpawnCost() * entry.getValue();
                }
                plugin.getMobManager().clearQueue(player.getUniqueId());
                if (totalRefund > 0) {
                    plugin.getGameManager().addGold(player.getUniqueId(), totalRefund);
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
                plugin.getMobManager().openMobSpawnerGUI(player);
                player.sendMessage(org.bukkit.ChatColor.RED + "Cleared the mob spawner queue and refunded " + totalRefund + " Gold!");
            } else if (slot == 23) { // Send Wave
                plugin.getMobManager().sendQueue(player.getUniqueId());
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
                player.sendMessage(org.bukkit.ChatColor.GREEN + "Spawning the queued mob wave!");
            } else if (slot == 26) { // Open upgrades screen
                plugin.getGameManager().openUpgradesGUI(player);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
        } else if (title.startsWith(org.bukkit.ChatColor.DARK_BLUE + "Buy Tower: ")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            String plotId = title.substring((org.bukkit.ChatColor.DARK_BLUE + "Buy Tower: ").length());
            int slot = event.getRawSlot();

            com.pauljang.towerDefense.towers.TowerType type = null;
            switch (slot) {
                case 11 -> type = com.pauljang.towerDefense.towers.TowerType.ARCHER;
                case 13 -> type = com.pauljang.towerDefense.towers.TowerType.MAGE;
                case 15 -> type = com.pauljang.towerDefense.towers.TowerType.FROST;
            }

            if (type != null) {
                int cost = type.getCost();
                if (plugin.getGameManager().removeGold(player.getUniqueId(), cost)) {
                    plugin.getTowerManager().placeTower(plotId, type);
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "Placed " + type.getDisplayName() + " on plot " + plotId + "!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
                    player.closeInventory();
                } else {
                    player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
            }
        } else if (title.startsWith(org.bukkit.ChatColor.DARK_BLUE + "Manage Tower: ")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            // Extract plot ID from title
            String plotId = title.substring((org.bukkit.ChatColor.DARK_BLUE + "Manage Tower: ").length());
            com.pauljang.towerDefense.towers.Tower tower = plugin.getTowerManager().getTower(plotId);
            if (tower == null) {
                player.closeInventory();
                return;
            }

            int slot = event.getRawSlot();
            switch (slot) {
                case 22 -> { // Upgrade Tower
                    int cost = tower.getUpgradeCost();
                    if (cost == -1) {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Tower is already at the maximum level!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                        return;
                    }
                    if (plugin.getGameManager().removeGold(player.getUniqueId(), cost)) {
                        tower.incrementLevel();
                        plugin.getTowerManager().updateHologram(tower);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Upgraded Tower to Level " + tower.getLevel() + "!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        // Refresh GUI
                        plugin.getTowerManager().openManageTowerGUI(player, plotId);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 31 -> { // Cycle Targeting Mode
                    com.pauljang.towerDefense.towers.TargetingMode current = tower.getTargetingMode();
                    com.pauljang.towerDefense.towers.TargetingMode next = com.pauljang.towerDefense.towers.TargetingMode.FIRST;
                    switch (current) {
                        case FIRST: next = com.pauljang.towerDefense.towers.TargetingMode.LAST; break;
                        case LAST: next = com.pauljang.towerDefense.towers.TargetingMode.STRONG; break;
                        case STRONG: next = com.pauljang.towerDefense.towers.TargetingMode.WEAK; break;
                        case WEAK: next = com.pauljang.towerDefense.towers.TargetingMode.CLOSE; break;
                        case CLOSE: next = com.pauljang.towerDefense.towers.TargetingMode.FIRST; break;
                    }
                    tower.setTargetingMode(next);
                    plugin.getTowerManager().updateHologram(tower);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    // Refresh GUI
                    plugin.getTowerManager().openManageTowerGUI(player, plotId);
                }
                case 40 -> { // Destroy Tower
                    int refund = tower.getTotalValue() / 2;
                    plugin.getTowerManager().removeTower(plotId);
                    plugin.getGameManager().addGold(player.getUniqueId(), refund);
                    player.closeInventory();
                    player.sendMessage(org.bukkit.ChatColor.RED + "Demolished Tower on plot " + plotId + "! Refunded " + refund + " Gold.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.0f);
                }
            }
        } else if (title.equals(org.bukkit.ChatColor.DARK_BLUE + "Player Upgrades")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            int slot = event.getRawSlot();
            java.util.UUID uuid = player.getUniqueId();
            com.pauljang.towerDefense.core.GameManager gm = plugin.getGameManager();

            switch (slot) {
                case 4 -> { // Passive Gold Generator Upgrade
                    int lvl = gm.getGoldGenLevel(uuid);
                    if (lvl >= 4) {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Passive Gold Generator is already at max level!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                        return;
                    }
                    int cost = lvl == 1 ? 100 : lvl == 2 ? 300 : 600;
                    if (gm.getExp(uuid) >= cost) {
                        gm.removeExp(uuid, cost);
                        gm.setGoldGenLevel(uuid, lvl + 1);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Upgraded Passive Gold Generator to Level " + (lvl + 1) + "!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough TD EXP! Requires " + cost + " EXP.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 10 -> { // Sword Upgrade
                    gm.upgradeSword(player);
                }
                case 11 -> { // Bow Upgrade
                    int lvl = gm.getBowLevel(uuid);
                    if (lvl >= 3) {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Your bow is already at max level!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                        return;
                    }
                    int cost = lvl == 1 ? 150 : 450;
                    if (gm.getExp(uuid) >= cost) {
                        gm.removeExp(uuid, cost);
                        gm.setBowLevel(uuid, lvl + 1);
                        gm.giveStarterWeapons(player);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Upgraded bow to Level " + (lvl + 1) + "!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough TD EXP! Requires " + cost + " EXP.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 15 -> { // Instant Heal II Splash Potion (Costs 30 EXP)
                    int cost = 30;
                    if (gm.getExp(uuid) >= cost) {
                        gm.removeExp(uuid, cost);
                        org.bukkit.inventory.ItemStack pot = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPLASH_POTION);
                        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) pot.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(org.bukkit.ChatColor.RED + "Healing Potion II");
                            org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName("HEALING");
                            if (type == null) type = org.bukkit.potion.PotionEffectType.getByName("INSTANT_HEAL");
                            if (type != null) {
                                meta.addCustomEffect(new org.bukkit.potion.PotionEffect(type, 1, 1), true);
                            }
                            pot.setItemMeta(meta);
                        }
                        player.getInventory().addItem(pot);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Purchased Splash Potion of Healing II!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough TD EXP! Requires " + cost + " EXP.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 16 -> { // Slowness Splash Potion (Costs 45 EXP)
                    int cost = 45;
                    if (gm.getExp(uuid) >= cost) {
                        gm.removeExp(uuid, cost);
                        org.bukkit.inventory.ItemStack pot = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPLASH_POTION);
                        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) pot.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(org.bukkit.ChatColor.BLUE + "Slowness Splash Potion");
                            org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName("SLOWNESS");
                            if (type == null) type = org.bukkit.potion.PotionEffectType.getByName("SLOW");
                            if (type != null) {
                                meta.addCustomEffect(new org.bukkit.potion.PotionEffect(type, 200, 1), true);
                            }
                            pot.setItemMeta(meta);
                        }
                        player.getInventory().addItem(pot);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Purchased Splash Potion of Slowness II!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough TD EXP! Requires " + cost + " EXP.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 17 -> { // Harming Splash Potion II (Costs 50 EXP)
                    int cost = 50;
                    if (gm.getExp(uuid) >= cost) {
                        gm.removeExp(uuid, cost);
                        org.bukkit.inventory.ItemStack pot = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPLASH_POTION);
                        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) pot.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(org.bukkit.ChatColor.DARK_PURPLE + "Harming Splash Potion II");
                            org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName("HARMING");
                            if (type == null) type = org.bukkit.potion.PotionEffectType.getByName("INSTANT_DAMAGE");
                            if (type != null) {
                                meta.addCustomEffect(new org.bukkit.potion.PotionEffect(type, 1, 1), true);
                            }
                            pot.setItemMeta(meta);
                        }
                        player.getInventory().addItem(pot);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Purchased Splash Potion of Harming II!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough TD EXP! Requires " + cost + " EXP.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 45 -> { // Back to spawner GUI
                    plugin.getMobManager().openMobSpawnerGUI(player);
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.8f);
                    return;
                }
            }
            gm.openUpgradesGUI(player);
        }
    }

    @EventHandler
    public void onBowShoot(org.bukkit.event.entity.EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player player)) return;

        int bowLevel = plugin.getGameManager().getBowLevel(player.getUniqueId());
        event.setCancelled(true);
        org.bukkit.util.Vector velocity = event.getProjectile().getVelocity();

        if (bowLevel == 3) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f);
            
            org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
            arrow.setVelocity(velocity);
            arrow.setPierceLevel(4);
            
            // Replenish the arrow consumed when charging the crossbow
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW));
        } else if (bowLevel == 2) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f);
            org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
            arrow.setVelocity(velocity);
            
            // Replenish the arrow consumed when charging the crossbow
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW));
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
            org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
            arrow.setVelocity(velocity);
        }
    }

    @EventHandler
    public void onItemDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            org.bukkit.Material type = event.getItem().getType();
            if (type == org.bukkit.Material.BOW || type == org.bukkit.Material.CROSSBOW || type.name().endsWith("_SWORD")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String displayName = meta.getDisplayName();
        if (displayName.equals(org.bukkit.ChatColor.LIGHT_PURPLE + "" + org.bukkit.ChatColor.BOLD + "Mob Spawner Menu")) {
            event.setCancelled(true);
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                plugin.getMobManager().openMobSpawnerGUI(player);
            }
        } else if (displayName.equals(org.bukkit.ChatColor.GOLD + "" + org.bukkit.ChatColor.BOLD + "Player Upgrades Menu")) {
            event.setCancelled(true);
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                plugin.getGameManager().openUpgradesGUI(player);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        org.bukkit.inventory.ItemStack item = event.getItemDrop().getItemStack();
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                if (name.equals(org.bukkit.ChatColor.LIGHT_PURPLE + "" + org.bukkit.ChatColor.BOLD + "Mob Spawner Menu") ||
                    name.equals(org.bukkit.ChatColor.GOLD + "" + org.bukkit.ChatColor.BOLD + "Player Upgrades Menu")) {
                    event.setCancelled(true);
                }
            }
        }
    }
}


