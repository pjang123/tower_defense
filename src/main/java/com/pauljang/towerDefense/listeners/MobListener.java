package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
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

            // Slime / Magma Cube Split Handling
            if (entity instanceof org.bukkit.entity.Slime || entity instanceof org.bukkit.entity.MagmaCube) {
                final Location deathLoc = entity.getLocation();
                final String arena = mobArena;
                int parentWpIndex = 0;
                for (com.pauljang.towerDefense.entities.TDMob tdMob : plugin.getMobManager().getActiveMobs()) {
                    if (tdMob.getEntity().equals(entity)) {
                        parentWpIndex = tdMob.getCurrentWaypointIndex();
                        break;
                    }
                }
                final int wpIndex = parentWpIndex;
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    double radius = 3.0;
                    for (org.bukkit.entity.Entity nearby : deathLoc.getWorld().getNearbyEntities(deathLoc, radius, radius, radius)) {
                        if (nearby instanceof org.bukkit.entity.Slime child) {
                            NamespacedKey mobKey = new NamespacedKey(plugin, "td_mob");
                            if (!child.getPersistentDataContainer().has(mobKey, PersistentDataType.BYTE)) {
                                child.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
                                child.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_arena"), PersistentDataType.STRING, arena);
                                child.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_preset"), PersistentDataType.STRING, child.getType() == org.bukkit.entity.EntityType.MAGMA_CUBE ? "magma_cube" : "slime");
                                child.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_gold_reward"), PersistentDataType.INTEGER, 0); // No farming split slimes
                                child.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_xp_reward"), PersistentDataType.INTEGER, 0);
                                
                                org.bukkit.attribute.AttributeInstance kbResist = child.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
                                if (kbResist != null) {
                                    kbResist.setBaseValue(1.0);
                                }
                                
                                java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
                                com.pauljang.towerDefense.entities.TDMob childTDMob = new com.pauljang.towerDefense.entities.TDMob(child, waypoints);
                                childTDMob.setCurrentWaypointIndex(wpIndex);
                                plugin.getMobManager().getActiveMobs().add(childTDMob);
                                plugin.getMobManager().updateHealthBar(child);
                            }
                        }
                    }
                }, 1L);
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
                case 10 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.ZOMBIE;
                case 11 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.SKELETON;
                case 12 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.SILVERFISH;
                case 13 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.SPIDER;
                case 14 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.PIGMAN;
                case 15 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.SLIME;
                case 16 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.CREEPER;
                case 20 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.BLAZE;
                case 21 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.MAGMA_CUBE;
                case 22 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.GHAST;
                case 23 -> presetType = com.pauljang.towerDefense.entities.PresetMobType.GIANT;
            }

            if (presetType != null) {
                if (event.isLeftClick()) {
                    int cost = plugin.getConfig().getInt("mobs." + presetType.name().toLowerCase() + ".spawn-cost", presetType.getSpawnCost());
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
                        int refund = plugin.getConfig().getInt("mobs." + presetType.name().toLowerCase() + ".spawn-cost", presetType.getSpawnCost());
                        plugin.getGameManager().addGold(player.getUniqueId(), refund, true); // Refund Gold
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.7f);
                    }
                }
                plugin.getMobManager().openMobSpawnerGUI(player);
                return;
            }

            if (slot == 38) { // Clear Queue and refund
                java.util.Map<com.pauljang.towerDefense.entities.PresetMobType, Integer> queue = plugin.getMobManager().getQueue(player.getUniqueId());
                int totalRefund = 0;
                for (java.util.Map.Entry<com.pauljang.towerDefense.entities.PresetMobType, Integer> entry : queue.entrySet()) {
                    int cost = plugin.getConfig().getInt("mobs." + entry.getKey().name().toLowerCase() + ".spawn-cost", entry.getKey().getSpawnCost());
                    totalRefund += cost * entry.getValue();
                }
                plugin.getMobManager().clearQueue(player.getUniqueId());
                if (totalRefund > 0) {
                    plugin.getGameManager().addGold(player.getUniqueId(), totalRefund);
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
                plugin.getMobManager().openMobSpawnerGUI(player);
                player.sendMessage(org.bukkit.ChatColor.RED + "Cleared the mob spawner queue and refunded " + totalRefund + " Gold!");
            } else if (slot == 40) { // Send Wave
                plugin.getMobManager().sendQueue(player.getUniqueId());
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
                player.sendMessage(org.bukkit.ChatColor.GREEN + "Spawning the queued mob wave!");
            } else if (slot == 42) { // Open upgrades screen
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
                        plugin.getTowerManager().buildTowerStructure(tower);
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
                    int cost = lvl == 1 ? plugin.getConfig().getInt("upgrades.gold-gen.upgrade-costs.level2", 100) : lvl == 2 ? plugin.getConfig().getInt("upgrades.gold-gen.upgrade-costs.level3", 300) : plugin.getConfig().getInt("upgrades.gold-gen.upgrade-costs.level4", 600);
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
                    int cost = lvl == 1 ? plugin.getConfig().getInt("upgrades.bow.upgrade-costs.level2", 150) : plugin.getConfig().getInt("upgrades.bow.upgrade-costs.level3", 450);
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
                case 12 -> { // Overcharge Spell
                    int cost = plugin.getConfig().getInt("spells.overcharge.cost", 250);
                    int duration = plugin.getConfig().getInt("spells.overcharge.duration", 10);
                    String arena = gm.getPlayerArena(uuid);
                    if (gm.hasGold(uuid, cost)) {
                        gm.removeGold(uuid, cost);
                        gm.castSpell(arena, "OVERCHARGE", duration);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Activated Spell: Overcharge on your track for " + duration + " seconds!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 13 -> { // Freeze Spell
                    int cost = plugin.getConfig().getInt("spells.freeze.cost", 200);
                    int duration = plugin.getConfig().getInt("spells.freeze.duration", 10);
                    String arena = gm.getPlayerArena(uuid);
                    if (gm.hasGold(uuid, cost)) {
                        gm.removeGold(uuid, cost);
                        gm.castSpell(arena, "FREEZE", duration);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Activated Spell: Freeze on your track for " + duration + " seconds!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 14 -> { // Damage Storm Spell
                    int cost = plugin.getConfig().getInt("spells.damage-storm.cost", 300);
                    int duration = plugin.getConfig().getInt("spells.damage-storm.duration", 10);
                    String arena = gm.getPlayerArena(uuid);
                    if (gm.hasGold(uuid, cost)) {
                        gm.removeGold(uuid, cost);
                        gm.castSpell(arena, "DAMAGE_STORM", duration);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Activated Spell: Damage Storm on your track for " + duration + " seconds!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 21 -> { // Haste Rush Sabotage (Cast on Opponent)
                    int cost = plugin.getConfig().getInt("spells.haste-rush.cost", 200);
                    int duration = plugin.getConfig().getInt("spells.haste-rush.duration", 6);
                    String playerArena = gm.getPlayerArena(uuid);
                    String targetArena = playerArena.equals("1") ? "2" : "1";
                    if (gm.hasGold(uuid, cost)) {
                        gm.removeGold(uuid, cost);
                        gm.castSpell(targetArena, "HASTE_RUSH", duration);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Activated Sabotage: Haste Rush on your opponent's track for " + duration + " seconds!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 22 -> { // Tower EMP Sabotage (Cast on Opponent)
                    int cost = plugin.getConfig().getInt("spells.tower-emp.cost", 250);
                    int duration = plugin.getConfig().getInt("spells.tower-emp.duration", 6);
                    String playerArena = gm.getPlayerArena(uuid);
                    String targetArena = playerArena.equals("1") ? "2" : "1";
                    if (gm.hasGold(uuid, cost)) {
                        gm.removeGold(uuid, cost);
                        gm.castSpell(targetArena, "TOWER_EMP", duration);
                        gm.disableRandomTower(targetArena, duration);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Activated Sabotage: Tower EMP on your opponent's track for " + duration + " seconds!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                }
                case 23 -> { // Slow Shield Sabotage (Cast on Opponent)
                    int cost = plugin.getConfig().getInt("spells.slow-shield.cost", 150);
                    int duration = plugin.getConfig().getInt("spells.slow-shield.duration", 10);
                    String playerArena = gm.getPlayerArena(uuid);
                    String targetArena = playerArena.equals("1") ? "2" : "1";
                    if (gm.hasGold(uuid, cost)) {
                        gm.removeGold(uuid, cost);
                        gm.castSpell(targetArena, "SLOW_SHIELD", duration);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Activated Sabotage: Slow Shield on your opponent's track for " + duration + " seconds!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + cost + " Gold.");
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
        if (item == null) return;

        // Custom bow / crossbow shooting with zero arrows in inventory
        if (item.getType() == org.bukkit.Material.BOW || item.getType() == org.bukkit.Material.CROSSBOW) {
            org.bukkit.event.block.Action action = event.getAction();
            if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                if (plugin.getGameManager().getCurrentState() != com.pauljang.towerDefense.core.GameState.ACTIVE) {
                    return;
                }
                
                if (player.hasCooldown(item.getType())) {
                    return;
                }
                
                event.setCancelled(true);
                
                int bowLevel = plugin.getGameManager().getBowLevel(player.getUniqueId());
                int cooldownTicks = 15; // default bow: 15 ticks (0.75s)
                
                if (item.getType() == org.bukkit.Material.CROSSBOW) {
                    cooldownTicks = bowLevel == 3 ? 8 : 10; // Level 3: 8 ticks (0.4s), Level 2: 10 ticks (0.5s)
                }

                player.setCooldown(item.getType(), cooldownTicks);
                
                org.bukkit.util.Vector direction = player.getEyeLocation().getDirection();
                org.bukkit.Location spawnLoc = player.getEyeLocation().add(direction.clone().multiply(1.2));
                
                org.bukkit.entity.Arrow arrow = spawnLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.Arrow.class);
                arrow.setShooter(player);
                arrow.setVelocity(direction.multiply(2.5));
                arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                
                if (item.getType() == org.bukkit.Material.CROSSBOW) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f);
                    if (bowLevel == 3) {
                        arrow.setPierceLevel(4);
                    }
                } else {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
                }
            }
            return;
        }

        if (!item.hasItemMeta()) return;

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

    @EventHandler
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        org.bukkit.entity.Entity victim = event.getEntity();
        NamespacedKey tdKey = new NamespacedKey(plugin, "td_mob");
        
        // Only run checks for custom TD Mobs
        if (!victim.getPersistentDataContainer().has(tdKey, PersistentDataType.BYTE)) {
            return;
        }

        // Determine the player responsible for the attack (melee or ranged/potions)
        org.bukkit.entity.Player attacker = null;
        org.bukkit.entity.Entity damager = event.getDamager();

        if (damager instanceof org.bukkit.entity.Player p) {
            attacker = p;
        } else if (damager instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof org.bukkit.entity.Player p) {
                attacker = p;
            }
        } else if (damager instanceof org.bukkit.entity.AreaEffectCloud cloud) {
            if (cloud.getSource() instanceof org.bukkit.entity.Player p) {
                attacker = p;
            }
        } else if (damager instanceof org.bukkit.entity.ThrownPotion potion) {
            if (potion.getShooter() instanceof org.bukkit.entity.Player p) {
                attacker = p;
            }
        }

        if (attacker != null) {
            NamespacedKey arenaKey = new NamespacedKey(plugin, "td_arena");
            String mobArena = victim.getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
            if (mobArena == null) mobArena = "1";

            String playerArena = plugin.getGameManager().getPlayerArena(attacker.getUniqueId());

            // Attack is cancelled if player is trying to hit a mob on the opponent's track
            if (!mobArena.equals(playerArena)) {
                event.setCancelled(true);
            }
        }
    }
}


