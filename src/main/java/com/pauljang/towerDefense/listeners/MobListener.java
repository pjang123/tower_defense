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
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        String worldName = event.getEntity().getWorld().getName();
        
        // Disable natural/unwanted mob spawning in game and lobby worlds
        if (worldName.equals("game_world") || worldName.equals("lobby_world")) {
            if (reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
                return;
            }
        }

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
        if (event.isCancelled()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return;

        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (mob.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            // Check for fire-related damage if the mob has fire immunity
            NamespacedKey fireImmuneKey = new NamespacedKey(plugin, "td_fire_immune");
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (mob.getPersistentDataContainer().has(fireImmuneKey, PersistentDataType.BYTE)) {
                if (cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                    cause == EntityDamageEvent.DamageCause.LAVA ||
                    cause == EntityDamageEvent.DamageCause.HOT_FLOOR ||
                    cause == EntityDamageEvent.DamageCause.MELTING) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Custom fire tick damage scaling
            if (cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
                NamespacedKey fireDmgKey = new NamespacedKey(plugin, "td_fire_damage");
                if (mob.getPersistentDataContainer().has(fireDmgKey, PersistentDataType.DOUBLE)) {
                    double customDmg = mob.getPersistentDataContainer().get(fireDmgKey, PersistentDataType.DOUBLE);
                    event.setDamage(customDmg);
                }
            }

            // Custom poison tick damage scaling
            if (cause == EntityDamageEvent.DamageCause.POISON) {
                NamespacedKey poisonDmgKey = new NamespacedKey(plugin, "td_poison_damage");
                if (mob.getPersistentDataContainer().has(poisonDmgKey, PersistentDataType.DOUBLE)) {
                    double customDmg = mob.getPersistentDataContainer().get(poisonDmgKey, PersistentDataType.DOUBLE);
                    event.setDamage(customDmg);
                }
            }

            // Slime / Magma Cube Shrinking on lethal damage (3 sizes: largest 4, medium 2, smallest 1)
            double damage = event.getFinalDamage();
            double health = mob.getHealth();
            if (health - damage <= 0) {
                if (mob instanceof org.bukkit.entity.Slime slime) {
                    int currentSize = slime.getSize();
                    if (currentSize > 1) {
                        int nextSize = currentSize > 2 ? 2 : 1;
                        
                        // Capture existing attribute base values before shrinking, as setSize recalculates them
                        double speedVal = 0.1;
                        org.bukkit.attribute.AttributeInstance speedAttr = mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                        if (speedAttr != null) speedVal = speedAttr.getBaseValue();

                        double kbVal = 1.0;
                        org.bukkit.attribute.AttributeInstance kbAttr = mob.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
                        if (kbAttr != null) kbVal = kbAttr.getBaseValue();

                        double armorVal = 0.0;
                        org.bukkit.attribute.AttributeInstance armorAttr = mob.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                        if (armorAttr != null) armorVal = armorAttr.getBaseValue();

                        double stepVal = 0.6;
                        org.bukkit.attribute.AttributeInstance stepAttr = mob.getAttribute(org.bukkit.attribute.Attribute.STEP_HEIGHT);
                        if (stepAttr != null) stepVal = stepAttr.getBaseValue();

                        // Cancel the lethal damage
                        event.setCancelled(true);
                        
                        // Shrink the slime
                        slime.setSize(nextSize);
                        
                        // Restore captured attribute base values
                        if (speedAttr != null) speedAttr.setBaseValue(speedVal);
                        if (kbAttr != null) kbAttr.setBaseValue(kbVal);
                        if (armorAttr != null) armorAttr.setBaseValue(armorVal);
                        if (stepAttr != null) stepAttr.setBaseValue(stepVal);
                        
                        // Recalculate and set the new max health based on the original config health
                        String presetKey = mob.getPersistentDataContainer().get(
                            new NamespacedKey(plugin, "td_preset"),
                            PersistentDataType.STRING
                        );
                        if (presetKey == null) presetKey = mob.getType().name().toLowerCase();
                        
                        double baseMaxHealth = plugin.getConfig().getDouble("mobs." + presetKey + ".health", 50.0);
                        double multiplier = nextSize == 2 ? 0.5 : 0.25;
                        double newMaxHealth = baseMaxHealth * multiplier;
                        
                        org.bukkit.attribute.AttributeInstance maxHealthAttr = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                        if (maxHealthAttr != null) {
                            maxHealthAttr.setBaseValue(newMaxHealth);
                            mob.setHealth(newMaxHealth);
                        }
                        
                        // Play damage hurt flash and sound
                        mob.playEffect(org.bukkit.EntityEffect.HURT);
                        org.bukkit.Sound hurtSound = (mob instanceof org.bukkit.entity.MagmaCube) 
                            ? org.bukkit.Sound.ENTITY_MAGMA_CUBE_HURT 
                            : org.bukkit.Sound.ENTITY_SLIME_HURT;
                        mob.getWorld().playSound(mob.getLocation(), hurtSound, 1.0f, 1.0f);
                        
                        // Play a shrinking sound and particles
                        mob.getWorld().playSound(mob.getLocation(), org.bukkit.Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.8f);
                        mob.getWorld().spawnParticle(org.bukkit.Particle.POOF, mob.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
                        
                        // Update health bar immediately
                        plugin.getMobManager().updateHealthBar(mob);
                        return;
                    }
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
    public void onMobTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        // TD mobs (notably Endermen) now keep their AI enabled so they can be driven along the
        // track. Vanilla Endermen teleport away when damaged, which would break the path flow, so
        // cancel any self-initiated teleport for entities tagged as TD mobs.
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (event.getEntity().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
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

            // Award experience only to the player who sent the mob (on the opposite track)
            NamespacedKey xpRewardKey = new NamespacedKey(plugin, "td_xp_reward");
            if (entity.getPersistentDataContainer().has(xpRewardKey, PersistentDataType.INTEGER)) {
                int xpReward = entity.getPersistentDataContainer().get(xpRewardKey, PersistentDataType.INTEGER);
                String senderArena = mobArena.equals("1") ? "2" : "1";
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameManager().getPlayerArena(player.getUniqueId()).equals(senderArena)) {
                        plugin.getGameManager().addExp(player.getUniqueId(), xpReward);
                    }
                }
            }

            // Slime / Magma Cube Split Handling
            if (entity instanceof org.bukkit.entity.Slime || entity instanceof org.bukkit.entity.MagmaCube) {
                final Location deathLoc = entity.getLocation();
                final String arena = mobArena;
                String parentWpId = "0";
                for (com.pauljang.towerDefense.entities.TDMob tdMob : plugin.getMobManager().getActiveMobs()) {
                    if (tdMob.getEntity().equals(entity)) {
                        parentWpId = tdMob.getCurrentWaypointId();
                        break;
                    }
                }
                final String wpId = parentWpId;
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
                                
                                java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(arena);
                                com.pauljang.towerDefense.entities.TDMob childTDMob = new com.pauljang.towerDefense.entities.TDMob(child, graph);
                                childTDMob.setCurrentWaypointId(wpId);
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
            return;
        }

        // Prevent Tower Ghasts and Golems from targeting players
        if (entity instanceof org.bukkit.entity.HappyGhast || entity instanceof org.bukkit.entity.Ghast || entity instanceof org.bukkit.entity.IronGolem) {
            for (com.pauljang.towerDefense.towers.Tower tower : plugin.getTowerManager().getPlacedTowers().values()) {
                if (entity.equals(tower.getSpawnedGhast()) || entity.equals(tower.getSpawnedGolem())) {
                    if (event.getTarget() instanceof org.bukkit.entity.Player) {
                        event.setCancelled(true);
                        event.setTarget(null);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        
        org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
        org.bukkit.Location lobbySpawn = lobbyWorld != null ? lobbyWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        player.teleport(lobbySpawn);

        if (plugin.getGameManager().getBossBar() != null) {
            plugin.getGameManager().getBossBar().addPlayer(player);
        }
        com.pauljang.towerDefense.core.GameState state = plugin.getGameManager().getCurrentState();
        if (state == com.pauljang.towerDefense.core.GameState.ACTIVE || state == com.pauljang.towerDefense.core.GameState.STARTING) {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(true);
            if (state == com.pauljang.towerDefense.core.GameState.ACTIVE) {
                plugin.getGameManager().giveStarterWeapons(player);
            }
        } else if (state == com.pauljang.towerDefense.core.GameState.LOBBY) {
            plugin.getGameManager().giveLobbyItems(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(true);
        }
        plugin.getGameManager().updateTabNames();
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        if (plugin.getGameManager().getCurrentState() == com.pauljang.towerDefense.core.GameState.ACTIVE) {
            String arena = plugin.getGameManager().getPlayerArena(player.getUniqueId());
            java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
            if (waypoints != null && !waypoints.isEmpty()) {
                event.setRespawnLocation(waypoints.get(0).clone().add(0, 1, 0));
            } else {
                org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
                if (gameWorld != null) {
                    event.setRespawnLocation(gameWorld.getSpawnLocation());
                }
            }
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getGameManager().getCurrentState() == com.pauljang.towerDefense.core.GameState.ACTIVE) {
                    plugin.getGameManager().giveStarterWeapons(player);
                }
            }, 1L);
        } else {
            org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
            org.bukkit.Location lobbySpawn = lobbyWorld != null ? lobbyWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
            event.setRespawnLocation(lobbySpawn);
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (isLobbyCompass(event.getCurrentItem()) || isLobbyCompass(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot < 9) {
                org.bukkit.inventory.ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(hotbarSlot);
                if (isLobbyCompass(hotbarItem)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Block offhand slot interactions in player's own inventory only
        if (event.getClickedInventory() instanceof org.bukkit.inventory.PlayerInventory) {
            if (event.getSlot() == 40 || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
                return;
            }
        }

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
        if (title.equals(org.bukkit.ChatColor.DARK_BLUE + "Open Games")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            int slot = event.getRawSlot();
            if (slot == 13) {
                player.closeInventory();
                plugin.getGameManager().toggleQueue(player);
            }
            return;
        }

        if (title.equals(org.bukkit.ChatColor.DARK_RED + "TD Mob Spawner")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            int slot = event.getRawSlot();
            String chainName = plugin.getMobManager().getChainForSlot(slot);

            if (chainName != null) {
                org.bukkit.event.inventory.ClickType clickType = event.getClick();
                if (event.isLeftClick()) {
                    // Open tier selection sub-GUI
                    plugin.getMobManager().openMobTierGUI(player, chainName);
                } else if (event.isRightClick()) {
                    java.util.Map<String, Integer> queue = plugin.getMobManager().getQueue(player.getUniqueId());
                    int count = queue.getOrDefault(chainName, 0);
                    int toRemove = (clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT)
                            ? Math.min(10, count) : Math.min(1, count);
                    if (toRemove > 0) {
                        for (int i = 0; i < toRemove; i++) {
                            plugin.getMobManager().removeFromQueue(player.getUniqueId(), chainName);
                        }
                        int currentTier = plugin.getGameManager().getMobTier(player.getUniqueId(), chainName);
                        com.pauljang.towerDefense.entities.MobStateProfile profile =
                                plugin.getMobManager().getUpgradeRegistry().getProfile(chainName, currentTier);
                        if (profile != null) {
                            int refund = (int) profile.getPrice() * toRemove;
                            plugin.getGameManager().addGold(player.getUniqueId(), refund, true);
                        }
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.7f);
                        plugin.getMobManager().openMobSpawnerGUI(player);
                    }
                }
                return;
            }

            if (slot == 38) { // Clear Queue and refund
                java.util.Map<String, Integer> queue = plugin.getMobManager().getQueue(player.getUniqueId());
                int totalRefund = 0;
                for (java.util.Map.Entry<String, Integer> entry : queue.entrySet()) {
                    if (entry.getValue() <= 0) continue;
                    int tier = plugin.getGameManager().getMobTier(player.getUniqueId(), entry.getKey());
                    com.pauljang.towerDefense.entities.MobStateProfile profile =
                            plugin.getMobManager().getUpgradeRegistry().getProfile(entry.getKey(), tier);
                    if (profile != null) totalRefund += (int) profile.getPrice() * entry.getValue();
                }
                plugin.getMobManager().clearQueue(player.getUniqueId());
                if (totalRefund > 0) plugin.getGameManager().addGold(player.getUniqueId(), totalRefund);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
                player.sendMessage(org.bukkit.ChatColor.RED + "Queue cleared. Refunded " + totalRefund + " Gold.");
                plugin.getMobManager().openMobSpawnerGUI(player);
            } else if (slot == 40) { // Send Wave
                plugin.getMobManager().sendQueue(player.getUniqueId());
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
                player.sendMessage(org.bukkit.ChatColor.GREEN + "Sending the mob wave!");
            } else if (slot == 42) { // Open upgrades screen
                player.closeInventory();
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getGameManager().openUpgradesGUI(player);
                }, 1L);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
        } else if (title.startsWith(org.bukkit.ChatColor.DARK_PURPLE + "Mob Tier: ")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            // Extract chain key (lowercase) from title
            String rawName = title.substring((org.bukkit.ChatColor.DARK_PURPLE + "Mob Tier: ").length());
            String chainKey = rawName.toLowerCase();

            int slot = event.getRawSlot();

            if (slot >= 10 && slot <= 14) {
                int selectedTier = slot - 9; // slot 10 = tier 1, 11 = tier 2, …, 14 = tier 5
                com.pauljang.towerDefense.entities.MobStateProfile profile =
                        plugin.getMobManager().getUpgradeRegistry().getProfile(chainKey, selectedTier);
                if (profile == null) return;

                // If tier is not yet unlocked, spend EXP to unlock it first
                if (!plugin.getGameManager().isTierUnlocked(player.getUniqueId(), chainKey, selectedTier)) {
                    int xpCost = com.pauljang.towerDefense.entities.MobManager.getTierUnlockCost(selectedTier);
                    if (plugin.getGameManager().getExp(player.getUniqueId()) >= xpCost) {
                        plugin.getGameManager().removeExp(player.getUniqueId(), xpCost);
                        plugin.getGameManager().unlockTier(player.getUniqueId(), chainKey, selectedTier);
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "Unlocked "
                                + plugin.getMobManager().getChainDisplayName(chainKey)
                                + " Tier " + selectedTier + "! (" + xpCost + " EXP spent)");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Not enough EXP to unlock Tier " + selectedTier
                                + "! Need " + xpCost + " EXP, you have "
                                + plugin.getGameManager().getExp(player.getUniqueId()) + ".");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                        plugin.getMobManager().openMobTierGUI(player, chainKey);
                        return;
                    }
                }

                int qty = (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) ? 5 : 1;
                int totalCost = (int) profile.getPrice() * qty;

                if (plugin.getGameManager().removeGold(player.getUniqueId(), totalCost)) {
                    plugin.getGameManager().setMobTier(player.getUniqueId(), chainKey, selectedTier);
                    for (int i = 0; i < qty; i++) {
                        plugin.getMobManager().addToQueue(player.getUniqueId(), chainKey);
                    }
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "Queued " + qty + "x "
                            + plugin.getMobManager().getChainDisplayName(chainKey)
                            + " Tier " + selectedTier + " for " + totalCost + " Gold.");
                } else {
                    player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Gold! Requires " + totalCost + " Gold.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                }
                plugin.getMobManager().openMobTierGUI(player, chainKey);
                return;
            }

            if (slot == 22) { // Back button
                player.closeInventory();
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getMobManager().openMobSpawnerGUI(player);
                }, 1L);
            }
        } else if (title.startsWith(org.bukkit.ChatColor.DARK_BLUE + "Buy Tower: ")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

            if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) return;

            String plotId = title.substring((org.bukkit.ChatColor.DARK_BLUE + "Buy Tower: ").length());
            int slot = event.getRawSlot();

            com.pauljang.towerDefense.towers.TowerType type = null;
            switch (slot) {
                case 10 -> type = com.pauljang.towerDefense.towers.TowerType.ARCHER;
                case 11 -> type = com.pauljang.towerDefense.towers.TowerType.FIRE;
                case 12 -> type = com.pauljang.towerDefense.towers.TowerType.PRISMARINE;
                case 13 -> type = com.pauljang.towerDefense.towers.TowerType.CHORUS;
                case 14 -> type = com.pauljang.towerDefense.towers.TowerType.REDSTONE;
                case 15 -> type = com.pauljang.towerDefense.towers.TowerType.POISON;
                case 16 -> type = com.pauljang.towerDefense.towers.TowerType.ICE;
                case 19 -> type = com.pauljang.towerDefense.towers.TowerType.GOLEM;
                case 20 -> type = com.pauljang.towerDefense.towers.TowerType.HAPPY_GHAST;
            }

            if (type != null) {
                org.bukkit.Location[] bounds = plugin.getPlotConfigManager().getPlotBounds(plotId);
                int plotSize = 3;
                if (bounds != null) {
                    int plotWidth = Math.abs(bounds[1].getBlockX() - bounds[0].getBlockX()) + 1;
                    int plotLength = Math.abs(bounds[1].getBlockZ() - bounds[0].getBlockZ()) + 1;
                    plotSize = Math.max(plotWidth, plotLength);
                }

                boolean is5x5Tower = (type == com.pauljang.towerDefense.towers.TowerType.GOLEM || 
                                     type == com.pauljang.towerDefense.towers.TowerType.HAPPY_GHAST);
                if (is5x5Tower && plotSize < 5) {
                    player.sendMessage(org.bukkit.ChatColor.RED + "You cannot place a 5x5 tower (" + type.getDisplayName() + ") on a 3x3 plot!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    player.closeInventory();
                    return;
                }

                String nameKey = type.name().toLowerCase() + "_1";
                int cost = plugin.getConfig().getInt("towers." + nameKey + ".cost", type.getCost());
                if (plugin.getGameManager().removeGold(player.getUniqueId(), cost)) {
                    plugin.getTowerManager().placeTower(plotId, type, player.getUniqueId());
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
                case 15 -> { // Return Ghast to Tower
                    if (tower.getType() == com.pauljang.towerDefense.towers.TowerType.HAPPY_GHAST) {
                        org.bukkit.entity.HappyGhast ghast = tower.getSpawnedGhast();
                        if (ghast != null && ghast.isValid()) {
                            new java.util.ArrayList<>(ghast.getPassengers()).forEach(p -> ghast.removePassenger(p));
                            double returnHeight = tower.getStructureSize() != null ? tower.getStructureSize().getBlockY() + 1.0 : 5.0;
                            org.bukkit.Location returnLoc = tower.getCenterLocation().clone().add(0, returnHeight, 0);
                            ghast.teleport(returnLoc);
                            ghast.setAI(false);
                            ghast.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                            tower.setAutopilot(true);
                            player.sendMessage(org.bukkit.ChatColor.GREEN + "Happy Ghast returned to its post!");
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
                        } else {
                            player.sendMessage(org.bukkit.ChatColor.RED + "Happy Ghast not found!");
                        }
                        plugin.getTowerManager().openManageTowerGUI(player, plotId);
                    }
                }
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
                        if (tower.getType() == com.pauljang.towerDefense.towers.TowerType.REDSTONE) {
                            String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                            plugin.getTowerManager().updateAllTowerHologramsInArena(arena);
                        }
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
                    if (tower.getType() == com.pauljang.towerDefense.towers.TowerType.REDSTONE) {
                        return;
                    }
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
                    player.closeInventory();
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        plugin.getMobManager().openMobSpawnerGUI(player);
                    }, 1L);
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

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onCompassInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item == null) return;

        // Compass click handling
        if (item.getType() == org.bukkit.Material.COMPASS && item.hasItemMeta()) {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
            org.bukkit.event.block.Action action = event.getAction();
            String worldName = player.getWorld().getName();
            
            // Lobby World Behavior: ONLY right-click to open games GUI (no teleport forward)
            if (worldName.equals("lobby_world")) {
                event.setCancelled(true);
                if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                    plugin.getGameManager().openGamesGUI(player);
                }
                return;
            }
            
            // Game World Behavior (before active match)
            if (worldName.equals("game_world") && plugin.getGameManager().getCurrentState() != com.pauljang.towerDefense.core.GameState.ACTIVE) {
                // RIGHT-CLICK: Teleport player forward 8 blocks horizontally
                if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    
                    Location start = player.getLocation().add(0, 0.5, 0); // start slightly above feet to avoid ground collision
                    double yawRad = Math.toRadians(player.getLocation().getYaw());
                    double dx = -Math.sin(yawRad);
                    double dz = Math.cos(yawRad);
                    org.bukkit.util.Vector dir = new org.bukkit.util.Vector(dx, 0.0, dz).normalize();
                    
                    double distance = 8.0;
                    org.bukkit.util.RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(start, dir, distance, org.bukkit.FluidCollisionMode.NEVER, true);
                    Location targetLoc;
                    if (rayTrace != null && rayTrace.getHitBlock() != null) {
                        targetLoc = rayTrace.getHitPosition().toLocation(player.getWorld()).subtract(dir.multiply(0.5));
                    } else {
                        targetLoc = start.clone().add(dir.multiply(distance));
                    }
                    targetLoc.subtract(0, 0.5, 0); // adjust back to feet level
                    targetLoc.setYaw(player.getLocation().getYaw());
                    targetLoc.setPitch(player.getLocation().getPitch());

                    // Ensure target location is safe from clipping
                    if (targetLoc.getBlock().getType().isSolid() || targetLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                        int attempts = 0;
                        while ((targetLoc.getBlock().getType().isSolid() || targetLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) && attempts < 5) {
                            targetLoc.add(0, 1, 0);
                            attempts++;
                        }
                    }
                    
                    player.teleport(targetLoc);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, player.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                    return;
                }
                
                // LEFT-CLICK: Return to lobby
                if (action == org.bukkit.event.block.Action.LEFT_CLICK_AIR || action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
                    if (lobbyWorld != null) {
                        player.teleport(lobbyWorld.getSpawnLocation());
                        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Returned to the lobby.");
                    }
                    return;
                }
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
        if (isLobbyCompass(item)) {
            event.setCancelled(true);
            return;
        }
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

        // Prevent players from hurting each other (PvP prevention)
        if (victim instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Entity damager = event.getDamager();
            org.bukkit.entity.Player attacker = null;

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
                event.setCancelled(true);
                return;
            }
        }

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

    @EventHandler
    public void onSlimeSplit(org.bukkit.event.entity.SlimeSplitEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        plugin.getGameManager().handlePlayerDisconnect(event.getPlayer());
        org.bukkit.entity.Player player = event.getPlayer();
        if (player.getWorld().getName().equals("game_world")) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getGameManager().checkGameStopConditions();
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerKick(org.bukkit.event.player.PlayerKickEvent event) {
        plugin.getGameManager().handlePlayerDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        org.bukkit.entity.Entity clicked = event.getRightClicked();

        // Check if we are in LOBBY state
        if (plugin.getGameManager().getCurrentState() != com.pauljang.towerDefense.core.GameState.LOBBY) {
            return;
        }

        // Check if player clicked a lobby NPC (e.g. Villager or ArmorStand)
        if (clicked instanceof org.bukkit.entity.Villager || clicked instanceof org.bukkit.entity.ArmorStand) {
            // Check if they are in the lobby world
            if (clicked.getWorld().getName().equals("lobby_world")) {
                event.setCancelled(true);
                plugin.getGameManager().toggleQueue(player);
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            String worldName = player.getWorld().getName();
            if (worldName.equals("lobby_world")) {
                event.setCancelled(true);
                if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
                    org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
                    if (lobbyWorld != null) {
                        player.teleport(lobbyWorld.getSpawnLocation());
                    }
                }
            } else if (worldName.equals("game_world")) {
                event.setCancelled(true);
                if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
                    String arena = plugin.getGameManager().getPlayerArena(player.getUniqueId());
                    java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
                    if (waypoints != null && !waypoints.isEmpty()) {
                        player.teleport(waypoints.get(0).clone().add(0, 1, 0));
                    } else {
                        player.teleport(player.getWorld().getSpawnLocation());
                    }
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerRideGhast(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        org.bukkit.entity.Entity clicked = event.getRightClicked();
        if (clicked instanceof org.bukkit.entity.HappyGhast ghast) {
            for (com.pauljang.towerDefense.towers.Tower tower : plugin.getTowerManager().getPlacedTowers().values()) {
                if (ghast.equals(tower.getSpawnedGhast())) {
                    event.setCancelled(true);
                    
                    String playerArena = plugin.getGameManager().getPlayerArena(event.getPlayer().getUniqueId());
                    String plotArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                    if (!playerArena.equals(plotArena)) {
                        event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "You cannot ride the opponent's Ghast!");
                        return;
                    }

                    // Only the tower owner can mount the Happy Ghast
                    java.util.UUID ownerId = tower.getOwnerId();
                    if (ownerId != null && !ownerId.equals(event.getPlayer().getUniqueId())) {
                        event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "Only the player who placed this tower can ride the Happy Ghast!");
                        return;
                    }

                    ghast.addPassenger(event.getPlayer());
                    ghast.setAI(true);
                    event.getPlayer().sendMessage(org.bukkit.ChatColor.GREEN + "You are now riding the Happy Ghast! Look to steer. Left-click to shoot!");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerLeftClick(org.bukkit.event.player.PlayerAnimationEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        if (player.getVehicle() instanceof org.bukkit.entity.HappyGhast ghast) {
            for (com.pauljang.towerDefense.towers.Tower tower : plugin.getTowerManager().getPlacedTowers().values()) {
                if (ghast.equals(tower.getSpawnedGhast())) {
                    if (player.hasCooldown(org.bukkit.Material.COMPASS)) {
                        return;
                    }
                    
                    int cooldownTicks = 50 - (tower.getLevel() - 1) * 10; // Tier 1: 50, Tier 2: 40, Tier 3: 30
                    player.setCooldown(org.bukkit.Material.COMPASS, cooldownTicks);
                    
                    org.bukkit.util.Vector dir = player.getEyeLocation().getDirection();
                    org.bukkit.Location spawnLoc = ghast.getLocation().add(dir.clone().multiply(3.0));
                    
                    org.bukkit.entity.LargeFireball fireball = spawnLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.LargeFireball.class, fb -> {
                        fb.setShooter(player);
                        fb.setDirection(dir);
                        fb.setIsIncendiary(false);
                        fb.setYield(0.0f);
                        fb.setMetadata("td_happy_fireball", new org.bukkit.metadata.FixedMetadataValue(plugin, tower));
                    });
                    
                    ghast.getWorld().playSound(ghast.getLocation(), org.bukkit.Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();
        
        if (toWorld.equals("game_world")) {
            if (plugin.getGameManager().getCurrentState() == com.pauljang.towerDefense.core.GameState.LOBBY) {
                plugin.getGameManager().giveLobbyItems(player);
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.setAllowFlight(true);
            }
            plugin.getGameManager().checkGameStartConditions();
        } else if (toWorld.equals("lobby_world")) {
            plugin.getGameManager().giveLobbyItems(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(true);
        }
        
        if (fromWorld.equals("game_world")) {
            plugin.getGameManager().checkGameStopConditions();
        }
        plugin.getGameManager().updateTabNames();
    }

    @EventHandler
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventorySlots().contains(40) || event.getRawSlots().contains(45)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.LargeFireball fireball) {
            if (fireball.hasMetadata("td_happy_fireball")) {
                if (event.getHitEntity() instanceof org.bukkit.entity.Player) {
                    event.setCancelled(true);
                    return;
                }
                java.util.List<org.bukkit.metadata.MetadataValue> metadata = fireball.getMetadata("td_happy_fireball");
                if (!metadata.isEmpty()) {
                    org.bukkit.metadata.MetadataValue val = metadata.get(0);
                    if (val.value() instanceof com.pauljang.towerDefense.towers.Tower tower) {
                        Location impactLoc = fireball.getLocation();
                        String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                        double damage = tower.getDamage();
                        
                        // Spawn custom explosion effect (non-destructive)
                        impactLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, impactLoc, 1);
                        impactLoc.getWorld().playSound(impactLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                        
                        // Damage mobs in 5.0 block radius
                        java.util.List<org.bukkit.entity.Mob> targets = plugin.getTowerManager().getMobsInRadius(impactLoc, 5.0, towerArena);
                        for (org.bukkit.entity.Mob mob : targets) {
                            mob.damage(damage);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getDismounted() instanceof org.bukkit.entity.HappyGhast ghast) {
            ghast.setAI(false);
            ghast.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            // Teleport back to the same spot 2 ticks later to counteract any drift from dismount physics
            org.bukkit.Location freezeLoc = ghast.getLocation().clone();
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (ghast.isValid()) {
                    ghast.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    ghast.teleport(freezeLoc);
                }
            }, 2L);
        }
    }

    @EventHandler
    public void onBlockFade(org.bukkit.event.block.BlockFadeEvent event) {
        org.bukkit.Material type = event.getBlock().getType();
        if (type == org.bukkit.Material.ICE || type == org.bukkit.Material.FROSTED_ICE || type == org.bukkit.Material.SNOW) {
            event.setCancelled(true);
        }
    }

    private boolean isLobbyCompass(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String displayName = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        return displayName.equals("Game Selector") || displayName.equals("Return to Lobby");
    }
}


